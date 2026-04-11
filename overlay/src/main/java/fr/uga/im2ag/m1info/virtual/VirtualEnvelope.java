package fr.uga.im2ag.m1info.virtual;

/**
 * The message format used exclusively between virtual nodes and their physical hosts.
 *
 * <p>While the physical layer uses {@code Envelope} for its own protocol (election, tree building, etc.),
 * the virtual layer uses {@code VirtualEnvelope} as its "packet format".
 *
 * <p>Key design: VirtualEnvelopes are NEVER sent directly between physical nodes.
 * Instead, they are serialized to JSON and embedded inside a physical DATA envelope's payload,
 * prefixed with "V|" to distinguish them from regular physical messages.
 *
 * <p>Example of how a VirtualEnvelope travels:
 * <ul>
 *     <li>Virtual node V2 wants to send "hello" to V5</li>
 *     <li>Creates {@code VirtualEnvelope.data(2, 5, ringSize, "hello")}</li>
 *     <li>Serializes: {@code "V|{type:VIRTUAL_DATA, src:2, dst:5, payload:'hello'}"}</li>
 *     <li>Sends to physical host's queue ({@code physical.node.1.virt})</li>
 *     <li>PhysicalHostService receives it, wraps it in a physical DATA broadcast</li>
 *     <li>Spanning tree floods it to all physical nodes</li>
 *     <li>The physical node hosting V5 sees V5 is local, delivers to {@code virtual.node.5}</li>
 * </ul>
 *
 * <p>The "V|" prefix ({@link #PAYLOAD_PREFIX}) is crucial: it allows PhysicalHostService
 * to distinguish virtual messages from regular physical ones just by looking at the payload.
 */
public final class VirtualEnvelope {

    /**
     * The type of this envelope — determines how it will be processed.
     */
    public enum Type {
        /**
         * An application-level message going from one virtual node to another.
         *
         * <p>Direction: VirtualNode → physicalHost → spanning tree → physicalHost → VirtualNode.
         * Fields used: virtualSourceId, virtualDestId, ringSize, payload.
         */
        VIRTUAL_DATA,

        /**
         * A virtual node introduces itself to a physical host.
         * The physical host adds it to hostedVirtuals and replies with VIRTUAL_HEARTBEAT_ACK.
         *
         * <p>Direction: VirtualNode → PhysicalHostService
         * (via {@code physical.node.<id>.virt} queue).
         * Fields used: virtualSourceId, ringSize.
         */
        VIRTUAL_REGISTER,

        /**
         * Periodic keep-alive sent by a virtual node to its current physical host.
         * If the physical host stops replying, the virtual node will migrate.
         *
         * <p>Direction: VirtualNode → PhysicalHostService.
         * Fields used: virtualSourceId.
         */
        VIRTUAL_HEARTBEAT,

        /**
         * Acknowledgment sent by the physical host back to the virtual node.
         * Doubles as the response to both VIRTUAL_REGISTER and VIRTUAL_HEARTBEAT.
         *
         * <p>Direction: PhysicalHostService → VirtualNode
         * (via {@code virtual.node.<id>} queue).
         * Fields used: virtualSourceId, hostPhysicalId.
         */
        VIRTUAL_HEARTBEAT_ACK
    }

    // -------------------- Fields shared by all envelope types --------------------------------------------------------

    /** The type of this envelope, which determines how it should be processed. */
    private Type type;

    /** The ID of the virtual node that created or last sent this envelope. */
    private int virtualSourceId;

    // -------------------- Fields used only for VIRTUAL_DATA ----------------------------------------------------------

    /** The ID of the virtual node that should receive this envelope. */
    private int virtualDestId;

    /** The total number of virtual nodes in the ring, used for ring modulo arithmetic. */
    private int ringSize;

    // -------------------- Fields used only for VIRTUAL_HEARTBEAT_ACK ------------------------------------------------

    /** The ID of the physical node that sent this acknowledgment. */
    private int hostPhysicalId;

    // -------------------- Fields used only for VIRTUAL_DATA ----------------------------------------------------------

    /** The actual text content of the message being sent. */
    private String payload;

    // -------------------- Constants ----------------------------------------------------------------------------------

    /**
     * Prefix that marks a physical DATA payload as containing a VirtualEnvelope.
     * Every serialized VirtualEnvelope starts with "V|" when sent through RabbitMQ.
     */
    public static final String PAYLOAD_PREFIX = "V|";

    // -------------------- Constructors -------------------------------------------------------------------------------

    /**
     * Private constructor to force use of static factory methods for clarity.
     * Each factory method corresponds to a specific envelope type and sets the relevant fields.
     */
    private VirtualEnvelope() {}

