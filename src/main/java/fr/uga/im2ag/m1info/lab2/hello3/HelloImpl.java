
package fr.uga.im2ag.m1info.lab2.hello3;

import java.rmi.RemoteException;

public class HelloImpl implements Hello {

    private String message;

    public HelloImpl(String s) {
        message = s;
    }

    public String sayHello(Info_itf client) throws RemoteException {
        System.out.println("sayHello called by " + client.getName());
        return message;
    }
}

