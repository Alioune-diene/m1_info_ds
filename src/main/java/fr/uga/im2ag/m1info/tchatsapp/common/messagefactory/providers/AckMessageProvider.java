package fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.providers;

import fr.uga.im2ag.m1info.tchatsapp.common.MessageType;
import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.AckMessage;
import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.ProtocolMessage;

import java.util.Set;

/**
 * Provider for creating AckMessage instances.
 */
public class AckMessageProvider implements MessageProvider {
    @Override
    public Set<MessageType> getType() {
        return Set.of(MessageType.MESSAGE_ACK);
    }

    @Override
    public ProtocolMessage createInstance() {
        return new AckMessage(0, 0);
    }
}