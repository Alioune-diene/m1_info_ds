package fr.uga.im2ag.m1info.tchatsapp.client.event.types;

import fr.uga.im2ag.m1info.tchatsapp.client.event.system.Event;
import fr.uga.im2ag.m1info.tchatsapp.common.MessageType;

/**
 * Event fired when a management operation fails.
 */
public class ManagementOperationFailedEvent extends Event {
    private final MessageType operationType;
    private final String messageId;
    private final String reason;

    /**
     * Constructor for ManagementOperationFailedEvent.
     *
     * @param source the source object that triggered this event
     * @param operationType the type of management operation
     * @param messageId the message ID
     * @param reason the failure reason
     */
    public ManagementOperationFailedEvent(Object source, MessageType operationType, String messageId, String reason) {
        super(source);
        this.operationType = operationType;
        this.messageId = messageId;
        this.reason = reason;
    }

    public MessageType getOperationType() {
        return operationType;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public Class<? extends Event> getEventType() {
        return ManagementOperationFailedEvent.class;
    }
}