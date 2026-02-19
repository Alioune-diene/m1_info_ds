package fr.uga.im2ag.m1info.tchatsapp.server.handlers.providers;

import fr.uga.im2ag.m1info.tchatsapp.common.MessageType;
import fr.uga.im2ag.m1info.tchatsapp.server.handlers.ServerMessageHandler;

import java.util.List;
import java.util.Set;

/**
 * Provider interface for server packet handlers.
 */
public interface ServerPacketHandlerProvider {

    /**
     * Returns the set of message types that the handlers from this provider can handle.
     * Used for conflict detection during registration.
     *
     * @return set of handled message types
     */
    Set<MessageType> getHandledTypes();

    /**
     * Creates new instances of the handlers provided by this provider.
     * Each call should return fresh instances.
     *
     * @return list of handler instances
     */
    List<ServerMessageHandler> createHandlers();
}