package fr.uga.im2ag.m1info.physical;

import com.google.gson.Gson;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PhysicalNode implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(PhysicalNode.class.getName());

    public static final String QUEUE_PREFIX = "physical.node.";

    private final int id;
    private final List<Integer> neighbors;
    private final Gson gson = new Gson();
    private final Connection connection;
    private final Channel channel;

    public PhysicalNode(int id, List<Integer> neighbors, String rabbitHost) throws IOException, TimeoutException {
        this.id = id;
        this.neighbors = List.copyOf(neighbors);

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(rabbitHost);

        this.connection = factory.newConnection("node-" + id);
        this.channel = connection.createChannel();

        channel.queueDeclare(myQueue(), false, false, false, null);

        LOG.info(() -> "Node " + id + " ready - neighbors : " + neighbors + " - queue : " + myQueue());
    }

    public void startListening(BiConsumer<Envelope, Integer> callback) throws IOException {
        DeliverCallback deliverCallback = (tag, delivery) -> {
            String json = new String(delivery.getBody(), StandardCharsets.UTF_8);
            Envelope env = gson.fromJson(json, Envelope.class);
            callback.accept(env, env.getSenderId());
        };
        channel.basicConsume(myQueue(), true, deliverCallback, tag -> {});
        LOG.info(() -> "Node " + id + " started listening on queue " + myQueue());
    }

    public void sendToNeighbor(int neighborId, Envelope env) throws IOException {
        if (!neighbors.contains(neighborId)) { throw new IllegalArgumentException(neighborId + " is not a neighbor of " + id); }
        String json = gson.toJson(env);
        channel.basicPublish("", QUEUE_PREFIX + neighborId, null, json.getBytes(StandardCharsets.UTF_8));
        LOG.fine(() -> String.format("  %d -> %d | %s", id, neighborId, env));
    }

    public void broadcast(Envelope env) {
        for (int n : neighbors) {
            try {
                sendToNeighbor(n, env);
            } catch (IOException ex) {
                LOG.log(Level.WARNING, String.format("Node %d failed to send message to neighbor %d", id, n), ex);
            }
        }
    }

    public int getId() { return id; }

    public List<Integer> getNeighbors() { return neighbors; }

    @Override
    public void close() {
        try {
            if (channel.isOpen())    channel.close();
            if (connection.isOpen()) connection.close();
            LOG.info(() -> "Node " + id + " disconnected");
        } catch (IOException | TimeoutException e) {
            LOG.log(Level.WARNING, "Node " + id + " : error during close", e);
        }
    }

    private String myQueue() { return QUEUE_PREFIX + id; }
}