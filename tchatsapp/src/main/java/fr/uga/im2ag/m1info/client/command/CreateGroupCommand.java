package fr.uga.im2ag.m1info.client.command;

import fr.uga.im2ag.m1info.client.event.system.EventBus;
import fr.uga.im2ag.m1info.client.event.types.GroupCreateEvent;
import fr.uga.im2ag.m1info.common.KeyInMessage;
import fr.uga.im2ag.m1info.common.MessageStatus;
import fr.uga.im2ag.m1info.common.MessageType;
import fr.uga.im2ag.m1info.common.model.GroupInfo;
import fr.uga.im2ag.m1info.common.repository.GroupRepository;

import java.util.Map;

public class CreateGroupCommand extends SendManagementMessageCommand {
    private final GroupRepository repo;

    public CreateGroupCommand(String commandId, GroupRepository repo) {
        super(commandId, MessageType.CREATE_GROUP);
        this.repo = repo;
    }

    @Override
    public boolean onAckReceived(MessageStatus ackType, Map<String, Object> params) {
        int groupId = Double.valueOf(params.get(KeyInMessage.GROUP_ID).toString()).intValue();
        int adminId = Double.valueOf(params.get(KeyInMessage.GROUP_ADMIN_ID).toString()).intValue();
        String groupName = (String) params.get(KeyInMessage.GROUP_NAME);
        GroupInfo groupInfo = new GroupInfo(groupId, adminId,  groupName);
        repo.add(groupInfo);
        EventBus.getInstance().publish(new GroupCreateEvent(this, groupInfo));
        System.out.printf("[CLIENT ] Group '%s' (ID: %d) successfully created.%n",
                          groupInfo.getGroupName(),
                          groupInfo.getGroupId());
        return true;
    }
}

