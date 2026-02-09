package fr.uga.im2ag.m1info.tchatsapp.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
 
/**
 * Provides AES-256-GCM authenticated encryption for message payloads.
 * GCM mode provides both confidentiality and authenticity.
 * 
 * Security properties:
 * Confidentiality: AES-256 encryption
 * Authenticity: 128-bit GCM authentication tag
 * Tamper detection: Any modification causes authentication failure
 */

public class SymmetricCipher {
    
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128; // 128 bits = 16 bytes
    private static final int NONCE_LENGTH = 12; // 96 bits = 12 bytes (recommended for GCM)
 
    private final SecureRandom secureRandom;
 
    /**
     * Creates a new SymmetricCipher with a secure random number generator.
     */
    public SymmetricCipher() {
        this.secureRandom = new SecureRandom();
    }

    /**
     * Encrypts plaintext using AES-256-GCM.
     * @param plaintext The data to encrypt
     * @param key The AES secret key
     * @param nonce The nonce (must be unique per encryption)
     * @param associatedData Additional authenticated data (can be null)
     * @return The ciphertext with authentication tag appended
     * @throws GeneralSecurityException if encryption fails
     */
    public byte[] encrypt(byte[] plaintext, SecretKey key, byte[] nonce, byte[] associatedData)
            throws GeneralSecurityException {
        if (nonce == null || nonce.length != NONCE_LENGTH) {
            throw new IllegalArgumentException("Nonce must be " + NONCE_LENGTH + " bytes");
        }
 
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, nonce);
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);
 
        // Add associated data if provided (authenticated but not encrypted)
        if (associatedData != null && associatedData.length > 0) {
            cipher.updateAAD(associatedData);
        }
 
        // Encrypt and append authentication tag
        return cipher.doFinal(plaintext);
    }
 
    /**
     * Decrypts ciphertext using AES-256-GCM and verifies authentication tag.
     * @param ciphertext The encrypted data with authentication tag
     * @param key The AES secret key
     * @param nonce The nonce used during encryption
     * @param associatedData Additional authenticated data (must match encryption)
     * @return The decrypted plaintext
     * @throws GeneralSecurityException if decryption or authentication fails
     */
    public byte[] decrypt(byte[] ciphertext, SecretKey key, byte[] nonce, byte[] associatedData)
            throws GeneralSecurityException {
        if (nonce == null || nonce.length != NONCE_LENGTH) {
            throw new IllegalArgumentException("Nonce must be " + NONCE_LENGTH + " bytes");
        }
 
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, nonce);
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);
 
        // Add associated data if provided (must match encryption)
        if (associatedData != null && associatedData.length > 0) {
            cipher.updateAAD(associatedData);
        }
 
        // Decrypt and verify authentication tag
        // If tag doesn't match, this will throw AEADBadTagException
        return cipher.doFinal(ciphertext);
    }
 
    /**
     * Generates a secure random nonce for GCM mode.
     * @return A 12-byte random nonce
     */
    public byte[] generateNonce() {
        byte[] nonce = new byte[NONCE_LENGTH];
        secureRandom.nextBytes(nonce);
        return nonce;
    }

    public int getNonceLength() {
        return NONCE_LENGTH;
    }
 
    public int getTagLength() {
        return GCM_TAG_LENGTH;
    }
}