package fr.uga.im2ag.m1info.tchatsapp_alt.common;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface IChatServer extends Remote {
    void join(String username, IChatClient client) throws RemoteException;
    void leave(String username) throws RemoteException;
    void sendMessage(String username, String message) throws RemoteException;
    List<String> getHistory() throws RemoteException;
}
