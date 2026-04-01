package fr.uga.im2ag.m1info.physical;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
        ArgumentCaptor<Envelope> captor = ArgumentCaptor.forClass(Envelope.class);
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


