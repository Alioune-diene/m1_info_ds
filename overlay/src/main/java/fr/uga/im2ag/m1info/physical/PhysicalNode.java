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

    private volatile MessageHandler handler;

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

    public void startListening(MessageHandler handler) throws IOException {
        this.handler = handler;

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String json = new String(delivery.getBody(), StandardCharsets.UTF_8);
            Message msg = gson.fromJson(json, Message.class);
            handleIncoming(msg);
        };

        channel.basicConsume(myQueue(), true, deliverCallback, tag -> {});
        LOG.info(() -> "Node " + id + " started listening on queue " + myQueue());
    }

    public void send(int destinationId, String payload) throws IOException {
        Message msg = new Message(id, destinationId, payload);
        flood(msg);
    }

    public void sendToNeighbor(int neighborId, Message msg) throws IOException {
        if (!neighbors.contains(neighborId)) {
            throw new IllegalArgumentException("Node " + neighborId + " is not a neighbor of node " + id);
        }

        String json = gson.toJson(msg);
        channel.basicPublish(
                "",
                QUEUE_PREFIX + neighborId,
                null,
                json.getBytes(StandardCharsets.UTF_8)
        );
        LOG.fine(() -> "Node " + id + " sent message to neighbor " + neighborId + " | dst=" + msg.getDestinationId());
    }

    public int getId() { return id; }

    public List<Integer> getNeighbors() { return neighbors; }

    private void handleIncoming(Message msg) {
        if (msg.getDestinationId() == id) {
            LOG.info(() -> "Node "  + id + " received message from " + msg.getSourceId() + " | payload='" + msg.getPayload() + "'");
            if (handler != null) { handler.onMessage(msg); }
        } else if (!msg.hasVisited(id)) {
            LOG.info(() -> "Node " + id + " relays message from " + msg.getSourceId() + " to dst=" + msg.getDestinationId());
            try { flood(msg); }
            catch (IOException e) { LOG.log(Level.WARNING, "Nœud " + id + " : erreur de relais", e); }
        } else {
            LOG.info(() -> "Node " + id + " ignored already visited message from " + msg.getSourceId() + " | dst=" + msg.getDestinationId());
        }
    }

    private void flood(Message msg) throws IOException {
        msg.markVisited(id);

        for (int neighbor : neighbors) {
            if (!msg.hasVisited(neighbor)) {
                sendToNeighbor(neighbor, msg);
            }
        }
    }

    private String myQueue() {
        return QUEUE_PREFIX + id;
    }

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
}