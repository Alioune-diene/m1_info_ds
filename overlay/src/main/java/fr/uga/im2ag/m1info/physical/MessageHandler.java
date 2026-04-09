package fr.uga.im2ag.m1info.physical;

/**
 * A simple callback interface used to handle incoming messages
 * Any class that wants to react to received messages implements this interface
 */
@FunctionalInterface
public interface MessageHandler {

    /**
     * Called by SpanningTreeManager when a new message is received from the network.
     * The message is already parsed and ready to be processed.
     * The implementation of this method defines how the application reacts to incoming messages.
     * @param message the received message, containing the sender's ID and the message content
     */
    void onMessage(Message message);
}