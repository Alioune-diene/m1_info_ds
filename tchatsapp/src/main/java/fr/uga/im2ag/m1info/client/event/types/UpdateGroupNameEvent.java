package fr.uga.im2ag.m1info.client.event.types;

import fr.uga.im2ag.m1info.client.event.system.Event;

/**
 * Event representing the reception of a text message in a conversation.
 */
public class UpdateGroupNameEvent extends Event {

    private final int groupId;
    private final String newName;

    /** Constructor for the UserPseudoUpdatedEvent class.
     *
     * @param source The source object that generated the event.
     * @param groupId The bgroup id  
     * @param newName The newName of the group  
     */
    public UpdateGroupNameEvent(Object source, int groupId, String newName) {
        super(source);
        this.groupId = groupId;
        this.newName = newName;
    }

    /** Gets the group ID where the member change occurred.
     *
     * @return The group ID.
     */
    public int getGroupId() {
        return groupId;
    }

    /** Get the new name
     *
     * @return The new Name.
     */
    public String getNewName() {
        return newName;
    }

    /** Gets the specific type of the event.
     *
     * @return The class type of the event.
     */
    @Override
    public Class<? extends Event> getEventType() {
        return UpdateGroupNameEvent.class;
    }
}

