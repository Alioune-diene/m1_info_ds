package fr.uga.im2ag.m1info.physical;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SpanningTreeManager}.
 * <p>
 * All tests are purely in-memory. {@link PhysicalNode} is always obtained via
 * {@code Mockito.mock(PhysicalNode.class)} so that no real AMQP connection is opened.
 */
class SpanningTreeManagerUnitTest {

    private PhysicalNode transport;

    @BeforeEach
    void setUp() {
        transport = mock(PhysicalNode.class);
    }

    // -------------------------------------------------------------------------
    // Initial state
    // -------------------------------------------------------------------------

    @Test
    void initialState_shouldBeElectingPhase() {
        SpanningTreeManager stm = new SpanningTreeManager(5, List.of(1, 2), transport);

        assertEquals(SpanningTreeManager.Phase.ELECTING, stm.getPhase(),
                "A newly created manager must start in ELECTING phase");
    }

    @Test
    void initialState_bestRootShouldBeOwnId() {
        SpanningTreeManager stm = new SpanningTreeManager(3, List.of(0, 1), transport);

        assertEquals(3, stm.getBestRootId(),
                "Best root candidate should default to own node ID before any election message arrives");
    }

    @Test
    void initialState_parentShouldBeMinusOne() {
        SpanningTreeManager stm = new SpanningTreeManager(2, List.of(), transport);

        assertEquals(-1, stm.getParentId(),
                "Parent should be -1 (no parent) before tree is built");
    }

    @Test
    void initialState_childrenShouldBeEmpty() {
        SpanningTreeManager stm = new SpanningTreeManager(0, List.of(), transport);

        assertTrue(stm.getChildren().isEmpty(),
                "Children set must be empty before tree construction");
    }

    // -------------------------------------------------------------------------
    // Election message handling
    // -------------------------------------------------------------------------

    @Test
    void onElection_shouldUpdateBestRootWhenCandidateHasLowerId() {
        // Node 5 : candidate 2 has a lower ID so 2 should win.
        SpanningTreeManager stm = new SpanningTreeManager(5, List.of(1, 2, 3), transport);

        stm.handleEnvelope(Envelope.election(1, 2, 0), 1);

        assertEquals(2, stm.getBestRootId(),
                "A lower-ID candidate must replace the current best root");
    }

    @Test
    void onElection_shouldNotUpdateBestRootWhenCandidateHasHigherId() {
        SpanningTreeManager stm = new SpanningTreeManager(1, List.of(5, 6), transport);

        stm.handleEnvelope(Envelope.election(5, 9, 0), 5);

        assertEquals(1, stm.getBestRootId(),
                "A higher-ID candidate must not replace a lower best root");
    }

    @Test
    void onElection_shouldForwardLowerCandidateToOtherNeighbors() throws IOException {
        // Node 5, neighbors [1, 2]. Election arrives from neighbor 1.
        SpanningTreeManager stm = new SpanningTreeManager(5, List.of(1, 2), transport);

        stm.handleEnvelope(Envelope.election(1, 0, 0), 1);

        // Must forward to neighbor 2 (not back to 1).
        ArgumentCaptor<Envelope> captor = ArgumentCaptor.forClass(Envelope.class);
        verify(transport, atLeastOnce()).sendToNeighbor(eq(2), captor.capture());

        boolean electionForwarded = captor.getAllValues().stream()
                .anyMatch(e -> e.getType() == Envelope.Type.ELECTION && e.getRootCandidateId() == 0);
        assertTrue(electionForwarded,
                "ELECTION envelope with the lower candidate must be forwarded to non-sender neighbors");
    }

    @Test
    void onElection_shouldNotForwardBackToSender() throws IOException {
        SpanningTreeManager stm = new SpanningTreeManager(5, List.of(1, 2), transport);

        stm.handleEnvelope(Envelope.election(1, 0, 0), 1);

        // Capture all sendToNeighbor(1, ...) calls – there should be none carrying an ELECTION.
        ArgumentCaptor.forClass(Envelope.class);
        verify(transport, never()).sendToNeighbor(eq(1), argThat(
                e -> e.getType() == Envelope.Type.ELECTION));
    }

    @Test
    void onElection_shouldIgnoreMessageWithOlderVersion() throws IOException {
        SpanningTreeManager stm = new SpanningTreeManager(5, List.of(1), transport);
        // Advance tree version by delivering a higher-version election first.
        stm.handleEnvelope(Envelope.election(1, 1, 2), 1);
        reset(transport);

        // Now deliver version 0, should be silently dropped.
        stm.handleEnvelope(Envelope.election(1, 0, 0), 1);

        // Candidate 0 is lower, but the version is stale; best root must NOT change to 0.
        // We can only assert that sendToNeighbor was not called with version-0 envelopes.
        verify(transport, never()).sendToNeighbor(anyInt(),
                argThat(e -> e.getTreeVersion() == 0 && e.getType() == Envelope.Type.ELECTION));
    }

