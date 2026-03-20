package fr.uga.im2ag.m1info.client.event.types;

import fr.uga.im2ag.m1info.client.event.system.Event;
import fr.uga.im2ag.m1info.common.model.GroupInfo;

/**
 * Event representing a creation of a group.
 */
public class GroupCreateEvent extends Event {
    private final GroupInfo groupInfo;

    /** Constructor for the GroupCreationEvent class.
     *
     * @param source The source object that generated the event.
     * @param groupInfo The group information.
     */
    public GroupCreateEvent(Object source, GroupInfo groupInfo) {
        super(source);
        this.groupInfo = groupInfo;
    }

    /** Gets the group information.
     *
     * @return The group information.
     */
    public GroupInfo getGroupInfo() {
        return groupInfo;
    }

    /** Gets the specific type of the event.
     *
     * @return The class type of the event.
     */
    @Override
    public Class<? extends Event> getEventType() {
        return GroupCreateEvent.class;
    }
}

