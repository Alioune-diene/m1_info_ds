package fr.uga.im2ag.m1info.virtual;
/**
 * The message format used exclusively between virtual nodes and their physical hosts.
 *
 * While the physical layer uses `Envelope` for its own protocol (election, tree building, ..),
 * the virtual layer uses `VirtualEnvelope` as its "packet format".
 *
 * Key design: VirtualEnvelopes are NEVER sent directly between physical nodes.
 * Instead, they are serialized to JSON and embedded inside a physical DATA envelope's payload,
 * prefixed with "V|" to distinguish them from regular physical messages.
 *
 * Example of how a VirtualEnvelope travels:
 *   Virtual node V2 wants to send "hello" to V5
 *   creates VirtualEnvelope.data(2, 5, ringSize, "hello")
 *   serializes: "V|{type:VIRTUAL_DATA, src:2, dst:5, payload:'hello'}"
 *   sends to physical host's queue (physical.node.1.virt)
 *   PhysicalHostService receives it, wraps it in a physical DATA broadcast
 *   Spanning tree floods it to all physical nodes
 *   The physical node hosting V5 sees V5 is local, delivers to virtual.node.5
 *
 * The "V|" prefix (PAYLOAD_PREFIX) is crucial: it allows PhysicalHostService
 * to distinguish virtual messages from regular physical ones just by looking at the payload.
 */
public final class VirtualEnvelope {
     /**
     * Prefix that marks a physical payload as containing a VirtualEnvelope.
     * Every serialized VirtualEnvelope starts with "V|" when sent through RabbitMQ.
     */
    public static final String PAYLOAD_PREFIX = "V|";

    public enum Type {
        /**
         * An application-level message going from one virtual node to another.
         * Direction: VirtualNode → physicalHost → spanning tree → physicalHost → VirtualNode
         * Fields used: virtualSourceId, virtualDestId, ringSize, payload
         */
        VIRTUAL_DATA,

        /**
         * A virtual node introduces itself to a physical host.
         * The physical host adds it to hostedVirtuals and replies with VIRTUAL_HEARTBEAT_ACK.
         * Direction: VirtualNode → PhysicalHostService (via physical.node.<id>.virt queue)
         * Fields used: virtualSourceId, ringSize
         */
        VIRTUAL_REGISTER,
 /**
         * Periodic keep-alive sent by a virtual node to its current physical host.
         * If the physical host stops replying, the virtual node will migrate.
         * Direction: VirtualNode → PhysicalHostService
         * Fields used: virtualSourceId
         */
        VIRTUAL_HEARTBEAT,

        /**
         * Acknowledgment sent by the physical host back to the virtual node.
         * Doubles as the response to both VIRTUAL_REGISTER and VIRTUAL_HEARTBEAT.
         * Direction: PhysicalHostService → VirtualNode (via virtual.node.<id> queue)
         * Fields used: virtualSourceId, hostPhysicalId
         */
        VIRTUAL_HEARTBEAT_ACK
    }
     //Message fields

    private Type type;
    private int virtualSourceId;// ID of the virtual node sending this envelope
    private int virtualDestId;// ID of the virtual node that should receive this (for VIRTUAL_DATA)
    private int ringSize;    // total number of virtual nodes in the ring (used at registration)
    private int hostPhysicalId; // physical node ID of the responding host (used in ACK)
    private String payload; // the actual text content (for VIRTUAL_DATA)

    private VirtualEnvelope() {}
    
    
     //Creates a VIRTUAL_DATA envelope: a message from one virtual node to another
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
     * Sent periodically by a virtual node to its physical host.
     * If the host stops responding, the virtual node migrates to another physical node.
     */
    public static VirtualEnvelope heartbeat(int virtualId) {
        VirtualEnvelope e = new VirtualEnvelope();
        e.type = Type.VIRTUAL_HEARTBEAT;
        e.virtualSourceId = virtualId;
        return e;
    }

    /**
     * Creates a VIRTUAL_HEARTBEAT_ACK envelope.
     * Sent by the physical host to confirm it's alive and still hosting this virtual node.
     * Also tells the virtual node which physical node it's currently hosted on.
     */

    public static VirtualEnvelope heartbeatAck(int virtualId, int physicalHostId) {
        VirtualEnvelope e = new VirtualEnvelope();
        e.type = Type.VIRTUAL_HEARTBEAT_ACK;
        e.virtualSourceId = virtualId;
        e.hostPhysicalId  = physicalHostId;
        return e;
    }
  
     // Getter

    public Type getType() { return type; }
    public int getVirtualSourceId() { return virtualSourceId; }
    public int getVirtualDestId() { return virtualDestId; }
    public int getRingSize() { return ringSize; }
    public int getHostPhysicalId() { return hostPhysicalId; }
    public String getPayload() { return payload; }

    @Override
    public String toString() {
        return String.format("VirtualEnvelope{type=%-24s src=%d dst=%d ring=%d host=%d}",
                type, virtualSourceId, virtualDestId, ringSize, hostPhysicalId);
    }
}
