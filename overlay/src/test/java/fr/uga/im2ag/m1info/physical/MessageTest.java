package fr.uga.im2ag.m1info.physical;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MessageTest {

    @Test
    void message_shouldPopulateAllFields() {
        Message m = new Message(1, 2, "payload");
        assertNotNull(m.getMessageId());
        assertEquals(1, m.getSourceId());
        assertEquals(2, m.getDestinationId());
        assertEquals("payload", m.getPayload());
    }

    @Test
    void message_shouldGenerateUniqueMessageIds() {
        Message a = new Message(0, 0, "x");
        Message b = new Message(0, 0, "x");

        assertNotNull(a.getMessageId());
        assertNotNull(b.getMessageId());
        assertNotEquals(a.getMessageId(), b.getMessageId(),"Two separate messages must have distinct message IDs");
    }

    @Test
    void message_messageIdShouldBeValidUuid() {
        Message m = new Message(3, 4, "p");
        assertDoesNotThrow(() -> UUID.fromString(m.getMessageId()));
    }

    @Test
    void toString_shouldNotThrowAndIncludeFields() {
        Message m = new Message(7, 8, "hello");
        assertDoesNotThrow(m::toString);
        String s = m.toString();
        assertTrue(s.contains("src=" + m.getSourceId()));
        assertTrue(s.contains("dst=" + m.getDestinationId()));
        assertTrue(s.contains("payload='" + m.getPayload() + "'"));
        assertTrue(s.contains("id='"));
    }

    @Test
    void shouldAcceptNullPayload() {
        Message m = new Message(5, 6, null);
        assertNull(m.getPayload());
        assertDoesNotThrow(m::toString);
        assertTrue(m.toString().contains("payload='null'"));
    }

    @Test
    void shouldAcceptEmptyPayload() {
        Message m = new Message(9, 10, "");
        assertEquals("", m.getPayload());
        assertTrue(m.toString().contains("payload=''"));
    }
}

