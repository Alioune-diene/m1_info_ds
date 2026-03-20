package fr.uga.im2ag.m1info.server.handlers;

import fr.uga.im2ag.m1info.common.MessageType;
import fr.uga.im2ag.m1info.common.messagefactory.ProtocolMessage;
import fr.uga.im2ag.m1info.common.messagefactory.TextMessage;
import fr.uga.im2ag.m1info.common.model.StoredMessage;
import fr.uga.im2ag.m1info.server.ChatServerContext;
import fr.uga.im2ag.m1info.server.util.AckHelper;

/**
 * Handler for relaying messages from sender to recipient with acknowledgments.
 */
public class RelayMessageHandler extends ValidatingServerMessageHandler {
    @Override
    public void handle(ProtocolMessage message, ChatServerContext serverContext) {
        if (!validateSenderRegistered(message, serverContext)) return;

        if (isGroupId(message.getTo(), serverContext)) {
            if (!validateSenderMemberOfGroup(message, serverContext)) return;
        } else {
            if (!validateRecipientExists(message, serverContext)) return;
            if (!checkContactRelationship(message.getFrom(), message.getTo(), serverContext)) {
                AckHelper.sendFailedAck(serverContext, message, "Not authorized");
                return;
            }
        }

        // Send SENT acknowledgment to sender
        AckHelper.sendSentAck(serverContext, message);

        // Relay message to recipient
        sendPacketToRecipient(message, serverContext);
        storeMessage(message, serverContext);
    }

    @Override
    public boolean canHandle(MessageType messageType) {
        return messageType == MessageType.TEXT ||
                messageType == MessageType.MEDIA ||
                messageType == MessageType.REACTION;
    }

    private void storeMessage(ProtocolMessage message, ChatServerContext serverContext) {
        String content = null;
        if (message instanceof TextMessage textMsg) {
            content = textMsg.getContent();
        } else if (message.getMessageType() != MessageType.TEXT) {
            // TODO: MEDIA et REACTION non stockés pour l'instant
            return;
        }

        String conversationId = isGroupId(message.getTo(), serverContext)
                ? ChatServerContext.groupConversationId(message.getTo())
                : ChatServerContext.privateConversationId(message.getFrom(), message.getTo());

        String replyTo = (message instanceof TextMessage tm) ? tm.getReplyToMessageId() : null;

        StoredMessage stored = new StoredMessage(
                message.getMessageId(),
                message.getFrom(),
                message.getTo(),
                content,
                message.getMessageType(),
                message.getTimestamp(),
                replyTo,
                conversationId
        );

        serverContext.getConversationRepository().addMessage(conversationId, stored);
    }
}