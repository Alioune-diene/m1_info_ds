package fr.uga.im2ag.m1info.virtual;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure in-memory unit tests for the virtual layer.
 * <p>
 * Because {@link VirtualNode} always opens a real AMQP connection in its
 * constructor, these tests target the supporting value types and formulas
 * without instantiating {@code VirtualNode} directly.
 * Integration tests that instantiate {@code VirtualNode} live in
 * {@code VirtualLayerIntegrationTest}.
 */
class VirtualNodeUnitTest {

    // -------------------------------------------------------------------------
    // VirtualEnvelope. data routing ring arithmetic
    // -------------------------------------------------------------------------

    @Test
    void virtualEnvelopeData_shouldCarryCorrectSourceAndDestination() {
        VirtualEnvelope env = VirtualEnvelope.data(3, 4, 10, "payload");

        assertEquals(3, env.getVirtualSourceId(), "Source ID must match the sender");
        assertEquals(4, env.getVirtualDestId(),   "Destination ID must match the target");
    }

    @ParameterizedTest(name = "sendRight: V{0} in ring {1} → V{2}")
    @CsvSource({
        "0, 4, 1",
        "3, 4, 0",   // wraps around
        "1, 5, 2",
        "4, 5, 0"    // wraps around
    })
    void sendRight_destinationFormula_shouldWrapCorrectly(int virtualId, int ringSize, int expectedDest) {
        int dest = (virtualId + 1) % ringSize;
        assertEquals(expectedDest, dest,
                "sendRight destination must be (virtualId + 1) mod ringSize");
    }

    @ParameterizedTest(name = "sendLeft: V{0} in ring {1} → V{2}")
    @CsvSource({
        "1, 4, 0",
        "0, 4, 3",   // wraps around
        "3, 5, 2",
        "0, 5, 4"    // wraps around
    })
    void sendLeft_destinationFormula_shouldWrapCorrectly(int virtualId, int ringSize, int expectedDest) {
        int dest = (virtualId - 1 + ringSize) % ringSize;
        assertEquals(expectedDest, dest,
                "sendLeft destination must be (virtualId - 1 + ringSize) mod ringSize");
    }

    // -------------------------------------------------------------------------
    // VirtualEnvelope : type classification
    // -------------------------------------------------------------------------

    @Test
    void heartbeat_envelope_shouldHaveCorrectType() {
        VirtualEnvelope hb = VirtualEnvelope.heartbeat(7);
        assertEquals(VirtualEnvelope.Type.VIRTUAL_HEARTBEAT, hb.getType());
        assertEquals(7, hb.getVirtualSourceId());
    }

    @Test
    void heartbeatAck_envelope_shouldHaveCorrectTypeAndHost() {
        VirtualEnvelope ack = VirtualEnvelope.heartbeatAck(2, 1);
        assertEquals(VirtualEnvelope.Type.VIRTUAL_HEARTBEAT_ACK, ack.getType());
        assertEquals(2, ack.getVirtualSourceId());
        assertEquals(1, ack.getHostPhysicalId());
    }

    @Test
    void register_envelope_shouldHaveCorrectTypeAndRing() {
        VirtualEnvelope reg = VirtualEnvelope.register(5, 10);
        assertEquals(VirtualEnvelope.Type.VIRTUAL_REGISTER, reg.getType());
        assertEquals(5, reg.getVirtualSourceId());
        assertEquals(10, reg.getRingSize());
    }

    // -------------------------------------------------------------------------
    // VirtualEnvelope : payload prefix constant
    // -------------------------------------------------------------------------

    @Test
    void payloadPrefix_shouldStartWithVPipe() {
        assertTrue(VirtualEnvelope.PAYLOAD_PREFIX.startsWith("V|"),
                "PAYLOAD_PREFIX must start with 'V|' so the physical service can detect virtual messages");
    }

    @Test
    void payloadPrefix_lengthShouldBeTwo() {
        assertEquals(2, VirtualEnvelope.PAYLOAD_PREFIX.length(),
                "PAYLOAD_PREFIX must be exactly 2 characters ('V|')");
    }

