package fr.uga.im2ag.m1info.tchatsapp.server.handlers;

import fr.uga.im2ag.m1info.tchatsapp.common.KeyInMessage;
import fr.uga.im2ag.m1info.tchatsapp.common.MessageStatus;
import fr.uga.im2ag.m1info.tchatsapp.common.MessageType;
import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.AckMessage;
import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.ManagementMessage;
import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.MessageFactory;
import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.ProtocolMessage;
import fr.uga.im2ag.m1info.tchatsapp.common.model.GroupInfo;
import fr.uga.im2ag.m1info.tchatsapp.server.ChatServerContext;
import fr.uga.im2ag.m1info.tchatsapp.common.model.UserInfo;
import fr.uga.im2ag.m1info.tchatsapp.server.util.AckHelper;

public class GroupMessageHandler extends ServerMessageHandler {
    @Override
    public void handle(ProtocolMessage message, ChatServerContext serverContext) {
        if (!(message instanceof ManagementMessage userMsg)) {
            throw new IllegalArgumentException("Invalid message type for ErrorMessageHandler");
        }

        switch (userMsg.getMessageType()) {
            case CREATE_GROUP -> createGroup(serverContext, userMsg);
            case LEAVE_GROUP -> leaveGroup(serverContext, userMsg);
            case ADD_GROUP_MEMBER -> addGroupMember(serverContext, userMsg);
            case REMOVE_GROUP_MEMBER -> removeGroupMember(serverContext, userMsg);
            case UPDATE_GROUP_NAME -> updateGroupName(serverContext, userMsg);
            case DELETE_GROUP -> deleteGroup(serverContext, userMsg);
            default -> throw new IllegalArgumentException("Unsupported group management message type");
        }
    }

    private static void updateGroupName(ChatServerContext serverContext, ManagementMessage groupManagementMessage) {
        int adminGroup = groupManagementMessage.getFrom();
        int groupId = groupManagementMessage.getTo();

        String newGroupName= groupManagementMessage.getParamAsType(KeyInMessage.GROUP_NAME, String.class);

        GroupInfo group = serverContext.getGroupRepository().findById(groupId);
        if (group == null) {
            LOG.warning(String.format("Group %d not found while trying to update name", groupId));
            return;
        }

        if (group.getAdminId() != adminGroup){
            LOG.warning(String.format("User %d is not the admin of group %d. Real admin is %d", adminGroup, groupId, group.getAdminId()));
            return;
        }

        if (newGroupName == null || newGroupName.isEmpty()) {
            LOG.warning(String.format("User %d provided invalid new groupName for group %d", adminGroup, groupId));
            AckHelper.sendFailedAck(serverContext, groupManagementMessage, "Invalid new group name (cannot be empty)");
            return;
        }

        group.setGroupName(newGroupName);
        serverContext.getGroupRepository().update(group.getGroupId(), group);
        LOG.info(String.format("Group %d updated name to %s", groupId, newGroupName));

        for (int memberId : group.getMembersId()) {
            if (serverContext.isClientConnected(memberId)) {
                serverContext.sendToClient(((ManagementMessage) MessageFactory.create(MessageType.UPDATE_GROUP_NAME, groupId, memberId))
                        .addParam(KeyInMessage.GROUP_ID, groupId)
                        .addParam(KeyInMessage.GROUP_NAME, newGroupName),
                        memberId
                );
            }
        }
        AckHelper.sendSentAck(serverContext, groupManagementMessage);
    }

    private static void removeGroupMember(ChatServerContext serverContext, ManagementMessage groupManagementMessage) {
        int adminGroup = groupManagementMessage.getFrom();
        int groupId = groupManagementMessage.getTo();

        int oldMemberID =  (int) Float.parseFloat(groupManagementMessage.getParamAsType(KeyInMessage.MEMBER_REMOVE_ID, String.class));

        GroupInfo group = serverContext.getGroupRepository().findById(groupId);
        UserInfo oldMember = serverContext.getUserRepository().findById(oldMemberID);
        if (group == null) {
            LOG.warning(String.format("Group %d not found while trying to remove member", groupId));
            return;
        }

        if (group.getAdminId() != adminGroup){
            LOG.warning(String.format("User %d is not the admin of group %d. Real admin is %d", adminGroup, groupId, group.getAdminId()));
            return;
        }

        if (oldMember == null) {
            LOG.warning(String.format("User %d provided invalid memberId to remove , [%d] not found", adminGroup, oldMemberID));
            AckHelper.sendFailedAck(serverContext, groupManagementMessage, "User to remove don't exists");
            return;
        }

        if (! group.hasMember(oldMemberID)) {
            LOG.warning(String.format("User %d provided invalid memberId to remove , [%d] not in group", adminGroup, oldMemberID));
            AckHelper.sendFailedAck(serverContext, groupManagementMessage, "User to remove is not in the group");
            return;
        }

        // Deleted member is still in the group, when he will receive the message, need to leave the group by comparing his ID and the removed one

        for (int memberId : group.getMembersId()) {
            if (serverContext.isClientConnected(memberId)) {
                serverContext.sendToClient(((ManagementMessage) MessageFactory.create(MessageType.REMOVE_GROUP_MEMBER, groupId, memberId))
                        .addParam(KeyInMessage.GROUP_ID, groupId)
                        .addParam(KeyInMessage.MEMBER_REMOVE_ID, oldMemberID),
                        memberId
                );
            }
        }
        
        group.removeMember(oldMemberID);
        serverContext.getGroupRepository().update(group.getGroupId(), group);
        LOG.info(String.format("Group %d remove member %d", groupId, oldMemberID));

        AckHelper.sendSentAck(serverContext, groupManagementMessage);
    }

