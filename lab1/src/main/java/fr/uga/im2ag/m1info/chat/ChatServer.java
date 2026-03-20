package fr.uga.im2ag.m1info.chat;

import java.io.*;
import java.net.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer {

    // Thread-safe map: username -> client handler
    private static ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java ChatServer <port>");
            System.exit(1);
        }

        int port = Integer.parseInt(args[0]);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Chat server started on port " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(clientSocket);
                Thread t = new Thread(handler);
                t.start();
            }
        }
    }

    public static boolean registerClient(String username, ClientHandler handler) {
        if (!clients.containsKey(username)) { 
            clients.put(username, handler);
            return true;
        } else {
            return false;
        }
    }

    public static void removeClient(String username) {
        clients.remove(username);
    }

    public static String getClientList() {
        if (clients.isEmpty()) {
            return "No users connected";
        }
        return String.join(", ", clients.keySet());
    }

    public static boolean sendMessageTo(String fromUser, String toUser, String message) {
        if (clients.containsKey(toUser)) {
            clients.get(toUser).sendMessage(fromUser, message);
            return true;
        } else {
            return false;
        }
    }
}

// Handles one client connection in its own thread
class ClientHandler implements Runnable {

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String username;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            // Setup streams
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            out.println("Enter username: ");
            this.username = in.readLine();

            if (this.username == null || this.username.trim().isEmpty()) {
                out.println("ERROR Invalid username");
                return;
            }

            if (!ChatServer.registerClient(this.username, this)) {
                out.println("ERROR Username already taken");
                return;
            }

            out.println("OK Welcome " + this.username);
            System.out.println("User connected: " + this.username);

            // Main loop - read commands from client
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                if (inputLine.equals("LIST")) {
                    out.println("USERS: " + ChatServer.getClientList());

                } else if (inputLine.startsWith("SEND ")) {
                    // Split: "SEND bob hello world" -> ["SEND", "bob", "hello world"]
                    String[] parts = inputLine.split(" ", 3);
                    if (parts.length < 3) {
                        out.println("ERROR Usage: SEND <user> <message>");
                    } else {
                        String toUser = parts[1];
                        String message = parts[2];
                        if (ChatServer.sendMessageTo(this.username, toUser, message)) {
                            out.println("OK Message sent");
                        } else {
                            out.println("ERROR User not found: " + toUser);
                        }
                    }

                } else if (inputLine.equals("QUIT")) {
                    out.println("OK Goodbye");
                    break;

                } else {
                    out.println("ERROR Unknown command");
                }
            }

        } catch (IOException e) {
            System.out.println("Error handling client: " + e.getMessage());
        } finally {
            // Clean up
            if (this.username != null) {
                ChatServer.removeClient(this.username);
                System.out.println("User disconnected: " + this.username);
            }
            try {
                socket.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    // Called by ChatServer to send a message TO this client
    public void sendMessage(String fromUser, String message) {
        out.println("MSG " + fromUser + " " + message);
    }
}
