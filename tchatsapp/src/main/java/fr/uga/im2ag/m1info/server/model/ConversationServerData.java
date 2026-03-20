package fr.uga.im2ag.m1info.server.model;

import fr.uga.im2ag.m1info.common.model.StoredMessage;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class ConversationServerData implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String conversationId;
    private final ArrayDeque<StoredMessage> messages;

    public ConversationServerData(String conversationId) {
        this.conversationId = conversationId;
        this.messages = new ArrayDeque<>();
    }

    public String getConversationId() {
        return conversationId;
    }

    public synchronized void addMessage(StoredMessage message, int maxMessages) {
        messages.addLast(message);
        while (messages.size() > maxMessages) {
            messages.pollFirst();
        }
    }

    public synchronized List<StoredMessage> getMessages() {
        return new ArrayList<>(messages);
    }
}
