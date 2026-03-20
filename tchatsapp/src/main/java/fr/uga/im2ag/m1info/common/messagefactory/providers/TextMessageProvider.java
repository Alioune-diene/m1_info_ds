package fr.uga.im2ag.m1info.common.messagefactory.providers;

import fr.uga.im2ag.m1info.common.MessageType;
import fr.uga.im2ag.m1info.common.messagefactory.ProtocolMessage;
import fr.uga.im2ag.m1info.common.messagefactory.TextMessage;

import java.util.Set;

public class TextMessageProvider implements MessageProvider {
    @Override
    public Set<MessageType> getType() {
        return Set.of(MessageType.TEXT);
    }

    @Override
    public ProtocolMessage createInstance() {
        return new TextMessage();
    }
}
