package fr.uga.im2ag.m1info.tchatsapp.crypto.keyexchange;

import fr.uga.im2ag.m1info.tchatsapp.crypto.SessionKeyManager;

import java.util.function.Consumer;

/**
 * Interface for key exchange managers.
 * <p>
 * Provides a common contract for managing key exchanges in different contexts
 * (private conversations, groups, etc.). Implementations handle the specific
 * protocols and requirements for their conversation types.
 * <p>
 * Thread Safety: Implementations must be thread-safe.
 *
 * @see PrivateKeyExchangeManager
 * @see GroupKeyExchangeManager
 * @see CompositeKeyExchangeManager
 */
public interface IKeyExchangeManager {

    /**
     * Checks if this manager can handle key exchange for the given conversation ID.
     *
     * @param conversationId the conversation/group/peer ID
     * @return true if this manager handles this type of conversation
     */
    boolean canHandle(int conversationId);

    /**
     * Initiates a key exchange with the target (peer or group).
     *
     * @param targetId the target ID (peer ID for private, group ID for groups)
     * @throws KeyExchangeException if the exchange cannot be initiated
     */
    void initiateKeyExchange(int targetId) throws KeyExchangeException;

    /**
     * Handles an incoming KEY_EXCHANGE request.
     *
     * @param peerId         the peer who sent the request
     * @param publicKeyBytes the public key or encrypted group key data
     * @throws KeyExchangeException if the request cannot be processed
     */
    void handleKeyExchangeRequest(int peerId, byte[] publicKeyBytes) throws KeyExchangeException;

    /**
     * Handles an incoming KEY_EXCHANGE_RESPONSE.
     *
     * @param peerId         the peer who sent the response
     * @param publicKeyBytes the public key or encrypted group key data
     * @throws KeyExchangeException if the response cannot be processed
     */
    void handleKeyExchangeResponse(int peerId, byte[] publicKeyBytes) throws KeyExchangeException;

    /**
     * Checks if a secure session exists with the target.
     *
     * @param targetId the target ID (peer ID for private, group ID for groups)
     * @return true if a session key exists and is valid
     */
    boolean hasSessionWith(int targetId);

    /**
     * Invalidates the session with the target.
     * <p>
     * This removes the session key and requires a new key exchange
     * before encrypted communication can resume.
     *
     * @param targetId the target ID
     */
    void invalidateSession(int targetId);

    /**
     * Invalidates the session with a specific reason.
     *
     * @param targetId the target ID
     * @param reason   the reason for invalidation
     */
    void invalidateSession(int targetId, String reason);

    /**
     * Sets the message sender callback for outgoing key exchange messages.
     *
     * @param messageSender the callback to send messages
     */
    void setMessageSender(Consumer<KeyExchangeMessageData> messageSender);

    /**
     * Adds a listener for key exchange events.
     *
     * @param listener the listener to add
     */
    void addListener(KeyExchangeListener listener);

    /**
     * Removes a listener.
     *
     * @param listener the listener to remove
     * @return true if the listener was removed
     */
    boolean removeListener(KeyExchangeListener listener);

    /**
     * Starts the key exchange manager.
     * <p>
     * Must be called before any key exchange operations.
     */
    void start();

    /**
     * Shuts down the key exchange manager.
     * <p>
     * Cancels all pending operations and releases resources.
     */
    void shutdown();

    /**
     * Checks if the manager is running.
     *
     * @return true if started and not shutdown
     */
    boolean isRunning();

    /**
     * Gets the session key manager.
     *
     * @return the session key manager
     */
    SessionKeyManager getSessionManager();

    /**
     * Gets the local client ID.
     *
     * @return the local client ID
     */
    int getLocalClientId();

    /**
     * Rotates the key for a group (only for group managers).
     *
     * @param groupId the group ID
     * @throws UnsupportedOperationException if not a group manager
     * @throws KeyExchangeException if rotation fails
     */
    default void rotateGroupKey(int groupId) throws KeyExchangeException {
        throw new UnsupportedOperationException("Key rotation not supported by this manager");
    }

    /**
     * Handles a group member change (only for group managers).
     *
     * @param groupId    the group ID
     * @param memberId   the member ID
     * @param isAddition true if member was added, false if removed
     * @throws UnsupportedOperationException if not a group manager
     */
    default void handleGroupMemberChange(int groupId, int memberId, boolean isAddition) {
        throw new UnsupportedOperationException("Group member changes not supported by this manager");
    }
}