package fr.uga.im2ag.m1info.common.model;

import fr.uga.im2ag.m1info.common.MessageType;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

public class StoredMessage implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String messageId;
    private final int from;
    private final int to;
    private final String content;
    private final MessageType type;
    private final Instant timestamp;
    private final String replyToMessageId;
    private final String conversationId;

    public StoredMessage(String messageId, int from, int to, String content,
                         MessageType type, Instant timestamp,
                         String replyToMessageId, String conversationId) {
        this.messageId = messageId;
        this.from = from;
        this.to = to;
        this.content = content;
        this.type = type;
        this.timestamp = timestamp;
        this.replyToMessageId = replyToMessageId;
        this.conversationId = conversationId;
    }

    public String getMessageId() { return messageId; }
    public int getFrom() { return from; }
    public int getTo() { return to; }
    public String getContent() { return content; }
    public MessageType getType() { return type; }
    public Instant getTimestamp() { return timestamp; }
    public String getReplyToMessageId() { return replyToMessageId; }
    public String getConversationId() { return conversationId; }
}