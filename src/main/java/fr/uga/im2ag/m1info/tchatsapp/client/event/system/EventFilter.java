package fr.uga.im2ag.m1info.tchatsapp.client.event.system;

/**
 * Interface for filtering events of type T.
 *
 * @param <T> the type of event to filter
 */
public interface EventFilter <T extends Event> {
    /**
     * Determines whether the given event is accepted by the filter.
     *
     * @param event the event to evaluate
     * @return true if the event is accepted, false otherwise
     */
    boolean accept(T event);
}
