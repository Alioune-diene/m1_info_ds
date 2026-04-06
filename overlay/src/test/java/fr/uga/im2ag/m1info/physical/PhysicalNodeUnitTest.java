package fr.uga.im2ag.m1info.physical;

import com.google.gson.Gson;
import com.rabbitmq.client.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PhysicalNodeUnitTest {

    private static final String RABBIT_HOST = "rabbit-host";

    private PhysicalNode createNode(int id, List<Integer> neighbors, Connection connection, Channel channel)
            throws IOException, TimeoutException {
        when(connection.createChannel()).thenReturn(channel);

        try (MockedConstruction<ConnectionFactory> ignored = mockConstruction(ConnectionFactory.class,
                (factory, context) -> when(factory.newConnection("node-" + id)).thenReturn(connection))) {
            return new PhysicalNode(id, neighbors, RABBIT_HOST);
        }
    }

    @Test
    void constructor_shouldDeclareQueueAndExposeNodeMetadata() throws IOException, TimeoutException {
        Connection connection = mock(Connection.class);
        Channel channel = mock(Channel.class);

        PhysicalNode node = createNode(7, List.of(2, 3), connection, channel);

        assertEquals(7, node.getId());
        assertEquals(List.of(2, 3), node.getNeighbors());
        assertSame(connection, node.getConnection());
        verify(channel).queueDeclare("physical.node.7", false, false, false, null);
    }

    @Test
    void getNeighbors_shouldReturnUnmodifiableSnapshot() throws IOException, TimeoutException {
        Connection connection = mock(Connection.class);
        Channel channel = mock(Channel.class);
        List<Integer> sourceNeighbors = List.of(8, 9);

        PhysicalNode node = createNode(4, sourceNeighbors, connection, channel);

        assertThrows(UnsupportedOperationException.class, () -> node.getNeighbors().add(10));
        assertEquals(sourceNeighbors, node.getNeighbors());
    }

    @Test
    void startListening_shouldDeserializeEnvelopeAndForwardSenderIdToCallback() throws Exception {
        Connection connection = mock(Connection.class);
        Channel channel = mock(Channel.class);
        PhysicalNode node = createNode(5, List.of(6), connection, channel);

        @SuppressWarnings("unchecked")
        BiConsumer<Envelope, Integer> callback = mock(BiConsumer.class);
        node.startListening(callback);

        ArgumentCaptor<DeliverCallback> deliverCaptor = ArgumentCaptor.forClass(DeliverCallback.class);
        verify(channel).basicConsume((String) eq("physical.node.5"), eq(true), deliverCaptor.capture(), (CancelCallback) any());

        Envelope incoming = Envelope.data(42, 42, 5, "hello", 1);
        Delivery delivery = mock(Delivery.class);
        when(delivery.getBody()).thenReturn(new Gson().toJson(incoming).getBytes(StandardCharsets.UTF_8));

        deliverCaptor.getValue().handle("consumer-tag", delivery);

        verify(callback).accept(argThat(env ->
                        env.getType() == Envelope.Type.DATA
                                && env.getSenderId() == 42
                                && env.getDataSourceId() == 42
                                && env.getDataDestId() == 5
                                && "hello".equals(env.getPayload())
                                && env.getTreeVersion() == 1),
                eq(42));
    }

    @Test
    void sendToNeighbor_shouldPublishSerializedEnvelopeToNeighborQueue() throws Exception {
        Connection connection = mock(Connection.class);
        Channel channel = mock(Channel.class);
        PhysicalNode node = createNode(9, List.of(10), connection, channel);

        Envelope env = Envelope.election(9, 1, 3);
        node.sendToNeighbor(10, env);

        ArgumentCaptor<byte[]> bodyCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(channel).basicPublish(eq(""), eq("physical.node.10"), isNull(), bodyCaptor.capture());

        String json = new String(bodyCaptor.getValue(), StandardCharsets.UTF_8);
        Envelope published = new Gson().fromJson(json, Envelope.class);
        assertEquals(Envelope.Type.ELECTION, published.getType());
        assertEquals(9, published.getSenderId());
        assertEquals(1, published.getRootCandidateId());
        assertEquals(3, published.getTreeVersion());
    }

    @Test
    void sendToNeighbor_shouldThrowWhenNeighborIsUnknown() throws IOException, TimeoutException {
        Connection connection = mock(Connection.class);
        Channel channel = mock(Channel.class);
        PhysicalNode node = createNode(11, List.of(12), connection, channel);

        assertThrows(IllegalArgumentException.class,
                () -> node.sendToNeighbor(99, Envelope.heartbeat(11, 0)));
        verify(channel, never()).basicPublish(anyString(), anyString(), any(), any());
    }

    @Test
    void broadcast_shouldContinueSendingWhenOneNeighborPublishFails() throws Exception {
        Connection connection = mock(Connection.class);
        Channel channel = mock(Channel.class);
        PhysicalNode node = createNode(20, List.of(21, 22), connection, channel);

        doThrow(new IOException("broken link"))
                .when(channel).basicPublish(eq(""), eq("physical.node.21"), isNull(), any());

        node.broadcast(Envelope.rebuild(20, 2));

        verify(channel).basicPublish(eq(""), eq("physical.node.21"), isNull(), any());
        verify(channel).basicPublish(eq(""), eq("physical.node.22"), isNull(), any());
    }

    @Test
    void close_shouldCloseOnlyOpenChannelAndConnection() throws IOException, TimeoutException {
        Connection connection = mock(Connection.class);
        Channel channel = mock(Channel.class);
        PhysicalNode node = createNode(30, List.of(31), connection, channel);

        when(channel.isOpen()).thenReturn(false);
        when(connection.isOpen()).thenReturn(true);

        assertDoesNotThrow(node::close);

        verify(channel, never()).close();
        verify(connection).close();
    }

    @Test
    void close_shouldSwallowCloseExceptions() throws IOException, TimeoutException {
        Connection connection = mock(Connection.class);
        Channel channel = mock(Channel.class);
        PhysicalNode node = createNode(40, List.of(41), connection, channel);

        when(channel.isOpen()).thenReturn(true);
        doThrow(new TimeoutException("cannot close channel")).when(channel).close();

        assertDoesNotThrow(node::close);
    }
}

