package fr.uga.im2ag.m1info.tchatsapp.common.messagefactory;

import fr.uga.im2ag.m1info.tchatsapp.common.MessageIdGenerator;
import fr.uga.im2ag.m1info.tchatsapp.common.MessageType;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * Abstract class representing a protocol message in the chat service.
 */
public abstract class ProtocolMessage implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private static final ThreadLocal<StringBuilder> STRING_BUILDER = ThreadLocal.withInitial(StringBuilder::new);

    protected static StringBuilder getStringBuilder() {
        StringBuilder sb = STRING_BUILDER.get();
        sb.setLength(0);
        return sb;
    }

    protected MessageType messageType;
    protected int from;
    protected int to;

    protected String messageId;
    protected Instant timestamp;

    /** Constructor for ProtocolMessage.
     *
     * @param messageType the type of the message
     * @param from the sender ID
     * @param to the recipient ID
     */
    public ProtocolMessage(MessageType messageType, int from, int to) {
        this.messageType = messageType;
        this.from = from;
        this.to = to;
        this.messageId = null;
        this.timestamp = Instant.now();
    }

    /** Get the type of the message.
     *
     * @return the message type
     */
    public MessageType getMessageType() {
        return messageType;
    }

    /** Get the sender ID of the message.
     *
     * @return the sender ID
     */
    public int getFrom() {
        return from;
    }

    /** Get the recipient ID of the message.
     *
     * @return the recipient ID
     */
    public int getTo() {
        return to;
    }

    /** Get the message ID.
     *
     * @return the message ID
     */
    public String getMessageId() {
        return messageId;
    }

    /** Get the timestamp of the message.
     *
     * @return the timestamp
     */
    public Instant getTimestamp() {
        return timestamp;
    }

    /** Set the sender ID of the message.
     *
     * @param from the sender ID
     */
    void setFrom(int from) {
        this.from = from;
    }

    /** Set the recipient ID of the message.
     *
     * @param to the recipient ID
     */
    void setTo(int to) {
        this.to = to;
    }

    /** Set the type of the message.
     *
     * @param messageType the message type
     */
    void setMessageType(MessageType messageType) {
        this.messageType = messageType;
    }

    /** Generate a new message ID using the provided MessageIdGenerator.
     * The timestamp is also updated to the current time.
     *
     * @param messageIdGenerator the MessageIdGenerator to use
     * @throws IllegalStateException if the 'from' field is not set
     */
    void generateNewMessageId(MessageIdGenerator messageIdGenerator) {
        if (this.from == -1) { throw new IllegalStateException("Cannot generate message ID: 'from' field is not set."); }
        timestamp = Instant.now();
        messageId = messageIdGenerator.generateId(from, timestamp.toEpochMilli());
    }
}
