package fr.uga.im2ag.m1info.tchatsapp.common.messagefactory;

import fr.uga.im2ag.m1info.tchatsapp.common.MessageType;

import java.io.Serial;
import java.time.Instant;

public class ReactionMessage extends ProtocolMessage {
    @Serial
    private static final long serialVersionUID = 1L;

    private String content;
    private String reactionToMessageId;


    public ReactionMessage() {
        super(MessageType.NONE, -1, -1);
        this.timestamp = Instant.EPOCH;
        this.messageId = null;
        this.content = "";
        this.reactionToMessageId = null;
    }

    public String getContent() {
        return content;
    }

    public ReactionMessage setContent(String content) {
        this.content = content;
        return this;
    }

    public String getReactionToMessageId() {
        return reactionToMessageId;
    }

    public ReactionMessage setReactionToMessageId(String reactionToMessageId) {
        this.reactionToMessageId = reactionToMessageId;
        return this;
    }

    @Override
    public String toString() {
        return "ReactionMessage{" +
                "messageId='" + messageId + '\'' +
                ", from=" + from +
                ", to=" + to +
                ", content='" + (content.length() > 50 ? content.substring(0, 50) + "..." : content) + '\'' +
                ", replyTo=" + reactionToMessageId +
                '}';
    }

}
