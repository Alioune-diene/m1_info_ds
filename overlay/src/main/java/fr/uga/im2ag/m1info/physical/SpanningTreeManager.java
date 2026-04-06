package fr.uga.im2ag.m1info.physical;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

public class SpanningTreeManager {
    private static final Logger LOG = Logger.getLogger(SpanningTreeManager.class.getName());

    private static final long ELECTION_TIMEOUT_MS = 3_000;
    private static final long BUILDING_TIMEOUT_MS = 5_000;
    private static final long HB_INTERVAL_MS = 2_000;
    private static final long HB_TIMEOUT_MS = 6_000;
    private static final int DEDUP_CACHE_SIZE = 1_000;

    public enum Phase { ELECTING, BUILDING, READY }

    public static final int BROADCAST_DEST = -1;

    private int nodeId;
    private final List<Integer> neighbors;
    private final PhysicalNode transport;

    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "stm-node-" + nodeId);
                t.setDaemon(true);
                return t;
            });

    private final Object stateLock = new Object();

    private Phase phase = Phase.ELECTING;
    private int treeVersion = 0;
    private int bestRootId;
    private int parentId = -1;
    private final Set<Integer> children = new HashSet<>();
    private int pendingAcks = 0;

    private ScheduledFuture<?> electionFinalizer;
    private ScheduledFuture<?> buildingFinalizer;

    private final Map<Integer, Long> lastHeartbeatReceived = new ConcurrentHashMap<>();
    private ScheduledFuture<?> hbSender;
    private ScheduledFuture<?> hbChecker;

    private final Queue<Envelope> dataBacklog = new ConcurrentLinkedQueue<>();

    private final Set<String> seenDataIds = Collections.synchronizedSet(
            Collections.newSetFromMap(new LinkedHashMap<>(DEDUP_CACHE_SIZE + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > DEDUP_CACHE_SIZE;
                }
            }));

    private volatile MessageHandler appHandler;

    public SpanningTreeManager(int nodeId, List<Integer> neighbors, PhysicalNode transport) {
        this.nodeId = nodeId;
        this.neighbors = neighbors;
        this.transport = transport;
        this.bestRootId = nodeId;
    }

    public void start(MessageHandler handler) {
        this.appHandler = handler;
        startElection(0);
    }

    private void startElection(int version) {
        synchronized (stateLock) {
            if (version < treeVersion) { return; }
            treeVersion = version;
            phase = Phase.ELECTING;
            bestRootId = nodeId;
            parentId = -1;
            children.clear();
            pendingAcks = 0;
            if (buildingFinalizer != null) { buildingFinalizer.cancel(false); buildingFinalizer = null; }
        }

        LOG.info(() -> String.format("[%d] Election started (v%d)", nodeId, version));
        transport.broadcast(Envelope.election(nodeId, nodeId, version));
        scheduleElectionFinalizer(version);
    }

    private void scheduleElectionFinalizer(int version) {
        synchronized (stateLock) {
            if (electionFinalizer != null) electionFinalizer.cancel(false);
            electionFinalizer = scheduler.schedule(() -> finalizeElection(version), ELECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }
    }

    private void scheduleBuildingFinalizer(int version) {
        synchronized (stateLock) {
            if (buildingFinalizer != null) buildingFinalizer.cancel(false);
            buildingFinalizer = scheduler.schedule(() -> forceBuildingComplete(version), BUILDING_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }
    }

    private void forceBuildingComplete(int version) {
        synchronized (stateLock) {
            if (version != treeVersion || phase != Phase.BUILDING) return;
            if (pendingAcks > 0) {
                LOG.warning(() -> String.format("[%d] Building timeout v%d — %d silent neighbors ignored, passing to READY phase", nodeId, version, pendingAcks));
            }
            transitionToReady(version);
        }
    }

    private void finalizeElection(int version) {
        int root;
        synchronized (stateLock) {
            if (version != treeVersion || phase != Phase.ELECTING) { return; }
            phase = Phase.BUILDING;
            root  = bestRootId;
        }

        if (root == nodeId) {
            LOG.info(() -> String.format("[%d] Elected as root (v%d)", nodeId, root));
            buildTreeAsRoot(version);
        } else {
            LOG.info(() -> String.format("[%d] Waiting for TREE_DISCOVER from root %d (v%d)", nodeId, root, version));
            scheduleBuildingFinalizer(version);
        }
    }

    private void buildTreeAsRoot(int version) {
        synchronized (stateLock) {
            if (version != treeVersion) { return; }
            parentId = -1;
            pendingAcks = neighbors.size();
        }

        if (neighbors.isEmpty()) {
            synchronized (stateLock) { transitionToReady(version); }
            return;
        }

        scheduleBuildingFinalizer(version);

        Envelope discover = Envelope.treeDiscover(nodeId, nodeId, version, 0);
        for (int n : neighbors) trySend(n, discover);
    }

    public void handleEnvelope(Envelope env, int fromNeighbor) {
        switch (env.getType()) {
            case ELECTION -> onElection(env, fromNeighbor);
            case TREE_DISCOVER -> onTreeDiscover(env, fromNeighbor);
            case TREE_PARENT_ACK -> onTreeParentAck(env, fromNeighbor);
            case TREE_REJECT -> onTreeReject(env);
            case DATA -> onData(env, fromNeighbor);
            case HEARTBEAT -> onHeartbeat(fromNeighbor);
            case HEARTBEAT_ACK -> onHeartbeatAck(env);
            case TOPOLOGY_REBUILD -> onTopologyRebuild(env);
        }
    }

    private void onElection(Envelope env, int from) {
        int candidate = env.getRootCandidateId();
        int version = env.getTreeVersion();
        boolean triggerRebuildNeeded = false;

        synchronized (stateLock) {
            if (version < treeVersion) { return; }

            if (version > treeVersion) {
                treeVersion = version;
                phase = Phase.ELECTING;
                bestRootId = nodeId;
                if (electionFinalizer != null) { electionFinalizer.cancel(false); }
                scheduleElectionFinalizer(version);
            }

            if (phase != Phase.ELECTING) {
                triggerRebuildNeeded = true;
            } else {
                if (candidate >= bestRootId) { return; }
                bestRootId = candidate;
            }
        }

        if (triggerRebuildNeeded) { triggerRebuild(); return; }

        Envelope forward = Envelope.election(nodeId, candidate, version);
        for (int n : neighbors) { if (n != from) { trySend(n, forward); } }

        scheduleElectionFinalizer(version);
    }

    private void onTreeDiscover(Envelope env, int from) {
        int version = env.getTreeVersion();
        int root = env.getRootCandidateId();

        synchronized (stateLock) {
            if (version < treeVersion) {
                trySend(from, Envelope.treeReject(nodeId, treeVersion));
                return;
            }

            if (version > treeVersion) {
                treeVersion = version;
                phase = Phase.BUILDING;
                bestRootId = root;
                parentId = -1;
                children.clear();
                if (electionFinalizer != null) { electionFinalizer.cancel(false); }
            }

            if (phase == Phase.ELECTING) {
                phase = Phase.BUILDING;
                bestRootId = root;
                if (electionFinalizer != null) { electionFinalizer.cancel(false); }
            }

            if (parentId != -1) {
                trySend(from, Envelope.treeReject(nodeId, version));
                return;
            }

            parentId = from;
            pendingAcks = neighbors.size() - 1;
        }

        trySend(from, Envelope.treeParentAck(nodeId, version));
        scheduleBuildingFinalizer(version);

        Envelope discover = Envelope.treeDiscover(nodeId, root, version, env.getLevel() + 1);
        for (int n : neighbors) { if (n != from) { trySend(n, discover); } }

        synchronized (stateLock) { if (pendingAcks == 0) { transitionToReady(version); } }
    }

    private void onTreeParentAck(Envelope env, int from) {
        synchronized (stateLock) {
            if (env.getTreeVersion() != treeVersion || phase != Phase.BUILDING) { return; }
            children.add(from);
            pendingAcks--;
            if (pendingAcks <= 0) { transitionToReady(treeVersion); }
        }
    }

    private void onTreeReject(Envelope env) {
        synchronized (stateLock) {
            if (env.getTreeVersion() != treeVersion || phase != Phase.BUILDING) { return; }
            pendingAcks--;
            if (pendingAcks <= 0) { transitionToReady(treeVersion); }
        }
    }

    private void transitionToReady(int version) {
        if (phase == Phase.READY) { return; }
        phase = Phase.READY;
        if (buildingFinalizer != null) { buildingFinalizer.cancel(false); buildingFinalizer = null; }

        int parentSnap = parentId;
        Set<Integer> childrenSnap = Set.copyOf(children);
        int versionSnap = version;

        scheduler.execute(() -> {
            LOG.info(() -> String.format(
                    "[%d] READY v%d | parent=%s | childs=%s",
                    nodeId, versionSnap,
                    parentSnap == -1 ? "ROOT" : String.valueOf(parentSnap),
                    childrenSnap));
            startHeartbeat();
            flushDataBacklog();
        });
    }

    public void sendData(int destinationId, String payload) {
        Envelope env = Envelope.data(nodeId, nodeId, destinationId, payload, treeVersion);

        if (getPhase() != Phase.READY) {
            dataBacklog.add(env);
            return;
        }
        floodOnTree(env, -1);
    }

    private void onData(Envelope env, int from) {
        String mid = env.getMessageId();
        if (mid != null && !seenDataIds.add(mid)) {
            LOG.fine(() -> String.format("[%d] Duplicate data id %s", nodeId, mid));
            return;
        }

        boolean isBroadcast = env.getDataDestId() == BROADCAST_DEST;
        boolean isForMe = env.getDataDestId() == nodeId;

        if (isForMe || isBroadcast) {
            MessageHandler h = appHandler;
            if (h != null) { h.onMessage(new Message(env.getDataSourceId(), env.getDataDestId(), env.getPayload())); }
        }

        if (!isForMe) {
            if (getPhase() == Phase.READY) { floodOnTree(env, from); } else { dataBacklog.add(env); }
        }
    }

    private void floodOnTree(Envelope env, int except) {
        Envelope forwarded = env.withSender(nodeId);

        Set<Integer> treeNeighbors;
        synchronized (stateLock) {
            treeNeighbors = new HashSet<>(children);
            if (parentId != -1) { treeNeighbors.add(parentId); }
        }

        for (int n : treeNeighbors) { if (n != except) trySend(n, forwarded); }
    }

    private void flushDataBacklog() {
        Envelope env;
        while ((env = dataBacklog.poll()) != null) {
            boolean isBroadcast = env.getDataDestId() == BROADCAST_DEST;
            boolean isForMe = env.getDataDestId() == nodeId;

            if (isForMe || isBroadcast) {
                MessageHandler h = appHandler;
                if (h != null) { h.onMessage(new Message(env.getDataSourceId(), env.getDataDestId(), env.getPayload())); }
            }
            if (!isForMe) { floodOnTree(env, -1); }
        }
    }

    private synchronized void startHeartbeat() {
        stopHeartbeatUnsafe();

        Set<Integer> treeNeighbors;
        synchronized (stateLock) {
            treeNeighbors = new HashSet<>(children);
            if (parentId != -1) { treeNeighbors.add(parentId); }
        }

        long now = System.currentTimeMillis();
        treeNeighbors.forEach(n -> lastHeartbeatReceived.put(n, now));

        int ver = treeVersion;

        hbSender = scheduler.scheduleAtFixedRate(() -> {
            Set<Integer> current;
            synchronized (stateLock) {
                current = new HashSet<>(children);
                if (parentId != -1) { current.add(parentId); }
            }
            current.forEach(n -> trySend(n, Envelope.heartbeat(nodeId, ver)));
        }, 0, HB_INTERVAL_MS, TimeUnit.MILLISECONDS);

        hbChecker = scheduler.scheduleAtFixedRate(() -> {
            long threshold = System.currentTimeMillis() - HB_TIMEOUT_MS;
            new HashMap<>(lastHeartbeatReceived).forEach((n, ts) -> { if (ts < threshold) { onNeighborFailed(n); } });
        }, HB_TIMEOUT_MS, HB_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private synchronized void stopHeartbeat() {
        stopHeartbeatUnsafe();
        lastHeartbeatReceived.clear();
    }

    private void stopHeartbeatUnsafe() {
        if (hbSender != null) { hbSender.cancel(false); hbSender = null; }
        if (hbChecker != null) { hbChecker.cancel(false); hbChecker = null; }
    }

    private void onHeartbeat(int from) {
        lastHeartbeatReceived.put(from, System.currentTimeMillis());
        trySend(from, Envelope.heartbeatAck(nodeId, treeVersion));
    }

    private void onHeartbeatAck(Envelope env) {
        lastHeartbeatReceived.put(env.getSenderId(), System.currentTimeMillis());
    }

    private void onNeighborFailed(int neighborId) {
        if (lastHeartbeatReceived.remove(neighborId) == null) { return; }
        LOG.warning(() -> String.format("[%d] Neighbor %d failed (no heartbeat)", nodeId, neighborId));
        triggerRebuild();
    }

    private void triggerRebuild() {
        int newVersion;
        synchronized (stateLock) {
            if (phase == Phase.ELECTING) { return; }
            newVersion = treeVersion + 1;
        }

        Envelope rebuildMsg = Envelope.rebuild(nodeId, newVersion);
        transport.broadcast(rebuildMsg);
        applyRebuild(newVersion);
    }

    private void onTopologyRebuild(Envelope env) {
        int incomingVersion = env.getTreeVersion();

        synchronized (stateLock) { if (incomingVersion <= treeVersion) { return; } }

        transport.broadcast(env);
        applyRebuild(incomingVersion);
    }

    private void applyRebuild(int newVersion) {
        synchronized (stateLock) {
            if (newVersion <= treeVersion) { return; }
        }
        stopHeartbeat();
        startElection(newVersion);
    }

    private void trySend(int neighbor, Envelope env) {
        try {
            transport.sendToNeighbor(neighbor, env);
        } catch (IOException e) {
            LOG.log(Level.WARNING, String.format("[%d] Failed to send message to neighbor %d", nodeId, neighbor), e);
        }
    }

    public Phase getPhase() {
        synchronized (stateLock) { return phase; }
    }

    public int getParentId() {
        synchronized (stateLock) { return parentId; }
    }

    public Set<Integer> getChildren() {
        synchronized (stateLock) { return Set.copyOf(children); }
    }

    public int getBestRootId() {
        synchronized (stateLock) { return bestRootId; }
    }

    public int getTreeVersion() {
        synchronized (stateLock) { return treeVersion; }
    }

    public String getStatusSummary() {
        synchronized (stateLock) {
            return String.format(
                    "phase=%-10s v=%-3d root=%-3d parent=%-5s children=%s",
                    phase, treeVersion, bestRootId,
                    parentId == -1 ? "ROOT" : String.valueOf(parentId),
                    children);
        }
    }
}
