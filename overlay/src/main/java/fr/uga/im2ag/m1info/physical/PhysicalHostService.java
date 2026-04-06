package fr.uga.im2ag.m1info.physical;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DeliverCallback;

import fr.uga.im2ag.m1info.virtual.VirtualEnvelope;
/**
 * The bridge between the physical layer and the virtual layer.
 *
 * Each physical node can "host" one or more virtual nodes.
 * A virtual node is a logical process that doesn't have its own physical network —
 * it relies on the physical node's spanning tree to communicate.
 *
 * PhysicalHostService does two things:
 *
 *  1. LISTENS on a special "virtual command" queue (physical.node.<id>.virt)
 *     for commands from virtual nodes (REGISTER, HEARTBEAT, DATA).
 *
 *  2. DELIVERS incoming virtual messages to the correct local virtual node's queue
 *     (virtual.node.<virtualId>) when a message arrives through the physical tree.
 *
 * Flow - Outgoing (virtual → physical → network):
 *   VirtualNode sends to physical.node.<hostId>.virt
 *   → PhysicalHostService.handleVirtualCommand()
 *   → SpanningTreeManager.sendData() [broadcasts through the spanning tree]
 *
 * Flow - Incoming (network → physical → virtual):
 *   PhysicalNode receives a DATA envelope carrying a VirtualEnvelope
 *   → SpanningTreeManager delivers it to the app handler (this.onMessage())
 *   → PhysicalHostService.deliverToLocalVirtual()
 *   → publishes to virtual.node.<destVirtualId> queue
 *
 * Implements MessageHandler so it can be plugged into SpanningTreeManager as the app handler.
 */

public class PhysicalHostService implements MessageHandler {

    private static final Logger LOG = Logger.getLogger(PhysicalHostService.class.getName());
      /**
     * The suffix added to a physical node's queue name to create the "virtual command" queue.
     * Example: physical node 2 → virtual command queue = "physical.node.2.virt"
     */
    public static final String VIRT_CMD_SUFFIX = ".virt";

    private final int physicalId;  // ID of the physical node we're serving
    private final SpanningTreeManager manager;// used to broadcast virtual data messages
    private final Gson gson = new Gson();
     /**
     * Set of virtual node IDs currently hosted on this physical node.
     * Virtual nodes register themselves here, then can send/receive data.
     */

    private final Set<Integer> hostedVirtuals = ConcurrentHashMap.newKeySet();
    private final Channel cmdChannel;  // dedicated channel for virtual command messages
    private volatile MessageHandler appHandler;  // optional extra handler for non-virtual messages
     /**
     * Creates a PhysicalHostService and starts listening on the virtual command queue.
     *
     * @param physicalId  ID of the hosting physical node
     * @param manager     the spanning tree manager (for broadcasting virtual data)
     * @param connection  the RabbitMQ connection (shared with PhysicalNode)
     */

