package fr.uga.im2ag.m1info.virtual;

import fr.uga.im2ag.m1info.physical.PhysicalNode;
import fr.uga.im2ag.m1info.physical.SpanningTreeManager;
import fr.uga.im2ag.m1info.physical.PhysicalHostService;
import org.junit.jupiter.api.AfterEach;
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
 * Integration tests for the virtual layer (VirtualNode + PhysicalHostService).
 * <p>
 * These tests require a live RabbitMQ broker at localhost:5672 (admin/admin).
 * Run with: {@code mvn -pl overlay test -Dintegration.tests=true}
 * <p>
 * Topology used in all tests unless noted:
 * <pre>
 *   P0 -- P1 -- P2   (physical line with spanning tree)
 *   V0 hosted on P0   V1 hosted on P1   V2 hosted on P2
 *   Virtual ring: V0 <-> V1 <-> V2   (ringSize=3, numPhysical=3)
 * </pre>
 * Physical IDs are 0,1,2 so that VirtualNode's host formula
 * (virtualId % numPhysical) maps directly to those IDs.
 */
@EnabledIfSystemProperty(named = "integration.tests", matches = "true")
class VirtualLayerIntegrationTest {

    private static final String RABBIT_HOST = "localhost";

    private final List<AutoCloseable> resources = new ArrayList<>();

    private PhysicalNode openPhysical(int id, List<Integer> neighbors)
            throws IOException, TimeoutException {
        PhysicalNode node = new PhysicalNode(id, neighbors, RABBIT_HOST);
        resources.add(node);
        return node;
    }

    private VirtualNode openVirtual(int virtualId, int ringSize, int numPhysical)
            throws IOException, TimeoutException {
        VirtualNode vn = new VirtualNode(virtualId, ringSize, numPhysical, RABBIT_HOST);
        resources.add(vn);
        return vn;
    }

    @AfterEach
    void tearDown() {
        for (int i = resources.size() - 1; i >= 0; i--) {
            try { resources.get(i).close(); } catch (Exception ignored) {}
        }
        resources.clear();
    }

    // -------------------------------------------------------------------------
    // Helper: 3-node physical spanning tree P0 -- P1 -- P2
    // -------------------------------------------------------------------------

    private record PhysicalLayer(
            PhysicalNode n0, PhysicalNode n1, PhysicalNode n2,
            SpanningTreeManager stm0, SpanningTreeManager stm1, SpanningTreeManager stm2) {}

    private PhysicalLayer buildPhysicalLayer() throws IOException, TimeoutException {
        PhysicalNode n0 = openPhysical(0, List.of(1));
        PhysicalNode n1 = openPhysical(1, List.of(0, 2));
        PhysicalNode n2 = openPhysical(2, List.of(1));

        SpanningTreeManager stm0 = new SpanningTreeManager(0, List.of(1), n0);
        SpanningTreeManager stm1 = new SpanningTreeManager(1, List.of(0, 2), n1);
        SpanningTreeManager stm2 = new SpanningTreeManager(2, List.of(1), n2);

        n0.startListening(stm0::handleEnvelope);
        n1.startListening(stm1::handleEnvelope);
        n2.startListening(stm2::handleEnvelope);

        stm0.start(msg -> {});
        stm1.start(msg -> {});
        stm2.start(msg -> {});

        return new PhysicalLayer(n0, n1, n2, stm0, stm1, stm2);
    }

