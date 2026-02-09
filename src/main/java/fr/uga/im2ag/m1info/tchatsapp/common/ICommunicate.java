package fr.uga.im2ag.m1info.tchatsapp.common;

import java.rmi.Remote;

public interface ICommunicate extends Remote {
    public void SendMessage(int clientId, Packet packet) throws Exception;
}
