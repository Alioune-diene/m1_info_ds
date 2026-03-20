package fr.uga.im2ag.m1info.common.rmi;

import fr.uga.im2ag.m1info.common.messagefactory.ProtocolMessage;
import fr.uga.im2ag.m1info.common.model.GroupInfo;
import fr.uga.im2ag.m1info.common.model.UserInfo;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Set;

public interface IChatServer extends Remote {
    void processMessage(ProtocolMessage message) throws RemoteException;
    int registerClient(String pseudo, IChatClient callback) throws RemoteException;
    boolean connectClient(int clientId, IChatClient callback) throws RemoteException;
    void disconnect(int clientId) throws RemoteException;

    String getClientPseudo(int clientId) throws RemoteException;
    Set<UserInfo> getContacts(int clientId) throws RemoteException;
    Set<GroupInfo> getGroups(int clientId) throws RemoteException;

}