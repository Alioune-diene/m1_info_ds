package fr.uga.im2ag.m1info.physical;

import com.google.gson.Gson;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DeliverCallback;
import fr.uga.im2ag.m1info.virtual.VirtualEnvelope;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.*;

public class PhysicalHostService implements MessageHandler {

    private static final Logger LOG = Logger.getLogger(PhysicalHostService.class.getName());

    public static final String VIRT_CMD_SUFFIX = ".virt";

    private final int physicalId;
    private final SpanningTreeManager manager;
    private final Gson gson = new Gson();

    private final Set<Integer> hostedVirtuals = ConcurrentHashMap.newKeySet();
    private final Channel cmdChannel;
    private volatile MessageHandler appHandler;

    public PhysicalHostService(int physicalId, SpanningTreeManager manager, Connection connection) throws IOException {
        this.physicalId = physicalId;
        this.manager = manager;
        this.cmdChannel = connection.createChannel();

        String cmdQueue = cmdQueue();
        cmdChannel.queueDeclare(cmdQueue, false, false, false, null);

        DeliverCallback cb = (tag, delivery) -> {
            String raw = new String(delivery.getBody(), StandardCharsets.UTF_8);
            if (raw.startsWith(VirtualEnvelope.PAYLOAD_PREFIX)) {
                String json = raw.substring(VirtualEnvelope.PAYLOAD_PREFIX.length());
                VirtualEnvelope env = gson.fromJson(json, VirtualEnvelope.class);
                handleVirtualCommand(env);
            } else {
                LOG.warning(() -> "[P" + physicalId + "] virtual command not recognized: " + raw);
            }
        };
        cmdChannel.basicConsume(cmdQueue, true, cb, tag -> {});
        LOG.info(() -> "[P" + physicalId + "] PhysicalHostService ready - queue : " + cmdQueue);
    }

    public void setAppHandler(MessageHandler appHandler) {
        this.appHandler = appHandler;
    }

    @Override
    public void onMessage(Message msg) {
        String payload = msg.getPayload();

        if (payload != null && payload.startsWith(VirtualEnvelope.PAYLOAD_PREFIX)) {
            String json = payload.substring(VirtualEnvelope.PAYLOAD_PREFIX.length());
            VirtualEnvelope env = gson.fromJson(json, VirtualEnvelope.class);
            deliverToLocalVirtual(env);
        } else {
            MessageHandler h = appHandler;
            if (h != null) { h.onMessage(msg); }
        }
    }

    private void handleVirtualCommand(VirtualEnvelope env) {
        switch (env.getType()) {

            case VIRTUAL_REGISTER -> {
                int vId = env.getVirtualSourceId();
                hostedVirtuals.add(vId);
                LOG.info(() -> "[P" + physicalId + "] Virtual node V" + vId + " registered - hosted: " + hostedVirtuals);
                sendAckToVirtual(vId);
            }

            case VIRTUAL_HEARTBEAT -> {
                sendAckToVirtual(env.getVirtualSourceId());
            }

            case VIRTUAL_DATA -> {
                String routed = VirtualEnvelope.PAYLOAD_PREFIX + gson.toJson(env);
                manager.sendData(SpanningTreeManager.BROADCAST_DEST, routed);
                LOG.info(() -> "[P" + physicalId + "] Virtual broadcast V" + env.getVirtualSourceId() + " -> V" + env.getVirtualDestId() + " : \"" + env.getPayload() + "\"");
            }

            default -> LOG.warning(() -> "[P" + physicalId + "] Virtual command not recognized: " + env);
        }
    }

    private void deliverToLocalVirtual(VirtualEnvelope env) {
        if (env.getType() != VirtualEnvelope.Type.VIRTUAL_DATA) { return; }
        int destId = env.getVirtualDestId();

        if (!hostedVirtuals.contains(destId)) { return; }

        String json = VirtualEnvelope.PAYLOAD_PREFIX + gson.toJson(env);
        String queue = "virtual.node." + destId;
        try {
            cmdChannel.basicPublish("", queue, null, json.getBytes(StandardCharsets.UTF_8));
            LOG.info(() -> "[P" + physicalId + "] Delivered V" + env.getVirtualSourceId() + "  V" + destId + " : \"" + env.getPayload() + "\"");
        } catch (IOException e) {
            LOG.log(Level.WARNING, "[P" + physicalId + "] Delivery failure for V" + destId + " (hosted: " + hostedVirtuals + ")", e);
        }
    }

    private void sendAckToVirtual(int virtualId) {
        VirtualEnvelope ack = VirtualEnvelope.heartbeatAck(virtualId, physicalId);
        String json  = VirtualEnvelope.PAYLOAD_PREFIX + gson.toJson(ack);
        String queue = "virtual.node." + virtualId;
        try {
            cmdChannel.basicPublish("", queue, null, json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOG.log(Level.WARNING, "[P" + physicalId + "] Failed to send heartbeat ACK to V" + virtualId + " (hosted: " + hostedVirtuals + ")", e);
        }
    }

    public Set<Integer> getHostedVirtuals() { return Set.copyOf(hostedVirtuals); }

    private String cmdQueue() { return "physical.node." + physicalId + VIRT_CMD_SUFFIX; }
}
