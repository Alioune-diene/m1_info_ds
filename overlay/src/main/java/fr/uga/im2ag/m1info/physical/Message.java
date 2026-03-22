package fr.uga.im2ag.m1info.physical;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Message {
    private String messageId;
    private int sourceId;
    private int destinationId;
    private String payload;
    private Set<Integer> visited;

    public Message() {}

    public Message(int sourceId, int destinationId, String payload) {
        this.messageId = UUID.randomUUID().toString();
        this.sourceId = sourceId;
        this.destinationId = destinationId;
        this.payload = payload;
        this.visited = new HashSet<>();
    }

    public String getMessageId() { return messageId; }
    public int getSourceId() { return sourceId; }
    public int getDestinationId() { return destinationId; }
    public String getPayload() { return payload; }

    public void markVisited(int nodeId) {
        if (visited == null) visited = new HashSet<>();
        visited.add(nodeId);
    }

    public boolean hasVisited(int nodeId) {
        return visited != null && visited.contains(nodeId);
    }

    public Set<Integer> getVisited() {
        return visited == null ? Collections.emptySet() : Collections.unmodifiableSet(visited);
    }

    @Override
    public String toString() {
        return String.format("Message{id='%s', src=%d, dst=%d, visited=%s, payload='%s'}",
                messageId, sourceId, destinationId, visited, payload);
    }
}