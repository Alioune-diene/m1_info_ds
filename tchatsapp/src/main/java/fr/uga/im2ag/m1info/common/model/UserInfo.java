package fr.uga.im2ag.m1info.common.model;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Class representing user information used by the chat server.
 */
public class UserInfo implements Serializable {
    private final int id;
    private String username;
    private final Set<Integer> contacts;
    private long lastLogin;

    /**
     * Constructs a UserInfo instance with all fields specified.
     *
     * @param id        the user ID
     * @param username  the username
     * @param contacts  the set of contact IDs
     * @param lastLogin the timestamp of the last login
     */
    public UserInfo(int id, String username, Set<Integer> contacts, long lastLogin) {
        this.id = id;
        this.username = username;
        this.contacts = contacts;
        this.lastLogin = lastLogin;
    }

    /**
     * Constructs a UserInfo instance with current time as last login.
     *
     * @param id       the user ID
     * @param username the username
     * @param contacts the set of contact IDs
     */
    public UserInfo(int id, String username, Set<Integer> contacts) {
        this(id, username, contacts, System.currentTimeMillis());
    }

    /**
     * Constructs a UserInfo instance with no contacts and current time as last login.
     *
     * @param id       the user ID
     * @param username the username
     */
    public UserInfo(int id, String username) {
        this(id, username, new HashSet<>(), System.currentTimeMillis());
    }

    /**
     * Gets the user ID.
     *
     * @return the user ID
     */
    public int getId() {
        return id;
    }

    /**
     * Gets the username.
     *
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * Checks if the user has a specific contact ID.
     *
     * @param contactId the contact ID to check
     * @return true if the contact ID exists, false otherwise
     */
    public boolean hasContact(int contactId) {
        return contacts.contains(contactId);
    }

    /**
     * Gets the set of contact IDs.
     *
     * @return the set of contact IDs
     */
    public Set<Integer> getContacts() {
        return Collections.unmodifiableSet(contacts);
    }

    /**
     * Gets the timestamp of the last login.
     *
     * @return the last login timestamp
     */
    public long getLastLogin() {
        return lastLogin;
    }

    /** Sets the username.
     *
     * @param username the new username
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /** Adds a contact ID to the contacts set.
     *
     * @param contactId the contact ID to add
     */
    public void addContact(int contactId) {
        contacts.add(contactId);
    }

    /** Removes a contact ID from the contacts set.
     *
     * @param contactId the contact ID to remove
     */
    public void removeContact(int contactId) {
        contacts.remove(contactId);
    }

    /** Updates the last login timestamp to the current time. */
    public void updateLastLogin() {
        this.lastLogin = System.currentTimeMillis();
    }
}
