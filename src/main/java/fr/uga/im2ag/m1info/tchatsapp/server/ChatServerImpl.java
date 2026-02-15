package fr.uga.im2ag.m1info.tchatsapp.server;

import fr.uga.im2ag.m1info.tchatsapp.common.*;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;


public class ChatServerImpl extends UnicastRemoteObject implements IChatServer {
    private final Map<String, IChatClient> clients = new ConcurrentHashMap<>();

    public ChatServerImpl() throws RemoteException {
        super();
    }

    @Override
    public void join(String username, IChatClient client) throws RemoteException {
        broadcast("Server", username + " has joined the chat.");
        clients.put(username, client);
        System.out.println(username + " connected.");
    }

    @Override
    public void leave(String username) throws RemoteException {
        clients.remove(username);
        broadcast("Server", username + " has left the chat.");
        System.out.println(username + " disconnected.");
    }

    @Override
    public void sendMessage(String username, String message) throws RemoteException {
        broadcast(username, message);
    }

    private void broadcast(String from, String message) {
        for (Map.Entry<String, IChatClient> entry : clients.entrySet()) {
            if (!entry.getKey().equals(from)) {
                try {
                    entry.getValue().receiveMessage(from, message);
                } catch (RemoteException e) {
                    System.err.println("Failed to reach " + entry.getKey() + ", removing.");
                    clients.remove(entry.getKey());
                }
            }
        }
    }


}