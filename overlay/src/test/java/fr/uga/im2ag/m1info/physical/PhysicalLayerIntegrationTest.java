package fr.uga.im2ag.m1info.physical;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the physical layer (PhysicalNode + SpanningTreeManager).
 * <p>
 * These tests require a live RabbitMQ broker at localhost:5672 (admin/admin).
 * Run with: {@code mvn -pl overlay test -Dintegration.tests=true}
 */
@EnabledIfSystemProperty(named = "integration.tests", matches = "true")
class PhysicalLayerIntegrationTest {

    private static final String RABBIT_HOST = "localhost";

    /** All nodes opened during a test — closed in {@link #tearDown()}. */
    private final List<PhysicalNode> openNodes = new ArrayList<>();

    private PhysicalNode openNode(int id, List<Integer> neighbors) throws IOException, TimeoutException {
        PhysicalNode node = new PhysicalNode(id, neighbors, RABBIT_HOST);
        openNodes.add(node);
        return node;
    }

    @AfterEach
    void tearDown() {
        for (PhysicalNode n : openNodes) {
            try { n.close(); } catch (Exception ignored) {}
        }
        openNodes.clear();
    }

    // -------------------------------------------------------------------------
    // Basic connectivity
    // -------------------------------------------------------------------------

    @Test
    void physicalNode_shouldOpenWithoutError() throws IOException, TimeoutException {
        PhysicalNode node = openNode(200, List.of());
        assertNotNull(node, "PhysicalNode constructor must succeed when the broker is reachable");
        assertEquals(200, node.getId());
        assertTrue(node.getNeighbors().isEmpty());
    }

    @Test
    void sendToNeighbor_shouldDeliverEnvelopeToListeningNeighbor()
            throws IOException, TimeoutException, InterruptedException {

        PhysicalNode sender   = openNode(210, List.of(211));
        PhysicalNode receiver = openNode(211, List.of(210));

        CountDownLatch latch = new CountDownLatch(1);
        List<Envelope> received = new CopyOnWriteArrayList<>();

        receiver.startListening((env, from) -> {
            received.add(env);
            latch.countDown();
        });

        Envelope envelope = Envelope.data(210, 210, 211, "hello-neighbor", 0);
        sender.sendToNeighbor(211, envelope);

        boolean arrived = latch.await(5, TimeUnit.SECONDS);
        assertTrue(arrived, "Envelope must arrive at the receiver within 5 seconds");
        assertEquals(1, received.size());
        assertEquals("hello-neighbor", received.get(0).getPayload());
    }

    @Test
    void sendToNeighbor_shouldThrowWhenTargetIsNotANeighbor()
            throws IOException, TimeoutException {

        PhysicalNode node = openNode(220, List.of(221));

        assertThrows(IllegalArgumentException.class,
                () -> node.sendToNeighbor(999, Envelope.election(220, 220, 0)),
                "Sending to a non-neighbor must throw IllegalArgumentException");
    }

    // -------------------------------------------------------------------------
    // Broadcast
    // -------------------------------------------------------------------------

    @Test
    void broadcast_shouldDeliverEnvelopeToAllNeighbors()
            throws IOException, TimeoutException, InterruptedException {

        PhysicalNode sender = openNode(230, List.of(231, 232));
        PhysicalNode r1     = openNode(231, List.of(230));
        PhysicalNode r2     = openNode(232, List.of(230));

        CountDownLatch latch = new CountDownLatch(2);
        List<Envelope> received = new CopyOnWriteArrayList<>();

        r1.startListening((env, from) -> { received.add(env); latch.countDown(); });
        r2.startListening((env, from) -> { received.add(env); latch.countDown(); });

        sender.broadcast(Envelope.election(230, 230, 1));

        boolean arrived = latch.await(5, TimeUnit.SECONDS);
        assertTrue(arrived, "broadcast() must deliver to every neighbor within 5 seconds");
        assertEquals(2, received.size(), "Both neighbors must each receive exactly one message");
    }

