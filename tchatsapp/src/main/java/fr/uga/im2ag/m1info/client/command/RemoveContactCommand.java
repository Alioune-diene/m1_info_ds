package fr.uga.im2ag.m1info.client.command;

import fr.uga.im2ag.m1info.client.event.system.EventBus;
import fr.uga.im2ag.m1info.client.event.types.ContactRemovedEvent;
import fr.uga.im2ag.m1info.client.repository.ContactClientRepository;
import fr.uga.im2ag.m1info.common.MessageStatus;
import fr.uga.im2ag.m1info.common.MessageType;

import java.util.Map;

public class RemoveContactCommand extends SendManagementMessageCommand {
    private final int contactId;
    private final ContactClientRepository repository;

    public RemoveContactCommand(String commandId, int contactId, ContactClientRepository repository) {
        super(commandId, MessageType.REMOVE_CONTACT);
        this.contactId = contactId;
        this.repository = repository;
    }

    @Override
    public boolean onAckReceived(MessageStatus ackType, Map<String, Object> params) {
        repository.delete(contactId);
        EventBus.getInstance().publish(new ContactRemovedEvent(this, contactId));
        System.out.printf("[Client] Contact with ID %d has been removed.%n", contactId);
        return true;
    }
}
