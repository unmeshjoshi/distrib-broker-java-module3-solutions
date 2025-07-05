# Distributed Broker System Demo

This guide will help you run a complete demo of the distributed Kafka-like broker system with multiple brokers, producers, and consumers.

## Prerequisites

- Docker installed and running
- Java 11 or higher
- Gradle (included in the project)

## Step 1: Start ZooKeeper

First, start ZooKeeper in a Docker container:

```bash
docker run --rm --name zookeeper-demo -p 2181:2181 zookeeper:3.8.1
```

Keep this terminal open and running.

## Step 2: Start Multiple Brokers

Open three new terminal windows/tabs and start three brokers:

**Terminal 1 - Broker 1:**
```bash
./gradlew runBroker -PbrokerId=1
```

**Terminal 2 - Broker 2:**
```bash
./gradlew runBroker -PbrokerId=2
```

**Terminal 3 - Broker 3:**
```bash
./gradlew runBroker -PbrokerId=3
```

Each broker will:
- Connect to ZooKeeper
- Register itself
- Participate in controller election
- Start listening for client connections

## Step 3: Create Topics

Open a new terminal and create topics using the Gradle task:

**Create a topic with 2 partitions and replication factor 3:**
```bash
./gradlew createTopic -PtopicName=demo-topic -Ppartitions=2 -PreplicationFactor=3
```

**Create additional topics:**
```bash
./gradlew createTopic -PtopicName=test-topic -Ppartitions=3 -PreplicationFactor=2
./gradlew createTopic -PtopicName=logs-topic -Ppartitions=1 -PreplicationFactor=3
```

**List topics and brokers:**
```bash
./gradlew listTopics
./gradlew listBrokers
```

## Step 4: Start Producers

Open new terminals to start producers:

**Producer 1:**
```bash
./gradlew runProducer
```

**Producer 2 (optional):**
```bash
./gradlew runProducer
```

In each producer:
1. Enter the topic name (e.g., "demo-topic")
2. Type your messages
3. Press Enter to send each message
4. Type `quit` to exit

## Step 5: Start Consumers

Open new terminals to start consumers:

**Consumer 1:**
```bash
./gradlew runConsumer
```

**Consumer 2 (optional):**
```bash
./gradlew runConsumer
```

In each consumer:
1. Enter the topic name (e.g., "demo-topic")
2. The consumer will start receiving messages
3. Type `quit` to exit

## Demo Workflow

1. **Start ZooKeeper** (Terminal 1)
2. **Start 3 Brokers** (Terminals 2-4)
3. **Create Topics** (Terminal 5)
4. **Start Producers** (Terminals 6-7)
5. **Start Consumers** (Terminals 8-9)

## Expected Behavior

- **Brokers**: Should show connection messages and controller election
- **Topic Creation**: Should show successful topic creation with partition assignments
- **Producers**: Should successfully send messages
- **Consumers**: Should receive messages from producers
- **Load Balancing**: Messages should be distributed across partitions

## Troubleshooting

### ZooKeeper Connection Issues
If you get connection timeout errors:
1. Make sure Docker is running
2. Check if port 2181 is available: `lsof -i :2181`
3. Restart ZooKeeper container if needed

### Broker Issues
- If a broker fails to start, check the logs for specific error messages
- Ensure ZooKeeper is running before starting brokers
- Check if the broker ports (9092, 9093, 9094) are available

### Network Issues
- The system uses "localhost" for local development
- For Docker networking issues, try restarting the ZooKeeper container

## Cleanup

To stop the demo:
1. Stop all applications (Ctrl+C in each terminal)
2. Stop ZooKeeper: `docker stop zookeeper-demo`
3. Remove the container: `docker rm zookeeper-demo`

## Advanced Usage

### Custom Broker Configuration
You can specify custom host and port for brokers:
```bash
./gradlew runBroker -PbrokerId=1 -Phost=localhost -Pport=9092
```

### Custom ZooKeeper Address
If ZooKeeper is running on a different address:
```bash
./gradlew runBroker -PbrokerId=1 -PzkConnect=your-zk-host:2181
```

### Multiple Topics and Partitions
- Create multiple topics with different partition counts
- Test message distribution across partitions
- Verify consumer group behavior

## System Architecture

- **ZooKeeper**: Coordination and metadata storage
- **Brokers**: Message storage and replication
- **Controller**: Leader election and partition management
- **Producers**: Message publishing
- **Consumers**: Message consumption
- **Replication**: Fault tolerance across brokers

This demo showcases a fully functional distributed messaging system similar to Apache Kafka!
