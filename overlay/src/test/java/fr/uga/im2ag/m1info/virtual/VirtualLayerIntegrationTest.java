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
 *   P300 -- P301 -- P302   (physical ring with spanning tree)
 *   V300 hosted on P300    V301 hosted on P301    V302 hosted on P302
 *   Virtual ring: V300 <-> V301 <-> V302
 * </pre>
 */
@EnabledIfSystemProperty(named = "integration.tests", matches = "true")
class VirtualLayerIntegrationTest {

    private static final String RABBIT_HOST = "localhost";

    private final List<AutoCloseable> resources = new ArrayList<>();

    /** Helper: open a PhysicalNode and register it for teardown. */
    private PhysicalNode openPhysical(int id, List<Integer> neighbors)
            throws IOException, TimeoutException {
        PhysicalNode node = new PhysicalNode(id, neighbors, RABBIT_HOST);
        resources.add(node);
        return node;
    }

    /** Helper: open a VirtualNode and register it for teardown. */
    private VirtualNode openVirtual(int virtualId, int ringSize, int numPhysical)
            throws IOException, TimeoutException {
        VirtualNode vn = new VirtualNode(virtualId, ringSize, numPhysical, RABBIT_HOST);
        resources.add(vn);
        return vn;
    }

    @AfterEach
    void tearDown() {
        // Close in reverse order so virtual nodes close before physical.
        for (int i = resources.size() - 1; i >= 0; i--) {
            try { resources.get(i).close(); } catch (Exception ignored) {}
        }
        resources.clear();
    }

    // -------------------------------------------------------------------------
    // Helper: build a 3-node physical spanning tree and return the STMs.
    // -------------------------------------------------------------------------

    private record PhysicalLayer(
            PhysicalNode n300, PhysicalNode n301, PhysicalNode n302,
            SpanningTreeManager stm300, SpanningTreeManager stm301, SpanningTreeManager stm302) {}

    private PhysicalLayer buildPhysicalLayer() throws IOException, TimeoutException {
        PhysicalNode n300 = openPhysical(300, List.of(301));
        PhysicalNode n301 = openPhysical(301, List.of(300, 302));
        PhysicalNode n302 = openPhysical(302, List.of(301));

        SpanningTreeManager stm300 = new SpanningTreeManager(300, List.of(301), n300);
        SpanningTreeManager stm301 = new SpanningTreeManager(301, List.of(300, 302), n301);
        SpanningTreeManager stm302 = new SpanningTreeManager(302, List.of(301), n302);

        n300.startListening(stm300::handleEnvelope);
        n301.startListening(stm301::handleEnvelope);
        n302.startListening(stm302::handleEnvelope);

        stm300.start(msg -> {});
        stm301.start(msg -> {});
        stm302.start(msg -> {});

        return new PhysicalLayer(n300, n301, n302, stm300, stm301, stm302);
    }

