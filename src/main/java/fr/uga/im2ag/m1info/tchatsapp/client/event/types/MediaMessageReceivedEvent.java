package fr.uga.im2ag.m1info.tchatsapp.client.event.types;

import fr.uga.im2ag.m1info.tchatsapp.client.event.system.Event;
import fr.uga.im2ag.m1info.tchatsapp.client.model.Message;

/**
 * Event representing the reception of a media message in a conversation.
 */
public class MediaMessageReceivedEvent extends MessageEvent {

    /** Constructor for the MediaMessageReceivedEvent class.
     *
     * @param source The source object that generated the event.
     * @param conversationId The ID of the conversation where the message was received.
     * @param message The media message that was received.
     */
    public MediaMessageReceivedEvent(Object source, String conversationId, Message message) {
        super(source, conversationId, message);
    }

    /** Gets the specific type of the event.
     *
     * @return The class type of the event.
     */
    @Override
    public Class<? extends Event> getEventType() {
        return MediaMessageReceivedEvent.class;
    }
}
