package fr.uga.im2ag.m1info.overlay.node;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import fr.uga.im2ag.m1info.overlay.messaging.Message;
import fr.uga.im2ag.m1info.overlay.messaging.MessageType;
import fr.uga.im2ag.m1info.overlay.ring.Router;
import fr.uga.im2ag.m1info.overlay.ring.VirtualRing;

import java.io.IOException;
import java.util.List;

/**
 * A single overlay node running as an independent JVM process.
 *
 * Each node:
 *  - listens on its own RabbitMQ queue: "node_<id>"
 *  - knows its ring neighbors (left and right)
 *  - knows the physical route to each ring neighbor (computed by Router)
 *  - can send messages left or right on the virtual ring via sendLeft/sendRight
 *  - forwards ROUTED messages hop-by-hop towards their destination
 */
public class OverlayNode {

    private final int nodeId;
    private final VirtualRing ring;
    private final Router router;

    private Channel channel;

    /** Queue name convention: each node listens on "node_<id>". */
    public static String queueName(int nodeId) {
        return "node_" + nodeId;
    }

    public OverlayNode(int nodeId, VirtualRing ring, Router router) {
        this.nodeId = nodeId;
        this.ring = ring;
        this.router = router;
    }

    public void start() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        factory.setUsername("admin");
        factory.setPassword("admin");

        Connection connection = factory.newConnection();
        channel = connection.createChannel();

        // Declare own queue
        channel.queueDeclare(queueName(nodeId), false, false, false, null);

        System.out.println("[Node " + nodeId + "] started. Ring: " + ring);
        System.out.println("[Node " + nodeId + "]   left  neighbor: " + ring.leftNeighbor(nodeId)
                + " via path " + router.shortestPath(nodeId, ring.leftNeighbor(nodeId)));
        System.out.println("[Node " + nodeId + "]   right neighbor: " + ring.rightNeighbor(nodeId)
                + " via path " + router.shortestPath(nodeId, ring.rightNeighbor(nodeId)));

        // Send HELLO to ring neighbors
        sendHello();

        // Start listening
        DeliverCallback onMessage = (tag, delivery) -> {
            Message msg = Message.fromBytes(delivery.getBody());
            handleMessage(msg);
        };
        channel.basicConsume(queueName(nodeId), true, onMessage, tag -> {});
    }

    // -------------------------------------------------------------------------
    // Public ring primitives (as defined in the lecture, slide 10)
    // -------------------------------------------------------------------------

    /** Send a message to the left ring neighbor, routed through the physical graph. */
    public void sendLeft(String payload) throws IOException {
        int dest = ring.leftNeighbor(nodeId);
        List<Integer> path = router.shortestPath(nodeId, dest);
        Message msg = new Message(MessageType.DATA, nodeId, dest, payload, path);
        System.out.println("[Node " + nodeId + "] sendLeft -> node " + dest + " path=" + path);
        forwardAlongPath(msg);
    }

    /** Send a message to the right ring neighbor, routed through the physical graph. */
    public void sendRight(String payload) throws IOException {
        int dest = ring.rightNeighbor(nodeId);
        List<Integer> path = router.shortestPath(nodeId, dest);
        Message msg = new Message(MessageType.DATA, nodeId, dest, payload, path);
        System.out.println("[Node " + nodeId + "] sendRight -> node " + dest + " path=" + path);
        forwardAlongPath(msg);
    }

    // -------------------------------------------------------------------------
    // Internal message handling
    // -------------------------------------------------------------------------

    private void handleMessage(Message msg) throws IOException {
        System.out.println("[Node " + nodeId + "] received " + msg);

        switch (msg.type) {
            case ROUTED, DATA -> handleRouted(msg);
            case HELLO        -> handleHello(msg);
            case HELLO_ACK    -> System.out.println("[Node " + nodeId + "] HELLO_ACK from node " + msg.srcNodeId);
        }
    }

    /**
     * If this node is the destination, deliver the message.
     * Otherwise, pop this node from the path and forward to the next hop.
     */
    private void handleRouted(Message msg) throws IOException {
        if (msg.dstNodeId == nodeId) {
            System.out.println("[Node " + nodeId + "] DELIVERED: '" + msg.payload
                    + "' from node " + msg.srcNodeId);
        } else {
            // Remove the current node from the front of the path and forward
            msg.path.remove(0);
            forwardAlongPath(msg);
        }
    }

    private void handleHello(Message msg) throws IOException {
        System.out.println("[Node " + nodeId + "] HELLO from node " + msg.srcNodeId);
        // Reply with HELLO_ACK
        List<Integer> ackPath = router.shortestPath(nodeId, msg.srcNodeId);
        Message ack = new Message(MessageType.HELLO_ACK, nodeId, msg.srcNodeId, "", ackPath);
        forwardAlongPath(ack);
    }

    private void sendHello() throws IOException {
        for (int neighbor : List.of(ring.leftNeighbor(nodeId), ring.rightNeighbor(nodeId))) {
            if (neighbor == nodeId) continue; // single-node edge case
            List<Integer> path = router.shortestPath(nodeId, neighbor);
            Message hello = new Message(MessageType.HELLO, nodeId, neighbor, "", path);
            forwardAlongPath(hello);
        }
    }

    /**
     * Sends the message to the next physical hop in msg.path (index 1, since index 0 is current node).
     */
    private void forwardAlongPath(Message msg) throws IOException {
        if (msg.path.size() < 2) {
            System.err.println("[Node " + nodeId + "] ERROR: cannot forward, path too short: " + msg.path);
            return;
        }
        int nextHop = msg.path.get(1);
        channel.basicPublish("", queueName(nextHop), null, msg.toBytes());
    }
}
