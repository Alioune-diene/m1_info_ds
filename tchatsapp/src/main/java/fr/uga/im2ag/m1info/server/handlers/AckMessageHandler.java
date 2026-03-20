package fr.uga.im2ag.m1info.server.handlers;

import fr.uga.im2ag.m1info.common.MessageType;
import fr.uga.im2ag.m1info.common.messagefactory.ProtocolMessage;
import fr.uga.im2ag.m1info.server.ChatServerContext;

public class AckMessageHandler extends ValidatingServerMessageHandler {
    @Override
    public void handle(ProtocolMessage message, ChatServerContext serverContext) {
        if (message.getTo() != 0) {
            if (!validateSenderRegistered(message, serverContext)) return;
            if (!validateRecipientExists(message, serverContext)) return;

            serverContext.sendToRecipient(message);
        }
    }

    @Override
    public boolean canHandle(MessageType messageType) {
        return messageType == MessageType.MESSAGE_ACK;
    }
}
