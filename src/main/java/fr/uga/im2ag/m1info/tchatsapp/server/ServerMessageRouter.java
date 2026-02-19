package fr.uga.im2ag.m1info.tchatsapp.server;

import fr.uga.im2ag.m1info.tchatsapp.common.MessageProcessor;
import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.ProtocolMessage;
import fr.uga.im2ag.m1info.tchatsapp.server.handlers.ServerHandlerContext;
import fr.uga.im2ag.m1info.tchatsapp.server.handlers.ServerMessageHandler;
import fr.uga.im2ag.m1info.tchatsapp.server.handlers.ServerMessageHandlerFactory;

import java.util.ArrayList;
import java.util.List;

public class ServerMessageRouter implements MessageProcessor {
    private final List<ServerMessageHandler> handlers;
    private final ChatServerContext serverContext; // ← changement

    public ServerMessageRouter(ChatServerContext serverContext) {
        this.serverContext = serverContext;
        this.handlers = new ArrayList<>();
    }

    public static ServerMessageRouter createWithServiceLoader(ChatServerContext serverContext, ServerHandlerContext handlerContext) {
        ServerMessageRouter router = new ServerMessageRouter(serverContext);
        List<ServerMessageHandler> handlers = ServerMessageHandlerFactory.loadHandlers(handlerContext);
        for (ServerMessageHandler handler : handlers) {
            router.addHandler(handler);
        }
        return router;
    }

    public void addHandler(ServerMessageHandler handler) {
        handlers.add(handler);
    }

    public void removeHandler(ServerMessageHandler handler) {
        handlers.remove(handler);
    }

    @Override
    public void process(ProtocolMessage message) {
        for (ServerMessageHandler handler : handlers) {
            if (handler.canHandle(message.getMessageType())) {
                handler.handle(message, serverContext);
                return;
            }
        }
        throw new RuntimeException("No handler found for message type: " + message.getMessageType());
    }
}
