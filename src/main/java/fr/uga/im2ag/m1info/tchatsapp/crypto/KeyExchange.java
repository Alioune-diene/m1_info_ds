package fr.uga.im2ag.m1info.tchatsapp.crypto;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;

/**
 * Handles ECDH (Elliptic Curve Diffie-Hellman) key exchange for establishing
 * shared secrets between clients
 * Uses Curve25519 for secure and efficient key agreement
 */
public class KeyExchange {

    private static final String KEY_ALGORITHM = "X25519";
    private static final String HKDF_ALGORITHM = "HmacSHA256";
    private static final int SESSION_KEY_LENGTH = 32; // 256 bits for AES-256


    static {
        // Register BouncyCastle provider for Curve25519 support
        Security.addProvider(new BouncyCastleProvider());
    }
    /**
     * Generates a new ECDH keypair for key exchange
     * @return A KeyPair containing private and public keys
     * @throws GeneralSecurityException if key generation fails
     */
    public KeyPair generateKeyPair() throws GeneralSecurityException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance(KEY_ALGORITHM, "BC");
        return keyGen.generateKeyPair();
    }

    /**
     * Derives a shared secret from our private key and peer's public key.
     * @param myPrivateKey Our private key
     * @param peerPublicKey The peer's public key
     * @return The shared secret bytes
     * @throws GeneralSecurityException if key agreement fails
     */
    public byte[] deriveSharedSecret(PrivateKey myPrivateKey, PublicKey peerPublicKey)
            throws GeneralSecurityException {
        KeyAgreement keyAgreement = KeyAgreement.getInstance(KEY_ALGORITHM, "BC");
        keyAgreement.init(myPrivateKey);
        keyAgreement.doPhase(peerPublicKey, true);
        return keyAgreement.generateSecret();
    }

    /**
     * Derives a session key from the shared secret using HKDF
     * @param sharedSecret The ECDH shared secret
     * @param conversationId Unique identifier for this conversation
     * @return The derived session key
     * @throws GeneralSecurityException if key derivation fails
     */
    public byte[] deriveSessionKey(byte[] sharedSecret, String conversationId)
            throws GeneralSecurityException {
        // HKDF has two phases: Extract and Expand
 
        // Phase 1: Extract - derive a pseudorandom key from the shared secret
        byte[] prk = hkdfExtract(null, sharedSecret);
 
        // Phase 2: Expand - expand the PRK into the session key with context
        byte[] info = conversationId.getBytes(StandardCharsets.UTF_8);
        return hkdfExpand(prk, info, SESSION_KEY_LENGTH);
    }


     /**
      * HKDF-Extract: Extracts a pseudorandom key from the input key material.
      * PRK = HMAC-Hash(salt, IKM)
      *
      * @param salt Optional salt (can be null, will use zero-filled array)
      * @param ikm Input Key Material (the shared secret)
      * @return Pseudorandom Key (PRK)
      * @throws GeneralSecurityException if HMAC fails
     */
    private byte[] hkdfExtract(byte[] salt, byte[] ikm) throws GeneralSecurityException {
        Mac mac = Mac.getInstance(HKDF_ALGORITHM);
 
        // If salt is null or empty, use a zero-filled array (as per RFC 5869)
        if (salt == null || salt.length == 0) {
            salt = new byte[mac.getMacLength()];
        }
 
        SecretKeySpec keySpec = new SecretKeySpec(salt, HKDF_ALGORITHM);
        mac.init(keySpec);
        return mac.doFinal(ikm);
    }


    /**
     * HKDF-Expand: Expands the PRK into the desired output key material.
     * Implements the expand phase as per RFC 5869.
     *
     * @param prk Pseudorandom Key from extract phase
     * @param info Context and application specific information
     * @param length Desired output key length in bytes
     * @return Output Key Material (OKM)
     * @throws GeneralSecurityException if HMAC fails or length is too large
     */
    private byte[] hkdfExpand(byte[] prk, byte[] info, int length) throws GeneralSecurityException {
        Mac mac = Mac.getInstance(HKDF_ALGORITHM);
        SecretKeySpec keySpec = new SecretKeySpec(prk, HKDF_ALGORITHM);
        mac.init(keySpec);
 
        int hashLen = mac.getMacLength();
        int iterations = (int) Math.ceil((double) length / hashLen);
 
        // HKDF can generate at most 255 * hash_length bytes
        if (iterations > 255) {
            throw new GeneralSecurityException("Requested key length too large for HKDF");
        }
 
        byte[] result = new byte[length];
        byte[] t = new byte[0];
        int offset = 0;
 
        for (int i = 1; i <= iterations; i++) {
            mac.reset();
            mac.update(t);
            mac.update(info);
            mac.update((byte) i);
            t = mac.doFinal();
 
            int copyLength = Math.min(hashLen, length - offset);
            System.arraycopy(t, 0, result, offset, copyLength);
            offset += copyLength;
        }
 
        return result;
    }
}