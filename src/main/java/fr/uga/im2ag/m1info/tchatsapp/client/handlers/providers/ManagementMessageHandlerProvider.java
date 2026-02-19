package fr.uga.im2ag.m1info.tchatsapp.client.handlers.providers;

import fr.uga.im2ag.m1info.tchatsapp.client.handlers.ClientMessageHandler;
import fr.uga.im2ag.m1info.tchatsapp.client.handlers.ManagementMessageHandler;
import fr.uga.im2ag.m1info.tchatsapp.common.MessageType;

import java.util.List;
import java.util.Set;

public class ManagementMessageHandlerProvider implements ClientPacketHandlerProvider {
    @Override
    public Set<MessageType> getHandledTypes() {
        return Set.of(
                MessageType.REMOVE_CONTACT,
                MessageType.UPDATE_PSEUDO,
                MessageType.CREATE_GROUP,
                MessageType.LEAVE_GROUP,
                MessageType.ADD_GROUP_MEMBER,
                MessageType.REMOVE_GROUP_MEMBER,
                MessageType.UPDATE_GROUP_NAME,
                MessageType.DELETE_GROUP
        );
    }

    @Override
    public List<ClientMessageHandler> createHandlers() {
        return List.of(new ManagementMessageHandler());
    }
}
