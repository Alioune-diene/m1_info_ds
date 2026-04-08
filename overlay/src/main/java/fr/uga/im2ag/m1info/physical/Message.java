package fr.uga.im2ag.m1info.physical;

import java.util.UUID;

/**
 * Represents an application-level message exchanged between physical nodes.
 */
public class Message {
    /**
     * A globally unique identifier for this message (generated automatically).
     */
    private final String messageId;

    /**
     * The ID of the node that originally created this message.
     */
    private final int sourceId;

    /** The ID of the node this message is meant to reach. */
    private final int destinationId;

    /** The actual content of the message */
    private final String payload;

    // -------------------- Constructors -------------------------------------------------------------------------------

    /**
     * Creates a new message with a unique ID.
     * @param sourceId The ID of the node that created this message.
     * @param destinationId The ID of the node this message is meant to reach.
     * @param payload The actual content of the message.
     */
    public Message(int sourceId, int destinationId, String payload) {
        this.messageId = UUID.randomUUID().toString();
        this.sourceId = sourceId;
        this.destinationId = destinationId;
        this.payload = payload;
    }

    // -------------------- Getters ------------------------------------------------------------------------------------

    /**
     * Getters for the message fields. No setters since messages are immutable after creation.
     */
    public String getMessageId() {
        return messageId;
    }

    /**
     * Getters for the message fields. No setters since messages are immutable after creation.
     */
    public int getSourceId() {
        return sourceId;
    }

    /**
     * Getters for the message fields. No setters since messages are immutable after creation.
     */
    public int getDestinationId() {
        return destinationId;
    }

    /** Getters for the message fields. No setters since messages are immutable after creation. */
    public String getPayload() { return payload; }

    @Override
    public String toString() {
        return String.format("Message{id='%s', src=%d, dst=%d, payload='%s'}",
                messageId, sourceId, destinationId, payload);
    }
}