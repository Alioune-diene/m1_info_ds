package fr.uga.im2ag.m1info.tchatsapp.client.model;

import java.time.Instant;

/**
 * Represents a contact request from one user to another.
 */
public class ContactRequest {
    /**
     * Status of a contact request.
     */
    public enum Status {
        PENDING,
        ACCEPTED,
        REJECTED,
        EXPIRED
    }

    private final String requestId;
    private final int senderId;
    private final int receiverId;
    private final Instant timestamp;
    private final Instant expiresAt;
    private Status status;

    /**
     * Create a new contact request.
     *
     * @param requestId unique identifier for this request
     * @param senderId ID of the user sending the request
     * @param receiverId ID of the user receiving the request
     * @param timestamp when the request was created
     * @param expiresAt when the request expires
     */
    public ContactRequest(String requestId, int senderId, int receiverId, Instant timestamp, Instant expiresAt) {
        this.requestId = requestId;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.timestamp = timestamp;
        this.expiresAt = expiresAt;
        this.status = Status.PENDING;
    }

    public String getRequestId() {
        return requestId;
    }

    public int getSenderId() {
        return senderId;
    }

    public int getReceiverId() {
        return receiverId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    /**
     * Check if this request has expired.
     *
     * @return true if the current time is after expiresAt and status is still PENDING
     */
    public boolean isExpired() {
        return status == Status.PENDING && Instant.now().isAfter(expiresAt);
    }

    /**
     * Check if this request is still active (pending and not expired).
     *
     * @return true if pending and not expired
     */
    public boolean isActive() {
        return status == Status.PENDING && !isExpired();
    }
}