package com.dist.cmd;

import com.dist.common.Config;
import com.dist.simplekafka.Server;
import org.apache.log4j.Logger;

import java.io.File;
import java.net.InetAddress;
import java.util.Arrays;

public class BrokerApp {
    private static final Logger logger = Logger.getLogger(BrokerApp.class);
    
    public static void main(String[] args) {
        BrokerApp app = new BrokerApp();
        app.run(args);
    }
    
    public void run(String[] args) {
        validateArguments(args);
        
        String zkAddress = args[0];
        int brokerId = Integer.parseInt(args[1]);
        String hostAddress = args.length > 2 ? args[2] : "localhost";
        
        displayStartupInfo(zkAddress, brokerId, hostAddress);
        
        try {
            Server server = createAndStartServer(zkAddress, brokerId, hostAddress);
            displayServerInfo(server);
            keepServerRunning(server);
            
        } catch (Exception e) {
            handleError(e);
        }
    }
    
    private void validateArguments(String[] args) {
        if (args.length < 2 || args.length > 3) {
            System.out.println("Usage: java BrokerApp <zookeeper-address> <broker-id> [host-address]");
            System.out.println("Example: java BrokerApp localhost:2181 1");
            System.out.println("Example: java BrokerApp localhost:2181 1 127.0.0.1");
            System.out.println("\nThis will start a broker that:");
            System.out.println("  - Registers with ZooKeeper");
            System.out.println("  - Participates in controller election");
            System.out.println("  - Listens for producer/consumer requests");
            System.out.println("  - Handles topic management");
            System.out.println("\nIf host-address is not specified, 'localhost' will be used.");
            System.exit(1);
        }
    }
    
    private void displayStartupInfo(String zkAddress, int brokerId, String hostAddress) {
        System.out.println("=== SimpleKafka Broker Application ===");
        System.out.println("ZooKeeper Address: " + zkAddress);
        System.out.println("Broker ID: " + brokerId);
        System.out.println("Host Address: " + hostAddress);
        System.out.println("Features: Controller Election, Topic Management, Producer/Consumer API");
        System.out.println("=========================================");
    }
    
    private Server createAndStartServer(String zkAddress, int brokerId, String hostAddress) throws Exception {
        int brokerPort = 9092 + brokerId;
        
        Config config = new Config(brokerId, hostAddress, brokerPort, zkAddress, 
                Arrays.asList("/tmp/broker-" + brokerId));
        
        // Create log directories
        createLogDirectories(config);
        
        System.out.println("Creating server with config: " + config.getBrokerId() + 
                ":" + config.getHostName() + ":" + config.getPort());
        
        Server server = Server.create(config);
        server.startup();
        
        System.out.println("✓ Broker " + brokerId + " started successfully!");
        return server;
    }
    
    private void createLogDirectories(Config config) {
        for (String logDir : config.getLogDirs()) {
            File dir = new File(logDir);
            if (!dir.exists()) {
                if (dir.mkdirs()) {
                    System.out.println("✓ Created log directory: " + dir.getAbsolutePath());
                } else {
                    System.err.println("⚠ Warning: Failed to create log directory: " + dir.getAbsolutePath());
                }
            } else {
                System.out.println("✓ Log directory already exists: " + dir.getAbsolutePath());
            }
        }
    }
    
    private void displayServerInfo(Server server) {
        System.out.println("\n=== BROKER STATUS ===");
        System.out.println("Broker ID: " + server.getConfig().getBrokerId());
        System.out.println("Host: " + server.getConfig().getHostName());
        System.out.println("Port: " + server.getConfig().getPort());
        System.out.println("Is Controller: " + (server.isController() ? "Yes" : "No"));
        System.out.println("Alive Brokers: " + server.aliveBrokers());
        System.out.println("====================\n");
    }
    
    private void keepServerRunning(Server server) {
        System.out.println("Broker is running and listening for requests...");
        System.out.println("Press Ctrl+C to shutdown gracefully.");
        System.out.println("\nTo test the broker:");
        System.out.println("1. Start multiple brokers: java BrokerApp localhost:2181 2");
        System.out.println("2. Create topics: java TopicCommandApp localhost:2181 createTopic test-topic 3 2");
        System.out.println("3. Use producer/consumer: java ProducerApp localhost:2181 9093");
        System.out.println("4. Use consumer: java ConsumerApp localhost:2181 9093");
        
        // Add shutdown hook for graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down broker " + server.getConfig().getBrokerId() + "...");
            server.shutdown();
            System.out.println("✓ Broker shutdown complete.");
        }));
        
        // Keep the main thread alive
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            System.out.println("Broker interrupted, shutting down...");
            server.shutdown();
        }
    }
    
    private void handleError(Exception e) {
        System.err.println("Error starting broker: " + e.getMessage());
        e.printStackTrace();
        System.exit(1);
    }
} 