# SimpleKafka Command Applications

This directory contains command-line applications for demonstrating the SimpleKafka distributed messaging system functionality.

## Applications

### 1. BrokerApp
Starts a SimpleKafka broker that registers with ZooKeeper, participates in controller election, and handles producer/consumer requests.

**Usage:**
```bash
# Using Gradle (recommended)
./gradlew runBroker
./gradlew runBroker -PzkAddress=localhost:2181 -PbrokerId=1

# Using Java directly
java BrokerApp <zookeeper-address> <broker-id>
java BrokerApp localhost:2181 1
```

**Features:**
- Registers with ZooKeeper
- Participates in controller election
- Listens for producer/consumer requests
- Handles topic management
- Graceful shutdown with Ctrl+C

### 2. TopicCommandApp
Manages topics in the SimpleKafka cluster.

**Usage:**
```bash
# Using Java directly
java TopicCommandApp <zookeeper-address> <command> [args...]
java TopicCommandApp localhost:2181 createTopic test-topic 3 2
```

**Commands:**
- `createTopic <topic-name> <partitions> <replication-factor>` - Create a new topic
- `listTopics` - List all available topics
- `listBrokers` - List all available brokers

**Convenience Gradle Tasks:**
```bash
# Create a topic
./gradlew createTopic -PtopicName=demo-topic -Ppartitions=3 -PreplicationFactor=2

# List topics
./gradlew listTopics

# List brokers
./gradlew listBrokers
```

### 3. ProducerApp
Interactive producer for sending messages to topics.

**Usage:**
```bash
# Using Gradle (recommended)
./gradlew runProducer -PbrokerPort=9093

# Using Java directly
java ProducerApp <zookeeper-address> <broker-port>
java ProducerApp localhost:2181 9093
```

**Interactive Commands:**
- `send <topic> <key> <message>` - Send a message to a topic
- `quit` - Exit the producer

**Example Session:**
```
producer> send test-topic user1 Hello World!
✓ Message sent successfully!
  Topic: test-topic
  Key: user1
  Message: Hello World!
  Offset: 1

producer> quit
Goodbye!
```

### 4. ConsumerApp
Interactive consumer for reading messages from topics.

**Usage:**
```bash
# Using Gradle (recommended)
./gradlew runConsumer -PbrokerPort=9093

# Using Java directly
java ConsumerApp <zookeeper-address> <broker-port>
java ConsumerApp localhost:2181 9093
```

**Interactive Commands:**
- `consume <topic>` - Consume messages from a topic
- `quit` - Exit the consumer

**Example Session:**
```
consumer> consume test-topic
✓ Found 1 messages:
----------------------------------------
Key: user1
Value: Hello World!
----------------------------------------

consumer> quit
Goodbye!
```

## Demo Workflow

Here's a complete demo workflow to test the SimpleKafka system:

### Step 1: Start ZooKeeper
Make sure ZooKeeper is running on localhost:2181

### Step 2: Start Multiple Brokers
Open multiple terminal windows and start brokers:

**Using Gradle (recommended):**
```bash
# Terminal 1 - Broker 1 (will become controller)
./gradlew runBroker -PbrokerId=1

# Terminal 2 - Broker 2
./gradlew runBroker -PbrokerId=2

# Terminal 3 - Broker 3
./gradlew runBroker -PbrokerId=3
```

**Using Java directly:**
```bash
# Terminal 1 - Broker 1 (will become controller)
java BrokerApp localhost:2181 1

# Terminal 2 - Broker 2
java BrokerApp localhost:2181 2

# Terminal 3 - Broker 3
java BrokerApp localhost:2181 3
```

### Step 3: Create a Topic
```bash
# Using Gradle
./gradlew createTopic -PtopicName=demo-topic -Ppartitions=2 -PreplicationFactor=3

# Using Java directly
java TopicCommandApp localhost:2181 createTopic demo-topic 2 3
```

### Step 4: Start Producer
```bash
# Using Gradle
./gradlew runProducer -PbrokerPort=9093

# Using Java directly
java ProducerApp localhost:2181 9093
```

