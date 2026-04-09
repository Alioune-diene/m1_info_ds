package fr.uga.im2ag.m1info.virtual;

import com.google.gson.Gson;
import com.rabbitmq.client.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.logging.*;
/**
 * A virtual node — a logical node that lives on top of the physical network.
 *
 * Virtual nodes form a logical RING: each virtual node has a left neighbor and a right neighbor.

 * Virtual nodes do NOT send directly to each other over the network.
 * Instead, each virtual node is "hosted" by a physical node.
 * It communicates by publishing to its physical host's command queue,
 * and the physical host broadcasts the message through the spanning tree.
 *
 * Default host assignment: virtualId % numPhysical
 * Example: V3 in a network of 2 physical nodes → hosted on P(3%2) = P1
 *
 * Fault tolerance — MIGRATION:
 * Each virtual node periodically sends a HEARTBEAT to its physical host.
 * If no ACK arrives within HB_TIMEOUT_MS, the virtual node assumes its host is dead
 * and migrates to another physical node (tries each one in round-robin order).
 */    
public class VirtualNode implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(VirtualNode.class.getName());
    /** Queue naming: every virtual node's own receive queue = "virtual.node.<id>" */
    public static final String VIRTUAL_QUEUE_PREFIX = "virtual.node.";
    /** Suffix added to physical node queue for virtual commands: "physical.node.<id>.virt" */
    public static final String HOST_VIRT_SUFFIX = ".virt";
    // Timing constants 
    /** How often (ms) to send heartbeats to the physical host. */
    private static final long HB_INTERVAL_MS = 3_000;
    /** How long (ms) with no ACK before we consider the host dead and start migrating. */
    private static final long HB_TIMEOUT_MS = 10_000;
    /** How long (ms) to wait after re-registering before checking if migration succeeded. */
    private static final long MIGRATE_RETRY_DELAY_MS = 2_000;
    // Identity
    private int virtualId = -1;  // this virtual node's unique ID within the ring
    private final int ringSize; // total number of virtual nodes in the ring
    private final int numPhysical;// total number of physical nodes (for host assignment)
    private final String rabbitHost;// RabbitMQ broker hostname
    private final Gson gson = new Gson();
     // RabbitMQ
    private final Connection connection;
    private final Channel    channel;

    private final Object hostLock = new Object();
     /**
     * The physical node currently hosting this virtual node.
     * Initially: virtualId % numPhysical
     * Updated: whenever a HEARTBEAT_ACK is received (the ACK tells us who sent it)
     */
    private int  currentHost;
     /** Timestamp (ms) of the last HEARTBEAT_ACK received from the physical host. */
    private long lastAckTimestamp;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "virt-" + virtualId);
        t.setDaemon(true);
        return t;
    });

    private ScheduledFuture<?> hbSender;
    private ScheduledFuture<?> hbChecker;
    
    /**
     * Called when a VIRTUAL_DATA message arrives for this node.
     */
    private volatile BiConsumer<Integer, String> messageHandler;
      /**
     * Creates a virtual node, connects to RabbitMQ, and declares its own queue.
     *
     * @param virtualId   this node's ring position (0 to ringSize-1)
     * @param ringSize    total number of virtual nodes in the ring
     * @param numPhysical total number of physical nodes
     * @param rabbitHost  RabbitMQ broker address
     */
    public VirtualNode(int virtualId, int ringSize, int numPhysical, String rabbitHost) throws IOException, TimeoutException {

        if (virtualId < 0 || virtualId >= ringSize) { throw new IllegalArgumentException("virtualId out of bounds [0, ringSize)"); }

        this.virtualId = virtualId;
        this.ringSize = ringSize;
        this.numPhysical = numPhysical;
        this.rabbitHost = rabbitHost;
        // Initial host: simple modulo assignment
        this.currentHost = virtualId % numPhysical;
        // Connect to RabbitMQ
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(rabbitHost);
        this.connection = factory.newConnection("virt-" + virtualId);
        this.channel = connection.createChannel();
          // Declare this virtual node's own receive queue
        // This is where the physical host will deliver messages intended for us
        channel.queueDeclare(myQueue(), false, false, false, null);

        LOG.info(() -> String.format("Virtual node %d/%d started — initial host: physical %d", virtualId, ringSize, currentHost));
    }
     /**
     * Starts the virtual node:
     *  1. Begins listening on our own queue for incoming messages
     *  2. Registers with our initial physical host
     *  3. Starts sending periodic heartbeats
     * @param handler called with (senderVirtualId, payload) when a message arrives
     */
    public void start(BiConsumer<Integer, String> handler) throws IOException {
        this.messageHandler = handler;
        startListening();
        registerWithHost(currentHost);
        startHeartbeat();
    }
     /**
     * Sends a message to the virtual node immediately to our RIGHT in the ring.
     * Right neighbor of V<id> = V<(id+1) % ringSize>
     *
     * @param payload the message text to send
     */
    public void sendRight(String payload) throws IOException {
        int dest = (virtualId + 1) % ringSize;
        sendToVirtual(dest, payload);
        LOG.info(() -> String.format("[V%d] sendRight -> V%d : \"%s\"", virtualId, dest, payload));
    }
     /**
     * Sends a message to the virtual node immediately to our LEFT in the ring.
     * Left neighbor of V<id> = V<(id-1+ringSize) % ringSize>
     * (+ringSize prevents negative modulo result in Java)
     *
     * @param payload the message text to send
     */

    public void sendLeft(String payload) throws IOException {
        int dest = (virtualId - 1 + ringSize) % ringSize;
        sendToVirtual(dest, payload);
        LOG.info(() -> String.format("[V%d] sendLeft -> V%d : \"%s\"", virtualId, dest, payload));
    }
     //Sending
      /**
     * Creates a VIRTUAL_DATA envelope and sends it to our physical host for routing.
     * The physical host will broadcast it through the spanning tree.
     */

    private void sendToVirtual(int destVirtualId, String payload) throws IOException {
        VirtualEnvelope env = VirtualEnvelope.data(virtualId, destVirtualId, ringSize, payload);
        publishToHost(env);
    }
    
    /**
     * Publishes a VirtualEnvelope to the current physical host's command queue.
     * Serializes to "V|<json>" format before sending.
     */
    private void publishToHost(VirtualEnvelope env) throws IOException {
        int host;
        synchronized (hostLock) { host = currentHost; }
        String json = VirtualEnvelope.PAYLOAD_PREFIX + gson.toJson(env);
        channel.basicPublish("", hostQueue(host), null, json.getBytes(StandardCharsets.UTF_8));
    }
     //Listening
       /**
     * Subscribes to our own queue ("virtual.node.<id>") to receive incoming messages.
     * Two types of messages can arrive:
     *  - VIRTUAL_HEARTBEAT_ACK: our physical host is alive → update currentHost and timestamp
     *  - VIRTUAL_DATA: an application message from another virtual node → call the app handler
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
     * Handles incoming VirtualEnvelopes dispatched by the listener callback.
     */

    private void handleIncoming(VirtualEnvelope env) {
        switch (env.getType()) {

            case VIRTUAL_HEARTBEAT_ACK -> {
                // Our physical host is alive and responded to our heartbeat
                synchronized (hostLock) {
                    lastAckTimestamp = System.currentTimeMillis(); // reset the timeout clock
                    currentHost = env.getHostPhysicalId();// update who our host is
                }
                LOG.fine(() -> "[V" + virtualId + "] HB_ACK received from physical " + env.getHostPhysicalId());
            }

            case VIRTUAL_DATA -> {
                BiConsumer<Integer, String> h = messageHandler;
                if (h != null) { h.accept(env.getVirtualSourceId(), env.getPayload()); }
            }

            default -> LOG.warning(() -> "[V" + virtualId + "] Message type not handled: " + env);
        }
    }
     // Host Registration 

    /**
     * Registers this virtual node with the specified physical host.
     * Sends a VIRTUAL_REGISTER message to the host's command queue.
     * The host will add us to its hostedVirtuals set and reply with a HEARTBEAT_ACK.
     *
     * @param hostId the physical node ID to register with
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
     //Heartbeat 

    /**
     * Starts the two-part heartbeat mechanism:
     *
     * Part 1 - hbSender:
     *   Every HB_INTERVAL_MS, sends VIRTUAL_HEARTBEAT to the current physical host.
     *   This tells the host "I'm still alive, don't forget about me."
     *
     * Part 2 - hbChecker:
     *   Every HB_INTERVAL_MS ,
     *   checks if we've received any ACK within the timeout window.
     *   If not → the host is dead → trigger migration.
     */

    private void startHeartbeat() {
        synchronized (hostLock) { lastAckTimestamp = System.currentTimeMillis(); }
         // Task 1: periodically send heartbeat to current host

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
        // Task 2: check if we have  received any ACK recently; if not, migrate
        hbChecker = scheduler.scheduleAtFixedRate(() -> {
            long elapsed;
            synchronized (hostLock) { elapsed = System.currentTimeMillis() - lastAckTimestamp; }
            if (elapsed > HB_TIMEOUT_MS) {
                LOG.warning(() -> "[V" + virtualId + "] Physical host " + currentHost + " unreachable for " + elapsed + "ms — starting migration...");
                migrate();
            }
        }, HB_TIMEOUT_MS, HB_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }
     // Migration 

    /**
     * Attempts to migrate to a different physical node when the current host is dead.
     *
     * Algorithm:
     *  - Starting from (failedHost + 1), try each physical node in round-robin order
     *  - For each candidate: declare its queue, update currentHost, send VIRTUAL_REGISTER
     *  - Wait MIGRATE_RETRY_DELAY_MS for a HEARTBEAT_ACK
     *  - If ACK received within HB_TIMEOUT_MS → migration succeeded
     *  - Otherwise try the next candidate
     *  - If all candidates fail → log a severe error (no hosts available)
     */
    private void migrate() {
        int failedHost;
        synchronized (hostLock) { failedHost = currentHost; }

        for (int k = 1; k < numPhysical; k++) {
            int candidate = (failedHost + k) % numPhysical;
            LOG.info(() -> "[V" + virtualId + "] Trying to migrate to physical " + candidate + "...");
            // Try declaring the candidate's command queue (fails if it doesn't exist)

            try {
                channel.queueDeclare(hostQueue(candidate), false, false, false, null);
            } catch (IOException e) {
                LOG.fine(() -> "[V" + virtualId + "] Physical " + candidate + " not available");
                continue;
            }
             // Switch our currentHost and reset the timestamp (assume it's alive)
            synchronized (hostLock) {
                currentHost = candidate;
                lastAckTimestamp = System.currentTimeMillis();
            }
             // Introduce ourselves to the new host
            registerWithHost(candidate);
             // Wait for an ACK to confirm the new host received our registration

            try { Thread.sleep(MIGRATE_RETRY_DELAY_MS); } catch (InterruptedException ignored) {}
            // Check if we got an ACK in time
            synchronized (hostLock) {
                long elapsed = System.currentTimeMillis() - lastAckTimestamp;
                if (elapsed < HB_TIMEOUT_MS) {
                    LOG.info(() -> "[V" + virtualId + "] Migration to physical " + candidate + " successful (ACK received in " + elapsed + "ms)");
                    return; // success — stop trying other candidates
                }
            }

            LOG.warning(() -> "[V" + virtualId + "] Migration to physical " + candidate + " failed (no ACK received)");
        }
          // If we get here, no physical node responded 
        LOG.severe(() -> "[V" + virtualId + "] Migration failed — no physical hosts available!");
    }
     // Getters / Status 
    public int getVirtualId() { return virtualId; }
    public int getRingSize() { return ringSize; }
    public int getCurrentHost() { synchronized (hostLock) { return currentHost; } }
    /** Returns a summary string of this virtual node's current state. */
    public String getStatus() {
        synchronized (hostLock) {
            return String.format("V%d/%d on P%d (last ACK %d ms ago)",
                    virtualId, ringSize, currentHost,
                    System.currentTimeMillis() - lastAckTimestamp);
        }
    }
    /** This node's own receive queue: "virtual.node.<id>" */
    private String myQueue() { return VIRTUAL_QUEUE_PREFIX + virtualId; }
    /** The virtual command queue of a given physical host: "physical.node.<host>.virt" */
    private String hostQueue(int host) { return "physical.node." + host + HOST_VIRT_SUFFIX; }
   // Cleanup 
    @Override
    public void close() {
        if (hbSender  != null) hbSender.cancel(false);
        if (hbChecker != null) hbChecker.cancel(false);
        scheduler.shutdownNow();
        try {
            if (channel.isOpen()) { channel.close(); }
            if (connection.isOpen()) { connection.close(); }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[V" + virtualId + "] Error during close", e);
        }
        LOG.info(() -> "[V" + virtualId + "] Disconnected");
    }
}