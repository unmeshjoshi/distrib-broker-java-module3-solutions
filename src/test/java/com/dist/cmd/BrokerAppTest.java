package com.dist.cmd;

import com.dist.net.InetAddressAndPort;
import com.dist.simplekafka.FetchIsolation;
import com.dist.simplekafka.SimpleConsumer;
import com.dist.simplekafka.SimpleProducer;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;

public class BrokerAppTest {
    //This is just to demonstrate how you can use SimpleProducer and SimpleConsumer
    // to work with the brokers started as explained in the DEMO_README.md
    @Test @Ignore

    public void testProcudingAndConsumingFromBrokerAppCluster() throws IOException {
        SimpleProducer producer = new SimpleProducer(InetAddressAndPort.create("127.0.0.1",9093));
        SimpleConsumer consumer = new SimpleConsumer(InetAddressAndPort.create("127.0.0.1",9093));

        producer.produce("demo-topic", "key1", "message1");
        producer.produce("demo-topic", "key2", "message2");
        producer.produce("demo-topic", "key3", "message3");

        Map<String, String> messages = consumer.consume("demo-topic", FetchIsolation.FetchLogEnd);
        for (String s : messages.values()) {
            System.out.println(s);
        }


    }
}
