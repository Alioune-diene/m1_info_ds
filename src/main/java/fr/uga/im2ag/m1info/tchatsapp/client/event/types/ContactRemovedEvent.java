package fr.uga.im2ag.m1info.tchatsapp.client.event.types;

import fr.uga.im2ag.m1info.tchatsapp.client.event.system.Event;

/**
 * Class representing an event when a contact is removed.
 */
public class ContactRemovedEvent extends ContactEvent {

    /** Constructor for the ContactRemovedEvent class.
     *
     * @param source The source object that generated the event.
     * @param contactId The ID of the contact that was removed.
     */
    public ContactRemovedEvent(Object source, int contactId) {
        super(source, contactId);
    }

    /** Gets the specific type of the event.
     *
     * @return The class type of the event.
     */
    @Override
    public Class<? extends Event> getEventType() {
        return ContactRemovedEvent.class;
    }
}