    private static void addGroupMember(ChatServerContext serverContext, ManagementMessage groupManagementMessage) {
        int adminGroup = groupManagementMessage.getFrom();
        int groupId = groupManagementMessage.getTo();
        int newMemberID = (int) Float.parseFloat(groupManagementMessage.getParamAsType(KeyInMessage.MEMBER_ADD_ID, String.class));

        GroupInfo group = serverContext.getGroupRepository().findById(groupId);
        UserInfo newMember = serverContext.getUserRepository().findById(newMemberID);
        if (group == null) {
            LOG.warning(String.format("Group %d not found while trying to add member", groupId));
            AckHelper.sendFailedAck(serverContext, groupManagementMessage, "Group not found");
            return;
        }

        if (group.getAdminId() != adminGroup){
            LOG.warning(String.format("User %d is not the admin of group %d. Real admin is %d", adminGroup, groupId, group.getAdminId()));
            AckHelper.sendFailedAck(serverContext, groupManagementMessage, "You are not the admin");
            return;
        }

        if (newMember == null) {
            LOG.warning(String.format("User %d provided invalid memberId to add , [%d] not found", adminGroup, newMemberID));
            AckHelper.sendFailedAck(serverContext, groupManagementMessage, "User to add don't exists");
            AckHelper.sendFailedAck(serverContext, groupManagementMessage, "User to add don't exists");
            return;
        }
        if (newMemberID == adminGroup) {
            LOG.warning(String.format("User %d provided invalid memberId to add , [%d] add himself", adminGroup, newMemberID));
            AckHelper.sendFailedAck(serverContext, groupManagementMessage, "User add himself");
            AckHelper.sendFailedAck(serverContext, groupManagementMessage, "Cannot add already inside user as member.");
            return;
        }


        for (int memberId : group.getMembersId()) {
            if (serverContext.isClientConnected(memberId)) {
                serverContext.sendToClient(((ManagementMessage) MessageFactory.create(MessageType.ADD_GROUP_MEMBER, groupId, memberId))
                        .addParam(KeyInMessage.GROUP_ID, groupId)
                        .addParam(KeyInMessage.MEMBER_ADD_ID, newMemberID)
                        .addParam(KeyInMessage.MEMBER_ADD_PSEUDO, newMember.getUsername()),
                        memberId
                );
            }
        }

        group.addMember(newMemberID, newMember.getUsername());
        serverContext.getGroupRepository().update(group.getGroupId(), group);
        LOG.info(String.format("Group %d add member %d", groupId, newMemberID));
        ManagementMessage message  = (ManagementMessage) MessageFactory.create(MessageType.ADD_GROUP_MEMBER, groupId, newMemberID);
        message.addParam(KeyInMessage.MEMBER_ADD_ID, newMemberID)
                .addParam(KeyInMessage.GROUP_ID, groupId)
                .addParam(KeyInMessage.GROUP_ADMIN_ID, adminGroup)
                .addParam(KeyInMessage.GROUP_NAME, group.getGroupName());
        int i = 0;
        for (var entry : group.getMembers().entrySet()) {
            message.addParam(KeyInMessage.GROUP_MEMBER_ID + i, entry.getKey());
            message.addParam(KeyInMessage.GROUP_MEMBER_PSEUDO + i, entry.getValue());
            i++;
        }

        serverContext.sendToClient(message, newMemberID);

        AckMessage ack = (AckMessage) MessageFactory.create(MessageType.MESSAGE_ACK, 0, groupManagementMessage.getFrom());
        ack.setAcknowledgedMessageId( groupManagementMessage.getMessageId() );
        ack.setAckType(MessageStatus.SENT);
        ack.addParam(KeyInMessage.GROUP_ID, groupId);
        ack.addParam(KeyInMessage.MEMBER_ADD_ID, newMemberID);
        ack.addParam(KeyInMessage.MEMBER_ADD_PSEUDO, newMember.getUsername());
        serverContext.sendToClient(ack, adminGroup);
    }


