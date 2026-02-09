package fr.uga.im2ag.m1info.tchatsapp.crypto.strategy;

import fr.uga.im2ag.m1info.tchatsapp.common.MessageType;
import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.EncryptedWrapper;
import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.ProtocolMessage;
import fr.uga.im2ag.m1info.tchatsapp.crypto.SessionKeyManager;
import fr.uga.im2ag.m1info.tchatsapp.crypto.SymmetricCipher;
import fr.uga.im2ag.m1info.tchatsapp.crypto.context.ConversationEncryptionContext;
import fr.uga.im2ag.m1info.tchatsapp.crypto.keyexchange.CompositeKeyExchangeManager;
import fr.uga.im2ag.m1info.tchatsapp.crypto.keyexchange.IKeyExchangeManager;

import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * AES-256-GCM encryption strategy supporting both private and group conversations.
 * <p>
 * This strategy encrypts messages using AES-256-GCM with:
 * <ul>
 *   <li>ECDH-derived keys for private conversations</li>
 *   <li>Symmetric group keys for group conversations</li>
 *   <li>Automatic context management and caching</li>
 *   <li>Support for all message types requiring encryption</li>
 * </ul>
 */
public class AESEncryptionStrategy implements EncryptionStrategy {

    private static final Logger LOG = Logger.getLogger(AESEncryptionStrategy.class.getName());

    // ========================= Message Types Excluded from Encryption =========================

    /**
     * Message types that should never be encrypted.
     * These are protocol-level messages required for establishing encryption.
     */
    private static final Set<MessageType> EXCLUDED_TYPES = Set.of(
            MessageType.KEY_EXCHANGE,
            MessageType.KEY_EXCHANGE_RESPONSE,
            MessageType.ENCRYPTED
    );

    // ========================= Dependencies =========================

    private final int localClientId;
    private final SessionKeyManager sessionManager;
    private final IKeyExchangeManager keyExchangeManager;
    private final SymmetricCipher cipher;

    // ========================= Context Cache =========================

    private final Map<String, ConversationEncryptionContext> contextCache;

    // ========================= Constructor =========================

    public AESEncryptionStrategy(int localClientId, SessionKeyManager sessionManager, IKeyExchangeManager keyExchangeManager) {
        if (localClientId <= 0) {
            throw new IllegalArgumentException("Local client ID must be positive: " + localClientId);
        }
        if (sessionManager == null) {
            throw new NullPointerException("Session manager cannot be null");
        }
        if (keyExchangeManager == null) {
            throw new NullPointerException("Key exchange manager cannot be null");
        }

        this.localClientId = localClientId;
        this.sessionManager = sessionManager;
        this.keyExchangeManager = keyExchangeManager;
        this.cipher = new SymmetricCipher();
        this.contextCache = new ConcurrentHashMap<>();
    }

    // ========================= EncryptionStrategy Implementation =========================

    @Override
    public ProtocolMessage encrypt(ProtocolMessage message) throws GeneralSecurityException {
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }

        if (!shouldEncrypt(message.getMessageType(), message.getTo())) {
            LOG.fine(String.format("Message to %d of type %s does not require encryption", message.getTo(), message.getMessageType()));
            return message;
        }

