package fr.uga.im2ag.m1info.client.model;

import java.io.Serializable;
import java.security.PublicKey;
import java.time.Instant;

public class ContactClient implements Serializable {
    private final int contactId;
    private String pseudo;
    private PublicKey publicKey;
    private Instant lastSeen;

    public ContactClient(int contactId, String pseudo) {
        this.contactId = contactId;
        this.pseudo = pseudo;
    }

    public int getContactId() {
        return contactId;
    }

    public Instant getLastSeen() {
        return lastSeen;
    }

    public String getPseudo() {
        return pseudo;
    }

    public void updatePseudo(String pseudo) {
        this.pseudo = pseudo;
    }

    public void updateLastSeen() {
        this.lastSeen = Instant.now();
    }

    public PublicKey getPublicKey() {
        return this.publicKey;
    }

    public void setPublicKey(PublicKey publicKey) {
        this.publicKey = publicKey;
    }
}
