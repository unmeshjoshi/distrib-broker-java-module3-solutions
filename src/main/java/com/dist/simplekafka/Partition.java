package com.dist.simplekafka;

import com.dist.common.Config;
import com.dist.common.JsonSerDes;
import com.dist.net.InetAddressAndPort;
import com.dist.net.RequestKeys;
import com.dist.net.RequestOrResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static com.dist.simplekafka.FetchIsolation.FetchLogEnd;

public class Partition {
    Logger logger = LogManager.getLogger(Partition.class);

    private final Config config;
    private final TopicAndPartition topicAndPartition;
    private final ReentrantLock lock = new ReentrantLock(); //lock for
    // high-watermark updates.
    private final Map<Integer, Long> replicaOffsets = new HashMap<>();
    private long highWatermark = 0L;
    private static final String LogFileSuffix = ".log";
    private final File logFile;
    private final Log log;

    public Partition(Config config, TopicAndPartition topicAndPartition) throws IOException {
        this.config = config;
        this.topicAndPartition = topicAndPartition;
        
        // Create log directory if it doesn't exist
        File logDir = new File(config.getLogDirs().get(0));
        if (!logDir.exists()) {
            if (!logDir.mkdirs()) {
                throw new IOException("Failed to create log directory: " + logDir.getAbsolutePath());
            }
        }
        
        this.logFile = new File(logDir,
                topicAndPartition.topic() + "-" + topicAndPartition.partition() + LogFileSuffix);
        this.log = new Log(logFile);
    }

