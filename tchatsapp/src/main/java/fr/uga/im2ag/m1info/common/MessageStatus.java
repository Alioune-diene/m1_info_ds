package fr.uga.im2ag.m1info.common;

/**
 * Enum representing the status of a message in its lifecycle.
 */
public enum MessageStatus {
    /** Message is being sent to the server */
    SENDING,

    /** Message has been received by the server */
    SENT,

    /** Message has been delivered to the recipient */
    DELIVERED,

    /** Message has been read by the recipient */
    READ,

    /** Message failed to send or deliver */
    FAILED,

    /** A critical failure occurred */
    CRITICAL_FAILURE;

    public static MessageStatus fromByte(byte b) {
        return switch (b) {
            case 0 -> SENDING;
            case 1 -> SENT;
            case 2 -> DELIVERED;
            case 3 -> READ;
            case 4 -> FAILED;
            case 5 -> CRITICAL_FAILURE;
            default -> throw new IllegalArgumentException("Invalid AckType byte: " + b);
        };
    }

    public byte toByte() {
        return switch (this) {
            case SENDING -> 0;
            case SENT -> 1;
            case DELIVERED -> 2;
            case READ -> 3;
            case FAILED -> 4;
            case CRITICAL_FAILURE -> 5;
        };
    }
}