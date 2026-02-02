
package fr.uga.im2ag.m1info.lab2.hello2;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface Hello extends Remote {
	public String sayHello()  throws RemoteException;
}
