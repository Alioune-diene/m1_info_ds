package fr.uga.im2ag.m1info.tchatsapp.common.messagefactory;

import fr.uga.im2ag.m1info.tchatsapp.common.MessageType;
import fr.uga.im2ag.m1info.tchatsapp.common.Packet;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;

/**
 * EncryptedWrapper is a special ProtocolMessage that encapsulates another message in an encrypted form.
 * It contains metadata necessary for decryption and identification of the original message type.
 */
public class EncryptedWrapper extends ProtocolMessage {

    private static final int NONCE_LENGTH = 12;
    private MessageType originalType;
    private long sequenceNumber;
    private byte[] nonce;
    private byte[] encryptedContent;

    /**
     * Default constructor for EncryptedWrapper.
     * This is used for message factory registration only.
     */
    public EncryptedWrapper() {
        super(MessageType.ENCRYPTED, -1, -1);
        this.timestamp = Instant.EPOCH;
        this.messageId = null;
    }

    /**
     * Private constructor to create an EncryptedWrapper with specified sender and recipient.
     *
     * @param from the sender id.
     * @param to the recipient id.
     */
    private EncryptedWrapper(int from, int to) {
        super(MessageType.ENCRYPTED, from, to);
        this.timestamp = Instant.now();
    }

    // ========================= Factory Methods =========================

    /**
     * Wraps and encrypts a ProtocolMessage into an EncryptedWrapper.
     *
     * @param message the original ProtocolMessage to encrypt and wrap.
     * @param context the EncryptionContext providing encryption capabilities.
     * @return an EncryptedWrapper containing the encrypted message.
     * @throws GeneralSecurityException if encryption fails.
     */
    public static EncryptedWrapper wrap(ProtocolMessage message, EncryptionContext context)
            throws GeneralSecurityException {

        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        if (message.getMessageType() == MessageType.ENCRYPTED) {
            throw new IllegalArgumentException("Cannot wrap an already encrypted message");
        }

        byte[] plainPayload = extractPayload(message);

        byte[] nonce = context.generateNonce();
        long sequenceNumber = context.getAndIncrementSequence();
        byte[] ciphertext = context.encrypt(plainPayload, nonce, sequenceNumber);

        EncryptedWrapper wrapper = new EncryptedWrapper(message.getFrom(), message.getTo());
        wrapper.originalType = message.getMessageType();
        wrapper.sequenceNumber = sequenceNumber;
        wrapper.nonce = nonce;
        wrapper.encryptedContent = ciphertext;

        wrapper.messageId = generateWrapperId(message.getMessageId());

        return wrapper;
    }

    /**
     * Unwraps and decrypts the original ProtocolMessage from the EncryptedWrapper.
     *
     * @param context the EncryptionContext providing decryption capabilities.
     * @return the decrypted original ProtocolMessage.
     * @throws GeneralSecurityException if decryption fails.
     */
    public ProtocolMessage unwrap(EncryptionContext context) throws GeneralSecurityException {
        byte[] plainPayload = context.decrypt(encryptedContent, nonce, sequenceNumber);
        return reconstructMessage(plainPayload);
    }

    // ========================= Getters =========================

    /**
     * Get the original message type before encryption.
     *
     * @return the original MessageType.
     */
    public MessageType getOriginalType() {
        return originalType;
    }

    /**
     * Get the sequence number used during encryption.
     *
     * @return the sequence number.
     */
    public long getSequenceNumber() {
        return sequenceNumber;
    }

    /**
     * Get the nonce used during encryption.
     *
     * @return a copy of the nonce byte array.
     */
    public byte[] getNonce() {
        return nonce != null ? nonce.clone() : null;
    }

    /**
     * Get the encrypted content (ciphertext).
     *
     * @return a copy of the encrypted content byte array.
     */
    public byte[] getEncryptedContent() {
        return encryptedContent != null ? encryptedContent.clone() : null;
    }

    // ========================= Serialization Methods =========================