    // -------------------------------------------------------------------------
    // Spanning-tree construction
    // -------------------------------------------------------------------------

    /**
     * Linear topology: 240 -- 241 -- 242.
     * All three nodes start SpanningTreeManager and the tree must converge to READY.
     */
    @Test
    void spanningTree_shouldConvergeToReadyOnThreeNodeLine()
            throws IOException, TimeoutException, InterruptedException {

        PhysicalNode n240 = openNode(240, List.of(241));
        PhysicalNode n241 = openNode(241, List.of(240, 242));
        PhysicalNode n242 = openNode(242, List.of(241));

        SpanningTreeManager stm240 = new SpanningTreeManager(240, List.of(241), n240);
        SpanningTreeManager stm241 = new SpanningTreeManager(241, List.of(240, 242), n241);
        SpanningTreeManager stm242 = new SpanningTreeManager(242, List.of(241), n242);

        n240.startListening((env, from) -> stm240.handleEnvelope(env, from));
        n241.startListening((env, from) -> stm241.handleEnvelope(env, from));
        n242.startListening((env, from) -> stm242.handleEnvelope(env, from));

        stm240.start(msg -> {});
        stm241.start(msg -> {});
        stm242.start(msg -> {});

        // Wait up to 15 seconds for all three to reach READY.
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            if (stm240.getPhase() == SpanningTreeManager.Phase.READY
                    && stm241.getPhase() == SpanningTreeManager.Phase.READY
                    && stm242.getPhase() == SpanningTreeManager.Phase.READY) {
                break;
            }
            Thread.sleep(300);
        }

