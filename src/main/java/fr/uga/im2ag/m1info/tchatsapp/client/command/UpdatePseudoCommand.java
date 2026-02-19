package fr.uga.im2ag.m1info.tchatsapp.client.command;

import fr.uga.im2ag.m1info.tchatsapp.client.event.system.EventBus;
import fr.uga.im2ag.m1info.tchatsapp.client.event.types.UserPseudoUpdatedEvent;
import fr.uga.im2ag.m1info.tchatsapp.client.model.UserClient;
import fr.uga.im2ag.m1info.tchatsapp.common.MessageStatus;
import fr.uga.im2ag.m1info.tchatsapp.common.MessageType;

import java.util.Map;

public class UpdatePseudoCommand extends SendManagementMessageCommand {
    private final String newPseudo;
    private final UserClient userClient;

    public UpdatePseudoCommand(String commandId, String newPseudo, UserClient userClient) {
        super(commandId, MessageType.UPDATE_PSEUDO);
        this.newPseudo = newPseudo;
        this.userClient = userClient;
    }

    @Override
    public boolean onAckReceived(MessageStatus ackType, Map<String, Object> params) {
        userClient.setPseudo(newPseudo);
        EventBus.getInstance().publish(new UserPseudoUpdatedEvent(this, newPseudo));
        System.out.printf("[Client] User pseudo has been updated to '%s'.%n", newPseudo);
        return true;
    }
}
