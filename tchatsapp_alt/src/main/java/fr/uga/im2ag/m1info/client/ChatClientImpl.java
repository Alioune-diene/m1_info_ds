package fr.uga.im2ag.m1info.client;

import fr.uga.im2ag.m1info.common.IChatClient;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;


public  class ChatClientImpl extends UnicastRemoteObject implements IChatClient  {
    
    public ChatClientImpl() throws RemoteException {
        super();
    }

    @Override
    public void receiveMessage(String username, String message) throws RemoteException {
        System.out.println(username + ": " + message);
    }
}
