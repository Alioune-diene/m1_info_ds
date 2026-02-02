
package fr.uga.im2ag.m1info.lab2.hello1;
import java.rmi.*;

public  class HelloImpl implements Hello {

	private String message;
 
	public HelloImpl(String s) {
		message = s ;
	}

	public String sayHello(String clientName) throws RemoteException {
	
        System.out.println("sayHello() called by: " + clientName);
        return message ;
	}
}

