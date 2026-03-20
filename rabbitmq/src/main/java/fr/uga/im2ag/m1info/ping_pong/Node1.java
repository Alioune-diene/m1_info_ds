package fr.uga.im2ag.m1info.ping_pong;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

/**
 * Node1 — starts the exchange by sending PING (simulates the PUSH button).
 * Then listens on pong_queue: when PONG arrives, replies PING.
 */
public class Node1 {

    private static final String PING_QUEUE = "ping_queue";
    private static final String PONG_QUEUE = "pong_queue";

    public static void main(String[] args) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        factory.setUsername("admin");
        factory.setPassword("admin");

        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        channel.queueDeclare(PING_QUEUE, false, false, false, null);
        channel.queueDeclare(PONG_QUEUE, false, false, false, null);

        // PUSH: send the first PING
        channel.basicPublish("", PING_QUEUE, null, "PING".getBytes());
        System.out.println("[Node1] PUSH -> sent PING");

        // When PONG is received, reply PING
        DeliverCallback onPong = (tag, delivery) -> {
            String msg = new String(delivery.getBody());
            System.out.println("[Node1] received " + msg + " -> sending PING");
            try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            channel.basicPublish("", PING_QUEUE, null, "PING".getBytes());
        };

        channel.basicConsume(PONG_QUEUE, true, onPong, tag -> {});
    }
}
