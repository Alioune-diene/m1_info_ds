package fr.uga.im2ag.m1info.tchatsapp.common.rmi;

import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.ProtocolMessage;
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IChatServer extends Remote {
    void processMessage(ProtocolMessage message) throws RemoteException;
    int registerClient(String pseudo, IChatClient callback) throws RemoteException;
    boolean connectClient(int clientId, IChatClient callback) throws RemoteException;
    void disconnect(int clientId) throws RemoteException;
}