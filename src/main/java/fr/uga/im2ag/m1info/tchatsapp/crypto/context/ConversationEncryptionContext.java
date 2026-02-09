package fr.uga.im2ag.m1info.tchatsapp.crypto.context;

import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.EncryptedWrapper;
import fr.uga.im2ag.m1info.tchatsapp.crypto.SessionKeyManager;
import fr.uga.im2ag.m1info.tchatsapp.crypto.SymmetricCipher;

import javax.crypto.SecretKey;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.Objects;

/**
 * Implementation of {@link EncryptedWrapper.EncryptionContext} for a specific conversation.
 * <p>
 * This class provides encryption and decryption capabilities for a single conversation
 * between two peers. It manages:
 * <ul>
 *   <li>Nonce generation for each encryption operation</li>
 *   <li>Sequence number management for replay protection</li>
 *   <li>AAD (Associated Authenticated Data) construction for metadata authentication</li>
 * </ul>
 * <p>
 * Thread Safety: This class is thread-safe when used with a thread-safe
 * {@link SessionKeyManager}.
 * <p>
 * Usage:
 * <pre>{@code
 * ConversationEncryptionContext ctx = new ConversationEncryptionContext(
 *     conversationId, sessionManager, cipher, localClientId, peerId
 * );
 * 
 * // Encrypt a message
 * EncryptedWrapper wrapper = EncryptedWrapper.wrap(message, ctx);
 * 
 * // Decrypt a message
 * ProtocolMessage decrypted = wrapper.unwrap(ctx);
 * }</pre>
 *
 * @see EncryptedWrapper.EncryptionContext
 * @see SessionKeyManager
 * @see SymmetricCipher
 */
public class ConversationEncryptionContext implements EncryptedWrapper.EncryptionContext {

    // ========================= Constants =========================

    /** Length of AAD: from (4) + to (4) + sequenceNumber (8) = 16 bytes */
    private static final int AAD_LENGTH = 16;

    // ========================= Dependencies =========================

    private final String conversationId;
    private final SessionKeyManager sessionManager;
    private final SymmetricCipher cipher;
    private final int localClientId;
    private final int peerId;

    // ========================= Constructor =========================

    /**
     * Creates a new ConversationEncryptionContext.
     *
     * @param conversationId the unique identifier for this conversation
     * @param sessionManager the session key manager containing the session key
     * @param cipher         the symmetric cipher for encryption/decryption
     * @param localClientId  the local client's ID (sender for outgoing, receiver for incoming)
     * @param peerId         the peer's ID
     * @throws NullPointerException     if any parameter is null
     * @throws IllegalArgumentException if conversationId is empty or IDs are invalid
     */
    public ConversationEncryptionContext(
            String conversationId,
            SessionKeyManager sessionManager,
            SymmetricCipher cipher,
            int localClientId,
            int peerId) {

        Objects.requireNonNull(conversationId, "Conversation ID cannot be null");
        Objects.requireNonNull(sessionManager, "Session manager cannot be null");
        Objects.requireNonNull(cipher, "Cipher cannot be null");

        if (conversationId.isEmpty()) {
            throw new IllegalArgumentException("Conversation ID cannot be empty");
        }
        if (localClientId <= 0) {
            throw new IllegalArgumentException("Local client ID must be positive: " + localClientId);
        }
        if (peerId <= 0) {
            throw new IllegalArgumentException("Peer ID must be positive: " + peerId);
        }

        this.conversationId = conversationId;
        this.sessionManager = sessionManager;
        this.cipher = cipher;
        this.localClientId = localClientId;
        this.peerId = peerId;
    }

    // ========================= EncryptionContext Implementation =========================

    /**
     * Generates a cryptographically secure random nonce.
     * <p>
     * The nonce is 12 bytes (96 bits) as recommended for AES-GCM.
     *
     * @return a new random nonce
     */
    @Override
    public byte[] generateNonce() {
        return cipher.generateNonce();
    }

    /**
     * Gets and increments the sequence number for sending.
     * <p>
     * This method is thread-safe and atomic.
     *
     * @return the next sequence number to use
     * @throws IllegalStateException if no session exists for this conversation
     */
    @Override
    public long getAndIncrementSequence() {
        return sessionManager.getNextSendSequence(conversationId);
    }

