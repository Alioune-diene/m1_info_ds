package fr.uga.im2ag.m1info.client.model;

import java.security.PrivateKey;
import java.security.PublicKey;

public class UserClient {
    private final int userId;
    private String pseudo;
    private PrivateKey privateKey;
    private PublicKey publicKey;


    public UserClient(int userId, String pseudo) {
        this.userId = userId;
        this.pseudo = pseudo;
    }

    public UserClient() {
        this.userId = -1;
        this.pseudo = "unknown";
    }

    public int getUserId() {
        return userId;
    }

    public String getPseudo() {
        return pseudo;
    }

    public void setPseudo(String pseudo) {
        this.pseudo = pseudo;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public void setKeyPair(PrivateKey privateKey, PublicKey publicKey) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
    }
}
