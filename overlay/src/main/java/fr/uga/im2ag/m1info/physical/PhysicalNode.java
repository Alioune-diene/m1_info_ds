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

/**
 * Represents a physical overlay node backed by RabbitMQ queues.
 * <p>
 * Each node:
 * <ul>
 *   <li>owns a dedicated queue named {@code physical.node.&lt;id&gt;},</li>
 *   <li>can consume JSON-serialized {@link Envelope} messages from that queue,</li>
 *   <li>can publish messages to queues of its declared neighbors.</li>
 * </ul>
 * <p>
 * The class encapsulates connection/channel lifecycle and exposes helper
 * methods for unicast and broadcast sends.
 */
public class PhysicalNode implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(PhysicalNode.class.getName());

    /**
     * Prefix used to build node queue names.
     * Full queue name format: {@code physical.node.<nodeId>}.
     */
    public static final String QUEUE_PREFIX = "physical.node.";

    /**
     * Unique identifier of this node in the physical topology.
     */
    private final int id;

    /** Immutable list of neighbor node IDs this node is allowed to send to. */
    private final List<Integer> neighbors;

    /** JSON serializer/deserializer used for {@link Envelope} payloads. */
    private final Gson gson = new Gson();

    /** RabbitMQ TCP connection owned by this node. */
    private final Connection connection;

    /** RabbitMQ channel used for queue declaration, publish, and consume. */
    private final Channel channel;

    /**
     * Creates and initializes a physical node.
     * <p>
     * Initialization includes:
     * <ol>
     *   <li>copying neighbor IDs into an immutable list,</li>
     *   <li>opening a RabbitMQ connection and channel,</li>
     *   <li>declaring this node's queue.</li>
     * </ol>
     *
     * @param id node identifier
     * @param neighbors list of neighbor node IDs this node can directly contact
     * @param rabbitHost RabbitMQ host name or IP address
     * @throws IOException if queue declaration or broker communication fails
     * @throws TimeoutException if connection/channel creation times out
     */
    public PhysicalNode(int id, List<Integer> neighbors, String rabbitHost) throws IOException, TimeoutException {
        this.id = id;
        this.neighbors = List.copyOf(neighbors);

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(rabbitHost);
        factory.setUsername("root");
        factory.setPassword("root");

        this.connection = factory.newConnection("node-" + id);
        this.channel = connection.createChannel();

        channel.queueDeclare(myQueue(), false, false, false, null);

        LOG.info(() -> "Node " + id + " ready - neighbors : " + neighbors + " - queue : " + myQueue());
    }

    /**
     * Starts consuming messages from this node's queue.
     * <p>
     * Incoming messages are deserialized from UTF-8 JSON into {@link Envelope}
     * instances and forwarded to the provided callback. The second callback argument
     * is the sender ID extracted from the envelope.
     * <p>
     * Consumption uses auto-ack mode.
     *
     * @param callback handler invoked for each received envelope and sender ID
     * @throws IOException if consumer registration fails
     */
    public void startListening(BiConsumer<Envelope, Integer> callback) throws IOException {
        DeliverCallback deliverCallback = (tag, delivery) -> {
            String json = new String(delivery.getBody(), StandardCharsets.UTF_8);
            Envelope env = gson.fromJson(json, Envelope.class);
            callback.accept(env, env.getSenderId());
        };
        channel.basicConsume(myQueue(), true, deliverCallback, tag -> {});
        LOG.info(() -> "Node " + id + " started listening on queue " + myQueue());
    }

    /**
     * Sends a message to a specific neighbor.
     * <p>
     * The method enforces topology constraints by refusing sends to non-neighbors.
     * The message is serialized as JSON and published to the destination node queue.
     *
     * @param neighborId destination node ID (must be present in {@link #neighbors})
     * @param env envelope to send
     * @throws IllegalArgumentException if {@code neighborId} is not a declared neighbor
     * @throws IOException if publish operation fails
     */
    public void sendToNeighbor(int neighborId, Envelope env) throws IOException {
        if (!neighbors.contains(neighborId)) { throw new IllegalArgumentException(neighborId + " is not a neighbor of " + id); }
        String json = gson.toJson(env);
        channel.basicPublish("", QUEUE_PREFIX + neighborId, null, json.getBytes(StandardCharsets.UTF_8));
        LOG.fine(() -> String.format("  %d -> %d | %s", id, neighborId, env));
    }

    /**
     * Broadcasts an envelope to all declared neighbors.
     * Failures are handled per-neighbor: one send error is logged and does not prevent attempting remaining neighbors.
     *
     * @param env envelope to broadcast
     */
    public void broadcast(Envelope env) {
        for (int n : neighbors) {
            try {
                sendToNeighbor(n, env);
            } catch (IOException ex) {
                LOG.log(Level.WARNING, String.format("Node %d failed to send message to neighbor %d", id, n), ex);
            }
        }
    }

    /**
     * Returns this node identifier.
     * @return node ID
     */
    public int getId() { return id;
    }

    /**
     * Returns the immutable list of neighbor IDs.
     * @return neighbor IDs
     */
    public List<Integer> getNeighbors() { return neighbors;
    }

    /**
     * Exposes the underlying RabbitMQ connection.
     * @return active connection instance
     */
    public Connection getConnection() { return connection;
    }

    /**
     * Closes channel and connection if still open.
     */
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

    /**
     * Builds this node's queue name.
     * @return queue name for this node
     */
    private String myQueue() { return QUEUE_PREFIX + id; }
}