package fr.uga.im2ag.m1info.server.handlers.providers;

import fr.uga.im2ag.m1info.common.MessageType;
import fr.uga.im2ag.m1info.server.handlers.AckMessageHandler;
import fr.uga.im2ag.m1info.server.handlers.ServerMessageHandler;

import java.util.List;
import java.util.Set;

public class AckMessageHandlerProvider implements ServerPacketHandlerProvider {
    @Override
    public Set<MessageType> getHandledTypes() {
        return Set.of(MessageType.MESSAGE_ACK);
    }

    @Override
    public List<ServerMessageHandler> createHandlers() {
        return List.of(new AckMessageHandler());
    }
}