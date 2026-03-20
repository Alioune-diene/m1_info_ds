package fr.uga.im2ag.m1info.client.command;

import fr.uga.im2ag.m1info.client.event.system.EventBus;
import fr.uga.im2ag.m1info.client.event.types.DeleteGroupEvent;
import fr.uga.im2ag.m1info.common.MessageStatus;
import fr.uga.im2ag.m1info.common.MessageType;
import fr.uga.im2ag.m1info.common.repository.GroupRepository;

import java.util.Map;

public class DeleteGroupCommand extends SendManagementMessageCommand {
    private final int groupID;
    private final GroupRepository groupRepository;

    public DeleteGroupCommand(String commandId, int groupID, GroupRepository repo) {

        super(commandId, MessageType.DELETE_GROUP);
        this.groupID = groupID;
        this.groupRepository = repo;
    }

    @Override
    public boolean onAckReceived(MessageStatus ackType, Map<String, Object> params) {
        groupRepository.delete(groupID);
        EventBus.getInstance().publish(new DeleteGroupEvent(this, groupID));
        System.out.printf("[CLIENT ] Group %d successfully deleted.%n", groupID);
        return true;
    }
    
}

