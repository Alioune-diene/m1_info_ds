package fr.uga.im2ag.m1info.physical;

import com.google.gson.Gson;
import com.rabbitmq.client.*;
import fr.uga.im2ag.m1info.virtual.VirtualEnvelope;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PhysicalHostServiceUnitTest {

    private static final Gson GSON = new Gson();

    private static final class Harness {
        final Connection connection;
        final Channel channel;
        final PhysicalHostService service;
        final DeliverCallback commandCallback;

        Harness(Connection connection, Channel channel, PhysicalHostService service, DeliverCallback commandCallback) {
            this.connection = connection;
            this.channel = channel;
            this.service = service;
            this.commandCallback = commandCallback;
        }
    }

    private Harness newHarness(int physicalId, SpanningTreeManager manager) throws IOException {
        Connection connection = mock(Connection.class);
        Channel channel = mock(Channel.class);
        when(connection.createChannel()).thenReturn(channel);

        PhysicalHostService service = new PhysicalHostService(physicalId, manager, connection);
        ArgumentCaptor<DeliverCallback> callbackCaptor = ArgumentCaptor.forClass(DeliverCallback.class);
        verify(channel).basicConsume(eq("physical.node." + physicalId + PhysicalHostService.VIRT_CMD_SUFFIX), eq(true), callbackCaptor.capture(), (CancelCallback) any());

        return new Harness(connection, channel, service, callbackCaptor.getValue());
    }

    private static Delivery deliveryWithBody(String body) {
        Delivery delivery = mock(Delivery.class);
        when(delivery.getBody()).thenReturn(body.getBytes(StandardCharsets.UTF_8));
        return delivery;
    }

    private static VirtualEnvelope publishedEnvelope(byte[] body) {
        String payload = new String(body, StandardCharsets.UTF_8);
        assertTrue(payload.startsWith(VirtualEnvelope.PAYLOAD_PREFIX));
        return GSON.fromJson(payload.substring(VirtualEnvelope.PAYLOAD_PREFIX.length()), VirtualEnvelope.class);
    }

    @Test
    void constructor_shouldDeclareVirtualCommandQueueAndStartConsumer() throws IOException {
        Connection connection = mock(Connection.class);
        Channel channel = mock(Channel.class);
        SpanningTreeManager manager = mock(SpanningTreeManager.class);
        when(connection.createChannel()).thenReturn(channel);

        new PhysicalHostService(12, manager, connection);

        verify(channel).queueDeclare("physical.node.12.virt", false, false, false, null);
        verify(channel).basicConsume(eq("physical.node.12.virt"), eq(true), any(DeliverCallback.class), any(CancelCallback.class));
    }

    @Test
    void registerCommand_shouldTrackHostedVirtualAndSendHeartbeatAck() throws IOException {
        SpanningTreeManager manager = mock(SpanningTreeManager.class);
        Harness harness = newHarness(7, manager);
        reset(harness.channel);

        VirtualEnvelope register = VirtualEnvelope.register(41, 5);
        harness.commandCallback.handle("tag", deliveryWithBody(VirtualEnvelope.PAYLOAD_PREFIX + GSON.toJson(register)));

        assertEquals(Set.of(41), harness.service.getHostedVirtuals());
        ArgumentCaptor<byte[]> bodyCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(harness.channel).basicPublish(eq(""), eq("virtual.node.41"), isNull(), bodyCaptor.capture());
        VirtualEnvelope ack = publishedEnvelope(bodyCaptor.getValue());
        assertEquals(VirtualEnvelope.Type.VIRTUAL_HEARTBEAT_ACK, ack.getType());
        assertEquals(41, ack.getVirtualSourceId());
        assertEquals(7, ack.getHostPhysicalId());
    }

    @Test
    void duplicateRegisterCommand_shouldKeepHostedVirtualsDeduplicated() throws IOException {
        SpanningTreeManager manager = mock(SpanningTreeManager.class);
        Harness harness = newHarness(8, manager);
        reset(harness.channel);

        String raw = VirtualEnvelope.PAYLOAD_PREFIX + GSON.toJson(VirtualEnvelope.register(99, 3));
        harness.commandCallback.handle("tag-1", deliveryWithBody(raw));
        harness.commandCallback.handle("tag-2", deliveryWithBody(raw));

        assertEquals(1, harness.service.getHostedVirtuals().size());
        assertTrue(harness.service.getHostedVirtuals().contains(99));
    }

    @Test
    void heartbeatCommand_shouldSendAckWithoutAddingHostedVirtual() throws IOException {
        SpanningTreeManager manager = mock(SpanningTreeManager.class);
        Harness harness = newHarness(9, manager);
        reset(harness.channel);

        VirtualEnvelope heartbeat = VirtualEnvelope.heartbeat(55);
        harness.commandCallback.handle("tag", deliveryWithBody(VirtualEnvelope.PAYLOAD_PREFIX + GSON.toJson(heartbeat)));

        assertTrue(harness.service.getHostedVirtuals().isEmpty());
        ArgumentCaptor<byte[]> bodyCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(harness.channel).basicPublish(eq(""), eq("virtual.node.55"), isNull(), bodyCaptor.capture());
        VirtualEnvelope ack = publishedEnvelope(bodyCaptor.getValue());
        assertEquals(VirtualEnvelope.Type.VIRTUAL_HEARTBEAT_ACK, ack.getType());
        assertEquals(55, ack.getVirtualSourceId());
        assertEquals(9, ack.getHostPhysicalId());
    }

    @Test
    void virtualDataCommand_shouldForwardBroadcastPayloadToSpanningTreeManager() throws IOException {
        SpanningTreeManager manager = mock(SpanningTreeManager.class);
        Harness harness = newHarness(10, manager);
        reset(harness.channel);

        VirtualEnvelope data = VirtualEnvelope.data(3, 8, 11, "hello virtual world");
        harness.commandCallback.handle("tag", deliveryWithBody(VirtualEnvelope.PAYLOAD_PREFIX + GSON.toJson(data)));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(manager).sendData(eq(SpanningTreeManager.BROADCAST_DEST), payloadCaptor.capture());
        String routed = payloadCaptor.getValue();
        assertTrue(routed.startsWith(VirtualEnvelope.PAYLOAD_PREFIX));
        VirtualEnvelope forwarded = GSON.fromJson(routed.substring(VirtualEnvelope.PAYLOAD_PREFIX.length()), VirtualEnvelope.class);
        assertEquals(VirtualEnvelope.Type.VIRTUAL_DATA, forwarded.getType());
        assertEquals(3, forwarded.getVirtualSourceId());
        assertEquals(8, forwarded.getVirtualDestId());
        assertEquals(11, forwarded.getRingSize());
        assertEquals("hello virtual world", forwarded.getPayload());
    }

    @Test
    void appPayloadShouldBeDelegatedToApplicationHandler() throws IOException {
        SpanningTreeManager manager = mock(SpanningTreeManager.class);
        Harness harness = newHarness(13, manager);
        reset(harness.channel);

        MessageHandler appHandler = mock(MessageHandler.class);
        harness.service.setAppHandler(appHandler);
        Message msg = new Message(2, 3, "plain payload");

        harness.service.onMessage(msg);

        verify(appHandler).onMessage(same(msg));
        verifyNoInteractions(harness.channel);
    }

    @Test
    void nullAppHandlerShouldIgnoreNonVirtualMessageWithoutFailing() throws IOException {
        SpanningTreeManager manager = mock(SpanningTreeManager.class);
        Harness harness = newHarness(14, manager);
        reset(harness.channel);

        assertDoesNotThrow(() -> harness.service.onMessage(new Message(2, 3, "plain payload")));
        verifyNoInteractions(harness.channel);
    }

    @Test
    void virtualPayloadShouldBeDeliveredToHostedLocalVirtualQueue() throws IOException {
        SpanningTreeManager manager = mock(SpanningTreeManager.class);
        Harness harness = newHarness(15, manager);
        reset(harness.channel);

        harness.commandCallback.handle("tag", deliveryWithBody(VirtualEnvelope.PAYLOAD_PREFIX + GSON.toJson(VirtualEnvelope.register(70, 4))));
        reset(harness.channel);

        VirtualEnvelope data = VirtualEnvelope.data(2, 70, 4, "local delivery");
        harness.service.onMessage(new Message(2, 15, VirtualEnvelope.PAYLOAD_PREFIX + GSON.toJson(data)));

        ArgumentCaptor<byte[]> bodyCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(harness.channel).basicPublish(eq(""), eq("virtual.node.70"), isNull(), bodyCaptor.capture());
        VirtualEnvelope delivered = publishedEnvelope(bodyCaptor.getValue());
        assertEquals(VirtualEnvelope.Type.VIRTUAL_DATA, delivered.getType());
        assertEquals(2, delivered.getVirtualSourceId());
        assertEquals(70, delivered.getVirtualDestId());
        assertEquals(4, delivered.getRingSize());
        assertEquals("local delivery", delivered.getPayload());
    }

    @Test
    void virtualPayloadForUnhostedDestinationShouldNotBePublished() throws IOException {
        SpanningTreeManager manager = mock(SpanningTreeManager.class);
        Harness harness = newHarness(16, manager);
        reset(harness.channel);

        VirtualEnvelope data = VirtualEnvelope.data(2, 999, 6, "not hosted");
        harness.service.onMessage(new Message(2, 16, VirtualEnvelope.PAYLOAD_PREFIX + GSON.toJson(data)));

        verifyNoInteractions(harness.channel);
    }

    @Test
    void hostedVirtualsSnapshotShouldBeUnmodifiableAndIndependent() throws IOException {
        SpanningTreeManager manager = mock(SpanningTreeManager.class);
        Harness harness = newHarness(17, manager);
        reset(harness.channel);

        harness.commandCallback.handle("tag", deliveryWithBody(VirtualEnvelope.PAYLOAD_PREFIX + GSON.toJson(VirtualEnvelope.register(5, 2))));

        Set<Integer> hostedVirtuals = harness.service.getHostedVirtuals();
        assertThrows(UnsupportedOperationException.class, () -> hostedVirtuals.add(6));
        assertEquals(Set.of(5), harness.service.getHostedVirtuals());
    }

    @Test
    void deliverCallbackMustLogWhenUnknowVirtualCommandIsReceived() throws IOException {
        SpanningTreeManager manager = mock(SpanningTreeManager.class);
        Harness harness = newHarness(18, manager);
        reset(harness.channel);

        String unknownCommand = "UNKNOWN_COMMAND";
        harness.commandCallback.handle("tag", deliveryWithBody(unknownCommand));

        verifyNoInteractions(harness.channel);
    }
}

