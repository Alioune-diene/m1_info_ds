package fr.uga.im2ag.m1info.client.event.system;

/**
 * Interface for observing events of type T.
 *
 * @param <T> the type of event to observe
 */
public interface EventObserver <T extends Event> {
    /**
     * Called when an event of type T occurs.
     *
     * @param event the event that occurred
     */
    void onEvent(T event);
}
