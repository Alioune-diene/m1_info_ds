package fr.uga.im2ag.m1info.client.event.system;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Represents a subscription to events of type T.
 *
 * @param <T> the type of event
 */
public class EventSubscription <T extends Event> {
    private final EventObserver<T> observer;
    private final EventFilter<T> filter;
    private final ExecutionMode executionMode;
    private AtomicBoolean active;

    /** Constructor for EventSubscription.
     *
     * @param observer      the observer that will receive events
     * @param filter        the filter to apply to events
     * @param executionMode the execution mode for event delivery
     */
    public EventSubscription(EventObserver<T> observer, EventFilter<T> filter, ExecutionMode executionMode) {
        this.observer = observer;
        this.filter = filter;
        this.executionMode = executionMode;
        this.active = new AtomicBoolean(true);
    }

    /** Constructor for EventSubscription with a default filter that accepts all events.
     *
     * @param observer      the observer that will receive events
     * @param executionMode the execution mode for event delivery
     */
    public EventSubscription(EventObserver<T> observer, ExecutionMode executionMode) {
        this(observer, event -> true, executionMode);
    }

    /** Gets the observer associated with this subscription.
     *
     * @return the event observer
     */
    public EventObserver<T> getObserver() {
        return observer;
    }

    /** Gets the filter associated with this subscription.
     *
     * @return the event filter
     */
    public EventFilter<T> getFilter() {
        return filter;
    }

    /** Gets the execution mode for this subscription.
     *
     * @return the execution mode
     */
    public ExecutionMode getExecutionMode() {
        return executionMode;
    }

    /** Checks if the subscription is active.
     *
     * @return true if the subscription is active, false otherwise
     */
    public boolean isActive() {
        return active.get();
    }

    /** Cancels the subscription, marking it as inactive. */
    public void cancel() {
        this.active.set(false);
    }

    /** Checks if the given event matches the filter of this subscription.
     *
     * @param event the event to check
     * @return true if the event matches the filter, false otherwise
     */
    public boolean matches(T event) {
        return filter.accept(event);
    }
}
