package fr.uga.im2ag.m1info.tchatsapp.common.messagefactory;

import fr.uga.im2ag.m1info.tchatsapp.common.MessageType;
import fr.uga.im2ag.m1info.tchatsapp.common.Packet;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Abstract base class for messages that can be serialized to and deserialized from
 * a Packet and raw payload bytes.
 */
public abstract class AbstractSerializableMessage extends ProtocolMessage {

    /**
     * Create a new message instance with the given packet metadata.
     *
     * @param messageType the message type enum identifying the message kind.
     * @param from the sender id.
     * @param to the recipient id.
     */
    protected AbstractSerializableMessage(MessageType messageType, int from, int to) {
        super(messageType, from, to);
    }

    // ========================= Template Methods =========================

    /**
     * Serialize the message into a Packet.
     *
     * @return a Packet containing the serialized payload and metadata.
     */
    @Override
    public final Packet toPacket() {
        validateBeforeSerialize();
        byte[] payload = serializePayload();
        return buildPacket(payload);
    }

    /**
     * Populate this message instance from a Packet.
     *
     * @param packet the incoming packet to parse.
     * @return this message instance populated from the packet.
     */
    @Override
    public final AbstractSerializableMessage fromPacket(Packet packet) {
        readPacketHeader(packet);
        deserializePayload(packet.getModifiablePayload());
        return this;
    }

    // ========================= Payload Serialization Methods =========================

    /**
     * Serialize only the payload bytes without wrapping in a Packet.
     * <p>
     * Useful when payloads need to be encrypted or embedded elsewhere.
     *
     * @return payload bytes.
     */
    public final byte[] toPayloadBytes() {
        validateBeforeSerialize();
        return serializePayload();
    }

    /**
     * Populate this message instance from raw payload bytes and metadata.
     *
     * @param payloadBytes raw UTF-8 payload bytes previously produced by toPayloadBytes().
     * @param type the MessageType to assign.
     * @param from the sender id.
     * @param to the recipient id.
     */
    public final void fromPayloadBytes(byte[] payloadBytes, MessageType type, int from, int to) {
        this.messageType = type;
        this.from = from;
        this.to = to;
        deserializePayload(ByteBuffer.wrap(payloadBytes));
    }

    // ========================= Abstract Methods =========================

    /**
     * Serialize message-specific content into the provided StringBuilder.
     * <p>
     * Implementations must append fields after the common header. Fields should be
     * separated by the '|' character. Do not append the common header here.
     *
     * @param sb the StringBuilder to write content fields into.
     */
    protected abstract void serializeContent(StringBuilder sb);

    /**
     * Deserialize message-specific content from the payload parts array.
     *
     * @param parts the payload split by '|'
     * @param startIndex the index in parts where message-specific fields begin
     */
    protected abstract void deserializeContent(String[] parts, int startIndex);

    /**
     * Return the expected number of parts when splitting the payload string using
     * String.split("\\|", getExpectedPartCount()).
     * <p>Do not forget to count the common header parts (messageId and timestamp) in the total.</p>
     *
     * @return expected part count used for splitting the payload.
     */
    protected abstract int getExpectedPartCount();

    // ========================= Validation Methods =========================

    /**
     * Validate the message state before serialization.
     * <p>
     * Implementations should throw IllegalStateException if required fields are missing.
     */
    protected void validateBeforeSerialize() {
        if (messageId == null) {
            throw new IllegalStateException("Message ID cannot be null");
        }
    }

    // ========================= Private Serialization Helpers =========================

    /**
     * Serialize the entire payload including common header and content.
     *
     * @return serialized payload bytes.
     */
    private byte[] serializePayload() {
        StringBuilder sb = getStringBuilder();
        writeCommonHeader(sb);
        serializeContent(sb);
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Deserialize the entire payload including common header and content.
     *
     * @param buffer ByteBuffer containing the payload bytes.
     */
    private void deserializePayload(ByteBuffer buffer) {
        String payloadStr = StandardCharsets.UTF_8.decode(buffer).toString();
        String[] parts = payloadStr.split("\\|", getExpectedPartCount());
        readCommonHeader(parts);
        deserializeContent(parts, 2);
    }

    // ========================= Protected Helper Methods =========================

    /**
     * Build a Packet with the given payload and current metadata.
     *
     * @param payload the payload bytes.
     * @return constructed Packet.
     */
    protected final Packet buildPacket(byte[] payload) {
        return new Packet.PacketBuilder(payload.length)
                .setMessageType(messageType)
                .setFrom(from)
                .setTo(to)
                .setPayload(payload)
                .build();
    }

    /**
     * Read and set the packet header fields into this message.
     *
     * @param packet the incoming Packet.
     */
    protected final void readPacketHeader(Packet packet) {
        this.messageType = packet.messageType();
        this.from = packet.from();
        this.to = packet.to();
    }

    /**
     * Write the common header fields into the provided StringBuilder.
     *
     * @param sb the StringBuilder to write into.
     */
    protected final void writeCommonHeader(StringBuilder sb) {
        sb.append(messageId).append("|").append(timestamp.toEpochMilli()).append("|");
    }

    /**
     * Read the common header fields from the provided parts array.
     *
     * @param parts the payload split by '|'.
     */
    protected final void readCommonHeader(String[] parts) {
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid payload: missing header fields");
        }
        this.messageId = parts[0];
        this.timestamp = Instant.ofEpochMilli(Long.parseLong(parts[1]));
    }

    /**
     * Utility method to join multiple fields into the StringBuilder separated by '|'.
     *
     * @param sb the StringBuilder to append to.
     * @param fields the fields to join.
     * @param <T> the type of the fields.
     */
    protected final <T> void joinFields(StringBuilder sb, T... fields) {
        for (int i = 0; i < fields.length; i++) {
            sb.append(fields[i]);
            if (i < fields.length - 1) {
                sb.append("|");
            }
        }
    }
}