    /**
     * Encrypts plaintext using AES-256-GCM with AAD.
     * <p>
     * The AAD includes: localClientId (4 bytes) + peerId (4 bytes) + sequenceNumber (8 bytes).
     * This ensures that the metadata is authenticated along with the ciphertext.
     *
     * @param plaintext      the data to encrypt
     * @param nonce          the nonce (must be unique per encryption)
     * @param sequenceNumber the sequence number for this message
     * @return the ciphertext with GCM authentication tag
     * @throws GeneralSecurityException if encryption fails
     * @throws IllegalStateException    if no session key exists
     */
    @Override
    public byte[] encrypt(byte[] plaintext, byte[] nonce, long sequenceNumber)
            throws GeneralSecurityException {

        SecretKey key = getSessionKeyOrThrow();
        byte[] aad = buildAAD(localClientId, peerId, sequenceNumber);

        return cipher.encrypt(plaintext, key, nonce, aad);
    }

    /**
     * Decrypts ciphertext using AES-256-GCM with AAD verification.
     * <p>
     * Also validates the sequence number for replay protection.
     *
     * @param ciphertext     the encrypted data with GCM tag
     * @param nonce          the nonce used during encryption
     * @param sequenceNumber the sequence number from the message
     * @return the decrypted plaintext
     * @throws GeneralSecurityException if decryption fails or AAD doesn't match
     * @throws SecurityException        if replay attack detected (sequence number validation failed)
     */
    @Override
    public byte[] decrypt(byte[] ciphertext, byte[] nonce, long sequenceNumber)
            throws GeneralSecurityException {

        SecretKey key = getSessionKeyOrThrow();

        // Validate sequence number for replay protection
        if (!sessionManager.validateReceiveSequence(conversationId, sequenceNumber)) {
            throw new SecurityException(
                    "Replay attack detected: invalid sequence number " + sequenceNumber +
                            " for conversation " + conversationId
            );
        }

        // AAD for decryption: peerId is sender, localClientId is receiver
        byte[] aad = buildAAD(peerId, localClientId, sequenceNumber);

        return cipher.decrypt(ciphertext, key, nonce, aad);
    }

    // ========================= Public Utility Methods =========================

    /**
     * Gets the conversation ID.
     *
     * @return the conversation ID
     */
    public String getConversationId() {
        return conversationId;
    }

    /**
     * Gets the local client ID.
     *
     * @return the local client ID
     */
    public int getLocalClientId() {
        return localClientId;
    }

    /**
     * Gets the peer ID.
     *
     * @return the peer ID
     */
    public int getPeerId() {
        return peerId;
    }

    /**
     * Checks if a valid session key exists.
     *
     * @return true if the session key is available
     */
    public boolean hasSessionKey() {
        return sessionManager.hasSession(conversationId);
    }

    /**
     * Checks if key rotation is recommended.
     *
     * @return true if the session key should be rotated
     */
    public boolean shouldRotateKey() {
        return sessionManager.shouldRotateKey(conversationId);
    }

    // ========================= Private Helper Methods =========================

    /**
     * Retrieves the session key or throws an exception if not available.
     *
     * @return the session key
     * @throws IllegalStateException if no session key exists
     */
    private SecretKey getSessionKeyOrThrow() {
        SecretKey key = sessionManager.getSessionKey(conversationId);
        if (key == null) {
            throw new IllegalStateException(
                    "No session key for conversation: " + conversationId
            );
        }
        return key;
    }

    /**
     * Builds the Associated Authenticated Data (AAD) for GCM.
     * <p>
     * Format: [from: 4 bytes][to: 4 bytes][sequenceNumber: 8 bytes]
     *
     * @param from           the sender ID
     * @param to             the recipient ID
     * @param sequenceNumber the sequence number
     * @return the AAD bytes
     */
    private static byte[] buildAAD(int from, int to, long sequenceNumber) {
        ByteBuffer buffer = ByteBuffer.allocate(AAD_LENGTH);
        buffer.putInt(from);
        buffer.putInt(to);
        buffer.putLong(sequenceNumber);
        return buffer.array();
    }

    @Override
    public String toString() {
        return "ConversationEncryptionContext{" +
                "conversationId='" + conversationId + '\'' +
                ", localClientId=" + localClientId +
                ", peerId=" + peerId +
                ", hasKey=" + hasSessionKey() +
                '}';
    }
}
