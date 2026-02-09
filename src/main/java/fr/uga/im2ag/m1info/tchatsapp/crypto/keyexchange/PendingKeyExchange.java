package fr.uga.im2ag.m1info.tchatsapp.crypto.keyexchange;

import java.security.KeyPair;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Represents a pending key exchange operation with a specific peer.
 * <p>
 * This class is immutable for thread-safety. State changes create new instances
 * via the {@link #withState(KeyExchangeState)} method.
 * <p>
 * Lifecycle:
 * <ol>
 *   <li>Created when initiating or receiving a key exchange request</li>
 *   <li>Stores ephemeral keypair used for this specific exchange</li>
 *   <li>Tracks timeout for automatic expiration</li>
 *   <li>Transitions to terminal state upon completion, failure, or timeout</li>
 * </ol>
 *
 * @see KeyExchangeManager
 * @see KeyExchangeState
 */
public final class PendingKeyExchange {
    
    /**
     * Default timeout duration for key exchange operations.
     * If no response is received within this time, the exchange expires.
     */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    
    private final int peerId;
    private final KeyPair ephemeralKeyPair;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final KeyExchangeState state;
    private final boolean initiator;
    private final String exchangeId;
    
    // ========================= Constructors =========================
    
    /**
     * Creates a new pending key exchange.
     *
     * @param peerId          the ID of the peer client
     * @param ephemeralKeyPair the ephemeral ECDH keypair for this exchange
     * @param initiator       true if this client initiated the exchange
     * @param timeout         duration before the exchange expires
     * @throws NullPointerException if ephemeralKeyPair or timeout is null
     * @throws IllegalArgumentException if peerId is not positive
     */
    public PendingKeyExchange(int peerId, KeyPair ephemeralKeyPair, boolean initiator, Duration timeout) {
        if (peerId <= 0) {
            throw new IllegalArgumentException("Peer ID must be positive: " + peerId);
        }
        Objects.requireNonNull(ephemeralKeyPair, "Ephemeral keypair cannot be null");
        Objects.requireNonNull(timeout, "Timeout cannot be null");
        
        this.peerId = peerId;
        this.ephemeralKeyPair = ephemeralKeyPair;
        this.initiator = initiator;
        this.createdAt = Instant.now();
        this.expiresAt = createdAt.plus(timeout);
        this.state = initiator ? KeyExchangeState.INITIATED : KeyExchangeState.RECEIVED;
        this.exchangeId = generateExchangeId(peerId, createdAt);
    }
    
    /**
     * Creates a new pending key exchange with default timeout.
     *
     * @param peerId          the ID of the peer client
     * @param ephemeralKeyPair the ephemeral ECDH keypair for this exchange
     * @param initiator       true if this client initiated the exchange
     */
    public PendingKeyExchange(int peerId, KeyPair ephemeralKeyPair, boolean initiator) {
        this(peerId, ephemeralKeyPair, initiator, DEFAULT_TIMEOUT);
    }
    
    /**
     * Private constructor for state transitions (immutable pattern).
     */
    private PendingKeyExchange(PendingKeyExchange original, KeyExchangeState newState) {
        this.peerId = original.peerId;
        this.ephemeralKeyPair = original.ephemeralKeyPair;
        this.createdAt = original.createdAt;
        this.expiresAt = original.expiresAt;
        this.initiator = original.initiator;
        this.exchangeId = original.exchangeId;
        this.state = newState;
    }
    
    // ========================= State Transitions =========================
    
    /**
     * Creates a new instance with the specified state.
     * Used for immutable state transitions.
     *
     * @param newState the new state
     * @return a new PendingKeyExchange with updated state
     * @throws IllegalStateException if current state is terminal
     * @throws NullPointerException if newState is null
     */
    public PendingKeyExchange withState(KeyExchangeState newState) {
        Objects.requireNonNull(newState, "New state cannot be null");
        
        if (this.state.isTerminal()) {
            throw new IllegalStateException(
                "Cannot transition from terminal state " + this.state + " to " + newState);
        }
        
        return new PendingKeyExchange(this, newState);
    }
    
    /**
     * Creates a new instance marked as completed.
     *
     * @return a new PendingKeyExchange in COMPLETED state
     */
    public PendingKeyExchange complete() {
        return withState(KeyExchangeState.COMPLETED);
    }
    
    /**
     * Creates a new instance marked as failed.
     *
     * @return a new PendingKeyExchange in FAILED state
     */
    public PendingKeyExchange fail() {
        return withState(KeyExchangeState.FAILED);
    }
    
    /**
     * Creates a new instance marked as expired.
     *
     * @return a new PendingKeyExchange in EXPIRED state
     */
    public PendingKeyExchange expire() {
        return withState(KeyExchangeState.EXPIRED);
    }
    
    // ========================= Getters =========================
    
    /**
     * Gets the peer client ID.
     *
     * @return the peer ID
     */
    public int getPeerId() {
        return peerId;
    }
    
    /**
     * Gets the ephemeral keypair used for this exchange.
     * <p>
     * Warning: The private key should be treated as sensitive.
     * It is used to derive the shared secret and should be discarded after use.
     *
     * @return the ephemeral ECDH keypair
     */
    public KeyPair getEphemeralKeyPair() {
        return ephemeralKeyPair;
    }
    
    /**
     * Gets the creation timestamp.
     *
     * @return when this exchange was initiated
     */
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    /**
     * Gets the expiration timestamp.
     *
     * @return when this exchange expires
     */
    public Instant getExpiresAt() {
        return expiresAt;
    }
    
    /**
     * Gets the current state.
     *
     * @return the current key exchange state
     */
    public KeyExchangeState getState() {
        return state;
    }
    
    /**
     * Checks if this client initiated the exchange.
     *
     * @return true if this client sent the initial KEY_EXCHANGE message
     */
    public boolean isInitiator() {
        return initiator;
    }
    
    /**
     * Gets the unique identifier for this exchange.
     * <p>
     * Format: "kex_{peerId}_{timestamp}"
     *
     * @return the exchange ID
     */
    public String getExchangeId() {
        return exchangeId;
    }
    
    // ========================= Status Checks =========================
    
    /**
     * Checks if this exchange has expired based on current time.
     *
     * @return true if current time is after expiresAt
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
    
    /**
     * Checks if this exchange has expired or is in EXPIRED state.
     *
     * @return true if expired by time or state
     */
    public boolean isExpiredOrMarkedExpired() {
        return isExpired() || state == KeyExchangeState.EXPIRED;
    }
    
    /**
     * Checks if this exchange is still valid (not expired and not in terminal state).
     *
     * @return true if exchange can still be completed
     */
    public boolean isValid() {
        return !isExpired() && !state.isTerminal();
    }
    
    /**
     * Checks if this exchange is complete.
     *
     * @return true if state is COMPLETED
     */
    public boolean isCompleted() {
        return state == KeyExchangeState.COMPLETED;
    }
    
    /**
     * Gets the remaining time before expiration.
     *
     * @return duration until expiration, or Duration.ZERO if already expired
     */
    public Duration getRemainingTime() {
        Duration remaining = Duration.between(Instant.now(), expiresAt);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }
    
    /**
     * Gets the elapsed time since creation.
     *
     * @return duration since creation
     */
    public Duration getElapsedTime() {
        return Duration.between(createdAt, Instant.now());
    }
    
    // ========================= Private Helpers =========================
    
    /**
     * Generates a unique exchange ID.
     */
    private static String generateExchangeId(int peerId, Instant timestamp) {
        return String.format("kex_%d_%d", peerId, timestamp.toEpochMilli());
    }
    
    // ========================= Object Methods =========================
    
    @Override
    public String toString() {
        return String.format(
            "PendingKeyExchange{exchangeId='%s', peerId=%d, state=%s, initiator=%s, " +
            "createdAt=%s, expiresAt=%s, expired=%s}",
            exchangeId, peerId, state, initiator, createdAt, expiresAt, isExpired()
        );
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PendingKeyExchange that)) return false;
        return peerId == that.peerId && exchangeId.equals(that.exchangeId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(peerId, exchangeId);
    }
}
