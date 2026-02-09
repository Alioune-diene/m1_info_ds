package fr.uga.im2ag.m1info.tchatsapp.crypto.keyexchange;

import fr.uga.im2ag.m1info.tchatsapp.crypto.KeyExchange;
import fr.uga.im2ag.m1info.tchatsapp.crypto.SessionKeyManager;
import fr.uga.im2ag.m1info.tchatsapp.storage.KeyStore;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages ECDH key exchanges for private peer-to-peer conversations.
 * <p>
 * This is the refactored version of the original KeyExchangeManager,
 * now implementing IKeyExchangeManager and focused on private conversations.
 * <p>
 * Thread Safety: This class is thread-safe.
 *
 * @see IKeyExchangeManager
 * @see KeyExchange
 * @see SessionKeyManager
 */
public class PrivateKeyExchangeManager implements IKeyExchangeManager {

    private static final Logger LOG = Logger.getLogger(PrivateKeyExchangeManager.class.getName());

    // ========================= Constants =========================

    private static final String KEY_ALGORITHM = "X25519";
    private static final String CRYPTO_PROVIDER = "BC";
    private static final Duration CLEANUP_INTERVAL = Duration.ofSeconds(5);
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final String PRIVATE_PREFIX = "private_";

    // ========================= Dependencies =========================

    private final int localClientId;
    private final KeyExchange keyExchange;
    private final SessionKeyManager sessionManager;
    private final KeyStore keyStore;

    // ========================= State =========================

    private final ConcurrentMap<Integer, PendingKeyExchange> pendingExchanges;
    private final ConcurrentMap<Integer, Integer> retryCounters;
    private final List<KeyExchangeListener> listeners;
    private volatile Consumer<KeyExchangeMessageData> messageSender;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running;
    private ScheduledFuture<?> cleanupTask;

    // ========================= Constructor =========================

