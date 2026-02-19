package fr.uga.im2ag.m1info.tchatsapp.server;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class ChatServerMain {
    public static void main (String[] args) {
        try {
            ChatServerImpl server = new ChatServerImpl();
            Registry registry = LocateRegistry.createRegistry(1099);
            registry.bind("ChatServer", server);
            System.out.println("Chat server is ready.");
        } catch (Exception e) {
            System.err.println("Server error: " + e);
            e.printStackTrace();
        }
    }
    
}
