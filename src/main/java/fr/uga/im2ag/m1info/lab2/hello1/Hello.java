
package fr.uga.im2ag.m1info.lab2.hello1;
import java.rmi.*;

public interface Hello extends Remote {
	public String sayHello(String clientName)  throws RemoteException;
}
