package fr.uga.im2ag.m1info.virtual;

import fr.uga.im2ag.m1info.physical.NetworkConfig;

import java.nio.file.Path;
import java.util.Scanner;
import java.util.logging.*;

public class VirtualMain {

    private static final Logger LOG = Logger.getLogger(VirtualMain.class.getName());

    public static void main(String[] args) {
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

        int numPhysical;
        try {
            numPhysical = NetworkConfig.fromFile(configPath).getNodeCount();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error while loading network configuration from file: " + configPath, e);
            System.exit(1);
            return;
        }

        VirtualNode vnode;
        try {
            vnode = new VirtualNode(virtualId, ringSize, numPhysical, rabbitHost);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error while initializing virtual node " + virtualId, e);
            System.exit(1);
            return;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(vnode::close));

        try {
            vnode.start((srcId, payload) -> System.out.printf("%n[V%d] <- V%d : \"%s\"%n> ", virtualId, srcId, payload));
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error while starting virtual node " + virtualId, e);
            vnode.close();
            System.exit(1);
            return;
        }

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