    private static void leaveGroup(ChatServerContext serverContext, ManagementMessage groupManagementMessage) {
        int groupMember = groupManagementMessage.getFrom();
        int groupId = groupManagementMessage.getTo();

        GroupInfo group = serverContext.getGroupRepository().findById(groupId);
        UserInfo newMember = serverContext.getUserRepository().findById(groupMember);
        if (group == null) {
            LOG.warning(String.format("Group %d not found while a member trying to leave", groupId));
            return;
        }

        if (! group.hasMember(groupMember)){
            LOG.warning(String.format("User %d is not in the group %d but try to leave i", groupMember, groupId));
            return;
        }

        if (newMember == null) {
            LOG.warning(String.format("User %d provided invalid memberId to leave , [%d] not found", groupMember, groupMember));
            AckHelper.sendFailedAck(serverContext, groupManagementMessage, "User to leave don't exists");
            return;
        }

        // Si la personne qui quitte est l'admin on supprime le groupe
        if ( group.getAdminId() == groupMember ){
            deleteGroup(serverContext, groupManagementMessage);
        } else {
            for (int memberId : group.getMembersId()) {
                if (serverContext.isClientConnected(memberId)) {
                    LOG.info(String.format("member %d leave group %d Send this info to %d", groupMember, groupId, memberId));
                    serverContext.sendToClient(((ManagementMessage) MessageFactory.create(MessageType.LEAVE_GROUP, groupId, memberId))
                            .addParam(KeyInMessage.GROUP_ID, groupId)
                            .addParam(KeyInMessage.MEMBER_REMOVE_ID, groupMember),
                            memberId
                    );
                }
            }
            group.removeMember(groupMember);
            serverContext.getGroupRepository().update(group.getGroupId(), group);
            LOG.info(String.format("member %d leave group %d", groupMember, groupId));
            AckHelper.sendSentAck(serverContext, groupManagementMessage);
        }
    }

    private static void deleteGroup(ChatServerContext serverContext, ManagementMessage groupManagementMessage) {
        int groupMember = groupManagementMessage.getFrom();
        int groupId = groupManagementMessage.getTo();

        GroupInfo group = serverContext.getGroupRepository().findById(groupId);
        UserInfo newMember = serverContext.getUserRepository().findById(groupMember);
        if (group == null) {
            LOG.warning(String.format("Group %d not found while a member trying to leave", groupId));
            return;
        }
        if (newMember == null) {
            LOG.warning(String.format("User %d provided invalid memberId to leave , [%d] not found", groupMember, groupMember));
            AckHelper.sendFailedAck(serverContext, groupManagementMessage, "User to leave don't exists");
            return;
        }
        if ( newMember.getId() != group.getAdminId() ){
            LOG.warning("Un utilisateur n'étant pas l'admin tente de supprimer");
            return;
        }

        for (int memberId : group.getMembersId()) {
            if (serverContext.isClientConnected(memberId)) {
                serverContext.sendToClient(((ManagementMessage) MessageFactory.create(MessageType.DELETE_GROUP, group.getGroupId(), memberId))
                        .addParam(KeyInMessage.GROUP_ID, group.getGroupId()),
                        memberId
                        );
            }
        }
        serverContext.getGroupRepository().delete(group.getGroupId());
        LOG.info(String.format("group %d destroy", group.getGroupId()));
        AckHelper.sendSentAck(serverContext, groupManagementMessage);
    }

    private static void createGroup(ChatServerContext serverContext, ManagementMessage groupManagementMessage) {
        int adminGroup = groupManagementMessage.getFrom();
        String newGroupName= groupManagementMessage.getParamAsType(KeyInMessage.GROUP_NAME, String.class);
        int groupID = serverContext.generateClientId();
        GroupInfo group = new GroupInfo(groupID, adminGroup, newGroupName);
        group.addMember(adminGroup, null);
        serverContext.getGroupRepository().add(group);
        LOG.info(String.format("Group %d with name %s now exist and admin is member %d", groupID, newGroupName, adminGroup));

        AckMessage ack = (AckMessage) MessageFactory.create(MessageType.MESSAGE_ACK, 0, groupManagementMessage.getFrom());
        ack.setAcknowledgedMessageId( groupManagementMessage.getMessageId() );
        ack.setAckType(MessageStatus.SENT);
        ack.addParam(KeyInMessage.GROUP_ID, groupID);
        ack.addParam(KeyInMessage.GROUP_ADMIN_ID, adminGroup);
        ack.addParam(KeyInMessage.GROUP_NAME, newGroupName);
        serverContext.sendToClient(ack, adminGroup);
    }


    @Override
    public boolean canHandle(MessageType messageType) {
        return messageType == MessageType.CREATE_GROUP
                || messageType == MessageType.LEAVE_GROUP
                || messageType == MessageType.ADD_GROUP_MEMBER
                || messageType == MessageType.REMOVE_GROUP_MEMBER
                || messageType == MessageType.UPDATE_GROUP_NAME
                || messageType == MessageType.DELETE_GROUP;
    }
}
