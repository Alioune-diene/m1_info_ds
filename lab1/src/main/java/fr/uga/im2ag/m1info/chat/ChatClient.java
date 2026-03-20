package fr.uga.im2ag.m1info.chat;

import java.io.*;
import java.net.*;
import java.util.Scanner;

public class ChatClient {

    private static volatile boolean running = true;

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("Usage: java ChatClient <host> <port>");
            System.exit(1);
        }

        String host = args[0];
        int port = Integer.parseInt(args[1]);

        try (
            Socket socket = new Socket(host, port);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            Scanner scanner = new Scanner(System.in)
        ) {
            System.out.println("Connected to chat server!");

            // Handle username registration
            String serverPrompt = in.readLine();
            System.out.print(serverPrompt);
            String username = scanner.nextLine().trim();
            out.println(username);

            String response = in.readLine();
            System.out.println(response);
            if (response.startsWith("ERROR")) {
                return;
            }

            Thread receiverThread = new Thread(new ReceiverThread(in));
            receiverThread.setDaemon(true);
            receiverThread.start();

            printMenu();

            while (running) {
                System.out.print("> ");
                String input = scanner.nextLine().trim();

                if (input.isEmpty()) continue;

                switch (input.toLowerCase()) {
                    case "1":
                    case "list":
                        out.println("LIST");
                        break;
                    case "2":
                    case "send":
                        System.out.print("To: ");
                        String recipient = scanner.nextLine().trim();
                        System.out.print("Message: ");
                        String message = scanner.nextLine().trim();
                        out.println("SEND " + recipient + " " + message);
                        break;
                    case "3":
                    case "quit":
                        out.println("QUIT");
                        running = false;
                        break;
                    default:
                        System.out.println("Unknown command");
                        break;
                }
            }

        } catch (ConnectException e) {
            System.err.println("Could not connect to server at " + host + ":" + port);
        }
    }

    private static void printMenu() {
        System.out.println("\n=== Chat Client ===");
        System.out.println("1. List users");
        System.out.println("2. Send message");
        System.out.println("3. Quit");
        System.out.println("===================\n");
    }
}

// Thread that continuously reads from server and prints messages
class ReceiverThread implements Runnable {

    private BufferedReader in;

    public ReceiverThread(BufferedReader in) {
        this.in = in;
    }

    @Override
    public void run() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                System.out.println("\n" + message);
                System.out.print("> ");
            }
        } catch (IOException e) {
            // Connection closed
        }
    }
}
