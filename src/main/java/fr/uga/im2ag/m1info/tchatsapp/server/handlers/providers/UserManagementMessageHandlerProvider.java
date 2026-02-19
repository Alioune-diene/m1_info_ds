package fr.uga.im2ag.m1info.tchatsapp.server.handlers.providers;

import fr.uga.im2ag.m1info.tchatsapp.common.MessageType;
import fr.uga.im2ag.m1info.tchatsapp.server.handlers.ServerMessageHandler;
import fr.uga.im2ag.m1info.tchatsapp.server.handlers.UserManagementMessageHandler;

import java.util.List;
import java.util.Set;

public class UserManagementMessageHandlerProvider implements ServerPacketHandlerProvider {
    @Override
    public Set<MessageType> getHandledTypes() {
        return Set.of(
                MessageType.REMOVE_CONTACT,
                MessageType.UPDATE_PSEUDO
        );
    }

    @Override
    public List<ServerMessageHandler> createHandlers() {
        return List.of(new UserManagementMessageHandler());
    }
}