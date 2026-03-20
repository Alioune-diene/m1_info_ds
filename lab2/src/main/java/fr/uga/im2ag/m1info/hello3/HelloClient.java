
package fr.uga.im2ag.m1info.hello3;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

public class HelloClient implements Accounting_itf {
	public static void main(String[] args) {

		try {
			if (args.length < 3) {
				System.out.println("Usage: java HelloClient <rmiregistry host> <rmiregistry port> <name>");
				return;
			}

			String host = args[0];
			int port = Integer.parseInt(args[1]);

			HelloClient client = new HelloClient(args[2]);
			Accounting_itf info = (Accounting_itf) UnicastRemoteObject.exportObject(client, 0);

			Registry registry = LocateRegistry.getRegistry(host, port);
			Hello h = (Hello) registry.lookup("Hello1Service");
			Hello2 h2 = (Hello2) registry.lookup("Hello2Service");
			Registry_itf r = (Registry_itf) registry.lookup("RegistryService");
            
            r.register(client);
			// Remote method invocation
            for(int i = 0; i < 30; i++) {
    			String res = h2.sayHello(info);
    			System.out.println(res);
            }
			// Stop the client
			UnicastRemoteObject.unexportObject(client, true);
		} catch (Exception e) {
			// System.err.println("Error on client: " + e);
			e.printStackTrace();
		}
	}

	public int callCount;

    public String name;

    public HelloClient(String name) {
        this.name = name;
    }

	@Override
	public void numberOfCalls(int number) throws RemoteException {
	   System.out.println("Notification: You have made " + number + " calls!");
    }

	@Override
	public String getName() throws RemoteException {
		return name;
	}
}
