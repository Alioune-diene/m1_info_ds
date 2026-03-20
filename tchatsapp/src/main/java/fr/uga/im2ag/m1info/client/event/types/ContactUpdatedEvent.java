package fr.uga.im2ag.m1info.client.event.types;

import fr.uga.im2ag.m1info.client.event.system.Event;

/**
 * Class representing an event when a contact is updated.
 */
public class ContactUpdatedEvent extends ContactEvent {

    /** Constructor for the ContactUpdatedEvent class.
     *
     * @param source The source object that generated the event.
     * @param contactId The ID of the contact that was updated.
     */
    public ContactUpdatedEvent(Object source, int contactId) {
        super(source, contactId);
    }

    /** Gets the specific type of the event.
     *
     * @return The class type of the event.
     */
    @Override
    public Class<? extends Event> getEventType() {
        return ContactUpdatedEvent.class;
    }
}
