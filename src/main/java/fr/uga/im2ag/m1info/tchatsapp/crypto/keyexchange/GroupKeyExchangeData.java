package fr.uga.im2ag.m1info.tchatsapp.crypto.keyexchange;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Encodes and decodes group key exchange data.
 * <p>
 * This class handles the serialization format for group keys sent via
 * KEY_EXCHANGE messages. The format includes a marker to distinguish
 * group key exchanges from regular ECDH exchanges.
 * <p>
 * Format for group key distribution:
 * <pre>
 * [MARKER: 1 byte][GROUP_ID: 4 bytes][ENCRYPTED_KEY_LENGTH: 4 bytes][ENCRYPTED_KEY: N bytes]
 * </pre>
 * <p>
 * Format for group key acknowledgment:
 * <pre>
 * [ACK_MARKER: 1 byte][GROUP_ID: 4 bytes]
 * </pre>
 * <p>
 * Thread Safety: This class is stateless and thread-safe.
 */
public class GroupKeyExchangeData {

    // ========================= Constants =========================

    /** Marker byte for group key exchange (0xFF) */
    private static final byte GROUP_KEY_MARKER = (byte) 0xFF;

    /** Marker byte for group key acknowledgment (0xFE) */
    private static final byte GROUP_KEY_ACK_MARKER = (byte) 0xFE;

    /** Minimum data size: marker (1) + groupId (4) */
    private static final int MIN_DATA_SIZE = 5;

    /** Size of ACK message: marker (1) + groupId (4) */
    private static final int ACK_DATA_SIZE = 5;

    // ========================= Fields =========================

    private final int groupId;
    private final byte[] encryptedGroupKey;
    private final boolean isAck;

    // ========================= Constructor =========================

    /**
     * Creates a new GroupKeyExchangeData.
     *
     * @param groupId          the group ID
     * @param encryptedGroupKey the encrypted group key (null for ACK)
     * @param isAck            true if this is an acknowledgment
     */
    private GroupKeyExchangeData(int groupId, byte[] encryptedGroupKey, boolean isAck) {
        this.groupId = groupId;
        this.encryptedGroupKey = encryptedGroupKey;
        this.isAck = isAck;
    }

    // ========================= Static Factory Methods =========================

    /**
     * Encodes group key data for transmission.
     *
     * @param groupId          the group ID
     * @param encryptedGroupKey the encrypted group key
     * @param isAck            true if this is an acknowledgment
     * @return the encoded data
     * @throws IllegalArgumentException if groupId is invalid or key is null/empty for non-ACK
     */
    public static byte[] encode(int groupId, byte[] encryptedGroupKey, boolean isAck) {
        if (groupId <= 0) {
            throw new IllegalArgumentException("Invalid group ID: " + groupId);
        }

        if (isAck) {
            return encodeAck(groupId);
        }

        if (encryptedGroupKey == null || encryptedGroupKey.length == 0) {
            throw new IllegalArgumentException("Encrypted group key cannot be null or empty");
        }

        // Allocate buffer: marker(1) + groupId(4) + keyLength(4) + key(N)
        ByteBuffer buffer = ByteBuffer.allocate(9 + encryptedGroupKey.length);
        buffer.put(GROUP_KEY_MARKER);
        buffer.putInt(groupId);
        buffer.putInt(encryptedGroupKey.length);
        buffer.put(encryptedGroupKey);

        return buffer.array();
    }

    /**
     * Encodes a group key acknowledgment.
     *
     * @param groupId the group ID
     * @return the encoded ACK data
     */
    public static byte[] encodeAck(int groupId) {
        if (groupId <= 0) {
            throw new IllegalArgumentException("Invalid group ID: " + groupId);
        }

        ByteBuffer buffer = ByteBuffer.allocate(ACK_DATA_SIZE);
        buffer.put(GROUP_KEY_ACK_MARKER);
        buffer.putInt(groupId);

        return buffer.array();
    }

    /**
     * Decodes group key exchange data.
     *
     * @param data the encoded data
     * @return the decoded GroupKeyExchangeData
     * @throws IllegalArgumentException if data is invalid
     */
    public static GroupKeyExchangeData decode(byte[] data) {
        if (data == null || data.length < MIN_DATA_SIZE) {
            throw new IllegalArgumentException("Invalid data: too short");
        }

        ByteBuffer buffer = ByteBuffer.wrap(data);
        byte marker = buffer.get();

        if (marker == GROUP_KEY_ACK_MARKER) {
            // Acknowledgment
            if (data.length != ACK_DATA_SIZE) {
                throw new IllegalArgumentException("Invalid ACK data size: " + data.length);
            }

            int groupId = buffer.getInt();
            return new GroupKeyExchangeData(groupId, null, true);

        } else if (marker == GROUP_KEY_MARKER) {
            // Group key distribution
            if (data.length < 9) {
                throw new IllegalArgumentException("Invalid group key data: too short");
            }

            int groupId = buffer.getInt();
            int keyLength = buffer.getInt();

            if (keyLength <= 0) {
                throw new IllegalArgumentException("Invalid key length: " + keyLength);
            }

            if (data.length != 9 + keyLength) {
                throw new IllegalArgumentException(String.format(
                        "Data size mismatch: expected %d, got %d",
                        9 + keyLength, data.length));
            }

            byte[] encryptedKey = new byte[keyLength];
            buffer.get(encryptedKey);

            return new GroupKeyExchangeData(groupId, encryptedKey, false);

        } else {
            throw new IllegalArgumentException("Invalid marker byte: " + marker);
        }
    }

    /**
     * Checks if data represents a group key exchange.
     *
     * @param data the data to check
     * @return true if this is a group key exchange (distribution or ACK)
     */
    public static boolean isGroupKeyExchange(byte[] data) {
        return data != null && data.length > 0 && (data[0] == GROUP_KEY_MARKER || data[0] == GROUP_KEY_ACK_MARKER);
    }

    /**
     * Checks if data represents a group key acknowledgment.
     *
     * @param data the data to check
     * @return true if this is a group key ACK
     */
    public static boolean isGroupKeyAck(byte[] data) {
        return data != null && data.length > 0 && data[0] == GROUP_KEY_ACK_MARKER;
    }

    // ========================= Getters =========================

    /**
     * Gets the group ID.
     *
     * @return the group ID
     */
    public int getGroupId() {
        return groupId;
    }

    /**
     * Gets the encrypted group key.
     *
     * @return the encrypted group key (null for ACK)
     */
    public byte[] getEncryptedGroupKey() {
        return encryptedGroupKey != null ? encryptedGroupKey.clone() : null;
    }

    /**
     * Checks if this is an acknowledgment.
     *
     * @return true if this is an ACK
     */
    public boolean isAck() {
        return isAck;
    }

    // ========================= Object Methods =========================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GroupKeyExchangeData that = (GroupKeyExchangeData) o;
        return groupId == that.groupId &&
                isAck == that.isAck &&
                Arrays.equals(encryptedGroupKey, that.encryptedGroupKey);
    }

    @Override
    public int hashCode() {
        int result = groupId;
        result = 31 * result + (isAck ? 1 : 0);
        result = 31 * result + Arrays.hashCode(encryptedGroupKey);
        return result;
    }

    @Override
    public String toString() {
        return String.format("GroupKeyExchangeData{groupId=%d, isAck=%s, keyLen=%d}",
                groupId, isAck, encryptedGroupKey != null ? encryptedGroupKey.length : 0);
    }
}