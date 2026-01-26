package fr.uga.im2ag.m1info.lab1.calculator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;

public class ClientCalculator {
    public static void main(String[] args) throws IOException {

        if (args.length != 2) {
            System.err.println("Usage: java EchoClient <host name> <port number>");
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
            System.out.println("Enter operation (+, -, *, /) followed by two integers:");
            while ((userInput = stdIn.readLine()) != null) {
                String[] inputs = userInput.split(" ");
                if (inputs.length != 3) {
                    System.out.println("Invalid input. Please enter an operation followed by two integers.");
                    continue;
                }
                EOperationType operation;
                switch (inputs[0]) {
                    case "+" -> operation = EOperationType.PLUS;
                    case "-" -> operation = EOperationType.MINUS;
                    case "*" -> operation = EOperationType.MULTIPLY;
                    case "/" -> operation = EOperationType.DIVIDE;
                    default -> {
                        System.out.println("Unknown operation. Please use +, -, *, or /.");
                        continue;
                    }
                }

                int a = Integer.parseInt(inputs[1]);
                int b = Integer.parseInt(inputs[2]);

                out.println(operation.ordinal());
                out.println(a);
                out.println(b);
                System.out.println("Sent operation " + operation.ordinal() + " with operands " + a + " and " + b);

                int result = Integer.parseInt(in.readLine());
                System.out.println("Received result: " + result);

                System.out.println("Enter operation (+, -, *, /) followed by two integers:");
            }
        } catch (UnknownHostException e) {
            System.err.println("Don't know about host " + hostName);
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Couldn't get I/O for the connection to " + hostName);
            System.exit(1);
        }
    }
}
