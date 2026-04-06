package fr.uga.im2ag.m1info.physical;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Envelope}.
 * <p>
 * These tests are purely in-memory and require no external infrastructure.
 */
class EnvelopeTest {

    // -------------------------------------------------------------------------
    // Factory methods ~ happy path
    // -------------------------------------------------------------------------

    @Test
    void election_shouldPopulateRootCandidate() {
        Envelope env = Envelope.election(1, 42, 3);

        assertEquals(42, env.getRootCandidateId(), "Election envelope must carry the root candidate ID");
    }

    @Test
    void election_shouldPopulateAllFields() {
        Envelope env = Envelope.election(1, 0, 3);

        assertEquals(Envelope.Type.ELECTION, env.getType());
        assertEquals(1, env.getSenderId());
        assertEquals(0, env.getRootCandidateId());
        assertEquals(3, env.getTreeVersion());
    }

    @Test
    void treeDiscover_shouldPopulateLevelAndRootCandidate() {
        Envelope env = Envelope.treeDiscover(2, 0, 1, 4);

        assertEquals(Envelope.Type.TREE_DISCOVER, env.getType());
        assertEquals(2, env.getSenderId());
        assertEquals(0, env.getRootCandidateId());
        assertEquals(1, env.getTreeVersion());
        assertEquals(4, env.getLevel());
    }

    @Test
    void treeParentAck_shouldHaveCorrectTypeAndVersion() {
        Envelope env = Envelope.treeParentAck(5, 2);

        assertEquals(Envelope.Type.TREE_PARENT_ACK, env.getType());
        assertEquals(5, env.getSenderId());
        assertEquals(2, env.getTreeVersion());
    }

    @Test
    void treeReject_shouldHaveCorrectTypeAndVersion() {
        Envelope env = Envelope.treeReject(3, 7);

        assertEquals(Envelope.Type.TREE_REJECT, env.getType());
        assertEquals(3, env.getSenderId());
        assertEquals(7, env.getTreeVersion());
    }

    @Test
    void data_shouldPopulateAllDataFields() {
        Envelope env = Envelope.data(1, 1, 4, "hello", 2);

        assertEquals(Envelope.Type.DATA, env.getType());
        assertEquals(1, env.getSenderId());
        assertEquals(1, env.getDataSourceId());
        assertEquals(4, env.getDataDestId());
        assertEquals("hello", env.getPayload());
        assertEquals(2, env.getTreeVersion());
    }

    @Test
    void data_shouldGenerateUniqueMessageIds() {
        Envelope a = Envelope.data(0, 0, 1, "msg", 1);
        Envelope b = Envelope.data(0, 0, 1, "msg", 1);

        assertNotNull(a.getMessageId());
        assertNotNull(b.getMessageId());
        assertNotEquals(a.getMessageId(), b.getMessageId(),
                "Two separate data envelopes must have distinct message IDs");
    }

    @Test
    void heartbeat_shouldHaveCorrectTypeAndSender() {
        Envelope env = Envelope.heartbeat(7, 1);

        assertEquals(Envelope.Type.HEARTBEAT, env.getType());
        assertEquals(7, env.getSenderId());
        assertEquals(1, env.getTreeVersion());
    }

    @Test
    void heartbeatAck_shouldHaveCorrectTypeAndSender() {
        Envelope env = Envelope.heartbeatAck(3, 5);

        assertEquals(Envelope.Type.HEARTBEAT_ACK, env.getType());
        assertEquals(3, env.getSenderId());
        assertEquals(5, env.getTreeVersion());
    }

    @Test
    void rebuild_shouldCarryNewVersion() {
        Envelope env = Envelope.rebuild(2, 10);

        assertEquals(Envelope.Type.TOPOLOGY_REBUILD, env.getType());
        assertEquals(2, env.getSenderId());
        assertEquals(10, env.getTreeVersion());
    }

    // -------------------------------------------------------------------------
    // withSender (deep copy)
    // -------------------------------------------------------------------------

    @Test
    void withSender_shouldReturnNewEnvelopeWithReplacedSenderId() {
        Envelope original = Envelope.data(1, 1, 4, "payload", 3);
        Envelope forwarded = original.withSender(99);

        assertNotSame(original, forwarded, "withSender must return a new instance");
        assertEquals(99, forwarded.getSenderId());
        assertEquals(original.getType(), forwarded.getType());
        assertEquals(original.getDataSourceId(), forwarded.getDataSourceId());
        assertEquals(original.getDataDestId(), forwarded.getDataDestId());
        assertEquals(original.getPayload(), forwarded.getPayload());
        assertEquals(original.getTreeVersion(), forwarded.getTreeVersion());
        assertEquals(original.getMessageId(), forwarded.getMessageId(),
                "Message ID must be preserved across forwarding hops");
    }

    @Test
    void withSender_shouldNotMutateOriginalEnvelope() {
        Envelope original = Envelope.election(5, 2, 1);
        original.withSender(100);

        assertEquals(5, original.getSenderId(), "Original envelope must remain unchanged after withSender");
    }

    // -------------------------------------------------------------------------
    // toString : smoke test (must not throw)
    // -------------------------------------------------------------------------

    @Test
    void toString_shouldNotThrowForAnyType() {
        Envelope[] envelopes = {
            Envelope.election(0, 0, 0),
            Envelope.treeDiscover(1, 0, 0, 0),
            Envelope.treeParentAck(2, 0),
            Envelope.treeReject(3, 0),
            Envelope.data(4, 0, 1, "test", 0),
            Envelope.heartbeat(5, 0),
            Envelope.heartbeatAck(6, 0),
            Envelope.rebuild(7, 1)
        };

        for (Envelope env : envelopes) {
            assertDoesNotThrow(env::toString, "toString() must not throw for type " + env.getType());
            assertNotNull(env.toString(), "toString() must return a non-null string");
            assertFalse(env.toString().isEmpty(), "toString() must return a non-empty string");

            assertTrue(env.toString().contains(env.getType().name()), "toString() should include the envelope type");
            assertTrue(env.toString().contains("type=" + env.getType().name()), "toString() should include the type field");
            assertTrue(env.toString().contains("sender=" + env.getSenderId()), "toString() should include the senderId field");
            assertTrue(env.toString().contains("v=" + env.getTreeVersion()), "toString() should include the treeVersion field");
            assertTrue(env.toString().contains("rootCand=" + env.getRootCandidateId()), "toString() should include the rootCandidateId field");
            assertTrue(env.toString().contains("dst=" + env.getDataDestId()), "toString() should include the data destination field for data envelopes");
            assertTrue(env.toString().contains("msgId=" + (env.getMessageId() != null ? env.getMessageId().substring(0, 8) : "-")), "toString() should include the message ID prefix");
        }
    }

    // -------------------------------------------------------------------------
    // data —:null/empty payload edge cases
    // -------------------------------------------------------------------------

    @Test
    void data_shouldAcceptEmptyPayload() {
        Envelope env = Envelope.data(0, 0, 1, "", 0);
        assertEquals("", env.getPayload());
    }

    @Test
    void data_shouldAcceptNullPayload() {
        Envelope env = Envelope.data(0, 0, 1, null, 0);
        assertNull(env.getPayload());
    }

    @ParameterizedTest
    @ValueSource(strings = {"hello", "world", "unicode: \u00e9\u00e0\u00fc", ""})
    void data_shouldPreserveArbitraryPayloads(String payload) {
        Envelope env = Envelope.data(0, 0, 1, payload, 0);
        assertEquals(payload, env.getPayload());
    }
}
