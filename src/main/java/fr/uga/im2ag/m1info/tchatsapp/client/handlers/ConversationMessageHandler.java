package fr.uga.im2ag.m1info.tchatsapp.client.handlers;

import fr.uga.im2ag.m1info.tchatsapp.client.ClientController;
import fr.uga.im2ag.m1info.tchatsapp.client.event.system.EventBus;
import fr.uga.im2ag.m1info.tchatsapp.client.event.types.FileTransferProgressEvent;
import fr.uga.im2ag.m1info.tchatsapp.client.event.types.MediaMessageReceivedEvent;
import fr.uga.im2ag.m1info.tchatsapp.client.event.types.ReactionMessageReceivedEvent;
import fr.uga.im2ag.m1info.tchatsapp.client.event.types.TextMessageReceivedEvent;
import fr.uga.im2ag.m1info.tchatsapp.client.media.MediaManager;
import fr.uga.im2ag.m1info.tchatsapp.client.model.ConversationClient;
import fr.uga.im2ag.m1info.tchatsapp.client.model.Message;
import fr.uga.im2ag.m1info.tchatsapp.client.model.VirtualMedia;
import fr.uga.im2ag.m1info.tchatsapp.common.MessageStatus;
import fr.uga.im2ag.m1info.tchatsapp.common.MessageType;
import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.MediaMessage;
import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.ProtocolMessage;
import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.ReactionMessage;
import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.TextMessage;

import java.util.Arrays;

public class ConversationMessageHandler extends ClientMessageHandler {
    private MediaManager mediaManager;

    @Override
    public void initialize(ClientHandlerContext context) {
        this.mediaManager = context.getMediaManager();
    }

    @Override
    public void handle(ProtocolMessage message, ClientController context) {
        if (message instanceof MediaMessage) {
            handleMediaMessage((MediaMessage) message, context);
        } else if (message instanceof TextMessage) {
            handleTextMessage((TextMessage) message, context);
        } else if (message instanceof ReactionMessage) {
            handleReactionMessage((ReactionMessage) message, context);
        } else {
            throw new IllegalArgumentException("Invalid message type for ConversationMessageHandler");
        }
    }

    private void handleMediaMessage(MediaMessage mediaMsg, ClientController context) {
        VirtualMedia completedMedia = mediaManager.processChunk(mediaMsg);

        int recipientId = mediaMsg.getFrom();
        int toUserId = mediaMsg.getTo();

        ConversationClient conversation;
        if (toUserId == context.getClientId()) {
            conversation = context.getOrCreatePrivateConversation(mediaMsg.getFrom());
        } else if (context.getGroupRepository().findById(toUserId) != null) {
            conversation = context.getOrCreateGroupConversation(toUserId);
        } else {
            throw new IllegalArgumentException("Message recipient not recognized by ConversationMessageHandler");
        }
        String conversationId = conversation.getConversationId();

        if (completedMedia != null) {
            Message msg = new Message(mediaMsg.getMessageId(), recipientId, toUserId, "[Media Message]", mediaMsg.getTimestamp(), mediaMsg.getReplyToMessageId());
            msg.setAttachedMedia(completedMedia);
            conversation.addMessage(msg);
            context.getConversationRepository().update(conversationId, conversation);

            EventBus.getInstance().publish(new MediaMessageReceivedEvent(this, conversationId, msg));

            EventBus.getInstance().publish(new FileTransferProgressEvent(
                    this,
                    conversationId,
                    completedMedia.getMediaId(),
                    completedMedia.getFileName(),
                    completedMedia.getFileSize(),
                    0,
                    true
            ));
        } else {
            MediaManager.TransferProgress progress = mediaManager.getTransferProgress(Arrays.toString(mediaMsg.getContent()));

            if (progress != null) {
                EventBus.getInstance().publish(new FileTransferProgressEvent(
                        this,
                        conversationId,
                        progress.mediaId,
                        progress.fileName,
                        progress.bytesReceived,
                        progress.chunksReceived,
                        false
                ));
            }
        }

        context.sendAck(mediaMsg, MessageStatus.DELIVERED);
        context.sendAck(mediaMsg, MessageStatus.READ); // TODO: Remove this line when read receipts are implemented properly
    }

    private void handleTextMessage(TextMessage textMsg, ClientController context) {
        int recipientId = textMsg.getTo();
        ConversationClient conversation;
        if (recipientId == context.getClientId()) {
            conversation = context.getOrCreatePrivateConversation(textMsg.getFrom());
        } else if (context.getGroupRepository().findById(recipientId) != null) {
            conversation = context.getOrCreateGroupConversation(recipientId);
        } else {
            throw new IllegalArgumentException("Message recipient not recognized by ConversationMessageHandler");
        }
        String conversationId = conversation.getConversationId();

        Message msg = new Message(
                textMsg.getMessageId(),
                textMsg.getFrom(),
                textMsg.getTo(),
                textMsg.getContent(),
                textMsg.getTimestamp(),
                textMsg.getReplyToMessageId()
                );
        conversation.addMessage(msg);
        context.getConversationRepository().update(conversationId, conversation);
        EventBus.getInstance().publish(new TextMessageReceivedEvent(this, conversationId, msg));

        context.sendAck(textMsg, MessageStatus.DELIVERED);
        context.sendAck(textMsg, MessageStatus.READ); // TODO: Remove this line when read receipts are implemented properly
    }

    private void handleReactionMessage(ReactionMessage reactionMsg, ClientController context){
        int recipientId = reactionMsg.getTo();
        ConversationClient conversation;
        if (recipientId == context.getClientId()) {
            conversation = context.getOrCreatePrivateConversation(reactionMsg.getFrom());
        } else if (context.getGroupRepository().findById(recipientId) != null) {
            conversation = context.getOrCreateGroupConversation(recipientId);
        } else {
            throw new IllegalArgumentException("Message recipient not recognized by ConversationMessageHandler");
        }

        String conversationId = conversation.getConversationId();
        conversation.addReactionToMessage(
                reactionMsg.getReactionToMessageId(),
                reactionMsg.getContent(),
                reactionMsg.getFrom()
        );
        context.getConversationRepository().update(conversationId, conversation);
        EventBus.getInstance().publish(new ReactionMessageReceivedEvent(this, conversationId, conversation.getMessage(reactionMsg.getReactionToMessageId())));

        context.sendAck(reactionMsg, MessageStatus.DELIVERED);
        context.sendAck(reactionMsg, MessageStatus.READ); // TODO: Remove this line when read receipts are implemented properly
    }

    @Override
    public boolean canHandle(MessageType messageType) {
        return messageType == MessageType.TEXT || messageType == MessageType.MEDIA || messageType == MessageType.REACTION;
    }
}
