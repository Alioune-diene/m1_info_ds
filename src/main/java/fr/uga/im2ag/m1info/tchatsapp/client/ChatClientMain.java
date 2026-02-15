package fr.uga.im2ag.m1info.tchatsapp.client;

import fr.uga.im2ag.m1info.tchatsapp.common.IChatServer;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Scanner;

public class ChatClientMain {
    public static void main (String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java ChatClientMain <server_host> <username>");
            return;
        }
        String serverHost = args[0];
        String username = args[1];

        try {
            Registry registry = LocateRegistry.getRegistry(serverHost, 1099);
            IChatServer server = (IChatServer) registry.lookup("ChatServer");
            ChatClientImpl client = new ChatClientImpl();
            server.join(username, client);
            System.out.println("Joined chat as " + username + ". Type messages (or /quit to leave):");

            Scanner scanner = new Scanner(System.in);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.equalsIgnoreCase("/quit")) {
                    server.leave(username);
                    break;
                }
                server.sendMessage(username, line);
            }
            scanner.close();
            UnicastRemoteObject.unexportObject(client, true);
        } catch (Exception e) {
            System.err.println("Client error: " + e);
            e.printStackTrace();
        }
    }
}
