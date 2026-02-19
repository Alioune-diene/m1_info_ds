package fr.uga.im2ag.m1info.tchatsapp.client.command;

import fr.uga.im2ag.m1info.tchatsapp.common.MessageStatus;

import java.util.Map;

/**
 * Interface for commands that await acknowledgment from the server.
 * Implements the Command pattern for tracking pending operations.
 */
public interface PendingCommand {
    /**
     * Get the unique identifier for this command.
     *
     * @return the command ID
     */
    String getCommandId();

    /**
     * Called when an acknowledgment is received for this command.
     *
     * @param ackType the type of acknowledgment received
     * @param params  additional parameters related to the acknowledgment
     * @return true if the command can be considered complete, and removed from pending list, false otherwise
     */
    boolean onAckReceived(MessageStatus ackType, Map<String, Object> params);

    /**
     * Called when the command fails.
     *
     * @param reason the reason for failure
     */
    void onAckFailed(String reason);
}