package fr.uga.im2ag.m1info.common.messagefactory.providers;

import fr.uga.im2ag.m1info.common.MessageType;
import fr.uga.im2ag.m1info.common.messagefactory.MediaMessage;
import fr.uga.im2ag.m1info.common.messagefactory.ProtocolMessage;

import java.util.Set;

public class MediaMessageProvider implements MessageProvider {
    @Override
    public Set<MessageType> getType() {
        return Set.of(MessageType.MEDIA);
    }

    @Override
    public ProtocolMessage createInstance() {
        return new MediaMessage();
    }
}

