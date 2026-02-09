package fr.uga.im2ag.m1info.tchatsapp.crypto.strategy;

import fr.uga.im2ag.m1info.tchatsapp.common.MessageType;
import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.EncryptedWrapper;
import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.ProtocolMessage;

import java.security.GeneralSecurityException;
import java.util.logging.Logger;

/**
 * No-operation encryption strategy that passes messages through unchanged.
 * <p>
 * This strategy is useful for:
 * <ul>
 *   <li>Testing and debugging without encryption overhead</li>
 *   <li>Development environments</li>
 *   <li>Gradual migration to encrypted communication</li>
 *   <li>Fallback when encryption is not available</li>
 * </ul>
 * <p>
 * Warning: This strategy provides NO security. Messages are sent in plaintext.
 * Do not use in production for sensitive communications.
 * <p>
 * Usage:
 * <pre>{@code
 * // For testing
 * EncryptionStrategy strategy = new NoOpEncryptionStrategy();
 *
 * // Messages pass through unchanged
 * ProtocolMessage encrypted = strategy.encrypt(message);
 * assert encrypted == message; // Same object
 * }</pre>
 *
 * @see EncryptionStrategy
 * @see AESEncryptionStrategy
 */
public class NoOpEncryptionStrategy implements EncryptionStrategy {

    private static final Logger LOG = Logger.getLogger(NoOpEncryptionStrategy.class.getName());

    private final boolean warnOnUse;

    // ========================= Constructors =========================

    /**
     * Creates a NoOpEncryptionStrategy with warnings enabled.
     */
    public NoOpEncryptionStrategy() {
        this(true);
    }

    /**
     * Creates a NoOpEncryptionStrategy.
     *
     * @param warnOnUse if true, logs warnings when encryption/decryption is bypassed
     */
    public NoOpEncryptionStrategy(boolean warnOnUse) {
        this.warnOnUse = warnOnUse;
        if (warnOnUse) {
            LOG.warning("NoOpEncryptionStrategy in use - messages are NOT encrypted!");
        }
    }

    // ========================= EncryptionStrategy Implementation =========================

    /**
     * Returns the message unchanged.
     * <p>
     * No encryption is performed.
     *
     * @param message the message to "encrypt"
     * @return the same message, unchanged
     */
    @Override
    public ProtocolMessage encrypt(ProtocolMessage message) throws GeneralSecurityException {
        if (warnOnUse) {
            LOG.fine(() -> "NoOp: Bypassing encryption for " + message.getMessageType());
        }
        return message;
    }

    /**
     * Attempts to unwrap an encrypted message without a key.
     * <p>
     * This will fail because we don't have the key to decrypt.
     * If you receive an EncryptedWrapper with NoOpStrategy, it means
     * the sender encrypted but you can't decrypt.
     *
     * @param wrapper the encrypted wrapper
     * @return never returns normally
     * @throws GeneralSecurityException always, since we can't decrypt
     */
    @Override
    public ProtocolMessage decrypt(EncryptedWrapper wrapper) throws GeneralSecurityException {
        // We can't actually decrypt without keys
        // This situation shouldn't occur in normal usage:
        // - If sender uses NoOp, they send plaintext (not EncryptedWrapper)
        // - If receiver uses NoOp but sender uses real encryption, we can't decrypt

        LOG.severe("Cannot decrypt EncryptedWrapper with NoOpEncryptionStrategy! " +
                "Sender used encryption but receiver has no keys.");

        throw new GeneralSecurityException(
                "Cannot decrypt: NoOpEncryptionStrategy has no decryption capability. " +
                        "The sender used encryption but this client is not configured for it."
        );
    }

    /**
     * Always returns false - no encryption needed.
     *
     * @param type the message type (ignored)
     * @param recipientId the recipient ID (ignored)
     * @return always false
     */
    @Override
    public boolean shouldEncrypt(MessageType type, int recipientId) {
        return false;
    }

    /**
     * Returns false - encryption is disabled.
     *
     * @return always false
     */
    @Override
    public boolean isEnabled() {
        return false;
    }

    /**
     * Always returns true - no session needed for pass-through.
     * <p>
     * This allows message sending to proceed without key exchange.
     *
     * @param peerId the peer ID (ignored)
     * @return always true
     */
    @Override
    public boolean hasSessionKey(int peerId) {
        // Pretend we always have a session so messages can be sent
        return true;
    }

    /**
     * Does nothing - no session needed.
     *
     * @param peerId the peer ID (ignored)
     */
    @Override
    public void ensureSessionExists(int peerId) {
        // No-op: sessions not needed
        if (warnOnUse) {
            LOG.fine("NoOp: Ignoring session check for peer " + peerId);
        }
    }

    @Override
    public String getName() {
        return "NoOp (Disabled)";
    }

    @Override
    public String toString() {
        return "NoOpEncryptionStrategy{warnOnUse=" + warnOnUse + "}";
    }
}
