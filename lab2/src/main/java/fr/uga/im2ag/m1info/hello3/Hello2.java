
package fr.uga.im2ag.m1info.hello3;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface Hello2 extends Remote {
	public String sayHello(Accounting_itf client) throws RemoteException;
}
