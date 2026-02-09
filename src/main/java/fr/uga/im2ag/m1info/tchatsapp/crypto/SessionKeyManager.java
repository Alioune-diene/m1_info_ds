package fr.uga.im2ag.m1info.tchatsapp.crypto;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
 
/**
 * Manages session keys and sequence numbers for conversations.
 * Provides replay protection and key rotation.
 */
public class SessionKeyManager {
 
    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();
 
    /**
     * Maximum allowed sequence number before key rotation is recommended.
     * Set to a large value (1 billion) to allow many messages before rotation.
     */
    public static final long MAX_SEQUENCE = 1_000_000_000L;
    /**
     * Stores information about an encryption session.
     */
    private static class SessionInfo {
        final SecretKey key;
        final AtomicLong sendSequence;
        final AtomicLong receiveSequence;
        final Instant createdAt;
        final AtomicLong messagesSent;
        final AtomicLong messagesReceived;
 
        SessionInfo(SecretKey key) {
            this.key = key;
            this.sendSequence = new AtomicLong(0);
            this.receiveSequence = new AtomicLong(-1);
            this.createdAt = Instant.now();
            this.messagesSent = new AtomicLong(0);
            this.messagesReceived = new AtomicLong(0);
        }
    }
    /**
     * If a session already exists, it will be replaced (useful for key rotation).
     *
     * @param conversationId The conversation identifier
     * @param key The session key
     * @throws IllegalArgumentException if conversationId is null or empty, or key is null
     */
    public void storeSessionKey(String conversationId, SecretKey key) {
        if (conversationId == null || conversationId.isEmpty()) {
            throw new IllegalArgumentException("Conversation ID cannot be null or empty");
        }
        if (key == null) {
            throw new IllegalArgumentException("Session key cannot be null");
        }
        sessions.put(conversationId, new SessionInfo(key));
    }
 
    /**
     * Retrieves the session key for a conversation.
     * @param conversationId The conversation identifier
     * @return The session key or null if not found
     */
    public SecretKey getSessionKey(String conversationId) {
        SessionInfo info = sessions.get(conversationId);
        return info != null ? info.key : null;
    }
 
    /**
     * Gets and increments the send sequence number for a conversation.
     * This is thread-safe and atomic.
     *
     * @param conversationId The conversation identifier
     * @return The next sequence number to use for sending
     * @throws IllegalStateException if no session exists for this conversation
     */
    public long getNextSendSequence(String conversationId) {
        SessionInfo info = sessions.get(conversationId);
        if (info == null) {
            throw new IllegalStateException("No session for conversation: " + conversationId);
        }
 
        long seq = info.sendSequence.getAndIncrement();
        info.messagesSent.incrementAndGet();
 
        return seq;
    }
 
    /**
     * Validates and updates the receive sequence number.
     * Prevents replay attacks by ensuring sequence numbers are increasing.
     * @param conversationId The conversation identifier
     * @param sequence The received sequence number
     * @return true if sequence is valid, false if it's a replay
     * @throws IllegalStateException if no session exists for this conversation
     */
    public boolean validateReceiveSequence(String conversationId, long sequence) {
        SessionInfo info = sessions.get(conversationId);
        if (info == null) {
            throw new IllegalStateException("No session for conversation: " + conversationId);
        }
 
        // Get the current expected sequence
        long expected = info.receiveSequence.get();
 
        // Sequence must be strictly greater than the last received
        if (sequence <= expected) {
            return false; // Replay or out-of-order
        }
 
        // Update to the new sequence
        info.receiveSequence.set(sequence);
        info.messagesReceived.incrementAndGet();
 
        return true;
    }

 
    /**
     * Gets the last received sequence number.
     * Useful for debugging and monitoring.
     *
     * @param conversationId The conversation identifier
     * @return The last received sequence number, or -1 if session doesn't exist
     */
    public long getCurrentReceiveSequence(String conversationId) {
        SessionInfo info = sessions.get(conversationId);
        return info != null ? info.receiveSequence.get() : -1;
    }
 
