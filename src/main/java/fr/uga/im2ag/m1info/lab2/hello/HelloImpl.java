
package fr.uga.im2ag.m1info.lab2.hello;
import java.rmi.*;

public  class HelloImpl implements Hello {

	private String message;
 
	public HelloImpl(String s) {
		message = s ;
	}

	public String sayHello() throws RemoteException {
		return message ;
	}
}

