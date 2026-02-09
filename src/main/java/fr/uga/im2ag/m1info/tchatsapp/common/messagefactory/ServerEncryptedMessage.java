package fr.uga.im2ag.m1info.tchatsapp.common.messagefactory;

import fr.uga.im2ag.m1info.tchatsapp.common.MessageType;
import fr.uga.im2ag.m1info.tchatsapp.common.Packet;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class ServerEncryptedMessage extends ProtocolMessage {

    /** Nonce length for AES-GCM (96 bits = 12 bytes) */
    public static final int NONCE_LENGTH = 12;

    private MessageType originalType;
    private byte[] nonce;
    private byte[] ciphertext;

    /**
     * Default constructor for deserialization.
     */
    public ServerEncryptedMessage() {
        super(MessageType.SERVER_ENCRYPTED, 0, 0);
        this.originalType = MessageType.NONE;
        this.nonce = new byte[NONCE_LENGTH];
        this.ciphertext = new byte[0];
    }

    /**
     * Creates a new ServerEncryptedMessage.
     *
     * @param from         the sender ID
     * @param to           the recipient ID
     * @param originalType the original message type before encryption
     * @param nonce        the nonce used for encryption
     * @param ciphertext   the encrypted payload
     */
    public ServerEncryptedMessage(int from, int to, MessageType originalType,
                                  byte[] nonce, byte[] ciphertext) {
        super(MessageType.SERVER_ENCRYPTED, from, to);
        setOriginalType(originalType);
        setNonce(nonce);
        setCiphertext(ciphertext);
    }

    // ========================= Getters/Setters =========================

    /**
     * Gets the original message type.
     *
     * @return the original message type
     */
    public MessageType getOriginalType() {
        return originalType;
    }

    /**
     * Sets the original message type.
     *
     * @param originalType the original message type
     * @return this message for chaining
     */
    public ServerEncryptedMessage setOriginalType(MessageType originalType) {
        this.originalType = originalType != null ? originalType : MessageType.NONE;
        return this;
    }

    /**
     * Gets the nonce.
     *
     * @return a copy of the nonce bytes
     */
    public byte[] getNonce() {
        return nonce != null ? nonce.clone() : null;
    }

    /**
     * Sets the nonce.
     *
     * @param nonce the nonce bytes (must be exactly 12 bytes)
     * @return this message for chaining
     * @throws IllegalArgumentException if nonce is null or not 12 bytes
     */
    public ServerEncryptedMessage setNonce(byte[] nonce) {
        if (nonce == null || nonce.length != NONCE_LENGTH) {
            throw new IllegalArgumentException("Nonce must be exactly " + NONCE_LENGTH + " bytes");
        }
        this.nonce = nonce.clone();
        return this;
    }

    /**
     * Gets the ciphertext.
     *
     * @return a copy of the ciphertext bytes
     */
    public byte[] getCiphertext() {
        return ciphertext != null ? ciphertext.clone() : null;
    }

    /**
     * Sets the ciphertext.
     *
     * @param ciphertext the ciphertext bytes
     * @return this message for chaining
     * @throws IllegalArgumentException if ciphertext is null
     */
    public ServerEncryptedMessage setCiphertext(byte[] ciphertext) {
        if (ciphertext == null) {
            throw new IllegalArgumentException("Ciphertext cannot be null");
        }
        this.ciphertext = ciphertext.clone();
        return this;
    }

    // ========================= Serialization =========================

    @Override
    public Packet toPacket() {
        if (nonce == null || nonce.length != NONCE_LENGTH) {
            throw new IllegalStateException("Nonce must be set before serialization");
        }
        if (ciphertext == null) {
            throw new IllegalStateException("Ciphertext must be set before serialization");
        }

        // Format: [4: original type] [12: nonce] [4: ciphertext length] [N: ciphertext]
        int payloadSize = Integer.BYTES + NONCE_LENGTH + Integer.BYTES + ciphertext.length;
        byte[] payload = new byte[payloadSize];
        ByteBuffer buffer = ByteBuffer.wrap(payload);

        buffer.putInt(originalType.ordinal());
        buffer.put(nonce);
        buffer.putInt(ciphertext.length);
        buffer.put(ciphertext);

        return new Packet.PacketBuilder(payloadSize)
                .setMessageType(MessageType.SERVER_ENCRYPTED)
                .setFrom(from)
                .setTo(to)
                .setPayload(payload)
                .build();
    }

    @Override
    public ProtocolMessage fromPacket(Packet packet) {
        this.messageType = packet.messageType();
        this.from = packet.from();
        this.to = packet.to();

        ByteBuffer buffer = packet.getPayload();

        // Read original type
        int typeOrdinal = buffer.getInt();
        this.originalType = MessageType.fromInt(typeOrdinal);

        // Read nonce
        this.nonce = new byte[NONCE_LENGTH];
        buffer.get(this.nonce);

        // Read ciphertext
        int ciphertextLength = buffer.getInt();
        if (ciphertextLength < 0 || ciphertextLength > buffer.remaining()) {
            throw new IllegalArgumentException("Invalid ciphertext length: " + ciphertextLength);
        }
        this.ciphertext = new byte[ciphertextLength];
        buffer.get(this.ciphertext);

        return this;
    }

    // ========================= Utility =========================

    /**
     * Gets the overhead size in bytes added by encryption.
     * This includes: original type (4) + nonce (12) + length field (4) + GCM tag (16)
     *
     * @return the overhead in bytes
     */
    public static int getEncryptionOverhead() {
        return Integer.BYTES + NONCE_LENGTH + Integer.BYTES + 16; // 16 = GCM tag
    }

    @Override
    public String toString() {
        return String.format("ServerEncryptedMessage{from=%d, to=%d, originalType=%s, ciphertextLen=%d}",
                from, to, originalType, ciphertext != null ? ciphertext.length : 0);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ServerEncryptedMessage that)) return false;
        return from == that.from &&
                to == that.to &&
                originalType == that.originalType &&
                Arrays.equals(nonce, that.nonce) &&
                Arrays.equals(ciphertext, that.ciphertext);
    }

    @Override
    public int hashCode() {
        int result = originalType.hashCode();
        result = 31 * result + from;
        result = 31 * result + to;
        result = 31 * result + Arrays.hashCode(nonce);
        result = 31 * result + Arrays.hashCode(ciphertext);
        return result;
    }
}
