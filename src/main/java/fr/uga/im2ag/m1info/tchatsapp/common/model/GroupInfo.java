package fr.uga.im2ag.m1info.tchatsapp.common.model;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class representing group information.
 */
public class GroupInfo implements Serializable {
    private final int id;
    private final int adminId;
    private String groupName;
    private final Map<Integer, String> members;

    /**
     * Constructs a GroupInfo instance with all fields specified.
     *
     * @param id        the group ID
     * @param adminID        the admin group ID
     * @param groupName  the groupName
     * @param members  the set of member IDs
     */
    public GroupInfo(int id, int adminID, String groupName, Map<Integer, String> members) {
        this.id = id;
        this.adminId = adminID;
        this.groupName = groupName;
        this.members = members;
    }

    /**
     * Constructs a GroupInfo instance with no members and current time as last login.
     *
     * @param id       the group ID
     * @param adminID        the admin group ID
     * @param groupName the groupName
     */
    public GroupInfo(int id, int adminID, String groupName) {
        this(id, adminID, groupName, new ConcurrentHashMap<>());
    }

    /**
     * Gets the group ID.
     *
     * @return the group ID
     */
    public int getGroupId() {
        return id;
    }

    /**
     * Gets the group admin ID.
     *
     * @return the group admin ID
     */
    public int getAdminId() {
        return adminId;
    }

    /**
     * Gets a non-modifiable view of the groupName.
     *
     * @return the groupName
     */
    public String getGroupName() {
        return groupName;
    }

    /**
     * Gets a non-modifiable view of the members.
     *
     * @return the map of member IDs to member names
     */
    public Map<Integer, String> getMembers() {
        return Map.copyOf(members);
    }

    /**
     * Gets a non-modifiable view of the member IDs.
     *
     * @return the set of member IDs
     */
    public Set<Integer> getMembersId() {
        return new HashSet<>(members.keySet());
    }

    public String getMemberName(int memberId) {
        return members.get(memberId);
    }

    public void setMemberName(int memberId, String memberName) {
        members.put(memberId, memberName);
    }

    /** Sets the groupName.
     *
     * @param groupName the new groupName
     */
    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    /** Adds a member ID to the members set.
     *
     * @param memberId the member ID to add
     */
    public void addMember(int memberId, String memberName) {
        if (memberName == null || memberName.isEmpty()) {
            memberName = "User #" + memberId;
        }
        members.put(memberId, memberName);
    }

    /** Removes a member ID from the members set.
     *
     * @param memberId the member ID to remove
     */
    public void removeMember(int memberId) {
        members.remove(memberId);
    }

    /**
     * Checks if the group has a specific member ID.
     *
     * @param memberId the member ID to check
     * @return true if the member ID exists, false otherwise
     */
    public boolean hasMember(int memberId) {
        return members.containsKey(memberId);
    }

    /**
     * Checks if the user is the admin of the group.
     *
     * @param userId the user ID to check
     * @return true if the user is the admin, false otherwise
     */
    public boolean isAdmin(int userId) {
        return adminId == userId;
    }
}
