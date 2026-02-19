package fr.uga.im2ag.m1info.tchatsapp.client.handlers;

import fr.uga.im2ag.m1info.tchatsapp.client.ClientController;
import fr.uga.im2ag.m1info.tchatsapp.client.event.types.ContactAddedEvent;
import fr.uga.im2ag.m1info.tchatsapp.client.event.types.ContactRequestReceivedEvent;
import fr.uga.im2ag.m1info.tchatsapp.client.event.types.ContactRequestResponseEvent;
import fr.uga.im2ag.m1info.tchatsapp.client.model.ContactClient;
import fr.uga.im2ag.m1info.tchatsapp.client.model.ContactRequest;
import fr.uga.im2ag.m1info.tchatsapp.client.repository.ContactClientRepository;
import fr.uga.im2ag.m1info.tchatsapp.common.MessageType;
import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.ContactRequestMessage;
import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.ContactRequestResponseMessage;
import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.ProtocolMessage;

import java.time.Instant;

public class ContactRequestHandler extends ClientMessageHandler {
    @Override
    public void handle(ProtocolMessage message, ClientController context) {
        if (message instanceof ContactRequestMessage crMsg) {
            handleRequest(crMsg, context);
        } else if (message instanceof ContactRequestResponseMessage crrMsg) {
            handleResponse(crrMsg, context);
        } else {
            throw new IllegalArgumentException("Invalid message type for ContactRequestHandler");
        }
    }

    @Override
    public boolean canHandle(MessageType messageType) {
        return messageType == MessageType.CONTACT_REQUEST
                || messageType == MessageType.CONTACT_REQUEST_RESPONSE;
    }

    /**
     * Handle an incoming contact request.
     */
    private void handleRequest(ContactRequestMessage crMsg, ClientController context) {
        int senderId = crMsg.getFrom();
        String requestId = crMsg.getRequestId();
        ContactClientRepository repo = context.getContactRepository();

        if (repo.isContact(senderId)) {
            return;
        }

        if (repo.hasSentRequestTo(senderId)) {
            System.out.println("[Client] Mutual contact request detected with user " + senderId);
            ContactRequest ourRequest = repo.getSentRequestTo(senderId);
            ourRequest.setStatus(ContactRequest.Status.ACCEPTED);
            repo.removeRequest(ourRequest.getRequestId());

            addContact(context, senderId, repo);

            System.out.println("[Client] Mutual contact request auto-accepted for user " + senderId);
            return;
        }

        if (repo.hasReceivedRequestFrom(senderId)) {
            System.out.println("[Client] Duplicate contact request from user " + senderId + " ignored");
            return;
        }

        ContactRequest request = new ContactRequest(
                requestId,
                senderId,
                context.getClientId(),
                crMsg.getTimestamp(),
                Instant.ofEpochMilli(crMsg.getExpirationTimestamp())
        );

        repo.addReceivedRequest(request);
        publishEvent(new ContactRequestReceivedEvent(this, request), context);

        System.out.println("[Client] Contact request received from user " + senderId);
    }

    /**
     * Handle a response to a contact request.
     */
    private void handleResponse(ContactRequestResponseMessage crMsg, ClientController context) {
        int responderId = crMsg.getFrom();
        String requestId = crMsg.getRequestId();
        boolean accepted = crMsg.isAccepted();
        ContactClientRepository repo = context.getContactRepository();

        ContactRequest sentRequest = repo.getSentRequestTo(responderId);
        if (sentRequest == null || !sentRequest.getRequestId().equals(requestId)) {
            System.err.println("[Client] Received response for unknown request: " + requestId);
            return;
        }

        sentRequest.setStatus(accepted ? ContactRequest.Status.ACCEPTED : ContactRequest.Status.REJECTED);
        repo.removeRequest(requestId);

        if (accepted) {
            addContact(context, responderId, repo);
        }

        publishEvent(new ContactRequestResponseEvent(this, requestId, responderId, accepted, true), context);
    }

    /**
     * Add a new contact to the client's contact list.
     *
     * @param context the client controller
     * @param senderId the ID of the new contact
     * @param repo the contact repository
     */
    private void addContact(ClientController context, int senderId, ContactClientRepository repo) {
        ContactClient newContact = new ContactClient(senderId, "User #" + senderId);
        repo.add(newContact);
        context.getOrCreatePrivateConversation(senderId);

        publishEvent(new ContactAddedEvent(this, senderId), context);
    }
}