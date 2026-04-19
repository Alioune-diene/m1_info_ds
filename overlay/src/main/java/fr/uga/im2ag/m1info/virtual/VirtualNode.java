package fr.uga.im2ag.m1info.virtual;

import com.google.gson.Gson;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A virtual node — a logical node that lives on top of the physical network.
 *
 * <p>Virtual nodes form a logical ring: each virtual node has a left neighbor and a right neighbor.
 * For example, with 4 virtual nodes (V0, V1, V2, V3):
 * <ul>
 *     <li>V0 ↔ V1 ↔ V2 ↔ V3 ↔ (back to V0)</li>
 * </ul>
 *
 * <p>Virtual nodes do NOT communicate directly with each other over the network.
 * Instead, each virtual node is "hosted" by a physical node.
 * It sends messages by publishing to its physical host's command queue,
 * and the physical host broadcasts the message through the spanning tree.
 *
 * <p>Default host assignment: {@code virtualId % numPhysical}.
 * Example: V3 in a network of 2 physical nodes is hosted on P(3%2) = P1.
 *
 * <p>Fault tolerance — MIGRATION:
 * Each virtual node periodically sends a HEARTBEAT to its physical host.
 * If no ACK arrives within {@code HB_TIMEOUT_MS}, the virtual node assumes its host is dead
 * and migrates to another physical node (tries each one in round-robin order).
 */
