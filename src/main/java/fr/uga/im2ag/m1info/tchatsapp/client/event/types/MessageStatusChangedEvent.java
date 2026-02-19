package fr.uga.im2ag.m1info.tchatsapp.client.event.types;

import fr.uga.im2ag.m1info.tchatsapp.client.event.system.Event;
import fr.uga.im2ag.m1info.tchatsapp.common.MessageStatus;

/**
 * Event fired when a message's status changes (SENDING -> SENT -> DELIVERED -> READ).
 */
public class MessageStatusChangedEvent extends Event {
    private final String messageId;
    private final MessageStatus newStatus;
    private final int conversationId;
    private final String errorReason;

    /**
     * Constructor for MessageStatusChangedEvent without error reason.
     *
     * @param source the source object that triggered this event
     * @param messageId the ID of the message
     * @param newStatus the new status
     * @param conversationId the conversation ID
     */
    public MessageStatusChangedEvent(Object source, String messageId, MessageStatus newStatus, int conversationId) {
        this(source, messageId, newStatus, conversationId, null);
    }

    /**
     * Constructor for MessageStatusChangedEvent with optional error reason.
     *
     * @param source the source object that triggered this event
     * @param messageId the ID of the message
     * @param newStatus the new status
     * @param conversationId the conversation ID
     * @param errorReason the error reason (if status is FAILED)
     */
    public MessageStatusChangedEvent(Object source, String messageId, MessageStatus newStatus, int conversationId, String errorReason) {
        super(source);
        this.messageId = messageId;
        this.newStatus = newStatus;
        this.conversationId = conversationId;
        this.errorReason = errorReason;
    }

    public String getMessageId() {
        return messageId;
    }

    public MessageStatus getNewStatus() {
        return newStatus;
    }

    public int getConversationId() {
        return conversationId;
    }

    public String getErrorReason() {
        return errorReason;
    }

    @Override
    public Class<? extends Event> getEventType() {
        return MessageStatusChangedEvent.class;
    }
}