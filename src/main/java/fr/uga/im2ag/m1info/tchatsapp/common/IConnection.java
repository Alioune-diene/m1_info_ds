package fr.uga.im2ag.m1info.tchatsapp.common;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IConnection extends Remote {
    public int Connect(String username, String password) throws RemoteException;
    public void Disconnect(int clientId) throws RemoteException;
    public int CreateAccount(String username, String password) throws RemoteException;
}
