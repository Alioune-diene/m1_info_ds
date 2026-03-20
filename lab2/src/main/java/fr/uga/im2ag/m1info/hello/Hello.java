
package fr.uga.im2ag.m1info.hello;
import java.rmi.*;

public interface Hello extends Remote {
	public String sayHello()  throws RemoteException;
}
