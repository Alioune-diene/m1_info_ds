package fr.uga.im2ag.m1info.tchatsapp.common.messagefactory;

import fr.uga.im2ag.m1info.tchatsapp.common.MessageType;
import fr.uga.im2ag.m1info.tchatsapp.common.model.StoredMessage;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

public class HistorySyncMessage extends ProtocolMessage {
    @Serial
    private static final long serialVersionUID = 1L;

    private List<StoredMessage> messages;

    public HistorySyncMessage() {
        super(MessageType.HISTORY_SYNC, -1, -1);
        this.messages = new ArrayList<>();
    }

    public List<StoredMessage> getMessages() {
        return messages;
    }

    public HistorySyncMessage setMessages(List<StoredMessage> messages) {
        this.messages = new ArrayList<>(messages);
        return this;
    }

    @Override
    public String toString() {
        return "HistorySyncMessage{from=" + from + ", to=" + to
                + ", messages=" + messages.size() + "}";
    }
}
