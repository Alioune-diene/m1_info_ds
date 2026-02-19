package fr.uga.im2ag.m1info.tchatsapp.common;

/** Enum representing different types of messages. */
public enum MessageType {
    TEXT,
    MEDIA,
    REACTION,
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
    MESSAGE_ACK,
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
}
