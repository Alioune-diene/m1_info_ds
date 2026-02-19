package fr.uga.im2ag.m1info.tchatsapp.common.rmi;

import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.ProtocolMessage;
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IChatClient extends Remote {
    void receiveMessage(ProtocolMessage message) throws RemoteException;
}