    // -------------------- Static factory methods for creating different envelope types -------------------------------

    /**
     * Creates a VIRTUAL_DATA envelope: an application-level message from one virtual node to another.
     *
     * @param sourceId the ID of the sending virtual node
     * @param destId   the ID of the destination virtual node
     * @param ringSize the total number of virtual nodes (needed for ring modulo arithmetic)
     * @param payload  the message content to deliver
     * @return a new VirtualEnvelope instance representing the data message
     */
    public static VirtualEnvelope data(int sourceId, int destId, int ringSize, String payload) {
        VirtualEnvelope e = new VirtualEnvelope();
        e.type = Type.VIRTUAL_DATA;
        e.virtualSourceId = sourceId;
        e.virtualDestId = destId;
        e.ringSize = ringSize;
        e.payload = payload;
        return e;
    }

    /**
     * Creates a VIRTUAL_REGISTER envelope.
     * Sent by a virtual node when it first starts up (or after migrating to a new host).
     * Tells the physical host "I exist, please track me."
     *
     * @param virtualId the virtual node's own ID
     * @param ringSize  the total number of virtual nodes in the ring
     * @return a new VirtualEnvelope instance representing the registration request
     */
    public static VirtualEnvelope register(int virtualId, int ringSize) {
        VirtualEnvelope e = new VirtualEnvelope();
        e.type = Type.VIRTUAL_REGISTER;
        e.virtualSourceId = virtualId;
        e.ringSize = ringSize;
        return e;
    }

    /**
     * Creates a VIRTUAL_HEARTBEAT envelope.
     * Sent periodically by a virtual node to its current physical host.
     * If the host stops responding, the virtual node migrates to another physical node.
     *
     * @param virtualId the ID of the virtual node sending the heartbeat
     * @return a new VirtualEnvelope instance representing the heartbeat signal
     */
    public static VirtualEnvelope heartbeat(int virtualId) {
        VirtualEnvelope e = new VirtualEnvelope();
        e.type = Type.VIRTUAL_HEARTBEAT;
        e.virtualSourceId = virtualId;
        return e;
    }

    /**
     * Creates a VIRTUAL_HEARTBEAT_ACK envelope.
     * Sent by the physical host to confirm it is alive and still hosting this virtual node.
     * Also informs the virtual node which physical node is currently its host.
     *
     * @param virtualId      the ID of the virtual node being acknowledged
     * @param physicalHostId the ID of the physical node sending this acknowledgment
     * @return a new VirtualEnvelope instance representing the heartbeat acknowledgment
     */
    public static VirtualEnvelope heartbeatAck(int virtualId, int physicalHostId) {
        VirtualEnvelope e = new VirtualEnvelope();
        e.type = Type.VIRTUAL_HEARTBEAT_ACK;
        e.virtualSourceId = virtualId;
        e.hostPhysicalId = physicalHostId;
        return e;
    }

    // -------------------- Getters ------------------------------------------------------------------------------------

    /**
     * Gets the type of this envelope, which determines how it should be processed by the receiving node.
     *
     * @return the type of this envelope
     */
    public Type getType() { return type; }

    /**
     * Gets the ID of the virtual node that created or last sent this envelope.
     *
     * @return the virtual source node ID
     */
    public int getVirtualSourceId() { return virtualSourceId; }

    /**
     * Gets the ID of the virtual node that should receive this envelope.
     * Relevant only for {@link Type#VIRTUAL_DATA} envelopes.
     *
     * @return the virtual destination node ID
     */
    public int getVirtualDestId() { return virtualDestId; }

    /**
     * Gets the total number of virtual nodes in the ring.
     * Used for ring modulo arithmetic when computing left and right neighbors.
     *
     * @return the ring size
     */
    public int getRingSize() { return ringSize; }

    /**
     * Gets the ID of the physical node that sent this acknowledgment.
     * Relevant only for {@link Type#VIRTUAL_HEARTBEAT_ACK} envelopes.
     *
     * @return the physical host node ID
     */
    public int getHostPhysicalId() { return hostPhysicalId; }

    /**
     * Gets the actual text content of the message being sent.
     * Relevant only for {@link Type#VIRTUAL_DATA} envelopes.
     *
     * @return the message payload, or {@code null} if this is not a data envelope
     */
    public String getPayload() { return payload; }

    @Override
    public String toString() {
        return String.format("VirtualEnvelope{type=%-24s src=%d dst=%d ring=%d host=%d}",
                type, virtualSourceId, virtualDestId, ringSize, hostPhysicalId);
    }
}
