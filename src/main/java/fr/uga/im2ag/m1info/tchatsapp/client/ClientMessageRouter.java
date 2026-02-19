package fr.uga.im2ag.m1info.tchatsapp.client;

import fr.uga.im2ag.m1info.tchatsapp.client.handlers.ClientHandlerContext;
import fr.uga.im2ag.m1info.tchatsapp.client.handlers.ClientMessageHandler;
import fr.uga.im2ag.m1info.tchatsapp.client.handlers.ClientMessageHandlerFactory;
import fr.uga.im2ag.m1info.tchatsapp.common.MessageProcessor;
import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.ProtocolMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * A ClientPaquetRouter that routes incoming protocol messages to the appropriate ClientPacketHandler.
 * This router maintains a ClientController that is passed to all handlers.
 */
public class ClientMessageRouter implements MessageProcessor {
    private final List<ClientMessageHandler> handlers;
    private final ClientController context;

    /**
     * Creates a ClientPaquetRouter with the specified list of handlers and context.
     *
     * @param handlers the list of ClientPacketHandler to be used by this router
     * @param context the client context to pass to handlers
     */
    public ClientMessageRouter(List<ClientMessageHandler> handlers, ClientController context) {
        this.handlers = new ArrayList<>(handlers);
        this.context = context;
    }

    /**
     * Creates a ClientPaquetRouter with an empty list of handlers and the given context.
     *
     * @param context the client context to pass to handlers
     */
    public ClientMessageRouter(ClientController context) {
        this.handlers = new ArrayList<>();
        this.context = context;
    }

    /**
     * Creates a ClientPaquetRouter loading handlers via ServiceLoader.
     *
     * @param context the client context to pass to handlers
     * @param handlerContext the context for handler initialization
     * @return a new ClientPaquetRouter with loaded handlers
     */
    public static ClientMessageRouter createWithServiceLoader(ClientController context, ClientHandlerContext handlerContext) {
        List<ClientMessageHandler> handlers = ClientMessageHandlerFactory.loadHandlers(handlerContext);
        return new ClientMessageRouter(handlers, context);
    }

    /**
     * Adds a ClientPacketHandler to the router.
     *
     * @param handler the ClientPacketHandler to be added
     */
    public void addHandler(ClientMessageHandler handler) {
        this.handlers.add(handler);
    }

    /**
     * Removes a ClientPacketHandler from the router.
     *
     * @param handler the ClientPacketHandler to be removed
     */
    public void removeHandler(ClientMessageHandler handler) {
        this.handlers.remove(handler);
    }

    @Override
    public void process(ProtocolMessage message) {
        for (ClientMessageHandler handler : handlers) {
            if (handler.canHandle(message.getMessageType())) {
                handler.handle(message, context);
                return;
            }
        }
        throw new IllegalArgumentException("No handler found for message type: " + message.getMessageType());
    }
}