        try {
            // Get or create encryption context
            ConversationEncryptionContext context = getOrCreateContext(message.getTo());

            if (context == null) {
                LOG.warning(String.format("No encryption context available for recipient %d", message.getTo()));
                return message;
            }
            LOG.fine(String.format("Encrypting %s message to %d", message.getMessageType(), message.getTo()));
            return EncryptedWrapper.wrap(message, context);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to encrypt message", e);
            throw new GeneralSecurityException("Encryption failed: " + e.getMessage(), e);
        }
    }

    @Override
    public ProtocolMessage decrypt(EncryptedWrapper wrapper) throws GeneralSecurityException {
        try {
            int senderId = wrapper.getFrom();
            int targetId = wrapper.getTo();

            String conversationId;
            int localIdForContext;
            int peerIdForContext;

            if (targetId == localClientId) {
                conversationId = getConversationId(senderId);
                localIdForContext = localClientId;
                peerIdForContext = senderId;
                LOG.fine(String.format("Decrypting private message from %d using context %s", senderId, conversationId));
            } else {
                conversationId = getConversationId(targetId);
                localIdForContext = targetId;
                peerIdForContext = senderId;
                LOG.fine(String.format("Decrypting group message from %d to group %d using context %s", senderId, targetId, conversationId));
            }

            ConversationEncryptionContext context = getOrCreateContextForConversation(conversationId, localIdForContext, peerIdForContext);

            if (context == null) {
                throw new GeneralSecurityException(String.format("No decryption context available for %s", conversationId));
            }

            ProtocolMessage decrypted = wrapper.unwrap(context);

            LOG.fine(String.format("Successfully decrypted %s message from %d (conversation %s)", decrypted.getMessageType(), senderId, conversationId));

            return decrypted;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to decrypt message", e);
            throw new GeneralSecurityException("Decryption failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean shouldEncrypt(MessageType messageType, int recipientId) {
        // Don't encrypt system messages
        // TODO: Encrypt
        if (recipientId == 0) {
            return false;
        }

        // Check for session key
        if (!hasSessionKey(recipientId)) {
            LOG.fine(String.format("No session key for recipient %d", recipientId));
            return false;
        }

        return !EXCLUDED_TYPES.contains(messageType);
    }

    /**
     * {@inheritDoc}
     *
     * @return always true for this strategy
     */
    @Override
    public boolean isEnabled() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasSessionKey(int peerId) {
        String conversationId = getConversationId(peerId);
        return sessionManager.hasSession(conversationId);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Initiates ECDH key exchange if no session exists.
     */
    @Override
    public void ensureSessionExists(int peerId) throws GeneralSecurityException {
        String conversationId = getConversationId(peerId);
        if (!sessionManager.hasSession(conversationId)) {
            throw new GeneralSecurityException("No session key exists for conversation " + conversationId);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invalidateContext(int peerId) {
        String conversationId = getConversationId(peerId);
        ConversationEncryptionContext removed = contextCache.remove(conversationId);
        if (removed != null) {
            LOG.fine(String.format("Invalidated encryption context for %s", conversationId));
        }
    }

    @Override
    public void clearAllContexts() {
        int count = contextCache.size();
        contextCache.clear();
        LOG.info(String.format("Cleared %d encryption contexts", count));
    }

    // ========================= Private Helper Methods =========================

    /**
     * Gets the conversation ID for a peer or group.
     */
    private String getConversationId(int targetId) {
        if (keyExchangeManager instanceof CompositeKeyExchangeManager composite) {
            if (composite.isGroup(targetId)) {
                return "group_" + targetId;
            }
        }

        return "private_" + targetId;
    }

    /**
     * Gets or creates an encryption context for a target.
     * Used for encryption (when we know the target).
     */
    private ConversationEncryptionContext getOrCreateContext(int targetId) {
        String conversationId = getConversationId(targetId);
        return getOrCreateContextForConversation(conversationId, localClientId, targetId);
    }

    /**
     * Gets or creates an encryption context for a specific conversation.
     * Used for both encryption and decryption.
     */
    private ConversationEncryptionContext getOrCreateContextForConversation(
            String conversationId, int localId, int remoteId) {

        // Check cache first
        ConversationEncryptionContext context = contextCache.get(conversationId);

        if (context != null && context.hasSessionKey()) {
            return context;
        }

        if (!sessionManager.hasSession(conversationId)) {
            LOG.fine(String.format("No session key for conversation %s", conversationId));
            return null;
        }

        context = new ConversationEncryptionContext(
                conversationId,
                sessionManager,
                cipher,
                localId,
                remoteId
        );

        contextCache.put(conversationId, context);
        LOG.fine(String.format("Created new encryption context for %s (local=%d, remote=%d)", conversationId, localId, remoteId));
        return context;
    }
}