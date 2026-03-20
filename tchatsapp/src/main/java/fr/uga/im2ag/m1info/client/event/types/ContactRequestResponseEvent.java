package fr.uga.im2ag.m1info.client.event.types;

import fr.uga.im2ag.m1info.client.event.system.Event;

/**
 * Event representing the response to a contact request.
 * This event is fired whenever a user responds to a contact request,
 * either by accepting or declining it.
 */
public class ContactRequestResponseEvent extends Event {
    private final String requestId;
    private final int otherUserId;
    private final boolean accepted;
    private final boolean wasSentByUs;

    /**
     * Constructor.
     *
     * @param source the source object
     * @param requestId the request ID
     * @param otherUserId the ID of the other user involved
     * @param accepted true if the request was accepted
     * @param wasSentByUs true if we sent the original request, false if we received it
     */
    public ContactRequestResponseEvent(Object source, String requestId, int otherUserId, boolean accepted, boolean wasSentByUs) {
        super(source);
        this.requestId = requestId;
        this.otherUserId = otherUserId;
        this.accepted = accepted;
        this.wasSentByUs = wasSentByUs;
    }

    /**
     * Get the request ID.
     *
     * @return the request ID
     */
    public String getRequestId() {
        return requestId;
    }

    /**
     * Get the other user's ID.
     *
     * @return the other user ID
     */
    public int getOtherUserId() {
        return otherUserId;
    }

    /**
     * Check if the request was accepted.
     *
     * @return true if accepted
     */
    public boolean isAccepted() {
        return accepted;
    }

    /**
     * Check if this was a response to a request we sent.
     *
     * @return true if we sent the original request
     */
    public boolean wasSentByUs() {
        return wasSentByUs;
    }

    @Override
    public Class<? extends Event> getEventType() {
        return ContactRequestResponseEvent.class;
    }
}