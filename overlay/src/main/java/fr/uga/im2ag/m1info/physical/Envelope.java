package fr.uga.im2ag.m1info.physical;

import java.util.UUID;

public final class Envelope {
    public enum Type {
        /** Annonce un candidat pour l'élection du nœud racine (min-ID gagne). */
        ELECTION,
        /** La racine propose à ses voisins de rejoindre l'arbre (BFS). */
        TREE_DISCOVER,
        /** Un nœud accepte la proposition de parenté. */
        TREE_PARENT_ACK,
        /** Un nœud rejette la proposition (parent déjà assigné). */
        TREE_REJECT,
        /** Message applicatif routé sur les arêtes de l'arbre. */
        DATA,
        /** Signal de vie envoyé aux voisins de l'arbre. */
        HEARTBEAT,
        /** Réponse à un heartbeat. */
        HEARTBEAT_ACK,
        /** Demande de reconstruction de l'arbre (nœud mort détecté). */
        TOPOLOGY_REBUILD
    }

    private Type type;
    private int senderId;
    private int treeVersion;

    // ELECTION / TREE_DISCOVER
    private int rootCandidateId;
    private int level;

    // DATA
    private String messageId;
    private int dataSourceId;
    private int dataDestId;
    private String payload;

    private Envelope() {}

    public static Envelope election(int senderId, int rootCandidateId, int treeVersion) {
        Envelope e = new Envelope();
        e.type = Type.ELECTION;
        e.senderId = senderId;
        e.rootCandidateId = rootCandidateId;
        e.treeVersion = treeVersion;
        return e;
    }

    public static Envelope treeDiscover(int senderId, int rootCandidateId, int treeVersion, int level) {
        Envelope e = new Envelope();
        e.type = Type.TREE_DISCOVER;
        e.senderId = senderId;
        e.rootCandidateId = rootCandidateId;
        e.treeVersion = treeVersion;
        e.level = level;
        return e;
    }

    public static Envelope treeParentAck(int senderId, int treeVersion) {
        Envelope e = new Envelope();
        e.type = Type.TREE_PARENT_ACK;
        e.senderId = senderId;
        e.treeVersion = treeVersion;
        return e;
    }

    public static Envelope treeReject(int senderId, int treeVersion) {
        Envelope e = new Envelope();
        e.type = Type.TREE_REJECT;
        e.senderId = senderId;
        e.treeVersion = treeVersion;
        return e;
    }

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

    public static Envelope heartbeat(int senderId, int treeVersion) {
        Envelope e = new Envelope();
        e.type = Type.HEARTBEAT;
        e.senderId = senderId;
        e.treeVersion = treeVersion;
        return e;
    }

    public static Envelope heartbeatAck(int senderId, int treeVersion) {
        Envelope e = new Envelope();
        e.type = Type.HEARTBEAT_ACK;
        e.senderId = senderId;
        e.treeVersion = treeVersion;
        return e;
    }

    public static Envelope rebuild(int senderId, int newTreeVersion) {
        Envelope e = new Envelope();
        e.type = Type.TOPOLOGY_REBUILD;
        e.senderId = senderId;
        e.treeVersion = newTreeVersion;
        return e;
    }

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
