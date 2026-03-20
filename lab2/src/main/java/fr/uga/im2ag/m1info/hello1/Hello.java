
package fr.uga.im2ag.m1info.hello1;
import java.rmi.*;

public interface Hello extends Remote {
	public String sayHello(String clientName)  throws RemoteException;
}
