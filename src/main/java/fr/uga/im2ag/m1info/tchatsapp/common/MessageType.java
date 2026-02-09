package fr.uga.im2ag.m1info.tchatsapp.common;

/** Enum representing different types of messages. */
public enum MessageType {
    TEXT,
    MEDIA,
    REACTION,
    CREATE_USER,
    CONNECT_USER,
    ACK_CONNECTION,
    CREATE_GROUP,
    UPDATE_PSEUDO,
    CONTACT_REQUEST,
    CONTACT_REQUEST_RESPONSE,
    REMOVE_CONTACT,
    ADD_GROUP_MEMBER,
    REMOVE_GROUP_MEMBER,
    UPDATE_GROUP_NAME,
    DELETE_GROUP,
    LEAVE_GROUP,
    MESSAGE_REACTION,
    NOTIFICATION,
    KEY_EXCHANGE,
    KEY_EXCHANGE_RESPONSE,
    ENCRYPTED,
    @Deprecated ENCRYPTED_TEXT,
    @Deprecated ENCRYPTED_FILE_CHUNK,
    FILE_TRANSFER_START,
    FILE_TRANSFER_ACK,
    GROUP_KEY_DISTRIBUTION,
    MESSAGE_ACK,
    SERVER_KEY_EXCHANGE,
    SERVER_KEY_EXCHANGE_RESPONSE,
    SERVER_ENCRYPTED,
    NONE;

    /** Convert an integer to a MessageType enum.
     *
     * @param i the integer to convert
     * @return the corresponding MessageType, or NONE if no match is found
     */
    public static MessageType fromInt(int i) {
        for (MessageType type : MessageType.values()) {
            if (type.ordinal() == i) {
                return type;
            }
        }
        return NONE;
    }

    /** Convert a byte to a MessageType enum.
     *
     * @param b the byte to convert
     * @return the corresponding MessageType, or NONE if no match is found
     */
    public static MessageType fromByte(byte b) {
        return fromInt(b);
    }

    /** Convert this MessageType enum to a byte.
     *
     * @return the byte representation of this MessageType
     */
    public byte toByte() {
        return (byte) this.ordinal();
    }

    /** Convert this MessageType enum to a string.
     *
     * @return the string representation of this MessageType
     */
    @Override
    public String toString() {
        return this.name();
    }

    /**
     * Check if this message type is related to server key exchange.
     *
     * @return true if this is a server key exchange message type
     */
    public boolean isServerKeyExchange() {
        return this == SERVER_KEY_EXCHANGE || this == SERVER_KEY_EXCHANGE_RESPONSE;
    }

    /**
     * Check if this message type should be encrypted for server communication.
     * Messages to/from the server (management messages) should be encrypted.
     *
     * @return true if this message type should use server encryption
     */
    public boolean requiresServerEncryption() {
        return switch (this) {
            case SERVER_KEY_EXCHANGE, SERVER_KEY_EXCHANGE_RESPONSE,
                 KEY_EXCHANGE, KEY_EXCHANGE_RESPONSE, NONE -> false;
            default -> true;
        };
    }
}
