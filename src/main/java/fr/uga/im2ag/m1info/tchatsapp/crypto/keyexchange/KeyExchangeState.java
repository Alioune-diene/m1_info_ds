package fr.uga.im2ag.m1info.tchatsapp.crypto.keyexchange;

/**
 * Represents the possible states of a key exchange operation.
 */
public enum KeyExchangeState {
    
    /**
     * No key exchange in progress.
     * Initial state before any exchange is initiated.
     */
    IDLE,
    
    /**
     * Key exchange has been initiated by the local client.
     * Waiting for KEY_EXCHANGE_RESPONSE from the peer.
     */
    INITIATED,
    
    /**
     * Key exchange request received from a peer.
     * Waiting for local processing to complete.
     */
    RECEIVED,
    
    /**
     * Key exchange completed successfully.
     * Session key has been derived and stored.
     */
    COMPLETED,
    
    /**
     * Key exchange failed due to an error.
     * May be retried.
     */
    FAILED,
    
    /**
     * Key exchange expired before completion.
     * Timeout occurred while waiting for response.
     */
    EXPIRED;
    
    /**
     * Checks if this state represents a terminal state.
     * Terminal states cannot transition to other states without starting a new exchange.
     *
     * @return true if this is a terminal state (COMPLETED, FAILED, or EXPIRED)
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == EXPIRED;
    }
    
    /**
     * Checks if this state represents an in-progress exchange.
     *
     * @return true if exchange is in progress (INITIATED or RECEIVED)
     */
    public boolean isInProgress() {
        return this == INITIATED || this == RECEIVED;
    }
    
    /**
     * Checks if this state allows sending encrypted messages.
     *
     * @return true only if state is COMPLETED
     */
    public boolean canEncrypt() {
        return this == COMPLETED;
    }
}
