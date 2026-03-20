package fr.uga.im2ag.m1info.client.handlers;

import fr.uga.im2ag.m1info.client.ClientController;
import fr.uga.im2ag.m1info.client.model.Message;
import fr.uga.im2ag.m1info.common.MessageType;
import fr.uga.im2ag.m1info.common.messagefactory.HistorySyncMessage;
import fr.uga.im2ag.m1info.common.messagefactory.ProtocolMessage;
import fr.uga.im2ag.m1info.common.model.StoredMessage;

public class HistorySyncClientHandler extends ClientMessageHandler {
    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(HistorySyncClientHandler.class.getName());

    @Override
    public void handle(ProtocolMessage protocolMessage, ClientController context) {
        if (!(protocolMessage instanceof HistorySyncMessage syncMsg)) return;

        int restored = 0;
        for (StoredMessage stored : syncMsg.getMessages()) {
            if (restoreMessage(stored, context)) {
                restored++;
            }
        }

        LOG.info("[HistorySync] Restored " + restored + " messages from server history.");
    }

    @Override
    public boolean canHandle(MessageType messageType) {
        return messageType == MessageType.HISTORY_SYNC;
    }

    private boolean restoreMessage(StoredMessage stored, ClientController context) {
        var conversation = switch (stored.getConversationId().startsWith("group_") ? "group" : "private") {
            case "group" -> {
                int groupId = Integer.parseInt(stored.getConversationId().substring(6));
                yield context.getOrCreateGroupConversation(groupId);
            }
            default -> {
                int peerId = (stored.getFrom() == context.getClientId())
                        ? stored.getTo()
                        : stored.getFrom();
                yield context.getOrCreatePrivateConversation(peerId);
            }
        };

        if (conversation.getMessage(stored.getMessageId()) != null) {
            return false;
        }

        Message msg = new Message(
                stored.getMessageId(),
                stored.getFrom(),
                stored.getTo(),
                stored.getContent() != null ? stored.getContent() : "",
                stored.getTimestamp(),
                stored.getReplyToMessageId()
        );
        msg.setRead(true);

        conversation.addMessage(msg);
        context.getConversationRepository().update(conversation.getConversationId(), conversation);
        return true;
    }
}