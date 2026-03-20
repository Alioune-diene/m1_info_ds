package fr.uga.im2ag.m1info.common.messagefactory.providers;

import fr.uga.im2ag.m1info.common.MessageType;
import fr.uga.im2ag.m1info.common.messagefactory.ContactRequestMessage;
import fr.uga.im2ag.m1info.common.messagefactory.ContactRequestResponseMessage;
import fr.uga.im2ag.m1info.common.messagefactory.ProtocolMessage;

import java.util.Set;

public class ContactRequestMessageProvider implements MessageProvider {
    @Override
    public Set<MessageType> getType() {
        return Set.of(MessageType.CONTACT_REQUEST, MessageType.CONTACT_REQUEST_RESPONSE);
    }

    @Override
    public ProtocolMessage createInstance() {
        throw new UnsupportedOperationException("Legacy createInstance() is not supported for ContactRequestMessageProvider. Use createInstance(MessageType) instead.");
    }

    @Override
    public ProtocolMessage createInstance(MessageType messageType) {
        return switch (messageType) {
            case CONTACT_REQUEST -> new ContactRequestMessage();
            case CONTACT_REQUEST_RESPONSE -> new ContactRequestResponseMessage();
            default -> throw new IllegalArgumentException("Unsupported type: " + messageType);
        };
    }
}