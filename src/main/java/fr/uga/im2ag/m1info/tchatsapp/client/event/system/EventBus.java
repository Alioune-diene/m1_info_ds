package fr.uga.im2ag.m1info.tchatsapp.client.event.system;

import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * EventBus is a singleton class that manages event subscriptions and publishing.
 */
public class EventBus {
    private static final EventBus instance = new EventBus();

    /** Thread-safe map of event subscriptions associated with event types */
    private final ConcurrentHashMap<Class<? extends Event>, CopyOnWriteArrayList<EventSubscription<?>>> subscriptions;
    private final ExecutorService executor;
    private final Logger logger;

    /** Private constructor to enforce singleton pattern */
    private EventBus() {
        this.subscriptions = new ConcurrentHashMap<>();
        this.executor = new ThreadPoolExecutor(
                2, 10, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        this.logger = Logger.getLogger(EventBus.class.getName());
    }

    /** Gets the singleton instance of the EventBus.
     *
     * @return the singleton EventBus instance
     */
    public static EventBus getInstance() {
        return instance;
    }

    /** Subscribes an observer to events of a specific type with a filter and execution mode.
     *
     * @param eventType the class type of the event to subscribe to
     * @param observer  the observer that will receive events
     * @param filter    the filter to apply to events
     * @param mode      the execution mode for event delivery
     * @param <T>       the type of event
     * @return the created EventSubscription
     */
    public <T extends Event> EventSubscription<T> subscribe(
            Class<T> eventType,
            EventObserver<T> observer,
            EventFilter<T> filter,
            ExecutionMode mode) {

        EventSubscription<T> subscription = new EventSubscription<>(observer, filter, mode);

        subscriptions.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                .add(subscription);

        return subscription;
    }

    /** Subscribes an observer to events of a specific type with execution mode.
     *
     * @param eventType the class type of the event to subscribe to
     * @param observer  the observer that will receive events
     * @param mode      the execution mode for event delivery
     * @param <T>       the type of event
     * @return the created EventSubscription
     */
    public <T extends Event> EventSubscription<T> subscribe(
            Class<T> eventType,
            EventObserver<T> observer,
            ExecutionMode mode) {
        return subscribe(eventType, observer, event -> true, mode);
    }

    /** Unsubscribes an observer from receiving events.
     *
     * @param subscription the EventSubscription to cancel
     * @param <T>          the type of event
     */
    public <T extends Event> void unsubscribe(EventSubscription<T> subscription) {
        subscription.cancel();
    }

    /** Publishes an event to all subscribed observers.
     *
     * @param event the event to publish
     * @param <T>   the type of event
     */
    public <T extends Event> void publish(T event) {
        Class<? extends Event> eventType = event.getEventType();

        CopyOnWriteArrayList<EventSubscription<?>> subs = subscriptions.get(eventType);
        if (subs == null) {
            return;
        }

        for (EventSubscription<?> sub : subs) {
            if (!sub.isActive()) {
                continue;
            }

            @SuppressWarnings("unchecked")
            EventSubscription<T> typedSub = (EventSubscription<T>) sub;

            if (!typedSub.matches(event)) {
                continue;
            }

            if (typedSub.getExecutionMode() == ExecutionMode.SYNC) {
                notifyObserver(typedSub, event);
            } else {
                executor.execute(() -> notifyObserver(typedSub, event));
            }
        }
    }

    /** Notifies an observer of an event, handling exceptions.
     *
     * @param subscription the EventSubscription containing the observer
     * @param event        the event to notify about
     * @param <T>          the type of event
     */
    private <T extends Event> void notifyObserver(EventSubscription<T> subscription, T event) {
        try {
            subscription.getObserver().onEvent(event);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error in event observer for event type: " + event.getEventType().getSimpleName(), e);
        }
    }

    /** Shuts down the EventBus executor service gracefully. */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}