    // -------------------------------------------------------------------------
    // TREE_DISCOVER handling
    // -------------------------------------------------------------------------

    @Test
    void onTreeDiscover_shouldSetParentToSender() {
        SpanningTreeManager stm = new SpanningTreeManager(3, List.of(0, 4), transport);

        Envelope discover = Envelope.treeDiscover(0, 0, 0, 0);
        stm.handleEnvelope(discover, 0);

        assertEquals(0, stm.getParentId(),
                "After accepting a TREE_DISCOVER, parent must be the sender node");
    }

    @Test
    void onTreeDiscover_shouldReplyWithParentAck() throws IOException {
        SpanningTreeManager stm = new SpanningTreeManager(3, List.of(0, 4), transport);

        stm.handleEnvelope(Envelope.treeDiscover(0, 0, 0, 0), 0);

        ArgumentCaptor<Envelope> captor = ArgumentCaptor.forClass(Envelope.class);
        verify(transport, atLeastOnce()).sendToNeighbor(eq(0), captor.capture());

        boolean ackSent = captor.getAllValues().stream()
                .anyMatch(e -> e.getType() == Envelope.Type.TREE_PARENT_ACK);
        assertTrue(ackSent, "A TREE_PARENT_ACK must be sent back to the discover sender");
    }

    @Test
    void onTreeDiscover_shouldRejectSecondDiscover() throws IOException {
        // Node 3, neighbors [0, 4]. Accept parent 0; then 4 also sends a discover.
        SpanningTreeManager stm = new SpanningTreeManager(3, List.of(0, 4), transport);
        stm.handleEnvelope(Envelope.treeDiscover(0, 0, 0, 0), 0);
        reset(transport);

        stm.handleEnvelope(Envelope.treeDiscover(4, 0, 0, 0), 4);

        ArgumentCaptor<Envelope> captor = ArgumentCaptor.forClass(Envelope.class);
        verify(transport, atLeastOnce()).sendToNeighbor(eq(4), captor.capture());
        boolean rejectSent = captor.getAllValues().stream()
                .anyMatch(e -> e.getType() == Envelope.Type.TREE_REJECT);
        assertTrue(rejectSent,
                "A node that already has a parent must reject subsequent TREE_DISCOVER messages");
    }

    @Test
    void onTreeDiscover_withOlderVersion_shouldRejectWithCurrentVersion() throws IOException {
        SpanningTreeManager stm = new SpanningTreeManager(3, List.of(0, 4), transport);

        stm.handleEnvelope(Envelope.treeDiscover(0, 0, 2, 0), 0);
        reset(transport);

        stm.handleEnvelope(Envelope.treeDiscover(4, 0, 1, 0), 4);

        verify(transport, atLeastOnce()).sendToNeighbor(eq(4), argThat(
                e -> e.getType() == Envelope.Type.TREE_REJECT && e.getTreeVersion() == 2));
    }

    // -------------------------------------------------------------------------
    // TREE_PARENT_ACK / TREE_REJECT handling (root side)
    // -------------------------------------------------------------------------

    @Test
    void onTreeParentAck_shouldAddSenderToChildren() {
        // Node 5 has neighbors [0, 6].
        // Drive it into BUILDING via a TREE_DISCOVER from neighbor 0 (node 0 becomes parent).
        // Node 5 will then be waiting for ACK/REJECT from its remaining neighbor 6.
        // When neighbor 6 replies with TREE_PARENT_ACK, node 6 must be added to children.
        SpanningTreeManager stm = new SpanningTreeManager(5, List.of(0, 6), transport);

        // TREE_DISCOVER from node 0 (root=0, version=0, level=0):
        //   - sets parent=0, pendingAcks=1 (one other neighbor: 6), phase=BUILDING
        //   - forwards TREE_DISCOVER to neighbor 6
        stm.handleEnvelope(Envelope.treeDiscover(0, 0, 0, 0), 0);

        // Confirm we are now in BUILDING with parent 0 before injecting the ACK.
        assertEquals(SpanningTreeManager.Phase.BUILDING, stm.getPhase(),
                "Precondition: node must be in BUILDING phase before receiving TREE_PARENT_ACK");
        assertEquals(0, stm.getParentId(),
                "Precondition: parent must be node 0 after accepting the TREE_DISCOVER");

        // Neighbor 6 responds with TREE_PARENT_ACK — version matches (0), phase is BUILDING.
        stm.handleEnvelope(Envelope.treeParentAck(6, 0), 6);

        assertTrue(stm.getChildren().contains(6),
                "TREE_PARENT_ACK must add the sender to the children set");
    }

