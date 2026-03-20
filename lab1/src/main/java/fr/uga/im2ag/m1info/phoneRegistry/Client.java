package fr.uga.im2ag.m1info.phoneRegistry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;

public class Client {
    public static void main(String[] args) throws IOException {

        if (args.length != 2) {
            System.err.println("Usage: java RegistryClient <host name> <port number>");
            System.exit(1);
        }

        String hostName = args[0];
        int portNumber = Integer.parseInt(args[1]);

        try (
            Socket echoSocket = new Socket(hostName, portNumber);
            PrintWriter out = new PrintWriter(echoSocket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(echoSocket.getInputStream()));
            BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in))
        ) {

            String userInput;
            printMenu();
            while ((userInput = stdIn.readLine()) != null) {
                EFunction function = EFunction.values()[Integer.parseInt(userInput) - 1];
                out.println(function.ordinal());
                switch (function) {
                    case ADD -> {
                        System.out.println("Enter first name:");
                        String firstName = stdIn.readLine();
                        System.out.println("Enter name:");
                        String name = stdIn.readLine();
                        System.out.println("Enter phone number:");
                        String phoneNumber = stdIn.readLine();
                        out.println(firstName);
                        out.println(name);
                        out.println(phoneNumber);
                        System.out.println("Response: " + in.readLine());
                    }
                    case GET_PHONE -> {
                        System.out.println("Enter name:");
                        String name = stdIn.readLine();
                        out.println(name);
                        String response = in.readLine();
                        if (response.equals(EResult.SUCCESS.name())) {
                            String phone = in.readLine();
                            System.out.println("Phone number: " + phone);
                        } else {
                            System.out.println("Person not found.");
                        }
                    }
                    case GET_ALL -> {
                        String line;
                        while (!(line = in.readLine()).equals(EResult.END_OF_LIST.name())) {
                            if (!line.equals(EResult.START_OF_LIST.name())) {
                                Person person = Person.deserialize(line);
                                System.out.println("Person: " + person.getFirstName() + " " + person.getName() + ", Phone: " + person.getPhoneNumber());
                            }
                        }
                    }
                    case SEARCH -> {
                        System.out.println("Enter name:");
                        String name = stdIn.readLine();
                        out.println(name);
                        String response = in.readLine();
                        if (response.equals(EResult.SUCCESS.name())) {
                            String personData = in.readLine();
                            Person person = Person.deserialize(personData);
                            System.out.println("Person found: " + person.getFirstName() + " " + person.getName() + ", Phone: " + person.getPhoneNumber());
                        } else {
                            System.out.println("Person not found.");
                        }
                    }
                    case NULL -> {
                        System.out.println("Exiting...");
                        return;
                    }
                }

                printMenu();
            }

        } catch (UnknownHostException e) {
            System.err.println("Don't know about host " + hostName);
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Couldn't get I/O for the connection to " + hostName);
            System.exit(1);
        }
    }

    static void printMenu() {
        System.out.println("Choose an option:");
        System.out.println("1. Add Person");
        System.out.println("2. Get Phone Number");
        System.out.println("3. Get All Persons");
        System.out.println("4. Search Person");
        System.out.println("5. Exit");
    }
}
