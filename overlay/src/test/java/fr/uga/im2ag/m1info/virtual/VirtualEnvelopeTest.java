package fr.uga.im2ag.m1info.virtual;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class VirtualEnvelopeTest {

    @Test
    void data_shouldSetTypeAndFields() {
        VirtualEnvelope env = VirtualEnvelope.data(1, 2, 3, "payload");

        assertEquals(VirtualEnvelope.Type.VIRTUAL_DATA, env.getType());
        assertEquals(1, env.getVirtualSourceId());
        assertEquals(2, env.getVirtualDestId());
        assertEquals(3, env.getRingSize());
        assertEquals("payload", env.getPayload());
    }

    @Test
    void register_shouldSetTypeSourceAndRing() {
        VirtualEnvelope env = VirtualEnvelope.register(7, 10);

        assertEquals(VirtualEnvelope.Type.VIRTUAL_REGISTER, env.getType());
        assertEquals(7, env.getVirtualSourceId());
        assertEquals(10, env.getRingSize());
        assertNull(env.getPayload());
        assertEquals(0, env.getVirtualDestId());
    }

    @Test
    void heartbeat_shouldSetTypeAndSource() {
        VirtualEnvelope env = VirtualEnvelope.heartbeat(5);

        assertEquals(VirtualEnvelope.Type.VIRTUAL_HEARTBEAT, env.getType());
        assertEquals(5, env.getVirtualSourceId());
    }

    @Test
    void heartbeatAck_shouldSetTypeSourceAndHost() {
        VirtualEnvelope env = VirtualEnvelope.heartbeatAck(9, 42);

        assertEquals(VirtualEnvelope.Type.VIRTUAL_HEARTBEAT_ACK, env.getType());
        assertEquals(9, env.getVirtualSourceId());
        assertEquals(42, env.getHostPhysicalId());
    }

    @Test
    void payloadPrefix_constantShouldBeVpipe() {
        assertEquals("V|", VirtualEnvelope.PAYLOAD_PREFIX);
    }

    @Test
    void toString_shouldContainTypeAndIds() {
        VirtualEnvelope env = VirtualEnvelope.data(3, 4, 5, "p");
        String s = env.toString();

        assertDoesNotThrow(env::toString);
        assertNotNull(s);
        assertFalse(s.isEmpty());

        assertTrue(s.contains(VirtualEnvelope.Type.VIRTUAL_DATA.name()));
        assertTrue(s.contains("src=" + env.getVirtualSourceId()));
        assertTrue(s.contains("dst=" + env.getVirtualDestId()));
        assertTrue(s.contains("ring=" + env.getRingSize()));
        assertTrue(s.contains("host=" + env.getHostPhysicalId()));
    }

    @Test
    void data_shouldAcceptEmptyPayload() {
        VirtualEnvelope env = VirtualEnvelope.data(0, 0, 1, "");
        assertEquals("", env.getPayload());
    }

    @Test
    void data_shouldAcceptNullPayload() {
        VirtualEnvelope env = VirtualEnvelope.data(0, 0, 1, null);
        assertNull(env.getPayload());
    }

    @ParameterizedTest
    @ValueSource(strings = {"hello", "", "unicode:\u00e9\u00e0\u00fc"})
    void data_shouldPreserveArbitraryPayloads(String payload) {
        VirtualEnvelope env = VirtualEnvelope.data(0, 0, 1, payload);
        assertEquals(payload, env.getPayload());
    }
}

