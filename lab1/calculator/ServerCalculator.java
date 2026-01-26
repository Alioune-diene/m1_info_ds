


import java.net.*;
import java.io.*;



public class ServerCalculator implements IServerCalculator {
    public static void main(String[] args) throws IOException {
        
        if (args.length != 1) {
            System.err.println("Usage: java EchoServer <port number>");
            System.exit(1);
        }
        
        int portNumber = Integer.parseInt(args[0]);
        
        try (
            ServerSocket serverSocket =
                new ServerSocket(Integer.parseInt(args[0]));
            Socket clientSocket = serverSocket.accept();     
            PrintWriter out =
                new PrintWriter(clientSocket.getOutputStream(), true);                   
            DataInputStream in = new DataInputStream(
                clientSocket.getInputStream())
        ) {
            Integer op;
            
            while (( op = in.readInt()) != null) {
                  Integer a, b, result;    
                  a = in.readInt();
                  b = in.readInt();
                
                switch (op) {
                     case EOperationType.PLUS:
                        result = plus(a, b);
                         break;

                     case MINUS:
                         result = minus(a, b);
                         break;

                    case  DIVIDE:
                         result = divide(a, b);
                         break;

                    case MULTIPLY:
                         result = multiply(a, b);
                         break;
                 }
                out.println(result);
            }
            System.out.println("I'm ending...");
        } catch (IOException e) {
            System.out.println("Exception caught when trying to listen on port "
                + portNumber + " or listening for a connection");
            System.out.println(e.getMessage());
        }
    }


    public int plus(int a , int b) { return a + b; }
    public int minus (int a, int b) { return a - b; }
    public int divide(int a, int b) { return a / b; }
    public int multiply(int a, int b) { return a * b; }



}
