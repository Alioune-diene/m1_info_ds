package fr.uga.im2ag.m1info.server.handlers.providers;

import fr.uga.im2ag.m1info.common.MessageType;
import fr.uga.im2ag.m1info.server.handlers.ContactRequestServerHandler;
import fr.uga.im2ag.m1info.server.handlers.ServerMessageHandler;

import java.util.List;
import java.util.Set;

public class ContactRequestServerHandlerProvider implements ServerPacketHandlerProvider {
    @Override
    public Set<MessageType> getHandledTypes() {
        return Set.of(MessageType.CONTACT_REQUEST, MessageType.CONTACT_REQUEST_RESPONSE);
    }

    @Override
    public List<ServerMessageHandler> createHandlers() {
        return List.of(new ContactRequestServerHandler());
    }
}