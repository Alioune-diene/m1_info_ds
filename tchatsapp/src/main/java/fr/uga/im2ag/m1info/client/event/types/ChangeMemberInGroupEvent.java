package fr.uga.im2ag.m1info.client.event.types;

import fr.uga.im2ag.m1info.client.event.system.Event;

/**
 * Event representing an update to the current user's pseudo.
 */
public class ChangeMemberInGroupEvent extends Event {
    private final int groupId;
    private final int memberId;
    private final String memberPseudo;
    private final boolean isAdded; // true if member is added, false if removed

    /** Constructor for the UserPseudoUpdatedEvent class.
     *
     * @param source The source object that generated the event.
     * @param groupId The new pseudo/username.
     * @param memberId The member being added or removed.
     * @param isAdded True if the member is added, false if removed.
     */
    public ChangeMemberInGroupEvent(Object source, int groupId, int memberId, String memberPseudo, boolean isAdded) {
        super(source);
        this.groupId = groupId;
        this.memberId = memberId;
        this.memberPseudo = memberPseudo;
        this.isAdded = isAdded;
    }

    /** Gets the group ID where the member change occurred.
     *
     * @return The group ID.
     */
    public int getGroupId() {
        return groupId;
    }

    /** Gets the member ID that was added or removed.
     *
     * @return The member ID.
     */
    public int getMemberId() {
        return memberId;
    }

    /** Gets the member pseudo that was added or removed.
     *
     * @return The member pseudo.
     */
    public String getMemberPseudo() {
        return memberPseudo;
    }

    /** Indicates whether the member was added or removed.
     *
     * @return True if the member was added, false if removed.
     */
    public boolean isAdded() {
        return isAdded;
    }

    /** Gets the specific type of the event.
     *
     * @return The class type of the event.
     */
    @Override
    public Class<? extends Event> getEventType() {
        return ChangeMemberInGroupEvent.class;
    }
}


