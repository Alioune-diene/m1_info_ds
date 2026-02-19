package fr.uga.im2ag.m1info.tchatsapp.server.handlers.providers;

import fr.uga.im2ag.m1info.tchatsapp.common.MessageType;
import fr.uga.im2ag.m1info.tchatsapp.server.handlers.RelayMessageHandler;
import fr.uga.im2ag.m1info.tchatsapp.server.handlers.ServerMessageHandler;

import java.util.List;
import java.util.Set;

public class RelayMessageHandlerProvider implements ServerPacketHandlerProvider {
    @Override
    public Set<MessageType> getHandledTypes() {
        return Set.of(MessageType.TEXT, MessageType.MEDIA);
    }

    @Override
    public List<ServerMessageHandler> createHandlers() {
        return List.of(new RelayMessageHandler());
    }
}