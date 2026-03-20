
package fr.uga.im2ag.m1info.hello3;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface Hello extends Remote {
	public String sayHello(Info_itf client)  throws RemoteException;
}
