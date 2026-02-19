package fr.uga.im2ag.m1info.tchatsapp.client.command;

import fr.uga.im2ag.m1info.tchatsapp.client.event.system.EventBus;
import fr.uga.im2ag.m1info.tchatsapp.client.event.types.MessageStatusChangedEvent;
import fr.uga.im2ag.m1info.tchatsapp.client.model.Message;
import fr.uga.im2ag.m1info.tchatsapp.client.repository.ConversationClientRepository;
import fr.uga.im2ag.m1info.tchatsapp.common.MessageStatus;

import java.util.Map;

/**
 * Command for tracking a pending text message.
 */
public class SendTextMessageCommand implements PendingCommand {
    private final String messageId;
    private final Message message;
    private final ConversationClientRepository repository;
    private MessageStatus currentStatus;

    /**
     * Constructor for SendTextMessageCommand.
     *
     * @param messageId the message ID
     * @param message the message object
     * @param repository the conversation repository
     */
    public SendTextMessageCommand(String messageId, Message message, ConversationClientRepository repository) {
        this.messageId = messageId;
        this.message = message;
        this.repository = repository;
        this.currentStatus = MessageStatus.SENDING;
    }

    @Override
    public String getCommandId() {
        return messageId;
    }

    @Override
    public boolean onAckReceived(MessageStatus ackType, Map<String, Object> params) {
        MessageStatus newStatus = switch (ackType) {
            case SENDING -> MessageStatus.SENDING;
            case SENT -> MessageStatus.SENT;
            case DELIVERED -> MessageStatus.DELIVERED;
            case READ -> MessageStatus.READ;
            case FAILED, CRITICAL_FAILURE -> MessageStatus.FAILED;
        };

        if (newStatus == currentStatus) {
            return false;
        }

        currentStatus = newStatus;
        message.setStatus(newStatus);

        EventBus.getInstance().publish(new MessageStatusChangedEvent(
                this,
                messageId,
                newStatus,
                message.getToUserId()
        ));

        System.out.printf("[Client] Message %s status changed to %s%n", messageId.substring(0, Math.min(8, messageId.length())), newStatus);
        return false;
    }

    @Override
    public void onAckFailed(String reason) {
        currentStatus = MessageStatus.FAILED;
        message.setStatus(MessageStatus.FAILED);

        EventBus.getInstance().publish(new MessageStatusChangedEvent(
                this,
                messageId,
                MessageStatus.FAILED,
                message.getToUserId(),
                reason
        ));

        System.err.printf("[Client] Message %s failed: %s%n", messageId.substring(0, Math.min(8, messageId.length())), reason);
    }

    public MessageStatus getCurrentStatus() {
        return currentStatus;
    }
}