public class VirtualNode implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(VirtualNode.class.getName());

    // -------------------- Queue naming constants ---------------------------------------------------------------------

    /** Prefix for a virtual node's own receive queue: {@code virtual.node.<id>}. */
    public static final String VIRTUAL_QUEUE_PREFIX = "virtual.node.";

    /** Suffix added to a physical node's queue name to form its virtual command queue:
     *  {@code physical.node.<id>.virt}. */
    public static final String HOST_VIRT_SUFFIX = ".virt";

    // -------------------- Timing constants ---------------------------------------------------------------------------

    /** How often (ms) to send heartbeats to the current physical host. */
    private static final long HB_INTERVAL_MS = 3_000;

    /** How long (ms) without a heartbeat ACK before the host is considered dead and migration starts. */
    private static final long HB_TIMEOUT_MS = 10_000;

    /** How long (ms) to wait after re-registering with a new host before checking if migration succeeded. */
    private static final long MIGRATE_RETRY_DELAY_MS = 2_000;

    // -------------------- Identity -----------------------------------------------------------------------------------

    /** This virtual node's unique ID within the ring. */
    private int virtualId = -1;

    /** Total number of virtual nodes in the ring. */
    private final int ringSize;

    /** Total number of physical nodes, used to compute the initial host and migration candidates. */
    private final int numPhysical;

    /** Hostname of the RabbitMQ broker. */
    private final String rabbitHost;

    /** Gson instance used to serialize and deserialize VirtualEnvelope objects. */
    private final Gson gson = new Gson();

    // -------------------- RabbitMQ -----------------------------------------------------------------------------------

    /** The TCP connection to the RabbitMQ broker. */
    private final Connection connection;

    /** The channel used for all publish and consume operations. */
    private final Channel channel;

    // -------------------- Host tracking ------------------------------------------------------------------------------

    /** Mutex protecting all host-related state. */
    private final Object hostLock = new Object();

    /**
     * The ID of the physical node currently hosting this virtual node.
     * Initially set to {@code virtualId % numPhysical}.
     * Updated whenever a VIRTUAL_HEARTBEAT_ACK is received, since the ACK carries the host's ID.
     */
    private int currentHost;

    /** Timestamp (ms) of the last VIRTUAL_HEARTBEAT_ACK received from the physical host. */
    private long lastAckTimestamp;

    // -------------------- Background tasks ---------------------------------------------------------------------------

    /** Thread pool for scheduling the heartbeat sender and checker tasks. */
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "virt-" + virtualId);
        t.setDaemon(true);
        return t;
    });

    /** Scheduled task that periodically sends VIRTUAL_HEARTBEAT to the current physical host. */
    private ScheduledFuture<?> hbSender;

    /** Scheduled task that checks whether a VIRTUAL_HEARTBEAT_ACK was received on time. */
    private ScheduledFuture<?> hbChecker;

    // -------------------- Application callback -----------------------------------------------------------------------

    /**
     * Called when a VIRTUAL_DATA message arrives for this node.
     * Parameters passed to the consumer: (senderVirtualId, messagePayload).
     */
    private volatile BiConsumer<Integer, String> messageHandler;

    // -------------------- Constructors -------------------------------------------------------------------------------

    /**
     * Creates a virtual node, connects to RabbitMQ, and declares its own receive queue.
     *
     * @param virtualId   this node's ring position (0 to ringSize - 1)
     * @param ringSize    total number of virtual nodes in the ring
     * @param numPhysical total number of physical nodes in the network
     * @param rabbitHost  hostname of the RabbitMQ broker
     * @throws IOException      if the RabbitMQ connection or channel cannot be created
     * @throws TimeoutException if the RabbitMQ connection attempt times out
     * @throws IllegalArgumentException if virtualId is outside the valid range [0, ringSize)
     */
    public VirtualNode(int virtualId, int ringSize, int numPhysical, String rabbitHost)
            throws IOException, TimeoutException {

        if (virtualId < 0 || virtualId >= ringSize) {
            throw new IllegalArgumentException("virtualId out of bounds [0, ringSize)");
        }  

        this.virtualId = virtualId;
        this.ringSize = ringSize;
        this.numPhysical = numPhysical;
        this.rabbitHost = rabbitHost;

        // Initial host assignment: simple modulo — e.g V5 in a 3-node network → P(5%3) = P2
        this.currentHost = virtualId % numPhysical;

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(rabbitHost);
        factory.setUsername("root");
        factory.setPassword("root");
        this.connection = factory.newConnection("virt-" + virtualId);
        this.channel = connection.createChannel();

        // Declare this virtual node's own receive queue so the physical host can deliver messages to it
        channel.queueDeclare(myQueue(), false, false, false, null);

        LOG.info(() -> String.format("Virtual node %d/%d started — initial host: physical %d",
                virtualId, ringSize, currentHost));
    }

    // -------------------- Public API ---------------------------------------------------------------------------------

    /**
     * Starts the virtual node by performing three actions in order:
     * <ol>
     *     <li>Begins listening on this node's own queue for incoming messages</li>
     *     <li>Registers with the initial physical host</li>
     *     <li>Starts sending periodic heartbeats to the host</li>
     * </ol>
     *
     * @param handler called with (senderVirtualId, payload) whenever a VIRTUAL_DATA message arrives
     * @throws IOException if the RabbitMQ consume operation fails
     */
    public void start(BiConsumer<Integer, String> handler) throws IOException {
        this.messageHandler = handler;
        startListening();
        registerWithHost(currentHost);
        startHeartbeat();
    }

    /**
     * Sends a message to the virtual node immediately to the right in the ring.
     * The right neighbor of V(id) is V((id+1) % ringSize).
     *
     * @param payload the message text to send
     * @throws IOException if the RabbitMQ publish operation fails
     */
    public void sendRight(String payload) throws IOException {
        int dest = (virtualId + 1) % ringSize;
        sendToVirtual(dest, payload);
        LOG.info(() -> String.format("[V%d] sendRight -> V%d : \"%s\"", virtualId, dest, payload));
    }

    /**
     * Sends a message to the virtual node immediately to the left in the ring.
     * The left neighbor of V(id) is V((id-1+ringSize) % ringSize).
     * The addition of ringSize prevents a negative modulo result in Java.
     *
     * @param payload the message text to send
     * @throws IOException if the RabbitMQ publish operation fails
     */
    public void sendLeft(String payload) throws IOException {
        int dest = (virtualId - 1 + ringSize) % ringSize;
        sendToVirtual(dest, payload);
        LOG.info(() -> String.format("[V%d] sendLeft -> V%d : \"%s\"", virtualId, dest, payload));
    }

    // -------------------- Sending ------------------------------------------------------------------------------------

    /**
     * Creates a VIRTUAL_DATA envelope for the given destination and publishes it to the physical host.
     * The physical host will broadcast it through the spanning tree so every physical node sees it,
     * and the one hosting the destination virtual node will deliver it locally.
     *
     * @param destVirtualId the ID of the destination virtual node
     * @param payload       the message content to deliver
     * @throws IOException if the RabbitMQ publish operation fails
     */
    private void sendToVirtual(int destVirtualId, String payload) throws IOException {
        VirtualEnvelope env = VirtualEnvelope.data(virtualId, destVirtualId, ringSize, payload);
        publishToHost(env);
    }

    /**
     * Serializes a VirtualEnvelope and publishes it to the current physical host's command queue.
     * The serialized form is {@code "V|<json>"} — the "V|" prefix allows PhysicalHostService
     * to distinguish virtual payloads from regular physical messages.
     *
     * @param env the VirtualEnvelope to send
     * @throws IOException if the RabbitMQ publish operation fails
     */
    private void publishToHost(VirtualEnvelope env) throws IOException {
        int host;
        synchronized (hostLock) { host = currentHost; }
        String json = VirtualEnvelope.PAYLOAD_PREFIX + gson.toJson(env);
        channel.basicPublish("", hostQueue(host), null, json.getBytes(StandardCharsets.UTF_8));
    }

    // -------------------- Listening ----------------------------------------------------------------------------------

    /**
     * Subscribes to this node's own queue ({@code virtual.node.<id>}) to receive incoming messages.
     *
     * <p>Two message types can arrive:
     * <ul>
     *     <li>{@link VirtualEnvelope.Type#VIRTUAL_HEARTBEAT_ACK} — the physical host is alive;
     *         updates {@code currentHost} and resets the heartbeat timeout</li>
     *     <li>{@link VirtualEnvelope.Type#VIRTUAL_DATA} — an application message from another
     *         virtual node; delivered to the registered {@code messageHandler}</li>
     * </ul>
     *
     * @throws IOException if the RabbitMQ consume operation fails
     */
    private void startListening() throws IOException {
        DeliverCallback cb = (tag, delivery) -> {
            String raw = new String(delivery.getBody(), StandardCharsets.UTF_8);
            if (raw.startsWith(VirtualEnvelope.PAYLOAD_PREFIX)) {
                String json = raw.substring(VirtualEnvelope.PAYLOAD_PREFIX.length());
                VirtualEnvelope env = gson.fromJson(json, VirtualEnvelope.class);
                handleIncoming(env);
            } else {
                LOG.warning(() -> "[V" + virtualId + "] Unrecognized message: " + raw);
            }
        };
        channel.basicConsume(myQueue(), true, cb, tag -> {});
        LOG.info(() -> "[V" + virtualId + "] listen on " + myQueue());
    }

    /**
     * Dispatches an incoming VirtualEnvelope to the appropriate handler based on its type.
     *
     * @param env the incoming envelope to handle
     */
    private void handleIncoming(VirtualEnvelope env) {
        switch (env.getType()) {

            case VIRTUAL_HEARTBEAT_ACK -> {
                // The physical host confirmed it is alive — reset the timeout clock and record the host ID
                synchronized (hostLock) {
                    lastAckTimestamp = System.currentTimeMillis();
                    currentHost = env.getHostPhysicalId();
                }
                LOG.fine(() -> "[V" + virtualId + "] HB_ACK received from physical " + env.getHostPhysicalId());
            }

            case VIRTUAL_DATA -> {
                // An application message arrived — deliver it to the registered handler
                BiConsumer<Integer, String> h = messageHandler;
                if (h != null) { h.accept(env.getVirtualSourceId(), env.getPayload()); }
            }

            default -> LOG.warning(() -> "[V" + virtualId + "] Message type not handled: " + env);
        }
    }

    // -------------------- Host registration --------------------------------------------------------------------------

    /**
     * Registers this virtual node with the specified physical host.
     * Sends a VIRTUAL_REGISTER message to the host's command queue ({@code physical.node.<id>.virt}).
     * The host will add this node to its {@code hostedVirtuals} set and reply with a VIRTUAL_HEARTBEAT_ACK.
     *
     * @param hostId the ID of the physical node to register with
     */
    private void registerWithHost(int hostId) {
        VirtualEnvelope reg = VirtualEnvelope.register(virtualId, ringSize);
        String json = VirtualEnvelope.PAYLOAD_PREFIX + gson.toJson(reg);
        try {
            channel.queueDeclare(hostQueue(hostId), false, false, false, null);
            channel.basicPublish("", hostQueue(hostId), null, json.getBytes(StandardCharsets.UTF_8));
            LOG.info(() -> "[V" + virtualId + "] registered with physical " + hostId);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "[V" + virtualId + "] Failed to register with physical " + hostId, e);
        }
    }

    // -------------------- Heartbeat ----------------------------------------------------------------------------------

    /**
     * Starts the two-part heartbeat mechanism used to detect physical host failures.
     *
     * <p>Part 1 — {@code hbSender}: every {@code HB_INTERVAL_MS}, sends a VIRTUAL_HEARTBEAT
     * to the current physical host to prove this virtual node is still alive.
     *
     * <p>Part 2 — {@code hbChecker}: after an initial delay of {@code HB_TIMEOUT_MS}, checks
     * every {@code HB_INTERVAL_MS} whether a VIRTUAL_HEARTBEAT_ACK was received within the timeout window.
     * If not, the host is considered dead and {@link #migrate()} is triggered.
     */
    private void startHeartbeat() {
        synchronized (hostLock) { lastAckTimestamp = System.currentTimeMillis(); }

        // Task 1: periodically send VIRTUAL_HEARTBEAT to the current physical host
        hbSender = scheduler.scheduleAtFixedRate(() -> {
            VirtualEnvelope hb = VirtualEnvelope.heartbeat(virtualId);
            String json = VirtualEnvelope.PAYLOAD_PREFIX + gson.toJson(hb);
            int host;
            synchronized (hostLock) { host = currentHost; }
            try {
                channel.basicPublish("", hostQueue(host), null, json.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                LOG.log(Level.WARNING, "[V" + virtualId + "] Failed to send heartbeat to physical " + host, e);
            }
        }, HB_INTERVAL_MS, HB_INTERVAL_MS, TimeUnit.MILLISECONDS);

        // Task 2: check if any ACK was received recently; if not, trigger migration
        hbChecker = scheduler.scheduleAtFixedRate(() -> {
            long elapsed;
            synchronized (hostLock) { elapsed = System.currentTimeMillis() - lastAckTimestamp; }
            if (elapsed > HB_TIMEOUT_MS) {
                LOG.warning(() -> "[V" + virtualId + "] Physical host " + currentHost
                        + " unreachable for " + elapsed + "ms — starting migration...");
                migrate();
            }
        }, HB_TIMEOUT_MS, HB_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    // -------------------- Migration ----------------------------------------------------------------------------------

    /**
     * Attempts to migrate this virtual node to a different physical host when the current host is dead.
     *
     * <p>Algorithm:
     * <ol>
     *     <li>Starting from (failedHost + 1), try each physical node in round-robin order</li>
     *     <li>For each candidate: declare its queue, update {@code currentHost}, send VIRTUAL_REGISTER</li>
     *     <li>Wait {@code MIGRATE_RETRY_DELAY_MS} for a VIRTUAL_HEARTBEAT_ACK</li>
     *     <li>If an ACK is received within {@code HB_TIMEOUT_MS} → migration succeeded, return</li>
     *     <li>Otherwise, try the next candidate</li>
     *     <li>If all candidates fail → log a severe error (no physical hosts available)</li>
     * </ol>
     */
    private void migrate() {
        int failedHost;
        synchronized (hostLock) { failedHost = currentHost; }

        for (int k = 1; k < numPhysical; k++) {
            int candidate = (failedHost + k) % numPhysical;
            LOG.info(() -> "[V" + virtualId + "] Trying to migrate to physical " + candidate + "...");

            // Attempt to declare the candidate's command queue — fails if that physical node is down
            try {
                channel.queueDeclare(hostQueue(candidate), false, false, false, null);
            } catch (IOException e) {
                LOG.fine(() -> "[V" + virtualId + "] Physical " + candidate + " not available");
                continue;
            }

            // Switch to the candidate host and reset the ACK timestamp
            synchronized (hostLock) {
                currentHost = candidate;
                lastAckTimestamp = System.currentTimeMillis();
            }

            // Introduce ourselves to the new host
            registerWithHost(candidate);

            // Wait briefly to give the new host time to reply with a VIRTUAL_HEARTBEAT_ACK
            try { Thread.sleep(MIGRATE_RETRY_DELAY_MS); } catch (InterruptedException ignored) {}

            // Check whether an ACK arrived in time
            synchronized (hostLock) {
                long elapsed = System.currentTimeMillis() - lastAckTimestamp;
                if (elapsed < HB_TIMEOUT_MS) {
                    LOG.info(() -> "[V" + virtualId + "] Migration to physical " + candidate
                            + " successful (ACK received in " + elapsed + "ms)");
                    return;
                }
            }

            LOG.warning(() -> "[V" + virtualId + "] Migration to physical " + candidate
                    + " failed (no ACK received)");
        }

        // All physical nodes were tried and none responded
        LOG.severe(() -> "[V" + virtualId + "] Migration failed — no physical hosts available!");
    }

    // -------------------- Getters / Status ---------------------------------------------------------------------------

    /**
     * Gets the unique ID of this virtual node within the ring.
     *
     * @return the virtual node ID
     */
    public int getVirtualId() { return virtualId; }

    /**
     * Gets the total number of virtual nodes in the ring.
     *
     * @return the ring size
     */
    public int getRingSize() { return ringSize; }

    /**
     * Gets the ID of the physical node currently hosting this virtual node.
     *
     * @return the current physical host ID
     */
    public int getCurrentHost() {
        synchronized (hostLock) { return currentHost; }
    }

    /**
     * Returns a one-line summary of this virtual node's current state,
     * including its ring position, current host, and time since the last heartbeat ACK.
     *
     * @return a formatted status string
     */
    public String getStatus() {
        synchronized (hostLock) {
            return String.format("V%d/%d on P%d (last ACK %d ms ago)",
                    virtualId, ringSize, currentHost,
                    System.currentTimeMillis() - lastAckTimestamp);
        }
    }

    // -------------------- Queue name helpers -------------------------------------------------------------------------

    /**
     * Returns the name of this virtual node's own receive queue.
     * Format: {@code virtual.node.<id>}
     *
     * @return this node's queue name
     */
    private String myQueue() { return VIRTUAL_QUEUE_PREFIX + virtualId; }

    /**
     * Returns the name of a physical node's virtual command queue.
     * Format: {@code physical.node.<host>.virt}
     *
     * @param host the physical node ID
     * @return the command queue name for that physical node
     */
    private String hostQueue(int host) { return "physical.node." + host + HOST_VIRT_SUFFIX; }

    // -------------------- Cleanup ------------------------------------------------------------------------------------

    /**
     * Stops heartbeat tasks and closes the RabbitMQ channel and connection.
     * Called automatically on JVM shutdown via the shutdown hook, or explicitly when the user types "quit".
     */
    @Override
    public void close() {
        if (hbSender  != null) hbSender.cancel(false);
        if (hbChecker != null) hbChecker.cancel(false);
        scheduler.shutdownNow();
        try {
            if (channel.isOpen())    { channel.close(); }
            if (connection.isOpen()) { connection.close(); }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[V" + virtualId + "] Error during close", e);
        }
        LOG.info(() -> "[V" + virtualId + "] Disconnected");
    }
}
