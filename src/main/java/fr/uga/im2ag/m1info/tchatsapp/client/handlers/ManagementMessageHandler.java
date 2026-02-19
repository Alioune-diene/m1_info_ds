package fr.uga.im2ag.m1info.tchatsapp.client.handlers;

import fr.uga.im2ag.m1info.tchatsapp.client.ClientController;
import fr.uga.im2ag.m1info.tchatsapp.client.event.types.*;
import fr.uga.im2ag.m1info.tchatsapp.client.model.ContactClient;
import fr.uga.im2ag.m1info.tchatsapp.common.KeyInMessage;
import fr.uga.im2ag.m1info.tchatsapp.common.MessageType;
import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.ManagementMessage;
import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.ProtocolMessage;
import fr.uga.im2ag.m1info.tchatsapp.common.model.GroupInfo;

public class ManagementMessageHandler extends ClientMessageHandler {
    @Override
    public void handle(ProtocolMessage message, ClientController context) {
        if (!(message instanceof ManagementMessage userMsg)) {
            throw new IllegalArgumentException("Invalid message type for ManagementMessageHandler");
        }

        switch (userMsg.getMessageType()) {
            case REMOVE_CONTACT -> removeContact(userMsg, context);
            case UPDATE_PSEUDO -> updatePseudo(userMsg, context);
            case LEAVE_GROUP -> leaveGroup(userMsg, context);
            case ADD_GROUP_MEMBER -> addGroupMember(userMsg, context);
            case REMOVE_GROUP_MEMBER -> removeGroupMember(userMsg, context);
            case UPDATE_GROUP_NAME -> updateGroupName(userMsg, context);
            case DELETE_GROUP -> deleteGroup(userMsg, context);
            default -> throw new IllegalArgumentException("Unsupported management message type: " + userMsg.getMessageType());
        }
    }


    @Override
    public boolean canHandle(MessageType messageType) {
        return messageType == MessageType.REMOVE_CONTACT
                || messageType == MessageType.UPDATE_PSEUDO
                || messageType == MessageType.CREATE_GROUP
                || messageType == MessageType.LEAVE_GROUP
                || messageType == MessageType.ADD_GROUP_MEMBER
                || messageType == MessageType.REMOVE_GROUP_MEMBER
                || messageType == MessageType.DELETE_GROUP
                || messageType == MessageType.UPDATE_GROUP_NAME;
    }

    private void removeContact(ManagementMessage message, ClientController context) {
        int contactId = message.getParamAsType("contactId", Integer.class);
        context.getContactRepository().delete(contactId);
        publishEvent(new ContactRemovedEvent(this, contactId), context);
    }

    private void updatePseudo(ManagementMessage message, ClientController context) {
        String newPseudo = message.getParamAsType("newPseudo", String.class);
        Integer contactId = message.getParamAsType("contactId", Integer.class);

        ContactClient contact = context.getContactRepository().findById(contactId);
        if (contact != null) {
            contact.updatePseudo(newPseudo);
            context.getContactRepository().update(contactId, contact);
            publishEvent(new ContactUpdatedEvent(this, contactId), context);
        }

        for (GroupInfo group : context.getGroupRepository().findAll()) {
            if (group.hasMember(contactId)) {
                group.setMemberName(contactId, newPseudo);
                context.getGroupRepository().update(group.getGroupId(), group);
            }
        }
    }

    private void deleteGroup(ManagementMessage message, ClientController context){
        int groupId = getIntInParam(message, KeyInMessage.GROUP_ID);
        context.getGroupRepository().delete(groupId);
        System.out.printf("[Client] Group %d is destroy !\n", groupId);
        publishEvent(new DeleteGroupEvent(this,  groupId), context);
    }

