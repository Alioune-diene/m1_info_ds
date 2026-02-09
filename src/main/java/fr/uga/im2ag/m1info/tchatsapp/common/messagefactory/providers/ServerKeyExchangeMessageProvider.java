package fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.providers;

import fr.uga.im2ag.m1info.tchatsapp.common.MessageType;
import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.ProtocolMessage;
import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.ServerKeyExchangeMessage;

import java.util.Set;

public class ServerKeyExchangeMessageProvider implements MessageProvider {

    @Override
    public Set<MessageType> getType() {
        return Set.of(
                MessageType.SERVER_KEY_EXCHANGE,
                MessageType.SERVER_KEY_EXCHANGE_RESPONSE
        );
    }

    @Override
    public ProtocolMessage createInstance() {
        return new ServerKeyExchangeMessage();
    }
}
