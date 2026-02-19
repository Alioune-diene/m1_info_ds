package fr.uga.im2ag.m1info.tchatsapp.client.event.types;

import fr.uga.im2ag.m1info.tchatsapp.client.event.system.Event;
import fr.uga.im2ag.m1info.tchatsapp.client.model.Message;

public class ReactionMessageReceivedEvent extends MessageEvent {

    public ReactionMessageReceivedEvent(Object source, String conversationId, Message message) {
        super(source, conversationId, message);
    }

    @Override
    public Class<? extends Event> getEventType() {
        return ReactionMessageReceivedEvent.class;
    }

}
