package fr.uga.im2ag.m1info.virtual;

public final class VirtualEnvelope {
    public static final String PAYLOAD_PREFIX = "V|";

    public enum Type {
        /**
         * Message applicatif de l'anneau (sendLeft / sendRight).
         * Champs utilisés : virtualSourceId, virtualDestId, ringSize, payload.
         */
        VIRTUAL_DATA,

        /**
         * Le nœud virtuel s'enregistre auprès d'un physique hôte.
         * Déclenche la réponse VIRTUAL_HEARTBEAT_ACK si le physique accepte.
         * Champs : virtualSourceId, ringSize.
         */
        VIRTUAL_REGISTER,

        /**
         * Signal de vie du virtuel vers son hôte physique.
         * Champs : virtualSourceId.
         */
        VIRTUAL_HEARTBEAT,

        /**
         * Réponse du physique hôte au heartbeat (ou au REGISTER).
         * Publiée directement dans {@code virtual.node.{virtualSourceId}}.
         * Champs : virtualSourceId (le virtuel concerné), hostPhysicalId.
         */
        VIRTUAL_HEARTBEAT_ACK
    }

    private Type type;
    private int virtualSourceId;
    private int virtualDestId;
    private int ringSize;
    private int hostPhysicalId;
    private String payload;

    private VirtualEnvelope() {}

    public static VirtualEnvelope data(int sourceId, int destId, int ringSize, String payload) {
        VirtualEnvelope e = new VirtualEnvelope();
        e.type = Type.VIRTUAL_DATA;
        e.virtualSourceId = sourceId;
        e.virtualDestId = destId;
        e.ringSize = ringSize;
        e.payload = payload;
        return e;
    }

    public static VirtualEnvelope register(int virtualId, int ringSize) {
        VirtualEnvelope e = new VirtualEnvelope();
        e.type = Type.VIRTUAL_REGISTER;
        e.virtualSourceId = virtualId;
        e.ringSize = ringSize;
        return e;
    }

    public static VirtualEnvelope heartbeat(int virtualId) {
        VirtualEnvelope e = new VirtualEnvelope();
        e.type = Type.VIRTUAL_HEARTBEAT;
        e.virtualSourceId = virtualId;
        return e;
    }

    public static VirtualEnvelope heartbeatAck(int virtualId, int physicalHostId) {
        VirtualEnvelope e = new VirtualEnvelope();
        e.type = Type.VIRTUAL_HEARTBEAT_ACK;
        e.virtualSourceId = virtualId;
        e.hostPhysicalId  = physicalHostId;
        return e;
    }

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
