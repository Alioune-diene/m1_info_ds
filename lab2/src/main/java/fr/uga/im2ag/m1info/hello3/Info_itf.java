package fr.uga.im2ag.m1info.hello3;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface Info_itf extends Remote {
    public String getName() throws RemoteException;
}
