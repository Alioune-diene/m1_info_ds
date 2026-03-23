package fr.uga.im2ag.m1info.physical;

import java.util.UUID;

public class Message {
    private final String messageId;
    private final int sourceId;
    private final int destinationId;
    private final String payload;

    public Message(int sourceId, int destinationId, String payload) {
        this.messageId = UUID.randomUUID().toString();
        this.sourceId = sourceId;
        this.destinationId = destinationId;
        this.payload = payload;
    }

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