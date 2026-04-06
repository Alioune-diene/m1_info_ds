package fr.uga.im2ag.m1info.physical;

import java.util.UUID;
/**
 * Represents an application-level message exchanged between physical nodes.
 */
public class Message {
     // A globally unique identifier for this message (generated automatically).
    private final String messageId;
    // The ID of the node that originally created this message.
    private final int sourceId;
    // The ID of the node this message is meant to reach.
    private final int destinationId;
    // The actual content of the message
    private final String payload;
    // Creates a new message.
    public Message(int sourceId, int destinationId, String payload) {
        this.messageId = UUID.randomUUID().toString();
        this.sourceId = sourceId;
        this.destinationId = destinationId;
        this.payload = payload;
    }
     // Getters
    public String getMessageId() { return messageId; }
    public int getSourceId() { return sourceId; }
    public int getDestinationId() { return destinationId; }
    public String getPayload() { return payload; }

    @Override
    public String toString() {
        return String.format("Message{id='%s', src=%d, dst=%d, payload='%s'}",
                messageId, sourceId, destinationId, payload);
    }
}