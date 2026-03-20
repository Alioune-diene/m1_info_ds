package fr.uga.im2ag.m1info.server.handlers.providers;

import fr.uga.im2ag.m1info.common.MessageType;
import fr.uga.im2ag.m1info.server.handlers.GroupMessageHandler;
import fr.uga.im2ag.m1info.server.handlers.ServerMessageHandler;

import java.util.List;
import java.util.Set;

public class GroupMessageHandlerProvider implements ServerPacketHandlerProvider {
    @Override
    public Set<MessageType> getHandledTypes() {
        return Set.of(
                MessageType.CREATE_GROUP,
                MessageType.LEAVE_GROUP,
                MessageType.ADD_GROUP_MEMBER,
                MessageType.REMOVE_GROUP_MEMBER,
                MessageType.UPDATE_GROUP_NAME
        );
    }

    @Override
    public List<ServerMessageHandler> createHandlers() {
        return List.of(new GroupMessageHandler());
    }
}