package fr.uga.im2ag.m1info.lab1.calculator;

import java.net.*;
import java.io.*;

public class ServerCalculator implements IServerCalculator {

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java ServerCalculator <port number>");
            System.exit(1);
        }

        int portNumber = Integer.parseInt(args[0]);
        ServerCalculator calculator = new ServerCalculator();

        try (
            ServerSocket serverSocket = new ServerSocket(portNumber);
            Socket clientSocket = serverSocket.accept();
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream());
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        ) {
            System.out.println("Client connected!");

            while (true) {
                try {
                    System.out.println("Waiting for operation...");
                    int op = Integer.parseInt(in.readLine());
                    int a = Integer.parseInt(in.readLine());
                    int b = Integer.parseInt(in.readLine());
                    int result;
                    System.out.printf("Received operation %d with operands %d and %d%n", op, a, b);

                    EOperationType operation = EOperationType.values()[op];

                    result = switch (operation) {
                        case PLUS -> calculator.plus(a, b);
                        case MINUS -> calculator.minus(a, b);
                        case DIVIDE -> calculator.divide(a, b);
                        case MULTIPLY -> calculator.multiply(a, b);
                        default -> 0;
                    };

                    out.println(result);
                    out.flush();

                    System.out.printf("Sent result: %d%n", result);
                } catch (EOFException e) {
                    break;
                }
            }
            System.out.println("Client disconnected.");

        } catch (IOException e) {
            System.out.println("Exception caught when trying to listen on port "
                    + portNumber + " or listening for a connection");
            System.out.println(e.getMessage());
        }
    }

    @Override
    public int plus(int a, int b) {
        return a + b;
    }

    @Override
    public int minus(int a, int b) {
        return a - b;
    }

    @Override
    public int divide(int a, int b) {
        return a / b;
    }

    @Override
    public int multiply(int a, int b) {
        return a * b;
    }
}