    private void waitForPhysicalReady(PhysicalLayer layer) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            if (layer.stm0().getPhase() == SpanningTreeManager.Phase.READY
                    && layer.stm1().getPhase() == SpanningTreeManager.Phase.READY
                    && layer.stm2().getPhase() == SpanningTreeManager.Phase.READY) return;
            Thread.sleep(300);
        }
    }

    /**
     * Attach PhysicalHostServices using setHandler() so the spanning tree is
     * NOT restarted (unlike calling start() a second time).
     */
    private void attachHostServices(PhysicalLayer layer) throws IOException {
        PhysicalHostService svc0 = new PhysicalHostService(0, layer.stm0(), layer.n0().getConnection());
        PhysicalHostService svc1 = new PhysicalHostService(1, layer.stm1(), layer.n1().getConnection());
        PhysicalHostService svc2 = new PhysicalHostService(2, layer.stm2(), layer.n2().getConnection());
        layer.stm0().setHandler(svc0);
        layer.stm1().setHandler(svc1);
        layer.stm2().setHandler(svc2);
    }

    // -------------------------------------------------------------------------
    // VirtualNode lifecycle
    // -------------------------------------------------------------------------

    @Test
    void virtualNode_shouldOpenAndReportCorrectIds() {
        try (VirtualNode vn = openVirtual(1, 4, 3)) {
            assertEquals(1, vn.getVirtualId(), "Virtual ID must match constructor argument");
            assertEquals(4, vn.getRingSize(),  "Ring size must match constructor argument");
        } catch (IOException | TimeoutException e) {
            fail("VirtualNode should not throw for valid arguments: " + e.getMessage());
        }
    }

    @Test
    void virtualNode_shouldAssignInitialHostAsVirtualIdModNumPhysical() {
        try (VirtualNode vn = openVirtual(4, 6, 3)) {
            // virtualId=4 % numPhysical=3 → initial host=1
            assertEquals(1, vn.getCurrentHost(),
                    "Initial host must be virtualId mod numPhysical");
        } catch (IOException | TimeoutException e) {
            fail("VirtualNode should not throw for valid arguments: " + e.getMessage());
        }
    }

    @Test
    void virtualNode_shouldThrowOnOutOfBoundsVirtualId() {
        try (VirtualNode ignored = openVirtual(10, 4, 3)) {
            fail("virtualId >= ringSize should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        } catch (IOException | TimeoutException e) {
            fail("Unexpected exception type: " + e.getMessage());
        }
    }

    @Test
    void virtualNode_shouldThrowOnNegativeVirtualId() {
        try (VirtualNode ignored = openVirtual(-1, 4, 3)) {
            fail("Negative virtualId should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        } catch (IOException | TimeoutException e) {
            fail("Unexpected exception type: " + e.getMessage());
        }
    }

    @Test
    void getStatus_shouldReturnNonBlankString() {
        try (VirtualNode vn = openVirtual(2, 4, 3)) {
            assertNotNull(vn.getStatus());
            assertFalse(vn.getStatus().isBlank(), "getStatus() must return a non-blank string");
        } catch (IOException | TimeoutException e) {
            fail("VirtualNode should not throw for valid arguments: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Registration and heartbeat ACK
    // -------------------------------------------------------------------------

    @Test
    void virtualNode_shouldReceiveHeartbeatAckAfterRegistering()
            throws IOException, TimeoutException, InterruptedException {

        PhysicalLayer layer = buildPhysicalLayer();
        waitForPhysicalReady(layer);
        attachHostServices(layer);

        // V0 → host = 0 % 3 = 0 = P0
        VirtualNode vn = openVirtual(0, 3, 3);
        vn.start((from, payload) -> {});

        Thread.sleep(3_000);

        assertEquals(0, vn.getCurrentHost(),
                "Current host must remain P0 after successful registration and ACK");
    }

    // -------------------------------------------------------------------------
    // Virtual ring message delivery (sendRight / sendLeft)
    // -------------------------------------------------------------------------

    @Test
    void sendRight_shouldDeliverMessageToNextVirtualNode()
            throws IOException, TimeoutException, InterruptedException {

        PhysicalLayer layer = buildPhysicalLayer();
        waitForPhysicalReady(layer);
        attachHostServices(layer);

        List<String> received1 = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        // Ring: V0 (P0) →right→ V1 (P1) →right→ V2 (P2) →right→ V0
        VirtualNode v0 = openVirtual(0, 3, 3);
        VirtualNode v1 = openVirtual(1, 3, 3);

        v0.start((from, payload) -> {});
        v1.start((from, payload) -> {
            received1.add(payload);
            latch.countDown();
        });

        Thread.sleep(4_000);

        v0.sendRight("hello-right");

        boolean arrived = latch.await(10, TimeUnit.SECONDS);
        assertTrue(arrived, "sendRight() must deliver to the next virtual node within 10 s");
        assertTrue(received1.contains("hello-right"), "V1 must receive the payload sent right by V0");
    }

    @Test
    void sendLeft_shouldDeliverMessageToPreviousVirtualNode()
            throws IOException, TimeoutException, InterruptedException {

        PhysicalLayer layer = buildPhysicalLayer();
        waitForPhysicalReady(layer);
        attachHostServices(layer);

        List<String> received0 = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        VirtualNode v0 = openVirtual(0, 3, 3);
        VirtualNode v1 = openVirtual(1, 3, 3);

        v0.start((from, payload) -> {
            received0.add(payload);
            latch.countDown();
        });
        v1.start((from, payload) -> {});

        Thread.sleep(4_000);

        v1.sendLeft("hello-left");

        boolean arrived = latch.await(10, TimeUnit.SECONDS);
        assertTrue(arrived, "sendLeft() must deliver to the previous virtual node within 10 s");
        assertTrue(received0.contains("hello-left"), "V0 must receive the payload sent left by V1");
    }

    // -------------------------------------------------------------------------
    // VNode failure: migration to another physical host
    // -------------------------------------------------------------------------

    @Test
    void virtualNode_shouldMigrateToAlternativeHostWhenCurrentHostFails()
            throws IOException, TimeoutException, InterruptedException {

        // Minimal physical layer: P0 -- P1
        PhysicalNode n0 = openPhysical(0, List.of(1));
        PhysicalNode n1 = openPhysical(1, List.of(0));

        SpanningTreeManager stm0 = new SpanningTreeManager(0, List.of(1), n0);
        SpanningTreeManager stm1 = new SpanningTreeManager(1, List.of(0), n1);

        n0.startListening(stm0::handleEnvelope);
        n1.startListening(stm1::handleEnvelope);
        stm0.start(msg -> {});
        stm1.start(msg -> {});

        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            if (stm0.getPhase() == SpanningTreeManager.Phase.READY
                    && stm1.getPhase() == SpanningTreeManager.Phase.READY) break;
            Thread.sleep(300);
        }

        PhysicalHostService svc0 = new PhysicalHostService(0, stm0, n0.getConnection());
        PhysicalHostService svc1 = new PhysicalHostService(1, stm1, n1.getConnection());
        stm0.setHandler(svc0);
        stm1.setHandler(svc1);

        // V0 → host = 0 % 2 = 0 = P0
        VirtualNode v0 = openVirtual(0, 4, 2);
        v0.start((from, payload) -> {});

        Thread.sleep(4_000);

        int hostBefore = v0.getCurrentHost();
        assertEquals(0, hostBefore, "Initial host must be P0");

        resources.remove(n0);
        n0.close();

        long migrationDeadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < migrationDeadline && v0.getCurrentHost() == hostBefore) {
            Thread.sleep(500);
        }

        assertNotEquals(hostBefore, v0.getCurrentHost(),
                "Virtual node must migrate away from the failed host within 20 s");
    }

    // -------------------------------------------------------------------------
    // Message delivery after host migration
    // -------------------------------------------------------------------------

    @Test
    void afterMigration_sendRight_shouldStillDeliverMessages()
            throws IOException, TimeoutException, InterruptedException {

        PhysicalNode n0 = openPhysical(0, List.of(1));
        PhysicalNode n1 = openPhysical(1, List.of(0));

        SpanningTreeManager stm0 = new SpanningTreeManager(0, List.of(1), n0);
        SpanningTreeManager stm1 = new SpanningTreeManager(1, List.of(0), n1);

        n0.startListening(stm0::handleEnvelope);
        n1.startListening(stm1::handleEnvelope);
        stm0.start(msg -> {});
        stm1.start(msg -> {});

        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            if (stm0.getPhase() == SpanningTreeManager.Phase.READY
                    && stm1.getPhase() == SpanningTreeManager.Phase.READY) break;
            Thread.sleep(300);
        }

        PhysicalHostService svc0 = new PhysicalHostService(0, stm0, n0.getConnection());
        PhysicalHostService svc1 = new PhysicalHostService(1, stm1, n1.getConnection());
        stm0.setHandler(svc0);
        stm1.setHandler(svc1);

        // Ring: V0 (P0) →right→ V1 (P1)
        List<String> receivedBy1 = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        VirtualNode v0 = openVirtual(0, 2, 2);
        VirtualNode v1 = openVirtual(1, 2, 2);

        v0.start((from, payload) -> {});
        v1.start((from, payload) -> {
            receivedBy1.add(payload);
            latch.countDown();
        });

        Thread.sleep(4_000);

        v0.sendRight("pre-failure-msg");
        boolean preFailed = latch.await(8, TimeUnit.SECONDS);
        assertTrue(preFailed, "Baseline message must arrive before any host failure");

        resources.remove(n0);
        n0.close();

        long migrationDeadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < migrationDeadline && v0.getCurrentHost() == 0) {
            Thread.sleep(500);
        }

        CountDownLatch postLatch = new CountDownLatch(1);
        receivedBy1.clear();
        v1.start((from, payload) -> {
            if ("post-migration-msg".equals(payload)) {
                receivedBy1.add(payload);
                postLatch.countDown();
            }
        });

        v0.sendRight("post-migration-msg");

        boolean postArrived = postLatch.await(10, TimeUnit.SECONDS);
        assertTrue(postArrived,
                "sendRight must still deliver after the virtual node has migrated to a new host");
    }
}
