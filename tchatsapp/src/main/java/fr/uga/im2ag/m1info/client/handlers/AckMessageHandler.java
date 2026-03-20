package fr.uga.im2ag.m1info.client.handlers;

import fr.uga.im2ag.m1info.client.ClientController;
import fr.uga.im2ag.m1info.client.command.PendingCommandManager;
import fr.uga.im2ag.m1info.common.MessageStatus;
import fr.uga.im2ag.m1info.common.MessageType;
import fr.uga.im2ag.m1info.common.messagefactory.AckMessage;
import fr.uga.im2ag.m1info.common.messagefactory.ProtocolMessage;

/**
 * Handler for processing acknowledgment messages from the server.
 */
public class AckMessageHandler extends ClientMessageHandler {
    private PendingCommandManager commandManager;

    /**
     * Default constructor for ServiceLoader instantiation.
     */
    public AckMessageHandler() {
        this.commandManager = null;
    }

    /**
     * Constructor for manual instantiation with dependency injection.
     *
     * @param commandManager the pending command manager
     */
    public AckMessageHandler(PendingCommandManager commandManager) {
        this.commandManager = commandManager;
    }

    @Override
    public void initialize(ClientHandlerContext context) {
        if (this.commandManager == null) {
            this.commandManager = context.getCommandManager();
        }
    }

    @Override
    public void handle(ProtocolMessage message, ClientController context) {
        if (!(message instanceof AckMessage ackMsg)) {
            throw new IllegalArgumentException("Invalid message type for AckMessageHandler");
        }

        if (ackMsg.getAckType() == MessageStatus.CRITICAL_FAILURE && ackMsg.getAcknowledgedMessageId() == "-1") {
            System.err.println("[Client] Received critical failure ack from server. Disconnecting...");
            context.disconnect();

            context.setLastError(ackMsg.getErrorReason());

            return;
        }

        if (commandManager == null) {
            throw new IllegalStateException("AckMessageHandler not initialized: commandManager is null");
        }

        commandManager.handleAck(ackMsg);
    }

    @Override
    public boolean canHandle(MessageType messageType) {
        return messageType == MessageType.MESSAGE_ACK;
    }
}