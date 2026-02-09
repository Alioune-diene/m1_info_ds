package fr.uga.im2ag.m1info.tchatsapp.common.messagefactory;

import fr.uga.im2ag.m1info.tchatsapp.common.MessageType;

import java.util.Base64;

/**
 * Protocol message for initiating an ECDH key exchange.
 */
public class KeyExchangeMessage extends AbstractSerializableMessage {
    private byte[] publicKey;

    /**
     * Default constructor for deserialization and message factory.
     */
    public KeyExchangeMessage() {
        super(MessageType.KEY_EXCHANGE, -1, -1);
        this.publicKey = new byte[0];
    }
    
    /**
     * Creates a new KeyExchangeMessage.
     *
     * @param from the sender ID
     * @param to   the recipient ID
     */
    public KeyExchangeMessage(int from, int to) {
        super(MessageType.KEY_EXCHANGE, from, to);
        this.publicKey = new byte[0];
    }
    
    // ========================= Getters/Setters =========================
    
    /**
     * Gets the public key.
     *
     * @return a copy of the public key bytes
     */
    public byte[] getPublicKey() {
        return publicKey != null ? publicKey.clone() : null;
    }
    
    /**
     * Sets the public key.
     *
     * @param publicKey the public key bytes
     * @return this message for chaining
     * @throws IllegalArgumentException if publicKey is null or empty
     */
    public KeyExchangeMessage setPublicKey(byte[] publicKey) {
        if (publicKey == null || publicKey.length == 0) {
            throw new IllegalArgumentException("Public key cannot be null or empty");
        }
        this.publicKey = publicKey.clone();
        return this;
    }
    
    /**
     * Gets the public key length in bytes.
     *
     * @return the length of the public key
     */
    public int getPublicKeyLength() {
        return publicKey != null ? publicKey.length : 0;
    }
    
    // ========================= Serialization =========================
    
    @Override
    protected void validateBeforeSerialize() {
        super.validateBeforeSerialize();
        if (publicKey == null || publicKey.length == 0) {
            throw new IllegalStateException("Public key must be set before serialization");
        }
    }
    
    @Override
    protected void serializeContent(StringBuilder sb) {
        String encoded = Base64.getEncoder().encodeToString(publicKey);
        sb.append(encoded);
    }
    
    @Override
    protected void deserializeContent(String[] parts, int startIndex) {
        if (parts.length > startIndex && !parts[startIndex].isEmpty()) {
            String encoded = parts[startIndex];
            this.publicKey = Base64.getDecoder().decode(encoded);
        } else {
            this.publicKey = new byte[0];
        }
    }
    
    @Override
    protected int getExpectedPartCount() {
        return 3;
    }

    @Override
    public String toString() {
        return String.format(
            "KeyExchangeMessage{messageId='%s', from=%d, to=%d, publicKeyLen=%d}",
            messageId, from, to, getPublicKeyLength()
        );
    }
}
