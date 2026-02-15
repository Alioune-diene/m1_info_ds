package fr.uga.im2ag.m1info.tchatsapp.server;

import fr.uga.im2ag.m1info.tchatsapp.common.*;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public class ChatServerImpl extends UnicastRemoteObject implements IChatServer {
    private final Map<String, IChatClient> clients = new ConcurrentHashMap<>();
    private final List<String> history = Collections.synchronizedList(new ArrayList<>());
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
        history.add(username + ": " + message);
        broadcast(username, message);
    }

    @Override
    public List<String> getHistory() throws RemoteException {
        return history;
    }
    
    private void broadcast(String from, String message) {
        for (Map.Entry<String, IChatClient> entry : clients.entrySet()) {
            if (!entry.getKey().equals(from)) {
                try {
                    entry.getValue().receiveMessage(from, message);
                } catch (RemoteException e) {
                    String username = entry.getKey();
                    System.err.println("Failed to reach " + username + ", removing.");
                    clients.remove(username);
                    broadcast("Server", username + " has left the chat.");
                }
            }
        }
    }


}
