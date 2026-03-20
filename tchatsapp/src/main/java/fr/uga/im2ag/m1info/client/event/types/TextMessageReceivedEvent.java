package fr.uga.im2ag.m1info.client.event.types;

import fr.uga.im2ag.m1info.client.event.system.Event;
import fr.uga.im2ag.m1info.client.model.Message;

/**
 * Event representing the reception of a text message in a conversation.
 */
public class TextMessageReceivedEvent extends MessageEvent {

    /** Constructor for the TextMessageReceivedEvent class.
     *
     * @param source The source object that generated the event.
     * @param conversationId The ID of the conversation where the message was received.
     * @param message The text message that was received.
     */
    public TextMessageReceivedEvent(Object source, String conversationId, Message message) {
        super(source, conversationId, message);
    }

    /** Gets the specific type of the event.
     *
     * @return The class type of the event.
     */
    @Override
    public Class<? extends Event> getEventType() {
        return TextMessageReceivedEvent.class;
    }
}
