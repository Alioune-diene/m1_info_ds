package fr.uga.im2ag.m1info.tchatsapp.client.event.types;

import fr.uga.im2ag.m1info.tchatsapp.client.event.system.Event;

/**
 * Event representing the reception of a text message in a conversation.
 */
public class DeleteGroupEvent extends Event {

    private final int groupId;

    /** Constructor for the UserPseudoUpdatedEvent class.
     *
     * @param source The source object that generated the event.
     * @param groupId The group id
     */
    public DeleteGroupEvent(Object source, int groupId) {
        super(source);
        this.groupId = groupId;
    }

    /** Gets the group ID where the member change occurred.
     *
     * @return The group ID.
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
        return DeleteGroupEvent.class;
    }
}