    public PhysicalHostService(int physicalId, SpanningTreeManager manager, Connection connection) throws IOException {
        this.physicalId = physicalId;
        this.manager = manager;
        this.cmdChannel = connection.createChannel();
         // Declare the virtual command queue: this is how virtual nodes talk to us
        String cmdQueue = cmdQueue();
        cmdChannel.queueDeclare(cmdQueue, false, false, false, null);
         // Set up the listener: parse incoming virtual envelopes and dispatch them
        DeliverCallback cb = (tag, delivery) -> {
            String raw = new String(delivery.getBody(), StandardCharsets.UTF_8);
            if (raw.startsWith(VirtualEnvelope.PAYLOAD_PREFIX)) {
                // Strip the "V|" prefix, then deserialize the JSON
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
     /** set a fallback handler for non-virtual application messages. */
    public void setAppHandler(MessageHandler appHandler) {
        this.appHandler = appHandler;
    }

    /**
     * Called by SpanningTreeManager when a DATA message arrives at this physical node.
     * If the payload contains a VirtualEnvelope (prefixed with "V|"),
     * we try to deliver it to a locally hosted virtual node.
     * Otherwise, pass it to the optional appHandler.
     */

    @Override
    public void onMessage(Message msg) {
        String payload = msg.getPayload();

        if (payload != null && payload.startsWith(VirtualEnvelope.PAYLOAD_PREFIX)) {
            // This is a virtual message routed through the physical spanning tree
            String json = payload.substring(VirtualEnvelope.PAYLOAD_PREFIX.length());
            VirtualEnvelope env = gson.fromJson(json, VirtualEnvelope.class);
            deliverToLocalVirtual(env);// check if the dest virtual is hosted here
        } else {
             // Regular (non-virtual) physical message — forward to app handler if set
            MessageHandler h = appHandler;
            if (h != null) { h.onMessage(msg); }
        }
    }

    /**
     * Handles commands coming directly from local virtual nodes on the cmd queue.
     *
     * Three command types:
     *  - VIRTUAL_REGISTER:  Virtual node V<id> is now hosted here → record it, send ACK
     *  - VIRTUAL_HEARTBEAT: Virtual node is still alive → send ACK (so it doesn't migrate)
     *  - VIRTUAL_DATA:      Virtual node wants to send data → broadcast via spanning tree
     */

    private void handleVirtualCommand(VirtualEnvelope env) {
        switch (env.getType()) {

            case VIRTUAL_REGISTER -> {
                // A virtual node has chosen this physical node as its host
                int vId = env.getVirtualSourceId();
                hostedVirtuals.add(vId); // register it locally
                LOG.info(() -> "[P" + physicalId + "] Virtual node V" + vId + " registered - hosted: " + hostedVirtuals);
                sendAckToVirtual(vId); // confirm registration
            }

            case VIRTUAL_HEARTBEAT -> {
                 // Virtual node is checking in — reply to prevent it from migrating
                sendAckToVirtual(env.getVirtualSourceId());
            }

            case VIRTUAL_DATA -> {
                  // Virtual node wants to send data to another virtual node
                // We wrap the VirtualEnvelope in a physical DATA message and broadcast it
                // through the spanning tree so it reaches every physical node
                String routed = VirtualEnvelope.PAYLOAD_PREFIX + gson.toJson(env);
                manager.sendData(SpanningTreeManager.BROADCAST_DEST, routed);
                LOG.info(() -> "[P" + physicalId + "] Virtual broadcast V" + env.getVirtualSourceId() + " -> V" + env.getVirtualDestId() + " : \"" + env.getPayload() + "\"");
            }

            default -> LOG.warning(() -> "[P" + physicalId + "] Virtual command not recognized: " + env);
        }
    }
    /**
     * Tries to deliver an incoming virtual message to a locally hosted virtual node.
     *
     * Only handles VIRTUAL_DATA type (other types wouldn't come through the spanning tree).
     * If the destination virtual node is hosted here, publishes directly to its queue.
     * If not, does nothing (the message was broadcast and some other physical host will handle it).
     */

    private void deliverToLocalVirtual(VirtualEnvelope env) {
        if (env.getType() != VirtualEnvelope.Type.VIRTUAL_DATA) { return; }
        int destId = env.getVirtualDestId();
         // Only deliver if the destination virtual node is actually running here
        if (!hostedVirtuals.contains(destId)) { return; }
        
        // Publish the message to the virtual node's own RabbitMQ queue
        String json = VirtualEnvelope.PAYLOAD_PREFIX + gson.toJson(env);
        String queue = "virtual.node." + destId;
        try {
            cmdChannel.basicPublish("", queue, null, json.getBytes(StandardCharsets.UTF_8));
            LOG.info(() -> "[P" + physicalId + "] Delivered V" + env.getVirtualSourceId() + "  V" + destId + " : \"" + env.getPayload() + "\"");
        } catch (IOException e) {
            LOG.log(Level.WARNING, "[P" + physicalId + "] Delivery failure for V" + destId + " (hosted: " + hostedVirtuals + ")", e);
        }
    }
      /**
     * Sends a VIRTUAL_HEARTBEAT_ACK to a virtual node's own queue.
     * This tells the virtual node: "I (physical node <physicalId>) acknowledge your heartbeat.
     *  You don't need to migrate."
     */

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