    @Override
    public Packet toPacket() {
        if (messageId == null) {
            throw new IllegalStateException("Message ID cannot be null");
        }
        if (nonce == null || nonce.length != NONCE_LENGTH) {
            throw new IllegalStateException("Nonce must be " + NONCE_LENGTH + " bytes");
        }
        if (encryptedContent == null) {
            throw new IllegalStateException("Encrypted content cannot be null");
        }

        byte[] messageIdBytes = messageId.getBytes(StandardCharsets.UTF_8);

        int payloadSize = 4 + messageIdBytes.length  // messageId
                + 8                           // timestamp
                + 1                           // originalType
                + 8                           // sequenceNumber
                + NONCE_LENGTH                // nonce
                + 4 + encryptedContent.length; // ciphertext

        ByteBuffer buffer = ByteBuffer.allocate(payloadSize);

        buffer.putInt(messageIdBytes.length);
        buffer.put(messageIdBytes);
        buffer.putLong(timestamp.toEpochMilli());
        buffer.put(originalType.toByte());
        buffer.putLong(sequenceNumber);
        buffer.put(nonce);
        buffer.putInt(encryptedContent.length);
        buffer.put(encryptedContent);

        return new Packet.PacketBuilder(payloadSize)
                .setMessageType(MessageType.ENCRYPTED)
                .setFrom(from)
                .setTo(to)
                .setPayload(buffer.array())
                .build();
    }

    @Override
    public EncryptedWrapper fromPacket(Packet packet) {
        this.messageType = packet.messageType();
        this.from = packet.from();
        this.to = packet.to();

        ByteBuffer buffer = packet.getPayload();

        int messageIdLen = buffer.getInt();
        byte[] messageIdBytes = new byte[messageIdLen];
        buffer.get(messageIdBytes);
        this.messageId = new String(messageIdBytes, StandardCharsets.UTF_8);

        this.timestamp = Instant.ofEpochMilli(buffer.getLong());
        this.originalType = MessageType.fromByte(buffer.get());
        this.sequenceNumber = buffer.getLong();
        this.nonce = new byte[NONCE_LENGTH];
        buffer.get(this.nonce);

        int ciphertextLen = buffer.getInt();
        this.encryptedContent = new byte[ciphertextLen];
        buffer.get(this.encryptedContent);

        return this;
    }

    // ========================= Private Helper Methods =========================

    /**
     * Extracts the payload bytes from a ProtocolMessage.
     * Uses optimized serialization if the message extends AbstractSerializableMessage,
     * otherwise falls back to a legacy Packet-based serialization.
     *
     * @param message the ProtocolMessage to extract from.
     * @return the payload byte array.
     */
    private static byte[] extractPayload(ProtocolMessage message) {
        if (message instanceof AbstractSerializableMessage asm) {
            return asm.toPayloadBytes();
        } else {
            System.out.println("Warning: Using fallback serialization for message type " + message.getMessageType() + ". Consider implementing AbstractSerializableMessage.");
            Packet packet = message.toPacket();
            ByteBuffer payloadBuffer = packet.getPayload();
            byte[] payload = new byte[payloadBuffer.remaining()];
            payloadBuffer.get(payload);
            return payload;
        }
    }

    /**
     * Reconstructs the original ProtocolMessage from decrypted payload bytes.
     * Uses optimized deserialization if the message extends AbstractSerializableMessage,
     * otherwise falls back to a legacy Packet-based deserialization.
     *
     * @param plainPayload the decrypted payload byte array.
     * @return the reconstructed ProtocolMessage.
     */
    private ProtocolMessage reconstructMessage(byte[] plainPayload) {
        ProtocolMessage template = MessageFactory.createEmpty(originalType);

        if (template instanceof AbstractSerializableMessage asm) {
            asm.fromPayloadBytes(plainPayload, originalType, from, to);
            return asm;
        } else {
            System.out.println("Warning: Using fallback deserialization for message type " + originalType + ". Consider implementing AbstractSerializableMessage.");
            Packet fakePacket = new Packet.PacketBuilder(plainPayload.length)
                    .setMessageType(originalType)
                    .setFrom(from)
                    .setTo(to)
                    .setPayload(plainPayload)
                    .build();
            return template.fromPacket(fakePacket);
        }
    }

    private static String generateWrapperId(String originalMessageId) {
        return "wra-" + originalMessageId;
    }

    @Override
    public String toString() {
        return "EncryptedWrapper{" +
                "messageId='" + messageId + '\'' +
                ", from=" + from +
                ", to=" + to +
                ", originalType=" + originalType +
                ", sequenceNumber=" + sequenceNumber +
                ", encryptedSize=" + (encryptedContent != null ? encryptedContent.length : 0) +
                '}';
    }

    // ========================= Interface EncryptionContext =========================

    /**
     * EncryptionContext provides the necessary methods for encryption and decryption operations,
     * including nonce generation and sequence number management.
     */
    public interface EncryptionContext {
        byte[] generateNonce();

        long getAndIncrementSequence();

        byte[] encrypt(byte[] plaintext, byte[] nonce, long sequenceNumber)
                throws GeneralSecurityException;

        byte[] decrypt(byte[] ciphertext, byte[] nonce, long sequenceNumber)
                throws GeneralSecurityException;
    }
}