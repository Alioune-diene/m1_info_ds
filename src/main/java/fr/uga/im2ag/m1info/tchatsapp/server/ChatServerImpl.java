package fr.uga.im2ag.m1info.tchatsapp.server;

import fr.uga.im2ag.m1info.tchatsapp.common.*;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.File;

public class ChatServerImpl extends UnicastRemoteObject implements IChatServer {
    private final Map<String, IChatClient> clients = new ConcurrentHashMap<>();
    private final List<String> history;
    private static final String HISTORY_FILE = "chat_history.dat";

    public ChatServerImpl() throws RemoteException {
        super();
        history = Collections.synchronizedList(loadHistory());
        System.out.println("Loaded " + history.size() + " messages from history.");
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
        saveHistory();
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

    private void saveHistory() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(HISTORY_FILE))) {
            oos.writeObject(new ArrayList<>(history));
        } catch (IOException e) {
            System.err.println("Failed to save history: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private ArrayList<String> loadHistory() {
        File file = new File(HISTORY_FILE);
        if (!file.exists()) {
            return new ArrayList<>();
        }
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            return (ArrayList<String>) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Failed to load history: " + e.getMessage());
            return new ArrayList<>();
        }
    }
}
