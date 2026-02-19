package fr.uga.im2ag.m1info.tchatsapp.client.event.types;

import fr.uga.im2ag.m1info.tchatsapp.client.event.system.Event;

/**
 * Event representing an update to the current user's pseudo.
 */
public class UserPseudoUpdatedEvent extends Event {
    private final String newPseudo;

    /** Constructor for the UserPseudoUpdatedEvent class.
     *
     * @param source The source object that generated the event.
     * @param newPseudo The new pseudo/username.
     */
    public UserPseudoUpdatedEvent(Object source, String newPseudo) {
        super(source);
        this.newPseudo = newPseudo;
    }

    /** Gets the new pseudo.
     *
     * @return The new pseudo/username.
     */
    public String getNewPseudo() {
        return newPseudo;
    }

    /** Gets the specific type of the event.
     *
     * @return The class type of the event.
     */
    @Override
    public Class<? extends Event> getEventType() {
        return UserPseudoUpdatedEvent.class;
    }
}