Send some messages:
```
producer> send demo-topic user1 Hello from producer!
producer> send demo-topic user2 Another message
producer> send demo-topic user3 Final message
```

### Step 5: Start Consumer
```bash
# Using Gradle
./gradlew runConsumer -PbrokerPort=9093

# Using Java directly
java ConsumerApp localhost:2181 9093
```

Consume messages:
```
consumer> consume demo-topic
```

## Gradle Tasks Reference

### Main Tasks
- `./gradlew runBroker` - Start a broker (default: broker ID 1)
- `./gradlew runTopicCommand` - Run topic management commands
- `./gradlew runProducer` - Start interactive producer
- `./gradlew runConsumer` - Start interactive consumer

### Convenience Tasks
- `./gradlew createTopic` - Create a topic with default settings
- `./gradlew listTopics` - List all topics
- `./gradlew listBrokers` - List all brokers
- `./gradlew demoHelp` - Show usage examples

### Task Parameters
- `-PzkAddress=<address>` - ZooKeeper address (default: localhost:2181)
- `-PbrokerId=<id>` - Broker ID (default: 1)
- `-PhostAddress=<address>` - Broker host address (default: localhost)
- `-PbrokerPort=<port>` - Broker port (default: 9093)
- `-Pcommand=<command>` - Topic command (default: listBrokers)
- `-PtopicName=<name>` - Topic name (default: test-topic)
- `-Ppartitions=<num>` - Number of partitions (default: 3)
- `-PreplicationFactor=<num>` - Replication factor (default: 2)

### Examples
```bash
# Start broker with custom settings
./gradlew runBroker -PzkAddress=localhost:2181 -PbrokerId=2

# Create topic with custom settings
./gradlew createTopic -PtopicName=my-topic -Ppartitions=5 -PreplicationFactor=3

# Run producer on specific broker port
./gradlew runProducer -PbrokerPort=9094

# List all available topics
./gradlew listTopics
```

## Port Mapping

Brokers use the following port mapping:
- Broker 1: Port 9093 (9092 + 1)
- Broker 2: Port 9094 (9092 + 2)
- Broker 3: Port 9095 (9092 + 3)

## Features Demonstrated

1. **Distributed Broker Coordination**: Multiple brokers register with ZooKeeper
2. **Controller Election**: One broker becomes the controller
3. **Topic Management**: Create topics with partitions and replication
4. **Producer API**: Send messages with keys to topics
5. **Consumer API**: Read messages from topics
6. **Partitioning**: Messages are distributed across partitions based on key hash
7. **Replication**: Topics can have multiple replicas for fault tolerance

## Troubleshooting

- **ZooKeeper Connection**: Ensure ZooKeeper is running on the specified address
- **Port Conflicts**: Make sure broker ports are not already in use
- **Topic Creation**: Ensure enough brokers are running for the replication factor
- **Message Consumption**: Use FetchLogEnd isolation for reading all messages
- **Gradle Issues**: Run `./gradlew demoHelp` for usage examples
- **Log Directory Issues**: Brokers automatically create log directories in `/tmp/broker-{id}/`. If you see "FileNotFoundException" errors, ensure the broker has write permissions to `/tmp/`
- **Permission Issues**: If log directory creation fails, check file system permissions for the `/tmp/` directory
- **Network Connectivity Issues**: If you see "BindException" or connection errors, try using "localhost" or "127.0.0.1" as the host address. For multi-machine setups, ensure all brokers can reach each other's network addresses.
- **ReplicaFetcherThread Errors**: The system now has improved error handling for replica fetcher threads. Connection errors won't crash the broker, and fetcher threads will retry with exponential backoff.
- **Broker Stability**: Brokers are now more resilient to network errors and won't stop listening due to individual request failures.
- **Network Optimization**: Improved polling frequency (5 seconds instead of 100ms), exponential backoff for failures, and thread pool for connection handling.
- **Socket Management**: Reduced socket timeout to 30 seconds and added TCP_NODELAY for better latency. 