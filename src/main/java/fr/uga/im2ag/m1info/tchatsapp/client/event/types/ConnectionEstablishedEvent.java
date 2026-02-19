package fr.uga.im2ag.m1info.tchatsapp.client.event.types;

import fr.uga.im2ag.m1info.tchatsapp.client.event.system.Event;

/**
 * Event representing a successfully established connection to the server.
 */
public class ConnectionEstablishedEvent extends Event {
    private final int clientId;
    private final String pseudo;
    private final boolean isNewUser;

    /** Constructor for the ConnectionEstablishedEvent class.
     *
     * @param source The source object that generated the event.
     * @param clientId The client ID assigned by the server.
     * @param pseudo The user's pseudo/username.
     * @param isNewUser Whether this is a newly created user account.
     */
    public ConnectionEstablishedEvent(Object source, int clientId, String pseudo, boolean isNewUser) {
        super(source);
        this.clientId = clientId;
        this.pseudo = pseudo;
        this.isNewUser = isNewUser;
    }

    /** Gets the client ID.
     *
     * @return The client ID.
     */
    public int getClientId() {
        return clientId;
    }

    /** Gets the pseudo.
     *
     * @return The pseudo/username.
     */
    public String getPseudo() {
        return pseudo;
    }

    /** Checks if this is a new user.
     *
     * @return true if this is a newly created account, false otherwise.
     */
    public boolean isNewUser() {
        return isNewUser;
    }

    /** Gets the specific type of the event.
     *
     * @return The class type of the event.
     */
    @Override
    public Class<? extends Event> getEventType() {
        return ConnectionEstablishedEvent.class;
    }
}