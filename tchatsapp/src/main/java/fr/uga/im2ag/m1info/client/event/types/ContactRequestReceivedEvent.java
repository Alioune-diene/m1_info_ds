package fr.uga.im2ag.m1info.client.event.types;

import fr.uga.im2ag.m1info.client.event.system.Event;
import fr.uga.im2ag.m1info.client.model.ContactRequest;

/**
 * Event representing the receipt of a new contact request.
 * This event is fired whenever a user receives a new contact request from another user.
 */
public class ContactRequestReceivedEvent extends Event {
    private final ContactRequest contactRequest;

    /**
     * Constructor.
     *
     * @param source the source object
     * @param contactRequest the received contact request
     */
    public ContactRequestReceivedEvent(Object source, ContactRequest contactRequest) {
        super(source);
        this.contactRequest = contactRequest;
    }

    /**
     * Get the contact request.
     *
     * @return the contact request
     */
    public ContactRequest getContactRequest() {
        return contactRequest;
    }

    /**
     * Get the sender ID.
     *
     * @return the sender user ID
     */
    public int getSenderId() {
        return contactRequest.getSenderId();
    }

    @Override
    public Class<? extends Event> getEventType() {
        return ContactRequestReceivedEvent.class;
    }
}