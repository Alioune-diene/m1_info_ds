package fr.uga.im2ag.m1info.rabbbitmq.ping_pong;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

/**
 * Node2 — listens on ping_queue: when PING arrives, replies PONG.
 */
public class Node2 {

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

        System.out.println("[Node2] waiting for PING...");

        // When PING is received, reply PONG
        DeliverCallback onPing = (tag, delivery) -> {
            String msg = new String(delivery.getBody());
            System.out.println("[Node2] received " + msg + " -> sending PONG");
            try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            channel.basicPublish("", PONG_QUEUE, null, "PONG".getBytes());
        };

        channel.basicConsume(PING_QUEUE, true, onPing, tag -> {});
    }
}
