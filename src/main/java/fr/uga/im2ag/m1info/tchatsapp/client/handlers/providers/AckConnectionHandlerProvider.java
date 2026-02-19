package fr.uga.im2ag.m1info.tchatsapp.client.handlers.providers;

import fr.uga.im2ag.m1info.tchatsapp.client.handlers.AckConnectionHandler;
import fr.uga.im2ag.m1info.tchatsapp.client.handlers.ClientMessageHandler;
import fr.uga.im2ag.m1info.tchatsapp.common.MessageType;

import java.util.List;
import java.util.Set;

public class AckConnectionHandlerProvider implements ClientPacketHandlerProvider {
    @Override
    public Set<MessageType> getHandledTypes() {
        return Set.of(MessageType.ACK_CONNECTION);
    }

    @Override
    public List<ClientMessageHandler> createHandlers() {
        return List.of(new AckConnectionHandler());
    }
}