    private void leaveGroup(ManagementMessage message, ClientController context){
        // TODO if admin leave group what happen ? e destroy the group or a new admin 
        // need to be choose
        int deleteMember = getIntInParam(message, KeyInMessage.MEMBER_REMOVE_ID);
        int groupId = getIntInParam(message, KeyInMessage.GROUP_ID);

        // just a group member not the admin
        if ( deleteMember == context.getClientId()){
            System.out.printf("[Client] You leave the group %d !\n", groupId);
            context.getGroupRepository().delete(groupId);
        } else {
            GroupInfo group = context.getGroupRepository().findById(groupId);
            group.removeMember(deleteMember);
            context.getGroupRepository().update(groupId, group);
            System.out.printf("[Client] User %d leave the group %d !\n", deleteMember, groupId);
        }
        publishEvent(new LeaveGroupEvent(this, groupId), context);
    }

    private int getIntInParam(ManagementMessage message, String s){
        return (int ) Float.parseFloat( message.getParamAsType(s, String.class));
    }

    private void addGroupMember(ManagementMessage message, ClientController context) {
        int newMember = getIntInParam(message, KeyInMessage.MEMBER_ADD_ID);
        String newMemberPseudo = message.getParamAsType(KeyInMessage.MEMBER_ADD_PSEUDO, String.class);
        int groupId = getIntInParam(message, KeyInMessage.GROUP_ID);
        // just a group member not the admin
        if ( newMember == context.getClientId()){
            System.out.printf("[Client] You have been add to the group %d !\n", groupId);
            String groupName = message.getParamAsType(KeyInMessage.GROUP_NAME, String.class);
            int adminId = getIntInParam(message, KeyInMessage.GROUP_ADMIN_ID);
            GroupInfo group = new GroupInfo(groupId, adminId, groupName);
            int i = 0;
            while (true){
                try{
                    int member = getIntInParam(message, KeyInMessage.GROUP_MEMBER_ID + i);
                    String memberPseudo = message.getParamAsType(KeyInMessage.GROUP_MEMBER_PSEUDO + i, String.class);
                    group.addMember(member, memberPseudo);
                    i++;
                } catch (Exception e){
                    break;
                }
            }
            context.getGroupRepository().add(group);
            publishEvent(new GroupCreateEvent(this, group), context);
        } else {
            GroupInfo group = context.getGroupRepository().findById(groupId);
            if (group == null) {
                return;
            }
            group.addMember(newMember, newMemberPseudo);
            context.getGroupRepository().update(groupId, group);
            System.out.printf("[Client] User %d have been add to the group %d !\n", newMember, groupId);
            publishEvent(new ChangeMemberInGroupEvent(this, groupId, newMember, newMemberPseudo, true), context);
        }
    }

    private void removeGroupMember(ManagementMessage message, ClientController context) {
        int deleteMember = getIntInParam(message, KeyInMessage.MEMBER_REMOVE_ID);
        int groupId = getIntInParam(message, KeyInMessage.GROUP_ID);
        // just a group member not the admin
        if ( deleteMember == context.getClientId()){
            System.out.printf("[Client] You have been remove of the group %d !\n", groupId);
            // TODO  a new member need to access every data like other members !!!
            context.getGroupRepository().delete(groupId);
            publishEvent(new LeaveGroupEvent(this, groupId), context);
        } else {
            GroupInfo group = context.getGroupRepository().findById(groupId);
            group.removeMember(deleteMember);
            context.getGroupRepository().update(groupId, group);
            System.out.printf("[Client] User %d have been removed to the group %d !\n", deleteMember, groupId);
            publishEvent(new ChangeMemberInGroupEvent(this, groupId, deleteMember, "", false), context);
        }
    }

    public void updateGroupName(ManagementMessage message, ClientController context){
        String newGroupName = message.getParamAsType(KeyInMessage.GROUP_NAME, String.class);
        Integer groupId = getIntInParam(message, KeyInMessage.GROUP_ID);
        GroupInfo group = context.getGroupRepository().findById(groupId);
        group.setGroupName(newGroupName);
        context.getGroupRepository().update(groupId, group);
        System.out.printf("[Client] Group %d have been renamed to %s !", groupId, newGroupName);
        publishEvent(new UpdateGroupNameEvent(this, groupId, newGroupName), context);
    }
}
