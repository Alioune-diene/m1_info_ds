package fr.uga.im2ag.m1info.tchatsapp.client.command;

import fr.uga.im2ag.m1info.tchatsapp.client.event.system.EventBus;
import fr.uga.im2ag.m1info.tchatsapp.client.event.types.ChangeMemberInGroupEvent;
import fr.uga.im2ag.m1info.tchatsapp.common.KeyInMessage;
import fr.uga.im2ag.m1info.tchatsapp.common.MessageStatus;
import fr.uga.im2ag.m1info.tchatsapp.common.MessageType;
import fr.uga.im2ag.m1info.tchatsapp.common.model.GroupInfo;
import fr.uga.im2ag.m1info.tchatsapp.common.repository.GroupRepository;

import java.util.Map;

public class AddMemberGroupCommand extends SendManagementMessageCommand {
    private final int groupID;
    private final GroupRepository repo;
    private final int member;

    public AddMemberGroupCommand(String commandId, int groupID, GroupRepository repo, int member) {

        super(commandId, MessageType.ADD_GROUP_MEMBER);
        this.groupID = groupID;
        this.member = member;
        this.repo = repo;
    }

    @Override
    public boolean onAckReceived(MessageStatus ackType, Map<String, Object> params) {
        GroupInfo group = repo.findById(groupID);
        String pseudo = String.valueOf(params.get(KeyInMessage.MEMBER_ADD_PSEUDO));
        group.addMember(member, pseudo);
        repo.update(groupID, group);
        EventBus.getInstance().publish(new ChangeMemberInGroupEvent(this, groupID, member, pseudo, true));
        System.out.printf("[Client] You successfully add member %d to the group %d\n",member, groupID);
        return true;
    }
    
}