        assertEquals(SpanningTreeManager.Phase.READY, stm240.getPhase(), "Node 240 must reach READY phase");
        assertEquals(SpanningTreeManager.Phase.READY, stm241.getPhase(), "Node 241 must reach READY phase");
        assertEquals(SpanningTreeManager.Phase.READY, stm242.getPhase(), "Node 242 must reach READY phase");
    }

    // -------------------------------------------------------------------------
    // Message delivery via spanning tree
    // -------------------------------------------------------------------------

    /**
     * Once the tree is READY, a unicast message from one leaf to another must
     * be routed through the root and delivered exactly once.
     */
    @Test
    void sendData_shouldDeliverUnicastMessageAcrossTree()
            throws IOException, TimeoutException, InterruptedException {

        PhysicalNode n250 = openNode(250, List.of(251));
        PhysicalNode n251 = openNode(251, List.of(250, 252));
        PhysicalNode n252 = openNode(252, List.of(251));

        List<Message> delivered = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        SpanningTreeManager stm250 = new SpanningTreeManager(250, List.of(251), n250);
        SpanningTreeManager stm251 = new SpanningTreeManager(251, List.of(250, 252), n251);
        SpanningTreeManager stm252 = new SpanningTreeManager(252, List.of(251), n252);

        n250.startListening((env, from) -> stm250.handleEnvelope(env, from));
        n251.startListening((env, from) -> stm251.handleEnvelope(env, from));
        n252.startListening((env, from) -> stm252.handleEnvelope(env, from));

        stm250.start(msg -> {});
        stm251.start(msg -> {});
        stm252.start(msg -> {
            if ("unicast-payload".equals(msg.getPayload())) {
                delivered.add(msg);
                latch.countDown();
            }
        });

        // Wait for READY
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            if (stm250.getPhase() == SpanningTreeManager.Phase.READY
                    && stm252.getPhase() == SpanningTreeManager.Phase.READY) break;
            Thread.sleep(300);
        }

        stm250.sendData(252, "unicast-payload");

        boolean arrived = latch.await(10, TimeUnit.SECONDS);
        assertTrue(arrived, "Unicast message from 250 to 252 must arrive within 10 s");
        assertEquals(252, delivered.get(0).getDestinationId());
        assertEquals(250, delivered.get(0).getSourceId());
    }

    /**
     * A broadcast with destination {@link SpanningTreeManager#BROADCAST_DEST} must
     * reach every node in the tree.
     */
    @Test
    void sendData_shouldDeliverBroadcastToAllNodes()
            throws IOException, TimeoutException, InterruptedException {

        PhysicalNode n260 = openNode(260, List.of(261));
        PhysicalNode n261 = openNode(261, List.of(260, 262));
        PhysicalNode n262 = openNode(262, List.of(261));

        CountDownLatch latch = new CountDownLatch(2); // 261 and 262 must each get it
        List<Message> delivered = new CopyOnWriteArrayList<>();

        SpanningTreeManager stm260 = new SpanningTreeManager(260, List.of(261), n260);
        SpanningTreeManager stm261 = new SpanningTreeManager(261, List.of(260, 262), n261);
        SpanningTreeManager stm262 = new SpanningTreeManager(262, List.of(261), n262);

        MessageHandler receiver = msg -> {
            if ("broadcast-payload".equals(msg.getPayload())) {
                delivered.add(msg);
                latch.countDown();
            }
        };

        n260.startListening((env, from) -> stm260.handleEnvelope(env, from));
        n261.startListening((env, from) -> stm261.handleEnvelope(env, from));
        n262.startListening((env, from) -> stm262.handleEnvelope(env, from));

        stm260.start(msg -> {});
        stm261.start(receiver);
        stm262.start(receiver);

        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            if (stm260.getPhase() == SpanningTreeManager.Phase.READY
                    && stm261.getPhase() == SpanningTreeManager.Phase.READY
                    && stm262.getPhase() == SpanningTreeManager.Phase.READY) break;
            Thread.sleep(300);
        }

        stm260.sendData(SpanningTreeManager.BROADCAST_DEST, "broadcast-payload");

        boolean arrived = latch.await(10, TimeUnit.SECONDS);
        assertTrue(arrived, "Broadcast must be received by every other node within 10 s");
    }

    // -------------------------------------------------------------------------
    // Node failure / topology rebuild
    // -------------------------------------------------------------------------

    /**
     * After a node is closed (simulating a crash), the remaining nodes must detect
     * the failure and trigger a topology rebuild (transition out of READY).
     * <p>
     * We only wait for the rebuild signal, not for full reconvergence to keep
     * the test duration predictable.
     */
    @Test
    void nodeFailure_shouldTriggerTopologyRebuildOnSurvivors()
            throws IOException, TimeoutException, InterruptedException {

        PhysicalNode n270 = openNode(270, List.of(271));
        PhysicalNode n271 = openNode(271, List.of(270, 272));
        PhysicalNode n272 = openNode(272, List.of(271));

        SpanningTreeManager stm270 = new SpanningTreeManager(270, List.of(271), n270);
        SpanningTreeManager stm271 = new SpanningTreeManager(271, List.of(270, 272), n271);
        SpanningTreeManager stm272 = new SpanningTreeManager(272, List.of(271), n272);

        n270.startListening((env, from) -> stm270.handleEnvelope(env, from));
        n271.startListening((env, from) -> stm271.handleEnvelope(env, from));
        n272.startListening((env, from) -> stm272.handleEnvelope(env, from));

        stm270.start(msg -> {});
        stm271.start(msg -> {});
        stm272.start(msg -> {});

        // Wait for READY
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            if (stm270.getPhase() == SpanningTreeManager.Phase.READY
                    && stm271.getPhase() == SpanningTreeManager.Phase.READY
                    && stm272.getPhase() == SpanningTreeManager.Phase.READY) break;
            Thread.sleep(300);
        }

        // Close node 272 to simulate a crash
        openNodes.remove(n272);
        n272.close();

        // Wait up to 20 seconds for the neighbour (271) to leave READY
        long failDeadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < failDeadline
                && stm271.getPhase() == SpanningTreeManager.Phase.READY) {
            Thread.sleep(500);
        }

        assertNotEquals(SpanningTreeManager.Phase.READY, stm271.getPhase(),
                "After its neighbor crashes, node 271 must leave READY and start a rebuild");
    }
}
