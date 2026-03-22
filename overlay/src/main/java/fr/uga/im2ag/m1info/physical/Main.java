package fr.uga.im2ag.m1info.physical;

import java.nio.file.Path;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {
    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar distributed-ring-runnable.jar" + " <nodeId> <configFile> [rabbitHost]");
            System.exit(1);
        }

        int nodeId = Integer.parseInt(args[0]);
        Path configPath = Path.of(args[1]);
        String rabbitHost = args.length >= 3 ? args[2] : "localhost";

        NetworkConfig config;
        try {
            config = NetworkConfig.fromFile(configPath);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error while loading network configuration from file: " + configPath, e);
            System.exit(1);
            return;
        }

        config.validateNodeId(nodeId);
        List<Integer> neighbors = config.getNeighbors(nodeId);

        PhysicalNode node;
        try {
            node = new PhysicalNode(nodeId, neighbors, rabbitHost);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error while initializing node " + nodeId, e);
            System.exit(1);
            return;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(node::close));

        try {
            node.startListening(msg ->
                    System.out.printf("[Node %d] <- received from %d : '%s'%n", nodeId, msg.getSourceId(), msg.getPayload())
            );
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error while starting node " + nodeId, e);
            node.close();
            System.exit(1);
            return;
        }

        System.out.printf("=== Physical node %d started. Neighbors: %s ===%n", nodeId, neighbors);
        System.out.println("Commandes : 'send <dst> <message>'  |  'quit'");

        try (Scanner scanner = new Scanner(System.in)) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();

                if (line.equals("quit")) { break; }

                if (line.startsWith("send ")) {
                    String[] parts = line.split(" ", 3);
                    if (parts.length < 3) {
                        System.err.println("Usage : send <dstId> <message>");
                        continue;
                    }
                    try {
                        int dst = Integer.parseInt(parts[1]);
                        String payload = parts[2];
                        node.send(dst, payload);
                        System.out.printf("[Node %d] -> send to %d : '%s'%n", nodeId, dst, payload);
                    } catch (NumberFormatException e) {
                        System.err.println("dstId must be an integer");
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "An error occurred while trying to send message to node " + nodeId, e);
                    }
                } else if (!line.isEmpty()) {
                    System.err.println("Unknown command: " + line);
                }
            }
        }

        node.close();
        System.out.println("Node " + nodeId + " stopped.");
    }
}