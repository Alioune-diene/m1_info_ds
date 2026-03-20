package fr.uga.im2ag.m1info.client.event.types;

import fr.uga.im2ag.m1info.client.event.system.Event;

/**
 * Event representing an error received from the server.
 */
public class ErrorEvent extends Event {

    public enum ErrorLevel {
        INFO,
        WARNING,
        ERROR,
        CRITICAL;

        @Override
        public String toString() {
            return name();
        }

        public static ErrorLevel fromString(String str) {
            for (ErrorLevel level : ErrorLevel.values()) {
                if (level.name().equalsIgnoreCase(str)) {
                    return level;
                }
            }
            throw new IllegalArgumentException("Unknown error level: " + str);
        }

        public int toInt() {
            return switch (this) {
                case INFO -> 0;
                case WARNING -> 1;
                case ERROR -> 2;
                case CRITICAL -> 3;
            };
        }

        public static ErrorLevel fromInt(int level) {
            return switch (level) {
                case 0 -> INFO;
                case 1 -> WARNING;
                case 2 -> ERROR;
                case 3 -> CRITICAL;
                default -> throw new IllegalStateException("Unexpected value: " + level);
            };
        }
    }

    private final ErrorLevel errorLevel;
    private final String errorType;
    private final String errorMessage;

    /** Constructor for the ErrorEvent class.
     *
     * @param source The source object that generated the event.
     * @param errorLevel The severity level of the error.
     * @param errorType The type/category of the error.
     * @param errorMessage The detailed error message.
     */
    public ErrorEvent(Object source, ErrorLevel errorLevel, String errorType, String errorMessage) {
        super(source);
        this.errorLevel = errorLevel;
        this.errorType = errorType;
        this.errorMessage = errorMessage;
    }

    /** Gets the error level.
     *
     * @return The error level.
     */
    public ErrorLevel getErrorLevel() {
        return errorLevel;
    }

    /** Gets the error type.
     *
     * @return The error type.
     */
    public String getErrorType() {
        return errorType;
    }

    /** Gets the error message.
     *
     * @return The error message.
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /** Gets the specific type of the event.
     *
     * @return The class type of the event.
     */
    @Override
    public Class<? extends Event> getEventType() {
        return ErrorEvent.class;
    }
}