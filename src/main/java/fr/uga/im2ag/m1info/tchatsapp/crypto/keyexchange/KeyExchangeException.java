package fr.uga.im2ag.m1info.tchatsapp.crypto.keyexchange;

import java.security.GeneralSecurityException;

/**
 * Exception thrown when a key exchange operation fails.
 * <p>
 * Provides detailed error codes to identify the cause of failure,
 * enabling appropriate error handling and recovery strategies.
 *
 * @see IKeyExchangeManager
 * @see ErrorCode
 */
public class KeyExchangeException extends GeneralSecurityException {
    
    /**
     * Error codes identifying specific failure causes.
     */
    public enum ErrorCode {
        /**
         * The public key received from peer is invalid or malformed.
         */
        INVALID_PUBLIC_KEY,
        
        /**
         * The peer ID is invalid (e.g., non-positive or same as local ID).
         */
        INVALID_PEER_ID,
        
        /**
         * A key exchange is already in progress with this peer.
         */
        EXCHANGE_ALREADY_IN_PROGRESS,
        
        /**
         * No pending key exchange exists for the received response.
         */
        NO_PENDING_EXCHANGE,
        
        /**
         * The key exchange expired before completion.
         */
        TIMEOUT,
        
        /**
         * Cryptographic operation failed (key generation, derivation, etc.).
         */
        CRYPTO_FAILURE,
        
        /**
         * Failed to store or load keys from persistent storage.
         */
        STORAGE_FAILURE,
        
        /**
         * A session already exists with this peer.
         * Use key rotation instead.
         */
        SESSION_ALREADY_EXISTS,
        
        /**
         * No session exists with this peer.
         * Initiate key exchange first.
         */
        NO_SESSION,
        
        /**
         * The received message does not match expected protocol.
         */
        PROTOCOL_VIOLATION,
        
        /**
         * The public key format is not supported or cannot be decoded.
         */
        UNSUPPORTED_KEY_FORMAT,
        
        /**
         * An internal error occurred.
         */
        INTERNAL_ERROR,

        /**
         * The operation is not permitted due to insufficient permissions.
         */
        PERMISSION_DENIED
    }

    private final ErrorCode errorCode;
    private final int peerId;
    
    // ========================= Constructors =========================
    
    /**
     * Creates a new KeyExchangeException.
     *
     * @param message   detailed error message
     * @param errorCode the error code
     */
    public KeyExchangeException(String message, ErrorCode errorCode) {
        super(message);
        this.errorCode = errorCode;
        this.peerId = -1;
    }
    
    /**
     * Creates a new KeyExchangeException with peer ID.
     *
     * @param message   detailed error message
     * @param errorCode the error code
     * @param peerId    the ID of the peer involved
     */
    public KeyExchangeException(String message, ErrorCode errorCode, int peerId) {
        super(message);
        this.errorCode = errorCode;
        this.peerId = peerId;
    }
    
    /**
     * Creates a new KeyExchangeException with a cause.
     *
     * @param message   detailed error message
     * @param errorCode the error code
     * @param cause     the underlying cause
     */
    public KeyExchangeException(String message, ErrorCode errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.peerId = -1;
    }
    
    /**
     * Creates a new KeyExchangeException with peer ID and cause.
     *
     * @param message   detailed error message
     * @param errorCode the error code
     * @param peerId    the ID of the peer involved
     * @param cause     the underlying cause
     */
    public KeyExchangeException(String message, ErrorCode errorCode, int peerId, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.peerId = peerId;
    }
    
    // ========================= Getters =========================
    
    /**
     * Gets the error code.
     *
     * @return the error code identifying the failure cause
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }
    
    /**
     * Gets the peer ID, if applicable.
     *
     * @return the peer ID, or -1 if not applicable
     */
    public int getPeerId() {
        return peerId;
    }
    
    /**
     * Checks if this error is recoverable.
     * <p>
     * Recoverable errors may be retried after a delay.
     *
     * @return true if the operation may be retried
     */
    public boolean isRecoverable() {
        return switch (errorCode) {
            case TIMEOUT, STORAGE_FAILURE, CRYPTO_FAILURE -> true;
            case INVALID_PUBLIC_KEY, INVALID_PEER_ID, PROTOCOL_VIOLATION,
                 EXCHANGE_ALREADY_IN_PROGRESS, NO_PENDING_EXCHANGE,
                 SESSION_ALREADY_EXISTS, NO_SESSION,
                 UNSUPPORTED_KEY_FORMAT, INTERNAL_ERROR, PERMISSION_DENIED -> false;
        };
    }
    
    // ========================= Factory Methods =========================
    
    /**
     * Creates an exception for an invalid peer ID.
     */
    public static KeyExchangeException invalidPeerId(int peerId) {
        return new KeyExchangeException(
            "Invalid peer ID: " + peerId,
            ErrorCode.INVALID_PEER_ID,
            peerId
        );
    }
    
    /**
     * Creates an exception for an invalid public key.
     */
    public static KeyExchangeException invalidPublicKey(int peerId, Throwable cause) {
        return new KeyExchangeException(
            "Invalid public key received from peer " + peerId,
            ErrorCode.INVALID_PUBLIC_KEY,
            peerId,
            cause
        );
    }
    
    /**
     * Creates an exception for exchange already in progress.
     */
    public static KeyExchangeException alreadyInProgress(int peerId) {
        return new KeyExchangeException(
            "Key exchange already in progress with peer " + peerId,
            ErrorCode.EXCHANGE_ALREADY_IN_PROGRESS,
            peerId
        );
    }
    
    /**
     * Creates an exception for no pending exchange.
     */
    public static KeyExchangeException noPendingExchange(int peerId) {
        return new KeyExchangeException(
            "No pending key exchange with peer " + peerId,
            ErrorCode.NO_PENDING_EXCHANGE,
            peerId
        );
    }
    
    /**
     * Creates an exception for timeout.
     */
    public static KeyExchangeException timeout(int peerId) {
        return new KeyExchangeException(
            "Key exchange with peer " + peerId + " timed out",
            ErrorCode.TIMEOUT,
            peerId
        );
    }
    
    /**
     * Creates an exception for session already exists.
     */
    public static KeyExchangeException sessionAlreadyExists(int peerId) {
        return new KeyExchangeException(
            "Session already exists with peer " + peerId + ". Use key rotation instead.",
            ErrorCode.SESSION_ALREADY_EXISTS,
            peerId
        );
    }
    
    /**
     * Creates an exception for no session.
     */
    public static KeyExchangeException noSession(int peerId) {
        return new KeyExchangeException(
            "No session exists with peer " + peerId,
            ErrorCode.NO_SESSION,
            peerId
        );
    }
    
    /**
     * Creates an exception for crypto failure.
     */
    public static KeyExchangeException cryptoFailure(String operation, Throwable cause) {
        return new KeyExchangeException(
            "Cryptographic operation failed: " + operation,
            ErrorCode.CRYPTO_FAILURE,
            cause
        );
    }
    
    /**
     * Creates an exception for storage failure.
     */
    public static KeyExchangeException storageFailure(String operation, Throwable cause) {
        return new KeyExchangeException(
            "Storage operation failed: " + operation,
            ErrorCode.STORAGE_FAILURE,
            cause
        );
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("KeyExchangeException{errorCode=").append(errorCode);
        if (peerId >= 0) {
            sb.append(", peerId=").append(peerId);
        }
        sb.append(", message='").append(getMessage()).append("'}");
        return sb.toString();
    }
}
