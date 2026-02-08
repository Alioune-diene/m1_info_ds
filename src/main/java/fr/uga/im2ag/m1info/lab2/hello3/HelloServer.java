
package fr.uga.im2ag.m1info.lab2.hello3;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

public class HelloServer {

    public static void main(String[] args) {
        try {
			// Create a Hello remote object
            HelloImpl h = new HelloImpl("Hello world !");
            Hello h_stub = (Hello) UnicastRemoteObject.exportObject(h, 0);

            // Create a registry remote object
            RegistryImpl r = new RegistryImpl();
            Registry_itf r_stub = (Registry_itf) UnicastRemoteObject.exportObject(r, 0);

            Hello2Impl h2 = new Hello2Impl("Hi there !", r);
            Hello2 h2_stub = (Hello2) UnicastRemoteObject.exportObject(h2, 0);


			// Register the remote object in RMI registry with a given identifier
            Registry registry = null;
            if (args.length > 0)
                registry = LocateRegistry.getRegistry(Integer.parseInt(args[0]));
            else
                registry = LocateRegistry.getRegistry();
            registry.rebind("Hello1Service", h_stub);
            registry.rebind("Hello2Service", h2_stub);
            registry.rebind("RegistryService", r_stub);

            System.out.println("Server ready");

        } catch (Exception e) {
            System.err.println("Error on server :" + e);
            e.printStackTrace();
        }
    }
}