    /**
     * Creates a new PrivateKeyExchangeManager.
     *
     * @param localClientId  the ID of the local client
     * @param sessionManager the session key manager
     * @param keyStore       the key store (may be null)
     */
    public PrivateKeyExchangeManager(int localClientId, SessionKeyManager sessionManager, KeyStore keyStore) {
        if (localClientId <= 0) {
            throw new IllegalArgumentException("Local client ID must be positive: " + localClientId);
        }
        Objects.requireNonNull(sessionManager, "Session manager cannot be null");

        this.localClientId = localClientId;
        this.keyExchange = new KeyExchange();
        this.sessionManager = sessionManager;
        this.keyStore = keyStore;

        this.pendingExchanges = new ConcurrentHashMap<>();
        this.retryCounters = new ConcurrentHashMap<>();
        this.listeners = new CopyOnWriteArrayList<>();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PrivateKeyExchange-Cleanup");
            t.setDaemon(true);
            return t;
        });
        this.running = new AtomicBoolean(false);

        loadPersistedSessions();
    }

    // ========================= IKeyExchangeManager Implementation =========================

    @Override
    public boolean canHandle(int conversationId) {
        return conversationId > 0;
    }

    @Override
    public void initiateKeyExchange(int targetId) throws KeyExchangeException {
        ensureRunning();
        validatePeerId(targetId);

        LOG.info("Initiating private key exchange with peer " + targetId);

        // Check if we already have a pending exchange
        PendingKeyExchange existing = pendingExchanges.get(targetId);
        if (existing != null && !existing.isExpiredOrMarkedExpired()) {
            LOG.fine("Key exchange already pending with peer " + targetId);
            return;
        }

        try {
            // Generate new key pair
            KeyPair keyPair = keyExchange.generateKeyPair();

            // Create pending exchange
            PendingKeyExchange pending = new PendingKeyExchange(targetId, keyPair, true);
            pendingExchanges.put(targetId, pending);

            // Send KEY_EXCHANGE message
            byte[] publicKeyBytes = keyPair.getPublic().getEncoded();
            sendKeyExchangeMessage(targetId, publicKeyBytes, false);

            // Notify listeners
            notifyInitiated(targetId, pending);

        } catch (Exception e) {
            handleExchangeFailure(targetId, e);
        }
    }

    @Override
    public void handleKeyExchangeRequest(int peerId, byte[] publicKeyBytes) throws KeyExchangeException {
        ensureRunning();
        validatePeerId(peerId);

        // Skip if this is group key data
        if (GroupKeyExchangeData.isGroupKeyExchange(publicKeyBytes)) {
            return;
        }

        LOG.info("Handling private key exchange request from peer " + peerId);

        try {
            PublicKey peerPublicKey = decodePublicKey(publicKeyBytes);

            // Check for existing exchange
            PendingKeyExchange existing = pendingExchanges.get(peerId);

            if (existing != null && existing.getState() == KeyExchangeState.INITIATED) {
                // We initiated, peer also initiated - resolve conflict
                if (shouldAcceptIncomingExchange(peerId)) {
                    LOG.fine("Accepting incoming exchange from " + peerId + " (conflict resolution)");
                    pendingExchanges.remove(peerId);
                } else {
                    LOG.fine("Rejecting incoming exchange from " + peerId + " (we have priority)");
                    return;
                }
            }

            // Generate our key pair
            KeyPair ourKeyPair = keyExchange.generateKeyPair();

            // Derive session key
            SecretKey sessionKey = deriveSessionKey(ourKeyPair, peerPublicKey, peerId);

            // Store session
            storeSession(peerId, sessionKey);

            // Create pending exchange in RECEIVED state
            PendingKeyExchange pending = new PendingKeyExchange(peerId, ourKeyPair, false);
            pendingExchanges.put(peerId, pending.complete());

            // Send KEY_EXCHANGE_RESPONSE
            sendKeyExchangeMessage(peerId, ourKeyPair.getPublic().getEncoded(), true);

            // Notify listeners
            notifyReceived(peerId);
            notifyCompleted(peerId, sessionKey);

        } catch (Exception e) {
            handleExchangeFailure(peerId, e);
        }
    }

    @Override
    public void handleKeyExchangeResponse(int peerId, byte[] publicKeyBytes) throws KeyExchangeException {
        ensureRunning();
        validatePeerId(peerId);

        // Skip if this is group key ACK
        if (GroupKeyExchangeData.isGroupKeyAck(publicKeyBytes)) {
            return;
        }

        LOG.info("Handling private key exchange response from peer " + peerId);

        PendingKeyExchange pending = pendingExchanges.get(peerId);
        if (pending == null) {
            LOG.warning("Received KEY_EXCHANGE_RESPONSE without pending exchange from " + peerId);
            return;
        }

        if (pending.getState() != KeyExchangeState.INITIATED) {
            LOG.warning("Unexpected KEY_EXCHANGE_RESPONSE in state " + pending.getState());
            return;
        }

        try {
            PublicKey peerPublicKey = decodePublicKey(publicKeyBytes);
            SecretKey sessionKey = deriveSessionKey(pending.getEphemeralKeyPair(), peerPublicKey, peerId);
            storeSession(peerId, sessionKey);
            pendingExchanges.put(peerId, pending.complete());
            retryCounters.remove(peerId);
            notifyCompleted(peerId, sessionKey);

        } catch (Exception e) {
            handleExchangeFailure(peerId, e);
        }
    }

    @Override
    public boolean hasSessionWith(int targetId) {
        String conversationId = PRIVATE_PREFIX + targetId;
        return sessionManager.hasSession(conversationId);
    }

    @Override
    public void invalidateSession(int targetId) {
        invalidateSession(targetId, "Manual invalidation");
    }

    @Override
    public void invalidateSession(int targetId, String reason) {
        String conversationId = PRIVATE_PREFIX + targetId;

        boolean removed = sessionManager.removeSession(conversationId);
        pendingExchanges.remove(targetId);
        retryCounters.remove(targetId);

        if (removed) {
            LOG.info(String.format("Invalidated session with peer %d: %s", targetId, reason));
            notifyInvalidated(targetId, reason);

            // Persist the removal
            if (keyStore != null) {
                try {
                    keyStore.deleteSessionKey(conversationId);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Failed to remove session key from store", e);
                }
            }
        }
    }

    @Override
    public void setMessageSender(Consumer<KeyExchangeMessageData> messageSender) {
        this.messageSender = messageSender;
    }

    @Override
    public void addListener(KeyExchangeListener listener) {
        listeners.add(listener);
    }

    @Override
    public boolean removeListener(KeyExchangeListener listener) {
        return listeners.remove(listener);
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            cleanupTask = scheduler.scheduleAtFixedRate(
                    this::cleanupExpiredExchanges,
                    CLEANUP_INTERVAL.toMillis(),
                    CLEANUP_INTERVAL.toMillis(),
                    TimeUnit.MILLISECONDS
            );
            LOG.info("PrivateKeyExchangeManager started for client " + localClientId);
        }
    }

    @Override
    public void shutdown() {
        if (running.compareAndSet(true, false)) {
            if (cleanupTask != null) {
                cleanupTask.cancel(false);
            }
            scheduler.shutdown();

            // Fail all pending exchanges
            pendingExchanges.forEach((peerId, pending) -> {
                notifyFailed(peerId, new KeyExchangeException(
                        "Manager shutdown",
                        KeyExchangeException.ErrorCode.INTERNAL_ERROR,
                        peerId
                ));
            });
            pendingExchanges.clear();

            LOG.info("PrivateKeyExchangeManager shutdown");
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public SessionKeyManager getSessionManager() {
        return sessionManager;
    }

    @Override
    public int getLocalClientId() {
        return localClientId;
    }

    // ========================= Private Helper Methods =========================

    private void loadPersistedSessions() {
        if (keyStore == null) {
            return;
        }

        try {
            Map<String, SecretKey> persisted = keyStore.loadAllSessionKeys();
            for (Map.Entry<String, SecretKey> entry : persisted.entrySet()) {
                String conversationId = entry.getKey();
                if (conversationId.startsWith(PRIVATE_PREFIX)) {
                    sessionManager.storeSessionKey(conversationId, entry.getValue());
                    LOG.fine("Loaded persisted session for " + conversationId);
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load persisted sessions", e);
        }
    }

    private void storeSession(int peerId, SecretKey sessionKey) {
        String conversationId = PRIVATE_PREFIX + peerId;
        sessionManager.storeSessionKey(conversationId, sessionKey);

        if (keyStore != null) {
            try {
                keyStore.saveSessionKey(conversationId, sessionKey);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to persist session key", e);
            }
        }
    }

    /**
     * Derives a session key from ECDH shared secret using HKDF.
     * <p>
     * Uses a canonical conversation ID (smaller ID first) as context for domain separation.
     * This ensures both parties derive the same key regardless of who initiated the exchange.
     * Follows NIST SP 800-56C recommendations for key derivation.
     *
     * @param ourKeyPair     our ephemeral key pair
     * @param peerPublicKey  the peer's public key
     * @param peerId         the peer ID
     * @return the derived AES-256 session key
     * @throws GeneralSecurityException if key derivation fails
     */
    private SecretKey deriveSessionKey(KeyPair ourKeyPair, PublicKey peerPublicKey, int peerId) throws GeneralSecurityException {
        byte[] sharedSecret = keyExchange.deriveSharedSecret(
                ourKeyPair.getPrivate(), peerPublicKey
        );

        // Create canonical conversation ID: always use the smaller ID first
        // This ensures both parties derive the same key from the shared secret
        int smallerId = Math.min(localClientId, peerId);
        int largerId = Math.max(localClientId, peerId);
        String canonicalConversationId = PRIVATE_PREFIX + smallerId + "_" + largerId;

        byte[] keyBytes = keyExchange.deriveSessionKey(sharedSecret, canonicalConversationId);

        return new SecretKeySpec(keyBytes, "AES");
    }

    private PublicKey decodePublicKey(byte[] encodedKey) throws KeyExchangeException {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM, CRYPTO_PROVIDER);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encodedKey);
            return keyFactory.generatePublic(keySpec);
        } catch (Exception e) {
            throw new KeyExchangeException("Invalid public key format", KeyExchangeException.ErrorCode.INVALID_PUBLIC_KEY, e);
        }
    }

    private void sendKeyExchangeMessage(int peerId, byte[] publicKeyBytes, boolean isResponse) {
        Consumer<KeyExchangeMessageData> sender = this.messageSender;
        if (sender == null) {
            LOG.warning("No message sender configured");
            return;
        }
        KeyExchangeMessageData data =new KeyExchangeMessageData(localClientId, peerId, publicKeyBytes, isResponse);
        sender.accept(data);
    }

    private boolean shouldAcceptIncomingExchange(int peerId) {
        // Resolve conflicts by comparing IDs - lower ID wins
        return peerId < localClientId;
    }

    private void validatePeerId(int peerId) throws KeyExchangeException {
        if (peerId <= 0) {
            throw KeyExchangeException.invalidPeerId(peerId);
        }
        if (peerId == localClientId) {
            throw new KeyExchangeException( "Cannot exchange keys with self", KeyExchangeException.ErrorCode.INVALID_PEER_ID, peerId);
        }
    }

    private void ensureRunning() throws KeyExchangeException {
        if (!running.get()) {
            throw new KeyExchangeException( "Manager is not running", KeyExchangeException.ErrorCode.INTERNAL_ERROR);
        }
    }

    private void cleanupExpiredExchanges() {
        pendingExchanges.forEach((peerId, pending) -> {
            if (pending.isExpiredOrMarkedExpired() && !pending.isCompleted()) {
                pendingExchanges.put(peerId, pending.expire());
                notifyExpired(peerId);
                LOG.info("Private key exchange with peer " + peerId + " expired");
            }
        });

        // Remove completed/failed exchanges
        pendingExchanges.entrySet().removeIf(entry -> {
            PendingKeyExchange p = entry.getValue();
            return p.getState().isTerminal() && p.getElapsedTime().compareTo(CLEANUP_INTERVAL.multipliedBy(2)) > 0;
        });
    }

    private void handleExchangeFailure(int peerId, Exception cause) throws KeyExchangeException {
        PendingKeyExchange pending = pendingExchanges.get(peerId);
        if (pending != null) {
            pendingExchanges.put(peerId, pending.fail());
        }

        KeyExchangeException exception = (cause instanceof KeyExchangeException kee)
                ? kee : new KeyExchangeException("Key exchange failed with peer " + peerId, KeyExchangeException.ErrorCode.CRYPTO_FAILURE, cause);

        notifyFailed(peerId, exception);
        throw exception;
    }

    // ========================= Listener Notifications =========================

    private void notifyInitiated(int peerId, PendingKeyExchange pending) {
        for (KeyExchangeListener listener : listeners) {
            try {
                listener.onKeyExchangeInitiated(peerId, pending);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Listener threw exception", e);
            }
        }
    }

    private void notifyReceived(int peerId) {
        for (KeyExchangeListener listener : listeners) {
            try {
                listener.onKeyExchangeReceived(peerId);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Listener threw exception", e);
            }
        }
    }

    private void notifyCompleted(int peerId, SecretKey sessionKey) {
        for (KeyExchangeListener listener : listeners) {
            try {
                listener.onKeyExchangeCompleted(peerId, sessionKey);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Listener threw exception", e);
            }
        }
    }

    private void notifyFailed(int peerId, KeyExchangeException cause) {
        for (KeyExchangeListener listener : listeners) {
            try {
                listener.onKeyExchangeFailed(peerId, cause);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Listener threw exception", e);
            }
        }
    }

    private void notifyExpired(int peerId) {
        for (KeyExchangeListener listener : listeners) {
            try {
                listener.onKeyExchangeExpired(peerId);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Listener threw exception", e);
            }
        }
    }

    private void notifyInvalidated(int peerId, String reason) {
        for (KeyExchangeListener listener : listeners) {
            try {
                listener.onSessionInvalidated(peerId, reason);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Listener threw exception", e);
            }
        }
    }
}