    // -------------------------------------------------------------------------
    // DATA handling
    // -------------------------------------------------------------------------

    @Test
    void sendData_whileNotReady_shouldBacklogAndNotSendImmediately() throws IOException {
        SpanningTreeManager stm = new SpanningTreeManager(7, List.of(0), transport);
        // Phase is ELECTING — data must go to backlog, not be transmitted immediately.

        stm.sendData(0, "hello");

        // No DATA envelope should have been sent to the transport yet.
        verify(transport, never()).sendToNeighbor(anyInt(),
                argThat(e -> e.getType() == Envelope.Type.DATA));
    }

    @Test
    void onData_shouldDeliverToHandlerWhenDestinationMatches() {
        SpanningTreeManager stm = new SpanningTreeManager(2, List.of(0), transport);
        MessageHandler handler = mock(MessageHandler.class);
        stm.start(handler);

        Envelope dataEnv = Envelope.data(0, 0, 2, "targeted-payload", 0);
        stm.handleEnvelope(dataEnv, 0);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(handler, atLeastOnce()).onMessage(captor.capture());

        Message delivered = captor.getAllValues().stream()
                .filter(m -> "targeted-payload".equals(m.getPayload()))
                .findFirst()
                .orElse(null);
        assertNotNull(delivered, "Handler must receive the message addressed to this node");
        assertEquals(0, delivered.getSourceId());
        assertEquals(2, delivered.getDestinationId());
    }

    @Test
    void onData_shouldDeliverToHandlerOnBroadcast() {
        SpanningTreeManager stm = new SpanningTreeManager(3, List.of(), transport);
        MessageHandler handler = mock(MessageHandler.class);
        stm.start(handler);

        Envelope broadcastEnv = Envelope.data(0, 0, SpanningTreeManager.BROADCAST_DEST, "broadcast-msg", 0);
        stm.handleEnvelope(broadcastEnv, 0);

        verify(handler, atLeastOnce()).onMessage(argThat(
                m -> "broadcast-msg".equals(m.getPayload())));
    }

    @Test
    void onData_shouldDropDuplicateMessageId() {
        SpanningTreeManager stm = new SpanningTreeManager(3, List.of(), transport);
        MessageHandler handler = mock(MessageHandler.class);
        stm.start(handler);

        Envelope env = Envelope.data(0, 0, SpanningTreeManager.BROADCAST_DEST, "dup", 0);
        stm.handleEnvelope(env, 0);
        stm.handleEnvelope(env, 0); // same envelope (same messageId)

        // Handler must be called exactly once despite two deliveries.
        verify(handler, times(1)).onMessage(any());
    }

    @Test
    void sendData_backloggedBeforeReady_shouldBeFlushedAfterTreeBecomesReady() throws IOException {
        SpanningTreeManager stm = new SpanningTreeManager(7, List.of(0), transport);

        stm.sendData(42, "queued-before-ready");
        verify(transport, never()).sendToNeighbor(anyInt(), argThat(e -> e.getType() == Envelope.Type.DATA));

        stm.handleEnvelope(Envelope.treeDiscover(0, 0, 0, 0), 0);

        verify(transport, timeout(1000).atLeastOnce()).sendToNeighbor(eq(0), argThat(
                e -> e.getType() == Envelope.Type.DATA
                        && e.getDataDestId() == 42
                        && "queued-before-ready".equals(e.getPayload())));
        assertEquals(SpanningTreeManager.Phase.READY, stm.getPhase());
    }

    @Test
    void onData_whenReady_shouldForwardToTreeNeighborsExceptSender() throws IOException {
        SpanningTreeManager stm = new SpanningTreeManager(5, List.of(0, 6), transport);

        stm.handleEnvelope(Envelope.treeDiscover(0, 0, 0, 0), 0);
        stm.handleEnvelope(Envelope.treeParentAck(6, 0), 6);
        assertEquals(SpanningTreeManager.Phase.READY, stm.getPhase());

        reset(transport);
        stm.handleEnvelope(Envelope.data(0, 0, 9, "forward-me", 0), 0);

        verify(transport, timeout(1000).atLeastOnce()).sendToNeighbor(eq(6), argThat(
                e -> e.getType() == Envelope.Type.DATA
                        && e.getDataDestId() == 9
                        && "forward-me".equals(e.getPayload())));
        verify(transport, never()).sendToNeighbor(eq(0), argThat(e -> e.getType() == Envelope.Type.DATA));
    }

