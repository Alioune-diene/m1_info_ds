package fr.uga.im2ag.m1info.server.handlers;

import fr.uga.im2ag.m1info.common.messagefactory.ProtocolMessage;
import fr.uga.im2ag.m1info.server.ChatServerContext;
import fr.uga.im2ag.m1info.common.model.UserInfo;
import fr.uga.im2ag.m1info.server.util.AckHelper;

public abstract class ValidatingServerMessageHandler extends ServerMessageHandler {
    protected boolean validateSenderRegistered(ProtocolMessage message, ChatServerContext ctx) {
        if (!ctx.isClientRegistered(message.getFrom())) {
            LOG.warning(() -> String.format(
                    "Message from unregistered user %d (type: %s)",
                    message.getFrom(), message.getMessageType()
            ));
            AckHelper.sendFailedAck(ctx, message, "Sender not registered");
            return false;
        }
        return true;
    }

    protected boolean validateRecipientExists(ProtocolMessage message, ChatServerContext ctx) {
        if (!ctx.isClientRegistered(message.getTo())) {
            LOG.warning(() -> String.format(
                    "Message to non-existent user %d (type: %s)",
                    message.getTo(), message.getMessageType()
            ));
            AckHelper.sendFailedAck(ctx, message, "Recipient not found");
            return false;
        }
        return true;
    }

    protected boolean validateSenderMemberOfGroup(ProtocolMessage message, ChatServerContext ctx) {
        UserInfo sender = ctx.getUserRepository().findById(message.getFrom());
        //if (!sender.isMemberOfGroup(message.getTo())) {
        if (!ctx.getGroupRepository().findById(message.getTo()).hasMember(message.getFrom())) {
            LOG.warning(() -> String.format(
                    "User %d is not a member of group %d (type: %s)",
                    message.getFrom(), message.getTo(), message.getMessageType()
            ));
            AckHelper.sendFailedAck(ctx, message, "Sender not member of group");
            return false;
        }
        return true;
    }

    protected boolean checkContactRelationship(int from, int to, ChatServerContext ctx) {
        UserInfo sender = ctx.getUserRepository().findById(from);
        return sender.hasContact(to);
    }

    protected boolean isGroupId(int id, ChatServerContext ctx) {
        return ctx.getGroupRepository().findById(id) != null;
    }

    protected void sendPacketToRecipient(ProtocolMessage message, ChatServerContext ctx) {
        ctx.sendToRecipient(message);
    }
}
