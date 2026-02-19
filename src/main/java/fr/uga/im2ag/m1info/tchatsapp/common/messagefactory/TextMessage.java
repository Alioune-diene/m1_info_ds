package fr.uga.im2ag.m1info.tchatsapp.common.messagefactory;

import fr.uga.im2ag.m1info.tchatsapp.common.MessageType;

import java.io.Serial;
import java.time.Instant;

/**
 * A text message that can contain plain text content and optionally reference
 * another message it is replying to.
 */
public class TextMessage extends ProtocolMessage {
    @Serial
    private static final long serialVersionUID = 1L;

    private String content;
    private String replyToMessageId;

    /** Default constructor for TextMessage.
     * This is used for message factory registration only.
     */
    public TextMessage() {
        super(MessageType.NONE, -1, -1);
        this.timestamp = Instant.EPOCH;
        this.messageId = null;
        this.content = "";
        this.replyToMessageId = null;
    }

    // ========================= Getters/Setters =========================

    /**
     * Get the text content of the message.
     *
     * @return the text content
     */
    public String getContent() {
        return content;
    }

    /** Set the text content of the message.
     *
     * @param content the text content to set
     * @return the TextMessage instance for method chaining
     */
    public TextMessage setContent(String content) {
        this.content = content;
        return this;
    }

    /** Get the ID of the message being replied to.
     *
     * @return the reply-to message ID, or null if not a reply
     */
    public String getReplyToMessageId() {
        return replyToMessageId;
    }

    /**
     * Set the ID of the message being replied to.
     *
     * @param replyToMessageId the reply-to message ID to set
     * @return the TextMessage instance for method chaining
     */
    public TextMessage setReplyToMessageId(String replyToMessageId) {
        this.replyToMessageId = replyToMessageId;
        return this;
    }

    // ========================= Serialization Methods =========================

    @Override
    public String toString() {
        return "TextMessage{" +
                "messageId='" + messageId + '\'' +
                ", from=" + from +
                ", to=" + to +
                ", content='" + (content.length() > 50 ? content.substring(0, 50) + "..." : content) + '\'' +
                ", replyTo=" + replyToMessageId +
                '}';
    }
}