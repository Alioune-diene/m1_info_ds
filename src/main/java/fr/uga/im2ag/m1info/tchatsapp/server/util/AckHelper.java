package fr.uga.im2ag.m1info.tchatsapp.server.util;

import fr.uga.im2ag.m1info.tchatsapp.common.MessageStatus;
import fr.uga.im2ag.m1info.tchatsapp.common.MessageType;
import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.AckMessage;
import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.MessageFactory;
import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.ProtocolMessage;
import fr.uga.im2ag.m1info.tchatsapp.server.ChatServerContext;

import java.util.logging.Logger;

/**
 * Utility class for sending acknowledgment messages from the server to clients.
 */
public class AckHelper {
    private static final Logger LOGGER = Logger.getLogger(AckHelper.class.getName());

    /**
     * Send a SENT acknowledgment to the client who sent the message.
     * This indicates the server has received and processed the message.
     *
     * @param serverContext the server context
     * @param originalMessage the original message that was received
     */
    public static void sendSentAck(ChatServerContext serverContext, ProtocolMessage originalMessage) {
        sendAck(serverContext, originalMessage, MessageStatus.SENT, null);
    }

    /**
     * Send a FAILED acknowledgment to the client.
     * This indicates the operation failed.
     *
     * @param serverContext the server context
     * @param originalMessage the original message that failed
     * @param errorReason the reason for failure
     */
    public static void sendFailedAck(ChatServerContext serverContext, ProtocolMessage originalMessage, String errorReason) {
        sendAck(serverContext, originalMessage, MessageStatus.FAILED, errorReason);
    }

    /**
     * Send a FAILED acknowledgment to a specific client.
     *
     * @param serverContext the server context
     * @param clientId the client to send the ACK to
     * @param messageId the ID of the message that failed
     * @param errorReason the reason for failure
     */
    public static void sendFailedAck(ChatServerContext serverContext, int clientId, String messageId, String errorReason) {
        AckMessage ack = (AckMessage) MessageFactory.create(MessageType.MESSAGE_ACK, 0, clientId);
        ack.setAcknowledgedMessageId(messageId);
        ack.setAckType(MessageStatus.FAILED);
        ack.setErrorReason(errorReason);

        serverContext.sendToClient(ack, clientId);
    }

    /**
     * Send a CRITICAL_FAILURE acknowledgment to a specific client.
     * This indicates a critical error occurred, and client
     * generally needs to disconnect.
     * <p>
     * Do not use this method for standard message failures.
     *
     * @param serverContext the server context
     * @param messageId the ID of the message that encountered a critical error
     * @param clientId the client to send the ACK to
     * @param reason the reason for the critical failure
     */
    public static void sendCriticalAck(ChatServerContext serverContext, String messageId, int clientId, String reason) {
        LOGGER.severe("Critical: " + reason + " for message ID: " + messageId);

        AckMessage ack = (AckMessage) MessageFactory.create(MessageType.MESSAGE_ACK, 0, clientId);
        ack.setAcknowledgedMessageId(messageId);
        ack.setAckType(MessageStatus.CRITICAL_FAILURE);
        ack.setErrorReason(reason);

        serverContext.sendToClient(ack, clientId);
    }

    /**
     * Internal method to send an ACK message.
     *
     * @param serverContext the server context
     * @param originalMessage the original message being acknowledged
     * @param ackType the type of acknowledgment
     * @param errorReason the error reason (if FAILED)
     */
    private static void sendAck(ChatServerContext serverContext, ProtocolMessage originalMessage, MessageStatus ackType, String errorReason) {
        if (originalMessage.getMessageId() == null || originalMessage.getMessageId().isEmpty()) {
            return;
        }

        AckMessage ack = (AckMessage) MessageFactory.create(MessageType.MESSAGE_ACK, 0, originalMessage.getFrom());
        ack.setAcknowledgedMessageId(originalMessage.getMessageId());
        ack.setAckType(ackType);

        if (errorReason != null) {
            ack.setErrorReason(errorReason);
        }

        serverContext.sendToClient(ack, originalMessage.getFrom());

        LOGGER.info(String.format("Sent %s ACK for message %s to client %d",
                ackType,
                originalMessage.getMessageId(),
                originalMessage.getFrom()));
    }
}