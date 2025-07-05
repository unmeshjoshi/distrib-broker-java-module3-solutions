package com.dist.cmd;

import com.dist.net.InetAddressAndPort;
import com.dist.simplekafka.SimpleConsumer;
import com.dist.simplekafka.FetchIsolation;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.Map;
import java.util.Scanner;

public class ConsumerApp {
    private static final Logger logger = Logger.getLogger(ConsumerApp.class);
    
    public static void main(String[] args) {
        ConsumerApp app = new ConsumerApp();
        app.run(args);
    }
    
    public void run(String[] args) {
        validateArguments(args);
        
        String zkAddress = args[0];
        int brokerPort = Integer.parseInt(args[1]);
        
        displayStartupInfo(zkAddress, brokerPort);
        
        try {
            InetAddressAndPort bootstrapBroker = InetAddressAndPort.create("localhost", brokerPort);
            SimpleConsumer consumer = new SimpleConsumer(bootstrapBroker);
            
            interactiveConsumer(consumer);
            
        } catch (Exception e) {
            handleError(e);
        }
    }
    
    private void validateArguments(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java ConsumerApp <zookeeper-address> <broker-port>");
            System.out.println("Example: java ConsumerApp localhost:2181 9093");
            System.out.println("\nThis will start an interactive consumer that:");
            System.out.println("  - Connects to the specified broker");
            System.out.println("  - Allows you to consume messages from topics");
            System.out.println("  - Shows message key-value pairs");
            System.exit(1);
        }
    }
    
    private void displayStartupInfo(String zkAddress, int brokerPort) {
        System.out.println("=== SimpleKafka Consumer Application ===");
        System.out.println("ZooKeeper Address: " + zkAddress);
        System.out.println("Broker Port: " + brokerPort);
        System.out.println("Features: Interactive message consumption");
        System.out.println("=========================================");
    }
    
    private void interactiveConsumer(SimpleConsumer consumer) {
        Scanner scanner = new Scanner(System.in);
        
        System.out.println("\nInteractive Consumer Started!");
        System.out.println("Commands:");
        System.out.println("  consume <topic>              - Consume messages from topic");
        System.out.println("  quit                         - Exit the consumer");
        System.out.println("\nExample: consume test-topic");
        
        while (true) {
            System.out.print("\nconsumer> ");
            String input = scanner.nextLine().trim();
            
            if (input.equalsIgnoreCase("quit")) {
                System.out.println("Goodbye!");
                break;
            }
            
            if (input.startsWith("consume ")) {
                handleConsumeCommand(consumer, input);
            } else if (!input.isEmpty()) {
                System.out.println("Unknown command. Use 'consume <topic>' or 'quit'");
            }
        }
        
        scanner.close();
    }
    
    private void handleConsumeCommand(SimpleConsumer consumer, String input) {
        try {
            String[] parts = input.split(" ", 2);
            if (parts.length < 2) {
                System.out.println("Error: consume command requires topic name");
                System.out.println("Usage: consume <topic>");
                return;
            }
            
            String topic = parts[1];
            
            System.out.println("Consuming messages from topic: " + topic);
            Map<String, String> messages = consumer.consume(topic, FetchIsolation.FetchLogEnd);
            
            if (messages.isEmpty()) {
                System.out.println("No messages found in topic: " + topic);
            } else {
                System.out.println("✓ Found " + messages.size() + " messages:");
                System.out.println("----------------------------------------");
                for (Map.Entry<String, String> entry : messages.entrySet()) {
                    System.out.println("Key: " + entry.getKey());
                    System.out.println("Value: " + entry.getValue());
                    System.out.println("----------------------------------------");
                }
            }
            
        } catch (IOException e) {
            System.err.println("Error consuming messages: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
        }
    }
    
    private void handleError(Exception e) {
        System.err.println("Error starting consumer: " + e.getMessage());
        e.printStackTrace();
        System.exit(1);
    }
} 