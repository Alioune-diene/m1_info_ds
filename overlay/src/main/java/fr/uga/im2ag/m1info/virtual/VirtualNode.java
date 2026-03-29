package fr.uga.im2ag.m1info.virtual;

import com.google.gson.Gson;
import com.rabbitmq.client.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.logging.*;

public class VirtualNode implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(VirtualNode.class.getName());

    public static final String VIRTUAL_QUEUE_PREFIX = "virtual.node.";
    public static final String HOST_VIRT_SUFFIX = ".virt";

    private static final long HB_INTERVAL_MS = 3_000;
    private static final long HB_TIMEOUT_MS = 10_000;
    private static final long MIGRATE_RETRY_DELAY_MS = 2_000;

    private int virtualId = -1;
    private final int ringSize;
    private final int numPhysical;
    private final String rabbitHost;
    private final Gson gson = new Gson();

    private final Connection connection;
    private final Channel    channel;

    private final Object hostLock = new Object();
    private int  currentHost;
    private long lastAckTimestamp;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "virt-" + virtualId);
        t.setDaemon(true);
        return t;
    });

    private ScheduledFuture<?> hbSender;
    private ScheduledFuture<?> hbChecker;

    private volatile BiConsumer<Integer, String> messageHandler;

    public VirtualNode(int virtualId, int ringSize, int numPhysical, String rabbitHost) throws IOException, TimeoutException {

        if (virtualId < 0 || virtualId >= ringSize) { throw new IllegalArgumentException("virtualId out of bounds [0, ringSize)"); }

        this.virtualId = virtualId;
        this.ringSize = ringSize;
        this.numPhysical = numPhysical;
        this.rabbitHost = rabbitHost;
        this.currentHost = virtualId % numPhysical;

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(rabbitHost);
        this.connection = factory.newConnection("virt-" + virtualId);
        this.channel = connection.createChannel();

        channel.queueDeclare(myQueue(), false, false, false, null);

        LOG.info(() -> String.format("Virtual node %d/%d started — initial host: physical %d", virtualId, ringSize, currentHost));
    }

    public void start(BiConsumer<Integer, String> handler) throws IOException {
        this.messageHandler = handler;
        startListening();
        registerWithHost(currentHost);
        startHeartbeat();
    }

    public void sendRight(String payload) throws IOException {
        int dest = (virtualId + 1) % ringSize;
        sendToVirtual(dest, payload);
        LOG.info(() -> String.format("[V%d] sendRight -> V%d : \"%s\"", virtualId, dest, payload));
    }

    public void sendLeft(String payload) throws IOException {
        int dest = (virtualId - 1 + ringSize) % ringSize;
        sendToVirtual(dest, payload);
        LOG.info(() -> String.format("[V%d] sendLeft -> V%d : \"%s\"", virtualId, dest, payload));
    }

    private void sendToVirtual(int destVirtualId, String payload) throws IOException {
        VirtualEnvelope env = VirtualEnvelope.data(virtualId, destVirtualId, ringSize, payload);
        publishToHost(env);
    }

    private void publishToHost(VirtualEnvelope env) throws IOException {
        int host;
        synchronized (hostLock) { host = currentHost; }
        String json = VirtualEnvelope.PAYLOAD_PREFIX + gson.toJson(env);
        channel.basicPublish("", hostQueue(host), null, json.getBytes(StandardCharsets.UTF_8));
    }

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

    private void handleIncoming(VirtualEnvelope env) {
        switch (env.getType()) {

            case VIRTUAL_HEARTBEAT_ACK -> {
                synchronized (hostLock) {
                    lastAckTimestamp = System.currentTimeMillis();
                    currentHost = env.getHostPhysicalId();
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

    private void startHeartbeat() {
        synchronized (hostLock) { lastAckTimestamp = System.currentTimeMillis(); }

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

        hbChecker = scheduler.scheduleAtFixedRate(() -> {
            long elapsed;
            synchronized (hostLock) { elapsed = System.currentTimeMillis() - lastAckTimestamp; }
            if (elapsed > HB_TIMEOUT_MS) {
                LOG.warning(() -> "[V" + virtualId + "] Physical host " + currentHost + " unreachable for " + elapsed + "ms — starting migration...");
                migrate();
            }
        }, HB_TIMEOUT_MS, HB_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void migrate() {
        int failedHost;
        synchronized (hostLock) { failedHost = currentHost; }

        for (int k = 1; k < numPhysical; k++) {
            int candidate = (failedHost + k) % numPhysical;
            LOG.info(() -> "[V" + virtualId + "] Trying to migrate to physical " + candidate + "...");

            try {
                channel.queueDeclare(hostQueue(candidate), false, false, false, null);
            } catch (IOException e) {
                LOG.fine(() -> "[V" + virtualId + "] Physical " + candidate + " not available");
                continue;
            }

            synchronized (hostLock) {
                currentHost = candidate;
                lastAckTimestamp = System.currentTimeMillis();
            }

            registerWithHost(candidate);

            try { Thread.sleep(MIGRATE_RETRY_DELAY_MS); } catch (InterruptedException ignored) {}

            synchronized (hostLock) {
                long elapsed = System.currentTimeMillis() - lastAckTimestamp;
                if (elapsed < HB_TIMEOUT_MS) {
                    LOG.info(() -> "[V" + virtualId + "] Migration to physical " + candidate + " successful (ACK received in " + elapsed + "ms)");
                    return;
                }
            }

            LOG.warning(() -> "[V" + virtualId + "] Migration to physical " + candidate + " failed (no ACK received)");
        }

        LOG.severe(() -> "[V" + virtualId + "] Migration failed — no physical hosts available!");
    }

    public int getVirtualId() { return virtualId; }
    public int getRingSize() { return ringSize; }
    public int getCurrentHost() { synchronized (hostLock) { return currentHost; } }

    public String getStatus() {
        synchronized (hostLock) {
            return String.format("V%d/%d on P%d (last ACK %d ms ago)",
                    virtualId, ringSize, currentHost,
                    System.currentTimeMillis() - lastAckTimestamp);
        }
    }

    private String myQueue() { return VIRTUAL_QUEUE_PREFIX + virtualId; }
    private String hostQueue(int host) { return "physical.node." + host + HOST_VIRT_SUFFIX; }

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