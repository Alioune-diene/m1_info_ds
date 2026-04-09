package fr.uga.im2ag.m1info.physical;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages distributed spanning-tree lifecycle for a physical overlay node.
 * <p>
 * This component is responsible for:
 * <ul>
 *   <li>Leader election (minimum node id wins) for a given tree version.</li>
 *   <li>Tree construction (parent/children relationships).</li>
 *   <li>Readiness transition and backlog flushing.</li>
 *   <li>Heartbeat-based neighbor failure detection and topology rebuild.</li>
 *   <li>Tree-based data flooding with message de-duplication.</li>
 * </ul>
 * <p>
 * The class is thread-safe for state mutations by guarding core mutable fields with
 * {@code stateLock}. Network I/O is delegated to {@link PhysicalNode}.
 */
public class SpanningTreeManager {
    private static final Logger LOG = Logger.getLogger(SpanningTreeManager.class.getName());

    /**
     * Delay before ending election and transitioning to BUILDING phase.
     */
    private static final long ELECTION_TIMEOUT_MS = 3_000;
    /** Maximum time allowed for tree-building acknowledgments. */
    private static final long BUILDING_TIMEOUT_MS = 5_000;
    /** Heartbeat send interval once the tree is READY. */
    private static final long HB_INTERVAL_MS = 2_000;
    /** Timeout after which a neighbor is considered failed if no heartbeat is seen. */
    private static final long HB_TIMEOUT_MS = 6_000;
    /** Maximum number of recently seen DATA message ids kept for duplicate suppression. */
    private static final int DEDUP_CACHE_SIZE = 1_000;

    /**
     * Internal protocol phase.
     * <ul>
     *   <li>{@code ELECTING}: selecting root for current version.</li>
     *   <li>{@code BUILDING}: assigning parent/children edges.</li>
     *   <li>{@code READY}: tree operational for data forwarding and heartbeat checks.</li>
     * </ul>
     */
    public enum Phase {
        /**
         * Initial phase where nodes exchange ELECTION messages to determine the root.
         */
        ELECTING,

        /**
         * Intermediate phase where nodes establish parent-child relationships based on the elected root.
         * Nodes that receive TREE_DISCOVER messages decide whether to accept (become child) or reject (stay out of tree).
         * BUILDING ends when all pending acknowledgments are received or a timeout occurs.
         */
        BUILDING,

        /**
         * Final phase where the spanning tree is considered stable and ready for data forwarding.
         * Nodes in this phase respond to heartbeats, forward DATA messages, and monitor neighbor liveness.
         * If a neighbor failure is detected, a topology rebuild is triggered.
         */
        READY
    }

    /** Special destination id used for network-wide broadcast semantics. */
    public static final int BROADCAST_DEST = -1;

    /** This node id in the physical overlay. */
    private int nodeId;
    /** Adjacent neighbor ids reachable via {@link PhysicalNode}. */
    private final List<Integer> neighbors;
    /** Low-level transport abstraction to send/broadcast protocol envelopes. */
    private final PhysicalNode transport;

