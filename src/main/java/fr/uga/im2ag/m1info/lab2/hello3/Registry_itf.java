package fr.uga.im2ag.m1info.lab2.hello3;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface Registry_itf extends Remote {
    public void register(Accounting_itf client) throws RemoteException;
}
