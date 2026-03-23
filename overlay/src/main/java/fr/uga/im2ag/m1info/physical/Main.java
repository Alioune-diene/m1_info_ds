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

        SpanningTreeManager manager = new SpanningTreeManager(nodeId, neighbors, node);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down node " + nodeId + "...");
            node.close();
        }));

        try {
            node.startListening(manager::handleEnvelope);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error while starting node " + nodeId, e);
            node.close();
            System.exit(1);
            return;
        }

        manager.start(msg -> System.out.printf("%n[Node %d] Received message from %d: \"%s\"%n> ", nodeId, msg.getSourceId(), msg.getPayload()));

        System.out.printf("=== Physical node %d started. Neighbors: %s ===%n", nodeId, neighbors);
        System.out.println("Commandes : 'send <dst> <message>'  |  'status'  |  'quit'");

        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("> ");
                if (!scanner.hasNextLine()) { break; }
                String line = scanner.nextLine().trim();

                switch (line) {
                    case "quit" -> { return; }
                    case "status" -> System.out.println("[Node " + nodeId + "] " + manager.getStatusSummary());
                    default -> {
                        if (line.startsWith("send ")) {
                            handleSendCommand(line, nodeId, manager);
                        } else if (!line.isEmpty()) {
                            System.err.println("Unknown command. Available commands: 'send <dst> <message>', 'status', 'quit'");
                        }
                    }
                }
            }
        }

        node.close();
    }

    private static void handleSendCommand(String line, int nodeId, SpanningTreeManager manager) {
        String[] parts = line.split(" ", 3);
        if (parts.length < 3) { System.err.println("Usage : send <dstId> <message>"); return; }
        try {
            int dst = Integer.parseInt(parts[1]);
            String payload = parts[2];

            SpanningTreeManager.Phase phase = manager.getPhase();
            if (phase != SpanningTreeManager.Phase.READY) {
                System.out.printf("[Node %d] Warning: Spanning tree is not ready (%s), message may not be delivered.%n", nodeId, phase);
            }

            manager.sendData(dst, payload);
            System.out.printf("[Node %d] -> send to %d : '%s'%n", nodeId, dst, payload);
        } catch (NumberFormatException e) {
            System.err.println("dstId must be an integer");
        }
    }
}