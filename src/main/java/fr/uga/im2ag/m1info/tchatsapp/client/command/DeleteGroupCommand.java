package fr.uga.im2ag.m1info.tchatsapp.client.command;

import fr.uga.im2ag.m1info.tchatsapp.client.event.system.EventBus;
import fr.uga.im2ag.m1info.tchatsapp.client.event.types.DeleteGroupEvent;
import fr.uga.im2ag.m1info.tchatsapp.common.MessageStatus;
import fr.uga.im2ag.m1info.tchatsapp.common.MessageType;
import fr.uga.im2ag.m1info.tchatsapp.common.repository.GroupRepository;

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

