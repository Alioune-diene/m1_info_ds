package fr.uga.im2ag.m1info.client.command;

import fr.uga.im2ag.m1info.client.event.system.EventBus;
import fr.uga.im2ag.m1info.client.event.types.ManagementOperationFailedEvent;
import fr.uga.im2ag.m1info.client.event.types.ManagementOperationSucceededEvent;
import fr.uga.im2ag.m1info.common.MessageStatus;
import fr.uga.im2ag.m1info.common.MessageType;

import java.util.Map;

/**
 * Command for tracking a pending management message.
 */
public class SendManagementMessageCommand implements PendingCommand {
    protected final String messageId;
    protected final MessageType operationType;

    /**
     * Constructor for SendManagementMessageCommand.
     *
     * @param messageId the message ID
     * @param operationType the type of management operation
     */
    public SendManagementMessageCommand(String messageId, MessageType operationType) {
        this.messageId = messageId;
        this.operationType = operationType;
    }

    @Override
    public String getCommandId() {
        return messageId;
    }

    @Override
    public boolean onAckReceived(MessageStatus ackType, Map<String, Object> params) {
        if (ackType == MessageStatus.SENT || ackType == MessageStatus.DELIVERED) {
            EventBus.getInstance().publish(new ManagementOperationSucceededEvent(
                    this,
                    operationType,
                    messageId
            ));

            System.out.printf("[Client] Management operation %s succeeded (message: %s)%n",
                    operationType,
                    messageId.substring(0, Math.min(8, messageId.length())));
        }
        return true;
    }

    @Override
    public void onAckFailed(String reason) {
        EventBus.getInstance().publish(new ManagementOperationFailedEvent(
                this,
                operationType,
                messageId,
                reason
        ));

        System.err.printf("[Client] Management operation %s failed: %s%n", operationType, reason);
    }
}
