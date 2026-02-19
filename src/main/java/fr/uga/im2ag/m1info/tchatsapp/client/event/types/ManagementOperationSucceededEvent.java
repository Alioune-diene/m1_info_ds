package fr.uga.im2ag.m1info.tchatsapp.client.event.types;

import fr.uga.im2ag.m1info.tchatsapp.client.event.system.Event;
import fr.uga.im2ag.m1info.tchatsapp.common.MessageType;

/**
 * Event fired when a management operation succeeds.
 */
public class ManagementOperationSucceededEvent extends Event {
    private final MessageType operationType;
    private final String messageId;

    /**
     * Constructor for ManagementOperationSucceededEvent.
     *
     * @param source the source object that triggered this event
     * @param operationType the type of management operation
     * @param messageId the message ID
     */
    public ManagementOperationSucceededEvent(Object source, MessageType operationType, String messageId) {
        super(source);
        this.operationType = operationType;
        this.messageId = messageId;
    }

    public MessageType getOperationType() {
        return operationType;
    }

    public String getMessageId() {
        return messageId;
    }

    @Override
    public Class<? extends Event> getEventType() {
        return ManagementOperationSucceededEvent.class;
    }
}