    @Test
    void onData_whenTargetIsSelf_shouldDeliverWithoutForwarding() throws IOException {
        SpanningTreeManager stm = new SpanningTreeManager(5, List.of(0, 6), transport);
        MessageHandler handler = mock(MessageHandler.class);
        stm.setHandler(handler);

        stm.handleEnvelope(Envelope.treeDiscover(0, 0, 0, 0), 0);
        stm.handleEnvelope(Envelope.treeParentAck(6, 0), 6);
        assertEquals(SpanningTreeManager.Phase.READY, stm.getPhase());

        reset(transport);
        stm.handleEnvelope(Envelope.data(0, 0, 5, "for-me", 0), 0);

        verify(handler, atLeastOnce()).onMessage(argThat(
                m -> m.getSourceId() == 0 && m.getDestinationId() == 5 && "for-me".equals(m.getPayload())));
        verify(transport, never()).sendToNeighbor(anyInt(), argThat(e -> e.getType() == Envelope.Type.DATA));
    }

    // -------------------------------------------------------------------------
    // HEARTBEAT / HEARTBEAT_ACK handling
    // -------------------------------------------------------------------------

    @Test
    void onHeartbeat_shouldSendHeartbeatAckToSender() throws IOException {
        SpanningTreeManager stm = new SpanningTreeManager(4, List.of(2), transport);

        stm.handleEnvelope(Envelope.heartbeat(2, 0), 2);

        ArgumentCaptor<Envelope> captor = ArgumentCaptor.forClass(Envelope.class);
        verify(transport, atLeastOnce()).sendToNeighbor(eq(2), captor.capture());

        boolean ackSent = captor.getAllValues().stream()
                .anyMatch(e -> e.getType() == Envelope.Type.HEARTBEAT_ACK);
        assertTrue(ackSent, "A HEARTBEAT must elicit a HEARTBEAT_ACK reply to the sender");
    }

    // -------------------------------------------------------------------------
    // TOPOLOGY_REBUILD handling
    // -------------------------------------------------------------------------

    @Test
    void onTopologyRebuild_shouldBroadcastRebuildMessageToAllNeighbors() {
        SpanningTreeManager stm = new SpanningTreeManager(3, List.of(0, 1, 4), transport);

        Envelope rebuild = Envelope.rebuild(0, 5);
        stm.handleEnvelope(rebuild, 0);

        // The manager must propagate the TOPOLOGY_REBUILD to all neighbors.
        verify(transport, atLeastOnce()).broadcast(argThat(
                e -> e.getType() == Envelope.Type.TOPOLOGY_REBUILD && e.getTreeVersion() == 5));
    }

    @Test
    void onTopologyRebuild_shouldIgnoreOlderOrEqualVersion() {
        SpanningTreeManager stm = new SpanningTreeManager(3, List.of(0), transport);
        // Deliver a higher-version message first to bump the stored treeVersion.
        stm.handleEnvelope(Envelope.rebuild(0, 10), 0);
        reset(transport);

        // A rebuild with an older version should be silently dropped.
        stm.handleEnvelope(Envelope.rebuild(0, 5), 0);

        verify(transport, never()).broadcast(argThat(e -> e.getTreeVersion() == 5));
    }

    @Test
    void onElection_whenAlreadyReady_shouldTriggerRebuildWithHigherVersion() {
        SpanningTreeManager stm = new SpanningTreeManager(5, List.of(0, 6), transport);

        stm.handleEnvelope(Envelope.treeDiscover(0, 0, 0, 0), 0);
        stm.handleEnvelope(Envelope.treeParentAck(6, 0), 6);
        assertEquals(SpanningTreeManager.Phase.READY, stm.getPhase());

        reset(transport);
        stm.handleEnvelope(Envelope.election(6, 1, 0), 6);

        verify(transport, atLeastOnce()).broadcast(argThat(
                e -> e.getType() == Envelope.Type.TOPOLOGY_REBUILD && e.getTreeVersion() == 1));
        assertEquals(1, stm.getTreeVersion());
        assertEquals(SpanningTreeManager.Phase.ELECTING, stm.getPhase());
    }

    // -------------------------------------------------------------------------
    // getStatusSummary
    // -------------------------------------------------------------------------

    @Test
    void getStatusSummary_shouldReturnNonEmptyString() {
        SpanningTreeManager stm = new SpanningTreeManager(1, List.of(0), transport);
        String summary = stm.getStatusSummary();

        assertNotNull(summary);
        assertFalse(summary.isBlank(), "Status summary must be a non-empty, non-blank string");
    }

    @Test
    void getStatusSummary_shouldContainPhaseAndVersion() {
        SpanningTreeManager stm = new SpanningTreeManager(1, List.of(0), transport);
        String summary = stm.getStatusSummary();

        assertTrue(summary.contains("ELECTING"), "Summary must mention current phase");
        assertTrue(summary.contains("v="), "Summary must mention tree version");
    }
}
