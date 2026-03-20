package fr.uga.im2ag.m1info.client.event.types;

import fr.uga.im2ag.m1info.client.event.system.Event;

/**
 * Event representing the addition of a contact.
 */
public class ContactAddedEvent extends ContactEvent {

    /** Constructor for the ContactAddedEvent class.
     *
     * @param source The source object that generated the event.
     * @param contactId The ID of the contact that was added.
     */
    public ContactAddedEvent(Object source, int contactId) {
        super(source, contactId);
    }

    /** Gets the specific type of the event.
     *
     * @return The class type of the event.
     */
    @Override
    public Class<? extends Event> getEventType() {
        return ContactAddedEvent.class;
    }
}
