package fr.uga.im2ag.m1info.common.messagefactory.providers;

import fr.uga.im2ag.m1info.common.MessageType;
import fr.uga.im2ag.m1info.common.messagefactory.HistorySyncMessage;
import fr.uga.im2ag.m1info.common.messagefactory.ProtocolMessage;

import java.util.Set;

public class HistorySyncMessageProvider implements MessageProvider {
    @Override
    public Set<MessageType> getType() {
        return Set.of(MessageType.HISTORY_SYNC);
    }

    @Override
    public ProtocolMessage createInstance() {
        return new HistorySyncMessage();
    }
}