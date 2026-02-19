package fr.uga.im2ag.m1info.tchatsapp.server.handlers;

import fr.uga.im2ag.m1info.tchatsapp.common.MessageType;
import fr.uga.im2ag.m1info.tchatsapp.server.handlers.providers.ServerPacketHandlerProvider;

import java.util.*;
import java.util.logging.Logger;

/**
 * Factory for loading and initializing server packet handlers via ServiceLoader.
 */
public class ServerMessageHandlerFactory {
    private static final Logger LOG = Logger.getLogger(ServerMessageHandlerFactory.class.getName());

    private ServerMessageHandlerFactory() {}

    /**
     * Loads all server packet handlers using ServiceLoader and initializes them.
     *
     * @param context the context providing dependencies for initialization
     * @return list of initialized handlers
     */
    public static List<ServerMessageHandler> loadHandlers(ServerHandlerContext context) {
        List<ServerMessageHandler> handlers = new ArrayList<>();
        Set<MessageType> registeredTypes = new HashSet<>();

        ServiceLoader<ServerPacketHandlerProvider> loader = ServiceLoader.load(ServerPacketHandlerProvider.class);

        for (ServerPacketHandlerProvider provider : loader) {
            Set<MessageType> providerTypes = provider.getHandledTypes();

            // Check for conflicts
            for (MessageType type : providerTypes) {
                if (registeredTypes.contains(type)) {
                    LOG.warning(String.format(
                            "Handler conflict: MessageType %s already registered. " +
                                    "Provider %s will be ignored for this type.",
                            type, provider.getClass().getSimpleName()
                    ));
                }
            }

            List<ServerMessageHandler> providerHandlers = provider.createHandlers();
            for (ServerMessageHandler handler : providerHandlers) {
                handler.initialize(context);
                handlers.add(handler);
                LOG.info(String.format(
                        "Registered server handler: %s for types: %s",
                        handler.getClass().getSimpleName(), providerTypes
                ));
            }

            registeredTypes.addAll(providerTypes);
        }

        if (handlers.isEmpty()) {
            LOG.warning("No server packet handler providers found! Check META-INF/services configuration.");
        }

        return handlers;
    }
}