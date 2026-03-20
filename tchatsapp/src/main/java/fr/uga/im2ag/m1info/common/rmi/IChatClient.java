package fr.uga.im2ag.m1info.common.rmi;

import fr.uga.im2ag.m1info.common.messagefactory.ProtocolMessage;
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IChatClient extends Remote {
    void receiveMessage(ProtocolMessage message) throws RemoteException;
}
