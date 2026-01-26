
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
            DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());
            DataInputStream in = new DataInputStream(clientSocket.getInputStream())
        ) {
            System.out.println("Client connected!");

            while (true) {
                try {
                    int op = in.readInt();
                    int a = in.readInt();
                    int b = in.readInt();
                    int result;

                    EOperationType operation = EOperationType.values()[op];

                    switch (operation) {
                        case PLUS:
                            result = calculator.plus(a, b);
                            break;
                        case MINUS:
                            result = calculator.minus(a, b);
                            break;
                        case DIVIDE:
                            result = calculator.divide(a, b);
                            break;
                        case MULTIPLY:
                            result = calculator.multiply(a, b);
                            break;
                        default:
                            result = 0;
                    }

                    out.writeInt(result);
                    out.flush();

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
    public int plus(int a, int b) { return a + b; }

    @Override
    public int minus(int a, int b) { return a - b; }

    @Override
    public int divide(int a, int b) { return a / b; }

    @Override
    public int multiply(int a, int b) { return a * b; }
}
