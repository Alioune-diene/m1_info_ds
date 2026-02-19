package fr.uga.im2ag.m1info.tchatsapp.client.event.types;

import fr.uga.im2ag.m1info.tchatsapp.client.event.system.Event;

/**
 * Abstract class representing an event related to a contact.
 */
public abstract class ContactEvent extends Event {
    private final int contactId;

    /** Constructor for the ContactEvent class.
     *
     * @param source The source object that generated the event.
     * @param contactId The ID of the contact related to the event.
     */
    public ContactEvent(Object source, int contactId) {
        super(source);
        this.contactId = contactId;
    }

    /** Gets the ID of the contact related to the event.
     *
     * @return The contact ID.
     */
    public int getContactId() {
        return contactId;
    }
}
