package fr.uga.im2ag.m1info.tchatsapp.client.command;

import fr.uga.im2ag.m1info.tchatsapp.common.MessageStatus;
import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.AckMessage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manager for tracking and handling pending commands that await acknowledgment.
 * Implements automatic timeout handling for expired commands.
 */
public class PendingCommandManager {
    private final Map<String, PendingCommand> pendingCommands;
    private final ScheduledExecutorService scheduler;
    private static final long DEFAULT_TIMEOUT_MS = 30_000;

    /**
     * Constructor for PendingCommandManager.
     */
    public PendingCommandManager() {
        this.pendingCommands = new ConcurrentHashMap<>();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PendingCommandTimeout");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Add a command to the pending queue.
     *
     * @param command the command to add
     */
    public void addPendingCommand(PendingCommand command) {
        pendingCommands.put(command.getCommandId(), command);

        scheduler.schedule(() -> {
            PendingCommand cmd = pendingCommands.remove(command.getCommandId());
            if (cmd != null) {
                cmd.onAckFailed("Timeout: no acknowledgment received");
            }
        }, DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Handle an acknowledgment message.
     *
     * @param ack the acknowledgment message
     */
    public void handleAck(AckMessage ack) {
        String msgId = ack.getAcknowledgedMessageId();
        PendingCommand command = pendingCommands.get(msgId);

        if (command == null) {
            return;
        }

        if (ack.getAckType() == MessageStatus.FAILED) {
            String reason = ack.getErrorReason() != null ? ack.getErrorReason() : "Unknown error";
            command.onAckFailed(reason);
            pendingCommands.remove(msgId);
        } else {
            if (command.onAckReceived(ack.getAckType(), ack.getAdditionalData()) || ack.getAckType() == MessageStatus.READ) {
                pendingCommands.remove(msgId);
            }
        }
    }

    /**
     * Remove a specific command from the pending queue.
     *
     * @param commandId the ID of the command to remove
     * @return the removed command, or null if not found
     */
    public PendingCommand removePendingCommand(String commandId) {
        return pendingCommands.remove(commandId);
    }

    /**
     * Shutdown the manager and clear all pending commands.
     */
    public void shutdown() {
        scheduler.shutdownNow();
        pendingCommands.clear();
        // TODO: Save pending commands to persistent storage if needed
    }
}