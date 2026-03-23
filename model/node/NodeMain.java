package fr.uga.im2ag.m1info.overlay.node;

import fr.uga.im2ag.m1info.overlay.graph.Graph;
import fr.uga.im2ag.m1info.overlay.graph.GraphLoader;
import fr.uga.im2ag.m1info.overlay.ring.Router;
import fr.uga.im2ag.m1info.overlay.ring.VirtualRing;

/**
 * Entry point for a single overlay node.
 *
 * Each node runs as its own independent JVM process.
 *
 * Usage:
 *   mvn exec:java -Dexec.mainClass=fr.uga.im2ag.m1info.overlay.node.NodeMain \
 *                 -Dexec.args="<nodeId> <graphFile>"
 *
 * Example (node 1, using the sample graph):
 *   mvn exec:java -Dexec.mainClass=fr.uga.im2ag.m1info.overlay.node.NodeMain \
 *                 -Dexec.args="1 src/main/resources/overlay/graph.txt"
 */
public class NodeMain {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: NodeMain <nodeId> <graphFile>");
            System.exit(1);
        }

        int nodeId = Integer.parseInt(args[0]);
        String graphFile = args[1];

        Graph graph = GraphLoader.load(graphFile);
        VirtualRing ring = new VirtualRing(graph); // simple choice: ring order = 1,2,...,n
        Router router = new Router(graph);

        OverlayNode node = new OverlayNode(nodeId, ring, router);
        node.start();

        // Keep the process alive to receive messages
        Thread.currentThread().join();
    }
}
