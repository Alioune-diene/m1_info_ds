package fr.uga.im2ag.m1info.tchatsapp.client.model;

import fr.uga.im2ag.m1info.tchatsapp.common.MessageStatus;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Message {
    private final String messageId;
    private final int fromUserId;
    private final int toUserId;
    private final String content;
    private final Instant timestamp;
    private final String replyToMessageId;
    private boolean isRead;
    private final Map<String, Set<Integer>> reactions;
    private Media attachedMedia;
    private MessageStatus status;

    public Message(String messageId, int fromUserId, int toUserId, String content, Instant timestamp, String replyToMessageId) {
        this.messageId = messageId;
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.content = content;
        this.timestamp = timestamp;
        this.replyToMessageId = replyToMessageId;
        this.isRead = false;
        this.reactions = new HashMap<>();
        this.status = MessageStatus.SENDING;
    }

    public String getMessageId() {
        return messageId;
    }

    public int getFromUserId() {
        return fromUserId;
    }

    public int getToUserId() {
        return toUserId;
    }

    public String getContent() {
        return content;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getReplyToMessageId() {
        return replyToMessageId;
    }

    public boolean isRead() {
        return isRead;
    }

    public Map<String, Set<Integer>> getReactions() {
        return Map.copyOf(reactions);
    }

    public Media getAttachedMedia() {
        return attachedMedia;
    }

    public void setRead(boolean read) {
        isRead = read;
    }

    public void setAttachedMedia(Media attachedMedia) {
        this.attachedMedia = attachedMedia;
    }

    public void addReaction(String reaction, int userId) {
        reactions.computeIfAbsent(reaction, k -> new java.util.HashSet<>()).add(userId);
    }

    public void removeReaction(String reaction, int userId) {
        Set<Integer> users = reactions.get(reaction);
        if (users != null) {
            users.remove(userId);
            if (users.isEmpty()) {
                reactions.remove(reaction);
            }
        }
    }

    public MessageStatus getStatus() {
        return status;
    }

    public void setStatus(MessageStatus status) {
        this.status = status;
    }
}