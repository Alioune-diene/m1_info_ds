package fr.uga.im2ag.m1info.tchatsapp.client.event.types;

import fr.uga.im2ag.m1info.tchatsapp.client.event.system.Event;

/**
 * Event representing an update to the current user's pseudo.
 */
public class LeaveGroupEvent extends Event {
    private final int groupId;

    /** Constructor for the UserPseudoUpdatedEvent class.
     *
     * @param source The source object that generated the event.
     * @param groupId The new pseudo/username.
     */
    public LeaveGroupEvent(Object source, int groupId) {
        super(source);
        this.groupId = groupId;
    }

    /** Gets the new pseudo.
     *
     * @return The new pseudo/username.
     */
    public int getGroupId() {
        return groupId;
    }

    /** Gets the specific type of the event.
     *
     * @return The class type of the event.
     */
    @Override
    public Class<? extends Event> getEventType() {
        return LeaveGroupEvent.class;
    }
}


