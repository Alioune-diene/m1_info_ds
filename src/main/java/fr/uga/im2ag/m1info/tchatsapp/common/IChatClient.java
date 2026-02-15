package fr.uga.im2ag.m1info.tchatsapp.common;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IChatClient extends Remote {
    void receiveMessage(String username, String message) throws RemoteException;

}