    public long append(String key, String message) {
        try {
            return log.append(key.getBytes(), message.getBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Log.Message> read(long startOffset, int replicaId,
                                  FetchIsolation isolation) {
        try {
            long endOffset;
            if (isolation == FetchIsolation.FetchHighWatermark) {
                if (startOffset >= highWatermark) {
                    return new ArrayList<>();
                }
                endOffset = highWatermark; // inclusive upper bound
            } else {
                endOffset = log.lastOffset();
            }

            if (startOffset > endOffset) {
                return new ArrayList<>();
            }

            List<Log.Message> messages = log.read(startOffset, endOffset);

            // For replica fetches (positive replicaId) update their progress with the **startOffset**
            // (replica's next fetch position will be startOffset in subsequent request)
            if (replicaId >= 0) {
                updateLastReadOffsetAndHighWaterMark(replicaId, startOffset);
            }
            return messages;

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public File getLogFile() {
        return logFile;
    }

    public void makeFollower(Broker leader) {
        addFetcher(this.topicAndPartition, log.lastOffset(), leader);
    }

    public void makeLeader() {

    }

    public long highWatermark() {
        return highWatermark;
    }

    private final Map<BrokerAndFetcherId, ReplicaFetcherThread> fetcherThreadMap = new HashMap<>();

    public void addFetcher(TopicAndPartition topicAndPartition, long initialOffset, Broker leaderBroker) {
        ReplicaFetcherThread fetcherThread = null;
        BrokerAndFetcherId key = new BrokerAndFetcherId(leaderBroker, getFetcherId(topicAndPartition));

        if (fetcherThreadMap.containsKey(key)) {
            fetcherThread = fetcherThreadMap.get(key);
        } else {
            fetcherThread = createFetcherThread(key.fetcherId, leaderBroker);
            fetcherThreadMap.put(key, fetcherThread);
            fetcherThread.start();
        }

        fetcherThread.addPartition(topicAndPartition, initialOffset);

        logger.info(String.format("Adding fetcher for partition [%s,%d], initOffset %d to broker %d with fetcherId %d",
                topicAndPartition.topic(), topicAndPartition.partition(),
                initialOffset, leaderBroker.id(), key.fetcherId));
    }


    private int getFetcherId(TopicAndPartition topicAndPartition) {
        return (topicAndPartition.topic().hashCode() + 31 * topicAndPartition.partition()); // % numFetchers
    }

    private ReplicaFetcherThread createFetcherThread(int fetcherId, Broker leaderBroker) {
        return new ReplicaFetcherThread(
                String.format("ReplicaFetcherThread-%d-%d", fetcherId,
                        leaderBroker.id()),
                leaderBroker,
                this,
                config
        );
    }

    private Map<Integer, Long> remoteReplicasMap = new HashMap<>();

    /**
     * Updates the last read offset for a replica and potentially updates the high watermark.
     * The high watermark represents the offset up to which all replicas have successfully replicated messages.
     * <p>
     * Log structure visualization:
     * ```
     * Offset: 0     1     2     3     4     5     6     7     8     9
     * [M0]  [M1]  [M2]  [M3]  [M4]  [M5]  [M6]  [M7]  [M8]  [M9]
     * |                   |                             |
     * |                   |                             |
     * First Offset      High Watermark                  Log End Offset
     * ↑                                ↑
     * Consumers can          Producer writes new messages
     * read up to here           at log end offset
     * <p>
     * Replica progress example:
     * Replica 1: Offset 6 (Leader)
     * Replica 2: Offset 4
     * Replica 3: Offset 4
     * → High Watermark = 4 (minimum of all replicas)
     * ```
     * <p>
     * This method is crucial for:
     * 1. Tracking replica progress: Keeps track of how far each replica has progressed in reading/replicating messages
     * 2. Consumer visibility: Only messages up to the high watermark are visible to consumers
     * 3. Ensuring consistency: Helps maintain the consistency guarantee that a message is only readable after it's replicated
     *
     * @param replicaId The ID of the replica reporting its progress
     * @param offset    The last offset that this replica has successfully read/replicated
     */
    public void updateLastReadOffsetAndHighWaterMark(int replicaId, long offset) {
        // Ignore updates coming from consumer reads (replicaId < 0)
        if (replicaId < 0) {
            return;
        }
        lock.lock();
        try {
            remoteReplicasMap.put(replicaId, offset);

            // Re-compute the high watermark as the minimum offset across **all** replicas
            // This ensures that the high watermark always reflects the current slowest replica
            // rather than only monotonically increasing. The tests expect this behaviour.
            long newHighWatermark = Collections.min(remoteReplicasMap.values()) + 1;
            // high watermark should never move past the current log end offset
            newHighWatermark = Math.min(newHighWatermark, log.lastOffset());
            if (highWatermark != newHighWatermark) {
                highWatermark = newHighWatermark;
                System.out.println("Updated highwatermark to " + highWatermark +
                        " for " + this.topicAndPartition + " on " + config.getBrokerId());
            }

        } finally {
            lock.unlock();
        }
    }

    public static class BrokerAndFetcherId {
        private final Broker broker;
        private final int fetcherId;

        public BrokerAndFetcherId(Broker broker, int fetcherId) {
            this.broker = broker;
            this.fetcherId = fetcherId;
        }
    }


    class ReplicaFetcherThread extends Thread {
        private static final Logger logger =
                LogManager.getLogger(ReplicaFetcherThread.class.getName());

        private final String name;
        private final Broker leaderBroker;
        private final Partition partition;
        private final Config config;

        private List<TopicAndPartition> topicPartitions = new ArrayList<>();

        private final AtomicBoolean isRunning = new AtomicBoolean(true);
        private final AtomicInteger correlationId = new AtomicInteger(0);
        private final SocketClient socketClient = new SocketClient();

        public ReplicaFetcherThread(String name, Broker leaderBroker, Partition partition, Config config) {
            this.name = name;
            this.leaderBroker = leaderBroker;
            this.partition = partition;
            this.config = config;
        }

        public void addPartition(TopicAndPartition topicAndPartition, long initialOffset) {
            topicPartitions.add(topicAndPartition);
        }
        
        public void shutdown() {
            isRunning.set(false);
            logger.info("Shutting down ReplicaFetcherThread " + name);
        }

        private int consecutiveFailures = 0;
        private static final int MAX_CONSECUTIVE_FAILURES = 5;
        private static final int BASE_RETRY_DELAY_MS = 1000;
        private static final int MAX_RETRY_DELAY_MS = 30000; // 30 seconds
        private static final int NORMAL_POLL_INTERVAL_MS = 5000; // 5 seconds

        private void doWork() {
            try {
                if (!topicPartitions.isEmpty()) {
                    TopicAndPartition topicPartition = topicPartitions.get(0); // expect only one for now.
                    ConsumeRequest consumeRequest = new ConsumeRequest(topicPartition, FetchLogEnd.toString(),
                            partition.lastOffset() + 1, config.getBrokerId());
                    RequestOrResponse request = new RequestOrResponse(RequestKeys.FetchKey,
                            JsonSerDes.serialize(consumeRequest), correlationId.getAndIncrement());
                    RequestOrResponse response = null;

                    logger.info(String.format("Fetching from leader broker %s:%d for partition %s", 
                            leaderBroker.host(), leaderBroker.port(), topicPartition));
                    
                    response = socketClient.sendReceiveTcp(request,
                            InetAddressAndPort.create(leaderBroker.host(), leaderBroker.port()));

                    ConsumeResponse consumeResponse = JsonSerDes.deserialize(response.getMessageBodyJson(),
                            ConsumeResponse.class);
                    
                    int messageCount = consumeResponse.getMessages().size();
                    if (messageCount > 0) {
                        logger.info(String.format("Replicating %d messages for partition %s in broker %d",
                                messageCount, topicPartition, config.getBrokerId()));
                        for (Map.Entry<String, String> m : consumeResponse.getMessages().entrySet()) {
                            partition.append(m.getKey(), m.getValue());
                        }
                    }
                    
                    // Reset failure count on success
                    consecutiveFailures = 0;
                }
            } catch (IOException e) {
                consecutiveFailures++;
                logger.error(String.format("Failed to connect to leader broker %s:%d for partition %s (attempt %d/%d): %s", 
                        leaderBroker.host(), leaderBroker.port(), 
                        topicPartitions.isEmpty() ? "unknown" : topicPartitions.get(0), 
                        consecutiveFailures, MAX_CONSECUTIVE_FAILURES, e.getMessage()));
                
                // Use exponential backoff with jitter
                int delay = Math.min(BASE_RETRY_DELAY_MS * (1 << Math.min(consecutiveFailures, 10)), MAX_RETRY_DELAY_MS);
                delay += (int)(Math.random() * 1000); // Add jitter
                
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        @Override
        public void run() {
            logger.info("Starting ReplicaFetcherThread " + name);
            try {
                while (isRunning.get()) {
                    try {
                        doWork();
                        // Use longer polling interval for normal operation
                        Thread.sleep(NORMAL_POLL_INTERVAL_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        // Log the error but don't stop the thread
                        logger.error("Error in ReplicaFetcherThread " + name + ": " + e.getMessage());
                        // Use exponential backoff for unexpected errors
                        try {
                            Thread.sleep(Math.min(2000 * (1 << Math.min(consecutiveFailures, 5)), 10000));
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            } catch (Throwable e) {
                if (isRunning.get()) {
                    logger.error("Fatal error in ReplicaFetcherThread " + name + ": " + e.getMessage(), e);
                }
            }
            logger.info("Stopped ReplicaFetcherThread " + name);
        }
    }

    private long lastOffset() {
        return log.lastOffset();
    }
}
