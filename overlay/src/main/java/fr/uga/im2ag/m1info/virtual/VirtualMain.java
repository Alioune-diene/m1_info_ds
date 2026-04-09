package fr.uga.im2ag.m1info.virtual;

import fr.uga.im2ag.m1info.physical.NetworkConfig;

import java.nio.file.Path;
import java.util.Scanner;
import java.util.logging.*;
/**
 * Entry point for running a virtual node.
 *
 * Each virtual node is a separate process that sits on TOP of the physical network.
 * Virtual nodes form a logical ring they only know about their left and right neighbors.
 * They don't know or care which physical nodes exist underneath.
 *
 * Available CLI commands:
 *  - "right <msg>"  → send message to the virtual node immediately to the right (V(id+1))
 *  - "left <msg>"   → send message to the virtual node immediately to the left (V(id-1))
 *  - "status"       → show current host and heartbeat timing info
 *  - "quit"         → shut down cleanly
 */
public class VirtualMain {

    private static final Logger LOG = Logger.getLogger(VirtualMain.class.getName());

    public static void main(String[] args) {
         //Step 1: Parse command-line arguments 
        if (args.length < 3) {
            System.err.println("Usage: virtual.VirtualMain <virtualId> <ringSize> <configFile> [rabbitHost]");
            System.exit(1);
        }

        int virtualId = Integer.parseInt(args[0]);
        int ringSize = Integer.parseInt(args[1]);
        Path configPath = Path.of(args[2]);
        String rabbitHost = args.length >= 4 ? args[3] : "localhost";

        if (ringSize < 2) { System.err.println("ringSize >= 2 required."); System.exit(1); }
        if (virtualId < 0 || virtualId >= ringSize) { System.err.println("virtualId out of bounds [0, ringSize)."); System.exit(1); }

        // Step 2: Find out how many physical nodes exist 

        // We need numPhysical to know which physical node to connect to initially
        // (initial host = virtualId % numPhysical) and to try alternatives during migration

        int numPhysical;
        try {
            numPhysical = NetworkConfig.fromFile(configPath).getNodeCount();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error while loading network configuration from file: " + configPath, e);
            System.exit(1);
            return;
        }
         //Step 3: Create and start the virtual node 

        VirtualNode vnode;
        try {
            vnode = new VirtualNode(virtualId, ringSize, numPhysical, rabbitHost);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error while initializing virtual node " + virtualId, e);
            System.exit(1);
            return;
        }
        // Register a shutdown hook to cleanly close RabbitMQ connections on Ctrl+C

        Runtime.getRuntime().addShutdownHook(new Thread(vnode::close));
         // Start the virtual node: begin listening, register with host, start heartbeats
        // The handler just prints incoming messages to the console

        try {
            vnode.start((srcId, payload) -> System.out.printf("%n[V%d] <- V%d : \"%s\"%n> ", virtualId, srcId, payload));
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error while starting virtual node " + virtualId, e);
            vnode.close();
            System.exit(1);
            return;
        }
         // Step 4: Interactive CLI 

        // Print a welcome banner showing ring neighbors

        System.out.printf("=== Virtual node %d / ring of %d  (hosted on P%d) ===%n", virtualId, ringSize, numPhysical);
        System.out.printf("    Neighbor : left=V%d  right=V%d%n", (virtualId - 1 + ringSize) % ringSize, (virtualId + 1) % ringSize);
        System.out.println("Commands : 'right <msg>'  |  'left <msg>'  |  'status'  |  'quit'");

        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("> ");
                if (!scanner.hasNextLine()) { break; }
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) { continue; }

                String cmd = line.split(" ", 2)[0];
                switch (cmd) {
                    case "quit" -> {
                        vnode.close();
                        return;
                    }
                    case "status" -> System.out.println(vnode.getStatus());
                    case "right" -> send(line, vnode, true);
                    case "left" -> send(line, vnode, false);
                    default -> System.err.println("Unknown command: " + cmd);
                }
            }
        }
    }

     /**
     * Parses a "right <msg>" or "left <msg>" command and sends the message.
     *
     * @param line  the full command line typed by the user
     * @param vnode the virtual node to send from
     * @param right true = sendRight, false = sendLeft
     */
    private static void send(String line, VirtualNode vnode, boolean right) {
        String[] parts = line.split(" ", 2);
        if (parts.length < 2) { System.err.println("Usage : " + parts[0] + " <message>"); return; }
        try {
            if (right) { vnode.sendRight(parts[1]); }
            else { vnode.sendLeft(parts[1]); }
        } catch (Exception e) {
            System.err.println("Error while sending message: " + e.getMessage());
        }
    }
}
