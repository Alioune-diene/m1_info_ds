
package fr.uga.im2ag.m1info.hello2;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

public class HelloClient implements Info_itf {
	public static void main(String[] args) {

		try {
			if (args.length < 3) {
				System.out.println("Usage: java HelloClient <rmiregistry host> <rmiregistry port> <name>");
				return;
			}

			String host = args[0];
			int port = Integer.parseInt(args[1]);

			HelloClient client = new HelloClient(args[2]);
			Info_itf info = (Info_itf) UnicastRemoteObject.exportObject(client, 0);

			Registry registry = LocateRegistry.getRegistry(host, port);
			Hello h = (Hello) registry.lookup("HelloService");

			// Remote method invocation
			String res = h.sayHello(info);
			System.out.println(res);

			// Stop the client
			UnicastRemoteObject.unexportObject(client, true);
		} catch (Exception e) {
			// System.err.println("Error on client: " + e);
			e.printStackTrace();
		}
	}

	public String name;

	public HelloClient(String name) {
		this.name = name;
	}

	@Override
	public String getName() throws RemoteException {
		return name;
	}
}
