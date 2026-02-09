package fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.providers;

import fr.uga.im2ag.m1info.tchatsapp.common.MessageType;
import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.ProtocolMessage;

import java.util.Set;

/**
 * Interface for providing message types and creating ProtocolMessage instances.
 * <p>
 * Implementations of this interface should specify the message types they support
 * and provide methods to create instances of those message types.
 * <p>
 * Please note that the parameterless createInstance() method is deprecated
 * in favor of createInstance(MessageType) to support multiple message types per provider.
 * This allows for more flexible and organized message handling.
 * <p>
 * When implementing this interface, ensure that the getType() method returns
 * all the MessageTypes that the provider can create instances for,
 * and vice versa for the createInstance(MessageType) method.
 */
public interface MessageProvider {
    /**
     * Get the set of MessageTypes supported by this provider.
     *
     * @return a Set of MessageType enums
     */
    Set<MessageType> getType();

    /**
     * Create a new instance of the ProtocolMessage.
     * <p>
     * This method is deprecated in favor of createInstance(MessageType)
     * to support multiple message types per provider.
     *
     * @return a new ProtocolMessage instance
     */
    @Deprecated
    ProtocolMessage createInstance();

    /**
     * Create a new instance of the ProtocolMessage for the given MessageType.
     * <p>
     * For compatibility, a default implementation is provided that
     * checks if the MessageType is supported and calls the parameterless createInstance().
     * <p>
     * The goal of this isn't to have one provider for all message types,
     * but to allow providers to handle multiple related message types if needed,
     * instead of forcing one provider per message type.
     * <p>
     * A good practice should be to group related message types in the same provider,
     * but avoid creating providers that handle too many different types.
     * 3-4 related types per provider is a reasonable guideline.
     *
     * @param messageType the MessageType to create an instance for
     * @return a new ProtocolMessage instance
     * @throws IllegalArgumentException if the MessageType is not supported by this provider
     */
    default ProtocolMessage createInstance(MessageType messageType) {
        if (getType().contains(messageType)) {
            return createInstance();
        }
        throw new IllegalArgumentException("MessageProvider does not support message type: " + messageType);
    }
}
