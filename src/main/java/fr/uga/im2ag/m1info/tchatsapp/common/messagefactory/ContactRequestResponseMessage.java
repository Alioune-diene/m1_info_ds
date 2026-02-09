package fr.uga.im2ag.m1info.tchatsapp.common.messagefactory;

import fr.uga.im2ag.m1info.tchatsapp.common.MessageType;

/**
 * Message representing a contact response.
 */
public class ContactRequestResponseMessage extends AbstractSerializableMessage {
    private String requestId;
    private boolean accepted;

    /**
     * Default constructor.
     */
    public ContactRequestResponseMessage() {
        super(MessageType.CONTACT_REQUEST, -1, -1);
        this.requestId = null;
        this.accepted = false;
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
     * Check if the request was accepted.
     *
     * @return true if accepted
     */
    public boolean isAccepted() {
        return accepted;
    }

    /**
     * Set the request ID.
     *
     * @param requestId the request ID
     * @return this message for chaining
     */
    public ContactRequestResponseMessage setRequestId(String requestId) {
        this.requestId = requestId;
        return this;
    }

    /**
     * Set whether the request was accepted.
     *
     * @param accepted true if accepted
     * @return this message for chaining
     */
    public ContactRequestResponseMessage setAccepted(boolean accepted) {
        this.accepted = accepted;
        return this;
    }

    // ========================= Serialization Methods =========================

    @Override
    protected void serializeContent(StringBuilder sb) {
        joinFields(sb, requestId != null ? requestId : messageId, accepted ? "1" : "0");
    }

    @Override
    protected void deserializeContent(String[] parts, int startIndex) {
        this.requestId = parts[startIndex];
        this.accepted = "1".equals(parts[startIndex + 1]);
    }

    @Override
    protected int getExpectedPartCount() {
        return 4;
    }
}