package fr.uga.im2ag.m1info.client.event.system;

import java.time.Instant;

/**
 * Abstract base class for all events in the chat service client.
 */
public abstract class Event {
    private final Instant timestamp;
    private final Object source;

    /** Constructor for the Event class.
     *
     * @param source The source object that generated the event.
     */
    public Event(Object source) {
        this.source = source;
        this.timestamp = Instant.now();
    }

    /** Gets the timestamp of when the event was created.
     *
     * @return The timestamp of the event.
     */
    public Instant getTimestamp() {
        return timestamp;
    }

    /** Gets the source object that generated the event.
     *
     * @return The source of the event.
     */
    public Object getSource() {
        return source;
    }

    /** Gets the specific type of the event.
     *
     * @return The class type of the event.
     */
    public abstract Class<? extends Event> getEventType();
}
