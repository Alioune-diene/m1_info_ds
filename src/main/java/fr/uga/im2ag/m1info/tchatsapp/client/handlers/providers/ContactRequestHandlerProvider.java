package fr.uga.im2ag.m1info.tchatsapp.client.handlers.providers;

import fr.uga.im2ag.m1info.tchatsapp.client.handlers.ClientMessageHandler;
import fr.uga.im2ag.m1info.tchatsapp.client.handlers.ContactRequestHandler;
import fr.uga.im2ag.m1info.tchatsapp.common.MessageType;

import java.util.List;
import java.util.Set;

public class ContactRequestHandlerProvider implements ClientPacketHandlerProvider {
    @Override
    public Set<MessageType> getHandledTypes() {
        return Set.of(MessageType.CONTACT_REQUEST, MessageType.CONTACT_REQUEST_RESPONSE);
    }

    @Override
    public List<ClientMessageHandler> createHandlers() {
        return List.of(new ContactRequestHandler());
    }
}