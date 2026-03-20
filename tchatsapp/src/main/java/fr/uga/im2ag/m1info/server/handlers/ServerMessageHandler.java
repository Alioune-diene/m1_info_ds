package fr.uga.im2ag.m1info.server.handlers;

import fr.uga.im2ag.m1info.common.MessageType;
import fr.uga.im2ag.m1info.common.messagefactory.ProtocolMessage;
import fr.uga.im2ag.m1info.server.ChatServerContext;

import java.util.logging.Logger;

/**
 * Abstract class for handling server packets.
 */
public abstract class ServerMessageHandler {
    protected static final Logger LOG = Logger.getLogger(ServerMessageHandler.class.getName());

    /**
     * Initializes the handler with required dependencies.
     * Called after construction by the ServiceLoader mechanism.
     * Default implementation does nothing; override if dependencies are needed.
     *
     * @param context the context providing dependencies
     */
    public void initialize(ServerHandlerContext context) {
    }

    /**
     * Handle the given protocol message.
     *
     * @param message the protocol message to handle
     * @param serverContext the server context
     */
    public abstract void handle(ProtocolMessage message, ChatServerContext serverContext);

    /**
     * Check if this handler can handle the given message type.
     *
     * @param messageType the message type to check
     * @return true if this handler can handle the message type, false otherwise
     */
    public abstract boolean canHandle(MessageType messageType);
}
