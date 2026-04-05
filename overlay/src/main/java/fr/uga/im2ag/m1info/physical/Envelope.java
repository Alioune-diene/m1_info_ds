package fr.uga.im2ag.m1info.physical;

import java.util.UUID;

// The packet that travels between physical nodes through RabbitMQ.
//  Envelope is the network-level packet used internally by the physical layer. 
//  It carries not only data, but also control information for:
//   - electing a root node (ELECTION)
//  - building a spanning tree (TREE_DISCOVER / TREE_PARENT_ACK / TREE_REJECT)
//  - forwarding application data (DATA)
//   - detecting node failures (HEARTBEAT / HEARTBEAT_ACK)
//  - rebuilding the tree after a failure (TOPOLOGY_REBUILD)




public final class Envelope {
     /**
     * The type of this envelope — determines how it will be processed.
     */
    public enum Type {
        /** Announces this node's candidate for root election (lowest ID wins). */
        ELECTION,
        /** The elected root invites its neighbors to join the spanning tree (BFS expansion). */
        TREE_DISCOVER,
        /** A node accepts a parent proposal during tree construction. */
        TREE_PARENT_ACK,
        /** A node rejects a parent proposal (it already has a parent). */
        TREE_REJECT,
        /** An application-level data message routed along the spanning tree. */
        DATA,
        /** A "are you still alive?" signal sent to tree neighbors. */
        HEARTBEAT,
         /** Response to a heartbeat: "yes, I'm still here". */
        HEARTBEAT_ACK,
        /** Triggers a full tree rebuild when a dead node is detected. */
        TOPOLOGY_REBUILD
    }
    // ---- Fields shared by all envelope types ----
    private Type type;
    private int senderId;  // the node that last forwarded this envelope
    private int treeVersion; // used to detect stale messages from old tree builds

     // ---- Fields used only for ELECTION and TREE_DISCOVER ----
    private int rootCandidateId; // the best (lowest) root candidate seen so far
    private int level; // BFS depth level from root (used in TREE_DISCOVER)

    // ---- Fields used only for DATA messages ----
    private String messageId;
    private int dataSourceId;
    private int dataDestId;
    private String payload;

    private Envelope() {}
     /**
     * Creates an ELECTION envelope.
     * Broadcast by every node at startup to propose itself as root.
     * The node with the lowest ID eventually wins.
     */
    public static Envelope election(int senderId, int rootCandidateId, int treeVersion) {
        Envelope e = new Envelope();
        e.type = Type.ELECTION;
        e.senderId = senderId;
        e.rootCandidateId = rootCandidateId;
        e.treeVersion = treeVersion;
        return e;
    }
    /**
     * Creates a TREE_DISCOVER envelope.
     * Sent by the elected root to its neighbors to start building the tree (BFS).
     * Each node that receives this picks the sender as its parent,
     * then forwards TREE_DISCOVER to its own neighbors.
     */
    public static Envelope treeDiscover(int senderId, int rootCandidateId, int treeVersion, int level) {
        Envelope e = new Envelope();
        e.type = Type.TREE_DISCOVER;
        e.senderId = senderId;
        e.rootCandidateId = rootCandidateId;
        e.treeVersion = treeVersion;
        e.level = level;
        return e;
    }
     /**
     * Creates a TREE_PARENT_ACK envelope.
     * Sent back when a node accepts the sender as its parent in the spanning tree.
     */
    public static Envelope treeParentAck(int senderId, int treeVersion) {
        Envelope e = new Envelope();
        e.type = Type.TREE_PARENT_ACK;
        e.senderId = senderId;
        e.treeVersion = treeVersion;
        return e;
    }
     /**
     * Creates a TREE_REJECT envelope.
     * Sent back when a node already has a parent and rejects a new parent proposal.
     */

    public static Envelope treeReject(int senderId, int treeVersion) {
        Envelope e = new Envelope();
        e.type = Type.TREE_REJECT;
        e.senderId = senderId;
        e.treeVersion = treeVersion;
        return e;
    }

      /**
     * Creates a DATA envelope.
     * Used to carry actual application-level messages through the spanning tree.
     */

    public static Envelope data(int senderId, int dataSourceId, int dataDestId, String payload, int treeVersion) {
        Envelope e = new Envelope();
        e.type = Type.DATA;
        e.senderId = senderId;
        e.dataSourceId = dataSourceId;
        e.dataDestId = dataDestId;
        e.payload = payload;
        e.treeVersion = treeVersion;
        e.messageId = UUID.randomUUID().toString();
        return e;
    }
      /**
     * Creates a HEARTBEAT envelope.
     * Sent periodically by each node to its tree neighbors to prove it's alive.
     * If no heartbeat is received within a timeout, the neighbor is declared dead.
     */
    public static Envelope heartbeat(int senderId, int treeVersion) {
        Envelope e = new Envelope();
        e.type = Type.HEARTBEAT;
        e.senderId = senderId;
        e.treeVersion = treeVersion;
        return e;
    }
      /**
     * Creates a HEARTBEAT_ACK envelope.
     * Sent in response to a received HEARTBEAT to confirm "I'm still alive".
     */

    public static Envelope heartbeatAck(int senderId, int treeVersion) {
        Envelope e = new Envelope();
        e.type = Type.HEARTBEAT_ACK;
        e.senderId = senderId;
        e.treeVersion = treeVersion;
        return e;
    }
     /**
     * Creates a TOPOLOGY_REBUILD envelope.
     * Triggered when a node detects that one of its tree neighbors has died.
     * Broadcast to all neighbors to initiate a full re-election and tree rebuild.
     *
     * @param newTreeVersion incremented version number to invalidate all old messages
     */

    public static Envelope rebuild(int senderId, int newTreeVersion) {
        Envelope e = new Envelope();
        e.type = Type.TOPOLOGY_REBUILD;
        e.senderId = senderId;
        e.treeVersion = newTreeVersion;
        return e;
    }
      /**
     * Creates a copy of this envelope with a different senderId.
     * Used when a node forwards an envelope — the content stays the same
     * but the sender field is updated to reflect who is forwarding it.
     */

    public Envelope withSender(int newSenderId) {
        Envelope copy = new Envelope();
        copy.type = this.type;
        copy.senderId = newSenderId;
        copy.treeVersion = this.treeVersion;
        copy.rootCandidateId = this.rootCandidateId;
        copy.level = this.level;
        copy.messageId = this.messageId;
        copy.dataSourceId = this.dataSourceId;
        copy.dataDestId = this.dataDestId;
        copy.payload = this.payload;
        return copy;
    }
      // Getters 
    public Type getType() { return type; }
    public int getSenderId() { return senderId; }
    public int getTreeVersion() { return treeVersion; }
    public int getRootCandidateId() { return rootCandidateId; }
    public int getLevel() { return level; }
    public String getMessageId() { return messageId; }
    public int getDataSourceId() { return dataSourceId; }
    public int getDataDestId() { return dataDestId; }
    public String getPayload() { return payload; }

    @Override
    public String toString() {
        return String.format("Envelope{type=%-16s sender=%d v=%d rootCand=%d dst=%d msgId=%.8s}",
                type, senderId, treeVersion, rootCandidateId, dataDestId, messageId != null ? messageId : "-");
    }
}