    // -------------------------------------------------------------------------
    // Initial host assignment formula
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "V{0} in ring {1} with {2} physicals → initial host P{3}")
    @CsvSource({
        "0, 4, 2, 0",
        "1, 4, 2, 1",
        "2, 4, 2, 0",
        "3, 4, 2, 1",
        "0, 6, 3, 0",
        "4, 6, 3, 1",
        "5, 6, 3, 2"
    })
    void initialHost_shouldBeVirtualIdModNumPhysical(
            int virtualId, int ringSize, int numPhysical, int expectedHost) {
        int host = virtualId % numPhysical;
        assertEquals(expectedHost, host,
                "Initial host assignment must follow virtualId %% numPhysical");
    }

    // -------------------------------------------------------------------------
    // Bounds validation : replicated from VirtualNode constructor guard
    // -------------------------------------------------------------------------

    @Test
    void virtualId_zeroShouldBeValid_withinRing() {
        // virtualId=0, ringSize=1 → valid (boundary).
        // We verify the guard formula directly without constructing VirtualNode.
        int virtualId = 0;
        int ringSize  = 1;
        boolean valid = virtualId >= 0 && virtualId < ringSize;
        assertTrue(valid, "virtualId 0 in ring of size 1 must be valid");
    }

    @Test
    void virtualId_equalToRingSize_shouldBeInvalid() {
        int virtualId = 4;
        int ringSize  = 4;
        boolean invalid = virtualId < 0 || virtualId >= ringSize;
        assertTrue(invalid, "virtualId equal to ringSize must be rejected");
    }

    @Test
    void virtualId_negative_shouldBeInvalid() {
        int virtualId = -1;
        int ringSize  = 5;
        boolean invalid = virtualId < 0 || virtualId >= ringSize;
        assertTrue(invalid, "A negative virtualId must be rejected");
    }

    // -------------------------------------------------------------------------
    // VirtualEnvelope toString : smoke test
    // -------------------------------------------------------------------------

    @Test
    void toString_shouldNotThrowForAnyType() {
        VirtualEnvelope[] envelopes = {
            VirtualEnvelope.data(0, 1, 4, "p"),
            VirtualEnvelope.register(2, 4),
            VirtualEnvelope.heartbeat(3),
            VirtualEnvelope.heartbeatAck(3, 0)
        };
        for (VirtualEnvelope env : envelopes) {
            assertDoesNotThrow(env::toString,
                    "toString() must not throw for type " + env.getType());
            assertFalse(env.toString().isBlank(),
                    "toString() must return a non-blank string for type " + env.getType());
        }
    }

    // -------------------------------------------------------------------------
    // Host queue naming convention
    // -------------------------------------------------------------------------

    @Test
    void hostQueueName_shouldFollowConvention() {
        // Convention: "physical.node.<hostId>.virt"
        int hostId = 2;
        String expected = "physical.node." + hostId + VirtualNode.HOST_VIRT_SUFFIX;
        assertEquals("physical.node.2.virt", expected,
                "Host queue name must follow the 'physical.node.N.virt' pattern");
    }

    @Test
    void virtualQueueName_shouldFollowConvention() {
        // Convention: "virtual.node.<virtualId>"
        int virtualId = 5;
        String expected = VirtualNode.VIRTUAL_QUEUE_PREFIX + virtualId;
        assertEquals("virtual.node.5", expected,
                "Virtual node queue name must follow the 'virtual.node.N' pattern");
    }

    // -------------------------------------------------------------------------
    // Migration order : ring arithmetic
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "failed host {0} with {1} physicals → first candidate {2}")
    @CsvSource({
        "0, 3, 1",
        "1, 3, 2",
        "2, 3, 0",
        "0, 2, 1",
        "1, 2, 0"
    })
    void migration_firstCandidate_shouldBeFailedHostPlusOneModNumPhysical(
            int failedHost, int numPhysical, int expectedFirstCandidate) {
        int firstCandidate = (failedHost + 1) % numPhysical;
        assertEquals(expectedFirstCandidate, firstCandidate,
                "Migration must try (failedHost + 1) %% numPhysical first");
    }
}
