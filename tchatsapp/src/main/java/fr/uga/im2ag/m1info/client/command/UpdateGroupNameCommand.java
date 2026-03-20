package fr.uga.im2ag.m1info.client.command;

import fr.uga.im2ag.m1info.client.event.system.EventBus;
import fr.uga.im2ag.m1info.client.event.types.UpdateGroupNameEvent;
import fr.uga.im2ag.m1info.common.MessageStatus;
import fr.uga.im2ag.m1info.common.MessageType;
import fr.uga.im2ag.m1info.common.model.GroupInfo;
import fr.uga.im2ag.m1info.common.repository.GroupRepository;

import java.util.Map;

public class UpdateGroupNameCommand extends SendManagementMessageCommand {
    private final int groupID;
    private final GroupRepository repo;
    private final String newName;

    public UpdateGroupNameCommand(String commandId, int groupID, GroupRepository repo, String newName) {

        super(commandId, MessageType.UPDATE_GROUP_NAME);
        this.groupID = groupID;
        this.newName = newName;
        this.repo = repo;
    }

    @Override
    public boolean onAckReceived(MessageStatus ackType, Map<String, Object> params) {
        GroupInfo group = repo.findById(groupID);
        group.setGroupName(newName);
        repo.update(groupID, group);
        EventBus.getInstance().publish(new UpdateGroupNameEvent(this, groupID, newName));
        System.out.printf("[Client] You successfully change group name to %s for group %d\n", newName, groupID);
        return true;
    }
    
}