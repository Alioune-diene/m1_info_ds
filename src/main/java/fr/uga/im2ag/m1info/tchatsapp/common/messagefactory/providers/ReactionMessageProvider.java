package fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.providers;

import fr.uga.im2ag.m1info.tchatsapp.common.MessageType;
import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.ProtocolMessage;
import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.ReactionMessage;

import java.util.Set;

public class ReactionMessageProvider implements MessageProvider {
    @Override
    public Set<MessageType> getType() {
        return Set.of(MessageType.REACTION);
    }

    @Override
    public ProtocolMessage createInstance() {
        return new ReactionMessage();
    }

}
