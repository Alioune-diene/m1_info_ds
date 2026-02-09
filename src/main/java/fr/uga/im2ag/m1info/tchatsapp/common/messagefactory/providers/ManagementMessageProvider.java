package fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.providers;

import fr.uga.im2ag.m1info.tchatsapp.common.MessageType;
import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.ManagementMessage;
import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.ProtocolMessage;

import java.util.Set;

public class ManagementMessageProvider implements MessageProvider {
    @Override
    public Set<MessageType> getType() {
        return Set.of(
                MessageType.CREATE_USER,
                MessageType.CONNECT_USER,
                MessageType.ACK_CONNECTION,
                MessageType.REMOVE_CONTACT,
                MessageType.CREATE_GROUP,
                MessageType.LEAVE_GROUP,
                MessageType.ADD_GROUP_MEMBER,
                MessageType.REMOVE_GROUP_MEMBER,
                MessageType.UPDATE_GROUP_NAME,
                MessageType.UPDATE_PSEUDO,
                MessageType.DELETE_GROUP
        );
    }

    @Override
    public ProtocolMessage createInstance() {
        return new ManagementMessage();
    }
}
