package fr.uga.im2ag.m1info.tchatsapp.crypto.keyexchange;

import javax.crypto.SecretKey;

/**
 * Listener interface for key exchange events.
 * <p>
 * Implementations receive notifications about key exchange lifecycle events.
 * All callbacks are invoked on the thread that triggered the event, so
 * implementations should avoid blocking operations or delegate to another thread.
 * <p>
 * Example usage:
 * <pre>{@code
 * keyExchangeManager.addListener(new KeyExchangeListener() {
 *     @Override
 *     public void onKeyExchangeCompleted(int peerId, SecretKey sessionKey) {
 *         log.info("Session established with peer {}", peerId);
 *         flushPendingMessages(peerId);
 *     }
 *     
 *     @Override
 *     public void onKeyExchangeFailed(int peerId, KeyExchangeException cause) {
 *         log.error("Key exchange with peer {} failed", peerId, cause);
 *         notifyUser("Could not establish secure connection");
 *     }
 * });
 * }</pre>
 *
 * @see KeyExchangeManager
 */
public interface KeyExchangeListener {
    
    /**
     * Called when a key exchange is initiated with a peer.
     * <p>
     * This is called after the KEY_EXCHANGE message has been queued for sending.
     *
     * @param peerId the ID of the peer client
     * @param pending the pending exchange state
     */
    default void onKeyExchangeInitiated(int peerId, PendingKeyExchange pending) {
        // Default: no-op
    }
    
    /**
     * Called when a KEY_EXCHANGE request is received from a peer.
     * <p>
     * This is called before processing the request.
     *
     * @param peerId the ID of the peer client
     */
    default void onKeyExchangeReceived(int peerId) {
        // Default: no-op
    }
    
    /**
     * Called when a key exchange completes successfully.
     * <p>
     * At this point, the session key has been derived and stored.
     * Encrypted communication with the peer is now possible.
     *
     * @param peerId     the ID of the peer client
     * @param sessionKey the derived session key (handle with care)
     */
    void onKeyExchangeCompleted(int peerId, SecretKey sessionKey);
    
    /**
     * Called when a key exchange fails.
     * <p>
     * Possible causes include:
     * <ul>
     *   <li>Invalid public key received</li>
     *   <li>Cryptographic operation failure</li>
     *   <li>Storage failure</li>
     *   <li>Protocol violation</li>
     * </ul>
     *
     * @param peerId the ID of the peer client
     * @param cause  the exception that caused the failure
     */
    void onKeyExchangeFailed(int peerId, KeyExchangeException cause);
    
    /**
     * Called when a key exchange expires before completion.
     * <p>
     * This typically means the peer did not respond within the timeout period.
     *
     * @param peerId the ID of the peer client
     */
    default void onKeyExchangeExpired(int peerId) {
        // Default: treat as failure
        onKeyExchangeFailed(peerId, new KeyExchangeException(
            "Key exchange with peer " + peerId + " expired", 
            KeyExchangeException.ErrorCode.TIMEOUT
        ));
    }
    
    /**
     * Called when a session key is rotated (replaced with a new one).
     * <p>
     * Key rotation occurs for security reasons (e.g., after many messages).
     *
     * @param peerId        the ID of the peer client
     * @param newSessionKey the new session key
     */
    default void onKeyRotated(int peerId, SecretKey newSessionKey) {
        // Default: no-op
    }
    
    /**
     * Called when a session with a peer is invalidated.
     * <p>
     * This may happen due to:
     * <ul>
     *   <li>Explicit invalidation request</li>
     *   <li>Security policy (e.g., session expired)</li>
     *   <li>Peer disconnection</li>
     * </ul>
     *
     * @param peerId the ID of the peer client
     * @param reason human-readable reason for invalidation
     */
    default void onSessionInvalidated(int peerId, String reason) {
        // Default: no-op
    }
}
