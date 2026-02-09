package fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.providers;

import fr.uga.im2ag.m1info.tchatsapp.common.MessageType;
import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.KeyExchangeMessage;
import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.KeyExchangeResponseMessage;
import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.ProtocolMessage;

import java.util.Set;

/**
 * Provider for key exchange message types.
 */
public class KeyExchangeMessageProvider implements MessageProvider {

    @Override
    public Set<MessageType> getType() {
        return Set.of(MessageType.KEY_EXCHANGE, MessageType.KEY_EXCHANGE_RESPONSE);
    }

    @Override
    public ProtocolMessage createInstance() {
        throw new UnsupportedOperationException("Legacy createInstance() is not supported. Use createInstance(MessageType) instead.");
    }

    /**
     * Creates instances for the specific message types.
     *
     * @param type the message type
     * @return appropriate message instance
     */
    public ProtocolMessage createInstance(MessageType type) {
        return switch (type) {
            case KEY_EXCHANGE -> new KeyExchangeMessage();
            case KEY_EXCHANGE_RESPONSE -> new KeyExchangeResponseMessage();
            default -> throw new IllegalArgumentException("Unsupported type: " + type);
        };
    }
}