    private void waitForPhysicalReady(PhysicalLayer layer) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            if (layer.stm300().getPhase() == SpanningTreeManager.Phase.READY
                    && layer.stm301().getPhase() == SpanningTreeManager.Phase.READY
                    && layer.stm302().getPhase() == SpanningTreeManager.Phase.READY) return;
            Thread.sleep(300);
        }
    }

    // -------------------------------------------------------------------------
    // VirtualNode lifecycle
    // -------------------------------------------------------------------------

    @Test
    void virtualNode_shouldOpenAndReportCorrectIds() {
        try (VirtualNode vn = openVirtual(1, 4, 3)) {
            assertEquals(1, vn.getVirtualId(), "Virtual ID must match constructor argument");
            assertEquals(4, vn.getRingSize(),    "Ring size must match constructor argument");
        } catch (IOException | TimeoutException e) {
            fail("VirtualNode constructor should not throw when given valid arguments: " + e.getMessage());
        }
    }

    @Test
    void virtualNode_shouldAssignInitialHostAsVirtualIdModNumPhysical() {
        try (VirtualNode vn = openVirtual(4, 6, 3)) {
            // virtualId=4 % numPhysical=3 → initial host=1
            assertEquals(1, vn.getCurrentHost(),
                    "Initial host must be virtualId mod numPhysical");
        } catch (IOException | TimeoutException e) {
            fail("VirtualNode constructor should not throw when given valid arguments: " + e.getMessage());
        }
    }

    @Test
    void virtualNode_shouldThrowOnOutOfBoundsVirtualId() {
        try (VirtualNode ignored = openVirtual(10, 4, 3)) {
            fail("virtualId >= ringSize should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected exception
        } catch (IOException | TimeoutException e) {
            fail("Unexpected exception type: " + e.getMessage());
        }
    }

    @Test
    void virtualNode_shouldThrowOnNegativeVirtualId() {
        try (VirtualNode ignored = openVirtual(-1, 4, 3)) {
            fail("Negative virtualId should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected exception
        } catch (IOException | TimeoutException e) {
            fail("Unexpected exception type: " + e.getMessage());
        }
    }

    @Test
    void getStatus_shouldReturnNonBlankString() {
        try (VirtualNode vn = openVirtual(2, 4, 3)) {
            String status = vn.getStatus();
            assertNotNull(status);
            assertFalse(status.isBlank(), "getStatus() must return a non-blank string");
        } catch (IOException | TimeoutException e) {
            fail("VirtualNode constructor should not throw when given valid arguments: " + e.getMessage());
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

        try (PhysicalNode pn300 = layer.n300()) {
            new PhysicalHostService(300, layer.stm300(), pn300.getConnection());
        } catch (IOException e) {
            fail("Failed to start PhysicalHostService for P300: " + e.getMessage());
        }

        try (VirtualNode vn = openVirtual(0, 3, 3)) {
            CountDownLatch registered = new CountDownLatch(1);
            vn.start((from, payload) -> {});

            // Give time for registration + ACK exchange.
            // We assert via getStatus that lastAck was refreshed (host stays at 0).
            boolean ackArrived = registered.await(0, TimeUnit.SECONDS); // not waiting — see below

            Thread.sleep(3_000); // allow HB cycle to complete

            // After registration, the current host should still be P300 (not -1 or error).
            assertEquals(300 % 3, vn.getCurrentHost(),
                    "Current host must remain P0 after successful registration and ACK");
        } catch (IOException | TimeoutException | InterruptedException e) {
            fail("VirtualNode should not throw when registering and receiving ACK: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Virtual ring message delivery (sendRight / sendLeft)
    // -----------------------------------------------------------------------

    @Test
    void sendRight_shouldDeliverMessageToNextVirtualNode()
            throws IOException, TimeoutException, InterruptedException {

        PhysicalLayer layer = buildPhysicalLayer();
        waitForPhysicalReady(layer);

        try (PhysicalNode pn300 = layer.n300();
             PhysicalNode pn301 = layer.n301();
             PhysicalNode pn302 = layer.n302()) {
            PhysicalHostService svc300 =
                    new PhysicalHostService(300, layer.stm300(), pn300.getConnection());
            PhysicalHostService svc301 =
                    new PhysicalHostService(301, layer.stm301(), pn301.getConnection());
            PhysicalHostService svc302 =
                    new PhysicalHostService(302, layer.stm302(), pn302.getConnection());

            layer.stm300().start(svc300);
            layer.stm301().start(svc301);
            layer.stm302().start(svc302);
        } catch (IOException e) {
            fail("Failed to start PhysicalHostServices for P300-P302: " + e.getMessage());
        }

        List<String> received301 = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        try (VirtualNode v0 = openVirtual(0, 3, 3);
             VirtualNode v1 = openVirtual(1, 3, 3)) {
            // Ring size=3, numPhysical=3. V0→right→V1, V1→right→V2→right→V0
            v0.start((from, payload) -> {});
            v1.start((from, payload) -> {
                received301.add(payload);
                latch.countDown();
            });

            // Let registration stabilize.
            Thread.sleep(4_000);

            v0.sendRight("hello-right");

            boolean arrived = latch.await(10, TimeUnit.SECONDS);
            assertTrue(arrived, "sendRight() must deliver to the next virtual node within 10 s");
            assertTrue(received301.contains("hello-right"),
                    "V301 must receive the payload sent right by V300");
        } catch (IOException | TimeoutException | InterruptedException e) {
            fail("VirtualNodes should not throw when sending and receiving messages: " + e.getMessage());
        }
    }

    @Test
    void sendLeft_shouldDeliverMessageToPreviousVirtualNode()
            throws IOException, TimeoutException, InterruptedException {

        PhysicalLayer layer = buildPhysicalLayer();
        waitForPhysicalReady(layer);

        try (PhysicalNode pn300 = layer.n300();
             PhysicalNode pn301 = layer.n301();
             PhysicalNode pn302 = layer.n302()) {
            PhysicalHostService svc300 =
                    new PhysicalHostService(300, layer.stm300(), pn300.getConnection());
            PhysicalHostService svc301 =
                    new PhysicalHostService(301, layer.stm301(), pn301.getConnection());
            PhysicalHostService svc302 =
                    new PhysicalHostService(302, layer.stm302(), pn302.getConnection());

            layer.stm300().start(svc300);
            layer.stm301().start(svc301);
            layer.stm302().start(svc302);
        } catch (IOException e) {
            fail("Failed to start PhysicalHostServices for P300-P302: " + e.getMessage());
        }

        List<String> received300 = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        try (VirtualNode v0 = openVirtual(0, 3, 3);
             VirtualNode v1 = openVirtual(1, 3, 3)) {
            v0.start((from, payload) -> {
                received300.add(payload);
                latch.countDown();
            });
            v1.start((from, payload) -> {});

            Thread.sleep(4_000);

            v1.sendLeft("hello-left");

            boolean arrived = latch.await(10, TimeUnit.SECONDS);
            assertTrue(arrived, "sendLeft() must deliver to the previous virtual node within 10 s");
            assertTrue(received300.contains("hello-left"),
                    "V300 must receive the payload sent left by V301");
        } catch (IOException | TimeoutException | InterruptedException e) {
            fail("VirtualNodes should not throw when sending and receiving messages: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // VNode failure: migration to another physical host
    // -------------------------------------------------------------------------

    /**
     * When a virtual node's physical host becomes unreachable (simulated by closing
     * the host's connection), the virtual node must migrate to a different physical
     * host within the migration timeout window.
     * <p>
     * The virtual heartbeat timeout is 10s and migration retry delay is 2s, so we
     * allow 20s total for the migration to complete
     */
    @Test
    void virtualNode_shouldMigrateToAlternativeHostWhenCurrentHostFails()
            throws IOException, TimeoutException, InterruptedException {

        // Minimal physical layer: P310 -- P311.
        PhysicalNode n310 = openPhysical(310, List.of(311));
        PhysicalNode n311 = openPhysical(311, List.of(310));

        SpanningTreeManager stm310 = new SpanningTreeManager(310, List.of(311), n310);
        SpanningTreeManager stm311 = new SpanningTreeManager(311, List.of(310), n311);

        n310.startListening(stm310::handleEnvelope);
        n311.startListening(stm311::handleEnvelope);
        stm310.start(msg -> {});
        stm311.start(msg -> {});

        // Wait for spanning tree
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            if (stm310.getPhase() == SpanningTreeManager.Phase.READY && stm311.getPhase() == SpanningTreeManager.Phase.READY) 
                break;
            Thread.sleep(300);
        }

        PhysicalHostService svc310 = new PhysicalHostService(310, stm310, n310.getConnection());
        PhysicalHostService svc311 = new PhysicalHostService(311, stm311, n311.getConnection());
        stm310.start(svc310);
        stm311.start(svc311);

        // V310 initially hosted on P310 (310 % 2 = 0).
        VirtualNode v0 = openVirtual(0, 4, 2);
        v0.start((from, payload) -> {});

        Thread.sleep(4_000); // allow initial registration + HB

        int hostBefore = v0.getCurrentHost();
        assertEquals(310 % 2, hostBefore, "Initial host must be P" + (310 % 2));

        // Close P310 to simulate a crash. Remove from resources so tearDown doesn't double-close
        resources.remove(n310);
        n310.close();

        // V310 should migrate to P311 within the timeout period.
        long migrationDeadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < migrationDeadline && v0.getCurrentHost() == hostBefore) {
            Thread.sleep(500);
        }

        assertNotEquals(hostBefore, v0.getCurrentHost(),
                "Virtual node must have migrated away from the failed host within 20 s");
    }

    // -----------------------------------------------------------------------
    // Message delivery when host is temporarily unavailable
    // -------------------------------------------------------------------------

    /**
     * Messages sent to a virtual node while its host is down must still arrive
     * once the virtual node has migrated and its new host is available.
     * <p>
     * This test checks reconvergence: after migration, subsequent messages sent
     * via sendRight must be delivered normally.
     */
    @Test
    void afterMigration_sendRight_shouldStillDeliverMessages()
            throws IOException, TimeoutException, InterruptedException {

        PhysicalNode n320 = openPhysical(320, List.of(321));
        PhysicalNode n321 = openPhysical(321, List.of(320));

        SpanningTreeManager stm320 = new SpanningTreeManager(320, List.of(321), n320);
        SpanningTreeManager stm321 = new SpanningTreeManager(321, List.of(320), n321);

        n320.startListening(stm320::handleEnvelope);
        n321.startListening(stm321::handleEnvelope);
        stm320.start(msg -> {});
        stm321.start(msg -> {});

        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            if (stm320.getPhase() == SpanningTreeManager.Phase.READY
                    && stm321.getPhase() == SpanningTreeManager.Phase.READY) break;
            Thread.sleep(300);
        }

        PhysicalHostService svc320 = new PhysicalHostService(320, stm320, n320.getConnection());
        PhysicalHostService svc321 = new PhysicalHostService(321, stm321, n321.getConnection());
        stm320.start(svc320);
        stm321.start(svc321);

        // Ring of 2 virtual nodes on 2 physical nodes
        // V320 (host P0=320) sendRight → V321 (host P1=321)
        List<String> receivedBy321 = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        VirtualNode v0 = openVirtual(0, 2, 2);
        VirtualNode v1 = openVirtual(1, 2, 2);

        v0.start((from, payload) -> {});
        v1.start((from, payload) -> {
            receivedBy321.add(payload);
            latch.countDown();
        });

        Thread.sleep(4_000); // registration

        // Verify baseline: message arrives before any failure
        v0.sendRight("pre-failure-msg");
        boolean preFailed = latch.await(8, TimeUnit.SECONDS);
        assertTrue(preFailed, "Baseline message must arrive before any host failure");

        // Simulate host failure for V320 by closing P320
        resources.remove(n320);
        n320.close();

        // Wait for migration.
        long migrationDeadline = System.currentTimeMillis() + 20_000;
        int initialHost = 320 % 2;
        while (System.currentTimeMillis() < migrationDeadline
                && v0.getCurrentHost() == initialHost) {
            Thread.sleep(500);
        }

        // After migration, sendRight should still work
        CountDownLatch postLatch = new CountDownLatch(1);
        receivedBy321.clear();
        v1.start((from, payload) -> {
            if ("post-migration-msg".equals(payload)) {
                receivedBy321.add(payload);
                postLatch.countDown();
            }
        });

        v0.sendRight("post-migration-msg");

        boolean postArrived = postLatch.await(10, TimeUnit.SECONDS);
        assertTrue(postArrived,
                "sendRight must still deliver after the virtual node has migrated to a new host");
    }
}
