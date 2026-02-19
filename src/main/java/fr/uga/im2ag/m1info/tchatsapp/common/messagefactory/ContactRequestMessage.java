package fr.uga.im2ag.m1info.tchatsapp.common.messagefactory;

import fr.uga.im2ag.m1info.tchatsapp.common.MessageType;

import java.io.Serial;

/**
 * Message representing a contact request.
 */
public class ContactRequestMessage extends ProtocolMessage {
    @Serial
    private static final long serialVersionUID = 1L;

    private String requestId;
    private long expirationTimestamp;

    /**
     * Default constructor.
     */
    public ContactRequestMessage() {
        super(MessageType.CONTACT_REQUEST, -1, -1);
        this.requestId = null;
        this.expirationTimestamp = 0;
    }

    // ========================= Getters/Setters =========================

    /**
     * Get the request ID.
     *
     * @return the request ID
     */
    public String getRequestId() {
        return requestId != null ? requestId : messageId;
    }

    /**
     * Get the expiration timestamp (only valid for requests).
     *
     * @return expiration timestamp in epoch millis
     */
    public long getExpirationTimestamp() {
        return expirationTimestamp;
    }

    /**
     * Set the request ID.
     *
     * @param requestId the request ID
     * @return this message for chaining
     */
    public ContactRequestMessage setRequestId(String requestId) {
        this.requestId = requestId;
        return this;
    }

    /**
     * Set the expiration timestamp.
     *
     * @param expirationTimestamp expiration time in epoch millis
     * @return this message for chaining
     */
    public ContactRequestMessage setExpirationTimestamp(long expirationTimestamp) {
        this.expirationTimestamp = expirationTimestamp;
        return this;
    }
}