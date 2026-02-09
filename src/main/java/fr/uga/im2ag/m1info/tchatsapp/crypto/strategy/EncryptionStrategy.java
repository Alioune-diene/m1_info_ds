package fr.uga.im2ag.m1info.tchatsapp.crypto.strategy;

import fr.uga.im2ag.m1info.tchatsapp.common.MessageType;
import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.EncryptedWrapper;
import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.ProtocolMessage;

import java.security.GeneralSecurityException;
import java.util.Set;

/**
 * Strategy interface for message encryption and decryption.
 * <p>
 * This interface defines the contract for encryption strategies, allowing
 * different implementations to be swapped easily:
 * <ul>
 *   <li>{@link NoOpEncryptionStrategy} - Pass-through for testing/debugging</li>
 *   <li>{@link AESEncryptionStrategy} - Full AES-256-GCM E2EE implementation</li>
 * </ul>
 * <p>
 * Usage:
 * <pre>{@code
 * EncryptionStrategy strategy = new AESEncryptionStrategy(sessionManager, keyExchangeManager);
 *
 * // Before sending
 * if (strategy.shouldEncrypt(message.getMessageType(), message.getTo())) {
 *     message = strategy.encrypt(message);
 * }
 *
 * // After receiving
 * if (message instanceof EncryptedWrapper wrapper) {
 *     message = strategy.decrypt(wrapper);
 * }
 * }</pre>
 *
 * @see NoOpEncryptionStrategy
 * @see AESEncryptionStrategy
 */
public interface EncryptionStrategy {

    // ========================= Encryption/Decryption =========================

    /**
     * Encrypts a message.
     * <p>
     * The returned message will be an {@link EncryptedWrapper} containing
     * the encrypted payload of the original message.
     *
     * @param message the message to encrypt
     * @return an EncryptedWrapper containing the encrypted message
     * @throws GeneralSecurityException if encryption fails
     * @throws IllegalStateException if no session key exists for the recipient
     */
    ProtocolMessage encrypt(ProtocolMessage message) throws GeneralSecurityException;

    /**
     * Decrypts an encrypted message.
     * <p>
     * Extracts and decrypts the original message from the wrapper.
     *
     * @param wrapper the encrypted wrapper
     * @return the decrypted original message
     * @throws GeneralSecurityException if decryption fails
     * @throws SecurityException if replay attack detected
     */
    ProtocolMessage decrypt(EncryptedWrapper wrapper) throws GeneralSecurityException;

    // ========================= Policy Methods =========================

    /**
     * Determines whether a message should be encrypted.
     * <p>
     * This method considers:
     * <ul>
     *   <li>Message type (some types are never encrypted)</li>
     *   <li>Recipient (e.g., server messages may have different rules)</li>
     *   <li>Whether a session key exists</li>
     * </ul>
     *
     * @param type the message type
     * @param recipientId the recipient ID (0 for server)
     * @return true if the message should be encrypted
     */
    boolean shouldEncrypt(MessageType type, int recipientId);

    /**
     * Checks if encryption is enabled.
     * <p>
     * A disabled strategy (like {@link NoOpEncryptionStrategy}) always returns false.
     *
     * @return true if encryption is enabled
     */
    boolean isEnabled();

    // ========================= Session Management =========================

    /**
     * Checks if a session key exists with a peer.
     *
     * @param peerId the peer ID
     * @return true if encrypted communication is possible
     */
    boolean hasSessionKey(int peerId);

    /**
     * Initiates a key exchange with a peer if needed.
     * <p>
     * This is a convenience method that checks if a session exists
     * and initiates key exchange if not.
     *
     * @param peerId the peer ID
     * @throws GeneralSecurityException if key exchange initiation fails
     */
    void ensureSessionExists(int peerId) throws GeneralSecurityException;

    // ========================= Default Methods =========================

    /**
     * Gets the set of message types that are never encrypted.
     * <p>
     * By default, this includes:
     * <ul>
     *   <li>KEY_EXCHANGE - Contains public keys, not sensitive</li>
     *   <li>KEY_EXCHANGE_RESPONSE - Same as above</li>
     * </ul>
     *
     * @return unmodifiable set of excluded message types
     */
    default Set<MessageType> getExcludedMessageTypes() {
        return Set.of(
                MessageType.KEY_EXCHANGE,
                MessageType.KEY_EXCHANGE_RESPONSE
        );
    }

    /**
     * Checks if a message type is excluded from encryption.
     *
     * @param type the message type
     * @return true if this type should never be encrypted
     */
    default boolean isExcludedFromEncryption(MessageType type) {
        return getExcludedMessageTypes().contains(type);
    }

    /**
     * Gets the name of this encryption strategy.
     * <p>
     * Useful for logging and debugging.
     *
     * @return the strategy name
     */
    default String getName() {
        return getClass().getSimpleName();
    }

    /**
     * Clears all encryption contexts/sessions.
     * <p>
     * Default implementation is a no-op.
     * Implementations with session management should override this.
     */
    default void clearAllContexts() {
        // Default no-op
    }

    /**
     * Invalidates the encryption context for a specific peer.
     * <p>
     * Default implementation is a no-op.
     * Implementations with session management should override this.
     *
     * @param peerId the peer ID whose context to invalidate
     */
    default void invalidateContext(int peerId) {
        // Default no-op
    }
}