    /**
     * Checks if a session exists for a conversation.
     *
     * @param conversationId The conversation identifier
     * @return true if session exists
     */
    public boolean hasSession(String conversationId) {
        return sessions.containsKey(conversationId);
    }
 
    /**
     * Removes a session and all associated data.
     * Use this before creating a new session for key rotation.
     *
     * @param conversationId The conversation identifier
     * @return true if session was removed, false if it didn't exist
     */
    public boolean removeSession(String conversationId) {
        return sessions.remove(conversationId) != null;
    }
 
    /**
     * Rotates the session key for a conversation.
     * This removes the old session and creates a new one with reset sequence numbers.
     *
     * @param conversationId The conversation identifier
     * @param newKey The new session key
     * @throws IllegalArgumentException if conversationId or newKey is null
     */
    public void rotateSessionKey(String conversationId, SecretKey newKey) {
        if (conversationId == null || conversationId.isEmpty()) {
            throw new IllegalArgumentException("Conversation ID cannot be null or empty");
        }
        if (newKey == null) {
            throw new IllegalArgumentException("New session key cannot be null");
        }
 
        // Remove old session and create new one (resets sequences to 0)
        sessions.put(conversationId, new SessionInfo(newKey));
    }
 
    /**
     * Checks if key rotation is recommended for a conversation.
     * Returns true if the send or receive sequence is approaching the maximum.
     *
     * @param conversationId The conversation identifier
     * @return true if key rotation is recommended
     */
    public boolean shouldRotateKey(String conversationId) {
        SessionInfo info = sessions.get(conversationId);
        if (info == null) {
            return false;
        }
 
        long sendSeq = info.sendSequence.get();
        long recvSeq = info.receiveSequence.get();
 
        return sendSeq >= MAX_SEQUENCE || recvSeq >= MAX_SEQUENCE;
    }
 
    /**
     * Gets statistics for a conversation session.
     *
     * @param conversationId The conversation identifier
     * @return SessionStats object, or null if session doesn't exist
     */
    public SessionStats getSessionStats(String conversationId) {
        SessionInfo info = sessions.get(conversationId);
        if (info == null) {
            return null;
        }
 
        return new SessionStats(
                info.sendSequence.get(),
                info.receiveSequence.get(),
                info.messagesSent.get(),
                info.messagesReceived.get(),
                info.createdAt
        );
    }
 
    /**
     * Gets all active conversation IDs.
     *
     * @return Set of conversation IDs with active sessions
     */
    public Set<String> getActiveConversations() {
        return sessions.keySet();
    }
 
    /**
     * Gets the total number of active sessions.
     *
     * @return Number of active sessions
     */
    public int getSessionCount() {
        return sessions.size();
    }
 
    /**
     * Clears all sessions. Use with caution!
     * Useful for logout or testing.
     */
    public void clearAllSessions() {
        sessions.clear();
    }
 
    /**
     * Statistics about a session.
     */
    public static class SessionStats {
        public final long currentSendSequence;
        public final long currentReceiveSequence;
        public final long messagesSent;
        public final long messagesReceived;
        public final Instant createdAt;
 
        public SessionStats(long currentSendSequence, long currentReceiveSequence,
                          long messagesSent, long messagesReceived, Instant createdAt) {
            this.currentSendSequence = currentSendSequence;
            this.currentReceiveSequence = currentReceiveSequence;
            this.messagesSent = messagesSent;
            this.messagesReceived = messagesReceived;
            this.createdAt = createdAt;
        }
 
        @Override
        public String toString() {
            return String.format(
                "SessionStats{sent=%d (seq=%d), received=%d (seq=%d), age=%ds}",
                messagesSent, currentSendSequence,
                messagesReceived, currentReceiveSequence,
                Instant.now().getEpochSecond() - createdAt.getEpochSecond()
            );
        }
    }
}