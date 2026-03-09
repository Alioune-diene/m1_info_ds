package fr.uga.im2ag.m1info.overlay.messaging;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

/**
 * A message exchanged between overlay nodes.
 *
 * For ROUTED messages, the path field contains the remaining physical hops.
 * Each intermediate node pops itself from the front of the path and forwards
 * the message to the next hop's RabbitMQ queue.
 */
public class Message {

    public MessageType type;
    public int srcNodeId;
    public int dstNodeId;
    public String payload;

    /**
     * Remaining physical hops for ROUTED messages.
     * e.g. [1, 3, 2, 4] means: currently at 1, next hop is 3, then 2, then 4 (destination).
     */
    public List<Integer> path;

    public Message() {}

    public Message(MessageType type, int src, int dst, String payload, List<Integer> path) {
        this.type = type;
        this.srcNodeId = src;
        this.dstNodeId = dst;
        this.payload = payload;
        this.path = path != null ? new ArrayList<>(path) : new ArrayList<>();
    }

    // --- Serialization (JSON via Gson) ---

    private static final Gson GSON = new Gson();

    public byte[] toBytes() {
        return GSON.toJson(this).getBytes();
    }

    public static Message fromBytes(byte[] bytes) {
        return GSON.fromJson(new String(bytes), Message.class);
    }

    @Override
    public String toString() {
        return "[" + type + " | src=" + srcNodeId + " dst=" + dstNodeId
                + " | path=" + path + " | payload='" + payload + "']";
    }
}
