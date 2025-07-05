package com.dist.cmd;

import com.dist.net.InetAddressAndPort;
import com.dist.simplekafka.SimpleProducer;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.Scanner;

public class ProducerApp {
    private static final Logger logger = Logger.getLogger(ProducerApp.class);
    
    public static void main(String[] args) {
        ProducerApp app = new ProducerApp();
        app.run(args);
    }
    
    public void run(String[] args) {
        validateArguments(args);
        
        String zkAddress = args[0];
        int brokerPort = Integer.parseInt(args[1]);
        
        displayStartupInfo(zkAddress, brokerPort);
        
        try {
            InetAddressAndPort bootstrapBroker = InetAddressAndPort.create("localhost", brokerPort);
            SimpleProducer producer = new SimpleProducer(bootstrapBroker);
            
            interactiveProducer(producer);
            
        } catch (Exception e) {
            handleError(e);
        }
    }
    
    private void validateArguments(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java ProducerApp <zookeeper-address> <broker-port>");
            System.out.println("Example: java ProducerApp localhost:2181 9093");
            System.out.println("\nThis will start an interactive producer that:");
            System.out.println("  - Connects to the specified broker");
            System.out.println("  - Allows you to send messages to topics");
            System.out.println("  - Shows message offsets");
            System.exit(1);
        }
    }
    
    private void displayStartupInfo(String zkAddress, int brokerPort) {
        System.out.println("=== SimpleKafka Producer Application ===");
        System.out.println("ZooKeeper Address: " + zkAddress);
        System.out.println("Broker Port: " + brokerPort);
        System.out.println("Features: Interactive message production");
        System.out.println("=========================================");
    }
    
    private void interactiveProducer(SimpleProducer producer) {
        Scanner scanner = new Scanner(System.in);
        
        System.out.println("\nInteractive Producer Started!");
        System.out.println("Commands:");
        System.out.println("  send <topic> <key> <message>  - Send a message");
        System.out.println("  quit                          - Exit the producer");
        System.out.println("\nExample: send test-topic user1 Hello World!");
        
        while (true) {
            System.out.print("\nproducer> ");
            String input = scanner.nextLine().trim();
            
            if (input.equalsIgnoreCase("quit")) {
                System.out.println("Goodbye!");
                break;
            }
            
            if (input.startsWith("send ")) {
                handleSendCommand(producer, input);
            } else if (!input.isEmpty()) {
                System.out.println("Unknown command. Use 'send <topic> <key> <message>' or 'quit'");
            }
        }
        
        scanner.close();
    }
    
    private void handleSendCommand(SimpleProducer producer, String input) {
        try {
            String[] parts = input.split(" ", 4);
            if (parts.length < 4) {
                System.out.println("Error: send command requires topic, key, and message");
                System.out.println("Usage: send <topic> <key> <message>");
                return;
            }
            
            String topic = parts[1];
            String key = parts[2];
            String message = parts[3];
            
            System.out.println("Sending message...");
            long offset = producer.produce(topic, key, message);
            System.out.println("✓ Message sent successfully!");
            System.out.println("  Topic: " + topic);
            System.out.println("  Key: " + key);
            System.out.println("  Message: " + message);
            System.out.println("  Offset: " + offset);
            
        } catch (IOException e) {
            System.err.println("Error sending message: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
        }
    }
    
    private void handleError(Exception e) {
        System.err.println("Error starting producer: " + e.getMessage());
        e.printStackTrace();
        System.exit(1);
    }
} 