    /** Scheduler for timeouts, periodic heartbeats, and deferred tasks. */
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "stm-node-" + nodeId);
                t.setDaemon(true);
                return t;
            });

    /** Lock protecting shared protocol state. */
    private final Object stateLock = new Object();

    /** Current protocol phase. */
    private Phase phase = Phase.ELECTING;
    /** Monotonic version of spanning-tree topology. */
    private int treeVersion = 0;
    /** Current best-known root candidate (minimum id wins). */
    private int bestRootId;
    /** Parent id in the tree, or {@code -1} if this node is root/unassigned. */
    private int parentId = -1;
    /** Children ids in the current tree. */
    private final Set<Integer> children = new HashSet<>();
    /** Pending responses from neighbors during BUILDING phase. */
    private int pendingAcks = 0;

    /** Timer ending election for a specific version. */
    private ScheduledFuture<?> electionFinalizer;
    /** Timer forcing BUILDING completion if neighbors stay silent. */
    private ScheduledFuture<?> buildingFinalizer;

    /** Last heartbeat timestamp per tree-neighbor id. */
    private final Map<Integer, Long> lastHeartbeatReceived = new ConcurrentHashMap<>();
    /** Periodic heartbeat sender task. */
    private ScheduledFuture<?> hbSender;
    /** Periodic heartbeat timeout checker task. */
    private ScheduledFuture<?> hbChecker;

    /** DATA envelopes queued while phase is not READY. */
    private final Queue<Envelope> dataBacklog = new ConcurrentLinkedQueue<>();

    /**
     * LRU-like set for DATA message ids to avoid duplicate delivery/forwarding.
     * Backed by an access-ordered {@link LinkedHashMap} with bounded size.
     */
    private final Set<String> seenDataIds = Collections.synchronizedSet(
            Collections.newSetFromMap(new LinkedHashMap<>(DEDUP_CACHE_SIZE + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > DEDUP_CACHE_SIZE;
                }
            }));

    /** Application-level callback for delivered messages. */
    private volatile MessageHandler appHandler;

    /**
     * Creates a spanning-tree manager for one node.
     *
     * @param nodeId this node id
     * @param neighbors direct physical neighbors
     * @param transport network transport used for envelope exchange
     */
    public SpanningTreeManager(int nodeId, List<Integer> neighbors, PhysicalNode transport) {
        this.nodeId = nodeId;
        this.neighbors = neighbors;
        this.transport = transport;
        this.bestRootId = nodeId;
    }

    /**
     * Starts protocol participation and triggers election at version 0.
     * @param handler application callback for delivered DATA payloads
     */
    public void start(MessageHandler handler) {
        this.appHandler = handler;
        startElection(0);
    }

    /**
     * Replaces application message handler without restarting protocol.
     * @param handler new application callback
     */
    public void setHandler(MessageHandler handler) {
        this.appHandler = handler;
    }

    /**
     * Initializes or restarts election for the given tree version.
     * Resets local tree state, broadcasts own candidacy, and schedules finalization.
     * @param version target tree version
     */
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

    /**
     * Schedules election finalization for a version (cancels previous one if any).
     * @param version version to finalize after timeout
     */
    private void scheduleElectionFinalizer(int version) {
        synchronized (stateLock) {
            if (electionFinalizer != null) electionFinalizer.cancel(false);
            electionFinalizer = scheduler.schedule(() -> finalizeElection(version), ELECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Schedules BUILDING timeout handler for a version (cancels previous one if any).
     * @param version version to force-complete if acks do not arrive
     */
    private void scheduleBuildingFinalizer(int version) {
        synchronized (stateLock) {
            if (buildingFinalizer != null) buildingFinalizer.cancel(false);
            buildingFinalizer = scheduler.schedule(() -> forceBuildingComplete(version), BUILDING_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Transitions BUILDING to READY when timeout fires, tolerating silent neighbors.
     * @param version expected current version
     */
    private void forceBuildingComplete(int version) {
        synchronized (stateLock) {
            if (version != treeVersion || phase != Phase.BUILDING) return;
            if (pendingAcks > 0) {
                LOG.warning(() -> String.format("[%d] Building timeout v%d — %d silent neighbors ignored, passing to READY phase", nodeId, version, pendingAcks));
            }
            transitionToReady(version);
        }
    }

    /**
     * Ends election and starts tree build for winner root.
     * @param version expected version being finalized
     */
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

    /**
     * Root-specific tree construction: sends TREE_DISCOVER to all neighbors.
     * @param version active tree version
     */
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

    /**
     * Entry point for protocol envelope handling.
     * @param env received envelope
     * @param fromNeighbor neighbor id that sent the envelope
     */
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

    /**
     * Handles election gossip and updates best root candidate.
     * <p>
     * If an election message arrives while not ELECTING (same/newer version), this node
     * requests a rebuild to converge all nodes to a coherent phase.
     *
     * @param env ELECTION envelope
     * @param from sender neighbor
     */
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
        for (int n : neighbors) { if (n != from) { trySend(n, forward); }
        }

        scheduleElectionFinalizer(version);
    }

    /**
     * Handles parent assignment and downward discovery propagation during BUILDING.
     * @param env TREE_DISCOVER envelope
     * @param from sender neighbor, candidate parent
     */
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

        synchronized (stateLock) { if (pendingAcks == 0) {
            transitionToReady(version);
        }
        }
    }

    /**
     * Handles acceptance from a child candidate.
     * @param env TREE_PARENT_ACK envelope
     * @param from acknowledging neighbor now considered child
     */
    private void onTreeParentAck(Envelope env, int from) {
        synchronized (stateLock) {
            if (env.getTreeVersion() != treeVersion || phase != Phase.BUILDING) { return; }
            children.add(from);
            pendingAcks--;
            if (pendingAcks <= 0) {
                transitionToReady(treeVersion);
            }
        }
    }

    /**
     * Handles rejection from a neighbor during tree building.
     * @param env TREE_REJECT envelope
     */
    private void onTreeReject(Envelope env) {
        synchronized (stateLock) {
            if (env.getTreeVersion() != treeVersion || phase != Phase.BUILDING) { return; }
            pendingAcks--;
            if (pendingAcks <= 0) {
                transitionToReady(treeVersion);
            }
        }
    }

    /**
     * Enters READY phase once tree-building completes.
     * Starts heartbeat monitoring and flushes queued DATA messages asynchronously.
     *
     * @param version version being finalized
     */
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

    /**
     * Sends application DATA into the tree.
     * If tree is not READY yet, message is queued for later forwarding.
     *
     * @param destinationId target node id, or {@link #BROADCAST_DEST}
     * @param payload message payload
     */
    public void sendData(int destinationId, String payload) {
        Envelope env = Envelope.data(nodeId, nodeId, destinationId, payload, treeVersion);

        if (getPhase() != Phase.READY) {
            dataBacklog.add(env);
            return;
        }
        floodOnTree(env, -1);
    }

    /**
     * Handles incoming DATA envelope with duplicate suppression and local delivery.
     * @param env DATA envelope
     * @param from sender neighbor
     */
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
            if (getPhase() == Phase.READY) { floodOnTree(env, from);
            } else {
                dataBacklog.add(env);
            }
        }
    }

    /**
     * Floods envelope over current tree edges, excluding one neighbor to avoid immediate bounce-back.
     * @param env envelope to forward
     * @param except neighbor id to skip; use {@code -1} to skip none
     */
    private void floodOnTree(Envelope env, int except) {
        Envelope forwarded = env.withSender(nodeId);

        Set<Integer> treeNeighbors;
        synchronized (stateLock) {
            treeNeighbors = new HashSet<>(children);
            if (parentId != -1) { treeNeighbors.add(parentId); }
        }

        for (int n : treeNeighbors) {
            if (n != except) trySend(n, forwarded);
        }
    }

    /**
     * Replays queued DATA once tree becomes READY.
     */
    private void flushDataBacklog() {
        Envelope env;
        while ((env = dataBacklog.poll()) != null) {
            boolean isBroadcast = env.getDataDestId() == BROADCAST_DEST;
            boolean isForMe = env.getDataDestId() == nodeId;

            if (isForMe || isBroadcast) {
                MessageHandler h = appHandler;
                if (h != null) { h.onMessage(new Message(env.getDataSourceId(), env.getDataDestId(), env.getPayload())); }
            }
            if (!isForMe) {
                floodOnTree(env, -1);
            }
        }
    }

    /**
     * Starts heartbeat sender/checker tasks for current tree-neighbor set.
     */
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

    /**
     * Stops heartbeat tasks and forgets all neighbor timestamps.
     */
    private synchronized void stopHeartbeat() {
        stopHeartbeatUnsafe();
        lastHeartbeatReceived.clear();
    }

    /**
     * Stops heartbeat tasks without clearing timestamp map.
     */
    private void stopHeartbeatUnsafe() {
        if (hbSender != null) { hbSender.cancel(false); hbSender = null; }
        if (hbChecker != null) {
            hbChecker.cancel(false);
            hbChecker = null;
        }
    }

    /**
     * Updates liveness for sender and replies with HEARTBEAT_ACK.
     * @param from neighbor that sent HEARTBEAT
     */
    private void onHeartbeat(int from) {
        lastHeartbeatReceived.put(from, System.currentTimeMillis());
        trySend(from, Envelope.heartbeatAck(nodeId, treeVersion));
    }

    /**
     * Updates liveness for sender on HEARTBEAT_ACK reception.
     * @param env HEARTBEAT_ACK envelope
     */
    private void onHeartbeatAck(Envelope env) {
        lastHeartbeatReceived.put(env.getSenderId(), System.currentTimeMillis());
    }

    /**
     * Handles detected neighbor failure and triggers rebuild if still relevant.
     * @param neighborId failed neighbor id
     */
    private void onNeighborFailed(int neighborId) {
        if (lastHeartbeatReceived.remove(neighborId) == null) { return; }
        LOG.warning(() -> String.format("[%d] Neighbor %d failed (no heartbeat)", nodeId, neighborId));
        triggerRebuild();
    }

    /**
     * Starts topology rebuild by incrementing version and broadcasting TOPOLOGY_REBUILD.
     */
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

    /**
     * Processes incoming TOPOLOGY_REBUILD and rebroadcasts newer versions.
     * @param env rebuild envelope
     */
    private void onTopologyRebuild(Envelope env) {
        int incomingVersion = env.getTreeVersion();

        synchronized (stateLock) { if (incomingVersion <= treeVersion) { return; } }

        transport.broadcast(env);
        applyRebuild(incomingVersion);
    }

    /**
     * Applies a rebuild by stopping heartbeat and restarting election for newer version.
     * @param newVersion target new version
     */
    private void applyRebuild(int newVersion) {
        synchronized (stateLock) {
            if (newVersion <= treeVersion) { return; }
        }
        stopHeartbeat();
        startElection(newVersion);
    }

    /**
     * Sends an envelope to a neighbor with warning-level logging on transport error.
     *
     * @param neighbor destination neighbor id
     * @param env envelope to send
     */
    private void trySend(int neighbor, Envelope env) {
        try {
            transport.sendToNeighbor(neighbor, env);
        } catch (IOException e) {
            LOG.log(Level.WARNING, String.format("[%d] Failed to send message to neighbor %d", nodeId, neighbor), e);
        }
    }

    /**
     * Gets current protocol phase.
     * @return current protocol phase
     */
    public Phase getPhase() {
        synchronized (stateLock) {
            return phase;
        }
    }

    /**
     * Gets current parent id in the tree.
     * @return current parent id, or {@code -1} if root/unassigned
     */
    public int getParentId() {
        synchronized (stateLock) {
            return parentId;
        }
    }

    /**
     * Gets current children ids in the tree.
     * @return immutable snapshot of current children set
     */
    public Set<Integer> getChildren() {
        synchronized (stateLock) {
            return Set.copyOf(children);
        }
    }

    /**
     * Gets current best-known root candidate id.
     * @return current best-known root id
     */
    public int getBestRootId() {
        synchronized (stateLock) {
            return bestRootId;
        }
    }

    /**
     * Gets current tree version.
     * @return current tree version
     */
    public int getTreeVersion() {
        synchronized (stateLock) {
            return treeVersion;
        }
    }

    /**
     * Returns a compact textual status snapshot for logging/diagnostics.
     * @return formatted protocol status
     */
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
