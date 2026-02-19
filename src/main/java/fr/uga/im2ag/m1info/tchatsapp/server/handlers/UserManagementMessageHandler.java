package fr.uga.im2ag.m1info.tchatsapp.server.handlers;

import fr.uga.im2ag.m1info.tchatsapp.common.MessageType;
import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.ManagementMessage;
import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.MessageFactory;
import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.ProtocolMessage;
import fr.uga.im2ag.m1info.tchatsapp.common.model.GroupInfo;
import fr.uga.im2ag.m1info.tchatsapp.server.ChatServerContext;
import fr.uga.im2ag.m1info.tchatsapp.common.model.UserInfo;
import fr.uga.im2ag.m1info.tchatsapp.server.util.AckHelper;

import java.util.HashSet;
import java.util.Set;

/**
 * Handler for user management messages such as user creation, connection, and contact management.
 */
public class UserManagementMessageHandler extends ValidatingServerMessageHandler {
    @Override
    public void handle(ProtocolMessage message, ChatServerContext serverContext) {
        if (!(message instanceof ManagementMessage userMsg)) {
            throw new IllegalArgumentException("Invalid message type for UserManagementHandler");
        }

        switch (userMsg.getMessageType()) {
            case REMOVE_CONTACT -> removeContact(serverContext, userMsg);
            case UPDATE_PSEUDO -> updatePseudo(serverContext, userMsg);
            default -> throw new IllegalArgumentException("Unsupported management message type");
        }
    }

    @Override
    public boolean canHandle(MessageType messageType) {
        return messageType == MessageType.REMOVE_CONTACT || messageType == MessageType.UPDATE_PSEUDO;
    }

    /**
     * Handles removing a contact for a user.
     *
     * @param serverContext    the server context
     * @param managementMessage the management message containing the remove contact request
     */
    private void removeContact(ChatServerContext serverContext, ManagementMessage managementMessage) {
        int from = managementMessage.getFrom();
        int contactId = managementMessage.getParamAsType("contactId", Integer.class);

        if (!validateSenderRegistered(managementMessage, serverContext)) { return; }
        if (!checkContactRelationship(from, contactId, serverContext)) {
            ServerMessageHandler.LOG.warning(String.format("User %d tried to remove non-existing contact %d", from, contactId));
            AckHelper.sendFailedAck(serverContext, managementMessage, "Contact not found");
            return;
        }

        try {
            UserInfo user = serverContext.getUserRepository().findById(from);
            UserInfo contact = serverContext.getUserRepository().findById(contactId);

            user.removeContact(contactId);
            serverContext.getUserRepository().update(user.getId(), user);

            contact.removeContact(from);
            serverContext.getUserRepository().update(contact.getId(), contact);

            ServerMessageHandler.LOG.info(String.format("User %d removed contact %d", from, contactId));

            serverContext.sendToClient((
                    (ManagementMessage) MessageFactory.create(MessageType.REMOVE_CONTACT, from, contactId))
                    .addParam("contactId", from), contactId
            );
            AckHelper.sendSentAck(serverContext, managementMessage);

        } catch (Exception e) {
            ServerMessageHandler.LOG.severe(String.format("Error while removing contact %d for user %d: %s", contactId, from, e.getMessage()));
            AckHelper.sendFailedAck(serverContext, managementMessage, "Internal server error");
        }
    }

    /**
     * Handles updating a user's pseudo (username).
     *
     * @param serverContext    the server context
     * @param managementMessage the management message containing the update pseudo request
     */
    private void updatePseudo(ChatServerContext serverContext, ManagementMessage managementMessage) {
        int from = managementMessage.getFrom();
        String newPseudo = managementMessage.getParamAsType("newPseudo", String.class);

        UserInfo user = serverContext.getUserRepository().findById(from);
        if (user == null) {
            ServerMessageHandler.LOG.warning(String.format("User %d not found while trying to update pseudo", from));
            AckHelper.sendFailedAck(serverContext, managementMessage, "User not found");
            return;
        }

        if (newPseudo == null || newPseudo.isEmpty()) {
            ServerMessageHandler.LOG.warning(String.format("User %d provided invalid new pseudo", from));
            AckHelper.sendFailedAck(serverContext, managementMessage, "Invalid pseudo");
            return;
        }

        user.setUsername(newPseudo);
        serverContext.getUserRepository().update(user.getId(), user);
        ServerMessageHandler.LOG.info(String.format("User %d updated pseudo to %s", from, newPseudo));

        // TODO: Find a less barbaric way to notify groups members of pseudo change
        // Maintaining a list of which users are in which groups would be more civilized
        Set<Integer> recipients = new HashSet<>(user.getContacts());
        for (GroupInfo group : serverContext.getGroupRepository().findAll()) {
            if (group.hasMember(from)) {
                recipients.addAll(group.getMembersId());
            }
        }
        recipients.remove(from);

        for (int recipientId : recipients) {
            if (serverContext.isClientConnected(recipientId)) {
                serverContext.sendToClient(((ManagementMessage) MessageFactory.create(MessageType.UPDATE_PSEUDO, from, recipientId))
                        .addParam("contactId", from)
                        .addParam("newPseudo", newPseudo), recipientId
                );
            }
        }

        AckHelper.sendSentAck(serverContext, managementMessage);
    }
}
