package fr.uga.im2ag.m1info.tchatsapp.storage;
 
import javax.crypto.SecretKey;
import java.io.IOException;
import java.util.Map;

/**
 * Handles secure storage of cryptographic keys.
 * Keys are encrypted at rest using a master key derived from the user's password.
 */
public interface KeyStore {
 
    /**
     * Saves a session key to persistent storage.
     * @param conversationId The conversation identifier
     * @param key The session key to store
     * @throws IOException if storage fails
     */
    void saveSessionKey(String conversationId, SecretKey key) throws IOException;
 
    /**
     * Loads a session key from persistent storage.
     * @param conversationId The conversation identifier
     * @return The session key or null if not found
     * @throws IOException if loading fails
     */
    SecretKey loadSessionKey(String conversationId) throws IOException;
 
    /**
     * Deletes a session key from storage.
     * @param conversationId The conversation identifier
     * @throws IOException if deletion fails
     */
    void deleteSessionKey(String conversationId) throws IOException;

    /**
     * Loads all session keys from storage.
     * @return A map of conversation IDs to their corresponding session keys
     * @throws IOException if loading fails
     */
    Map<String, SecretKey> loadAllSessionKeys() throws IOException;
}