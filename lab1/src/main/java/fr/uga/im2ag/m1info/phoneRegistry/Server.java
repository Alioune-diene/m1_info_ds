package fr.uga.im2ag.m1info.phoneRegistry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

import static fr.uga.im2ag.m1info.phoneRegistry.EResult.*;

public class Server {
    public static void main(String[] args) throws IOException {

        if (args.length != 1) {
            System.err.println("Usage: java PersonServer <port number>");
            System.exit(1);
        }

        int portNumber = Integer.parseInt(args[0]);

        try (
                ServerSocket serverSocket = new ServerSocket(Integer.parseInt(args[0]));
                Socket clientSocket = serverSocket.accept();
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        ) {

            EFunction function;
            String inputLine;

            while ((inputLine = in.readLine()) != null) {
                function = EFunction.values()[Integer.parseInt(inputLine)];
                System.out.println("Received function: " + function);

                switch (function) {
                    case ADD -> {
                        String firstName = in.readLine();
                        String name = in.readLine();
                        String phoneNumber = in.readLine();
                        PersonRegistry.addPerson(new Person(name, firstName, phoneNumber));
                        out.println(SUCCESS);
                    }
                    case GET_PHONE -> {
                        String name = in.readLine();
                        String phoneNumber = PersonRegistry.getPhone(name);
                        if (phoneNumber != null) {
                            out.println(SUCCESS);
                            out.println(phoneNumber);
                        } else {
                            out.println(NOT_FOUND);
                        }
                    }
                    case GET_ALL -> {
                        out.println(START_OF_LIST);
                        for (Person person : PersonRegistry.getAllPersons()) {
                            out.println(person.serialize());
                        }
                        out.println(END_OF_LIST);
                    }
                    case SEARCH -> {
                        String name = in.readLine();
                        Person person = PersonRegistry.search(name);
                        if (person != null) {
                            out.println(SUCCESS);
                            out.println(person.serialize());
                        } else {
                            out.println(NOT_FOUND);
                        }
                    }
                    default -> out.println(UNKNOWN_FUNCTION);
                }
            }

            System.out.println("I'm ending...");
        } catch (IOException e) {
            System.out.println("Exception caught when trying to listen on port " + portNumber + " or listening for a connection");
            System.out.println(e.getMessage());
        }
    }
}
