package fr.uga.im2ag.m1info.tchatsapp.client;

import fr.uga.im2ag.m1info.tchatsapp.client.command.*;
import fr.uga.im2ag.m1info.tchatsapp.client.event.system.*;
import fr.uga.im2ag.m1info.tchatsapp.client.event.types.ConnectionEstablishedEvent;
import fr.uga.im2ag.m1info.tchatsapp.client.handlers.ClientHandlerContext;
import fr.uga.im2ag.m1info.tchatsapp.client.media.MediaManager;
import fr.uga.im2ag.m1info.tchatsapp.client.model.*;
import fr.uga.im2ag.m1info.tchatsapp.client.repository.ContactClientRepository;
import fr.uga.im2ag.m1info.tchatsapp.client.repository.ConversationClientRepository;
import fr.uga.im2ag.m1info.tchatsapp.common.*;
import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.*;
import fr.uga.im2ag.m1info.tchatsapp.common.repository.GroupRepository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

/**
 * Context providing access to client functionalities for packet handlers.
 * This class encapsulates the client and provides controlled access to its operations.
 */
public class ClientController {
    private final Client client;
    private volatile boolean connectionEstablished;
    private volatile String lastErrorMessage;

    private final ConversationClientRepository conversationRepository;
    private final ContactClientRepository contactRepository;
    private final GroupRepository groupRepository;
    private final UserClient activeUser;
    private final EventBus eventBus;
    private final MediaManager mediaManager;

    /* ----------------------- Constructor ----------------------- */

    /**
     * Creates a new ClientController with specified repositories.
     *
     * @param client the client instance to wrap
     * @param conversationRepository the conversation repository
     * @param contactRepository the contact repository
     * @param groupRepository the group repository
     */
    public ClientController(Client client,
                            ConversationClientRepository conversationRepository,
                            ContactClientRepository contactRepository,
                            GroupRepository groupRepository,
                            UserClient user) {
        this.client = client;
        this.connectionEstablished = false;
        this.lastErrorMessage = null;
        this.conversationRepository = conversationRepository;
        this.contactRepository = contactRepository;
        this.groupRepository = groupRepository;
        this.activeUser = user;
        this.eventBus = EventBus.getInstance();
        this.mediaManager = new MediaManager(client.getClientId());
    }

    /**
     * Creates a new ClientController.
     *
     * @param client the client instance to wrap
     */
    public ClientController(Client client) {
        this(client, new ConversationClientRepository(),
                new ContactClientRepository(),
                new GroupRepository(),
                new UserClient());
    }

    /* ----------------------- Client Operations ----------------------- */

    /**
     * Connect to the server.
     *
     * @param host the server hostname or IP address
     * @param port the server port
     * @param username the username to use for the connection
     * @return true if connected successfully, false otherwise
     * @throws Exception if a network error occurs
     */
    public boolean connect(String host, int port, String username) throws Exception {
        boolean result = client.connect(host, port, username);
        if (result) {
            connectionEstablished = true;
            activeUser.setPseudo(username);

            boolean isNewUser = (client.getClientId() != 0);
            publishEvent(new ConnectionEstablishedEvent(this, client.getClientId(), username, isNewUser));
        }
        return result;
    }

    /**
     * Disconnect from the server.
     */
    public void disconnect() {
        client.disconnect();
        connectionEstablished = false;
    }

    /**
     * Initializes packet handlers and the packet processor.
     * <p>
     * Must be called after encryption initialization if encryption is desired.
     */
    public void initializeHandlers() {
        ClientHandlerContext handlerContext = ClientHandlerContext.builder()
                .commandManager(client.getCommandManager())
                .mediaManager(mediaManager)
                .build();

        ClientMessageRouter router = ClientMessageRouter.createWithServiceLoader(this, handlerContext);
        client.setMessageProcessor(router);
    }

    private MessageProcessor createMessageProcessor(ClientMessageRouter router) {
        return router;
    }

    /* ----------------------- Accessors ----------------------- */

    /**
     * Get the client ID.
     *
     * @return the client ID
     */
    public int getClientId() {
        return client.getClientId();
    }

    /**
     * Get the conversation repository.
     *
     * @return the conversation repository
     */
    public ConversationClientRepository getConversationRepository() {
        return conversationRepository;
    }

    /**
     * Get the contact repository.
     *
     * @return the contact repository
     */
    public ContactClientRepository getContactRepository() {
        return contactRepository;
    }

    /**
     * Get the group repository.
     *
     * @return the group repository
     */
    public GroupRepository getGroupRepository() {
        return groupRepository;
    }

    /**
     * Get the active user.
     *
     * @return the active user
     */
    public UserClient getActiveUser() {
        return activeUser;
    }

    /**
     * Get the pending command manager.
     *
     * @return the pending command manager
     */
    public PendingCommandManager getCommandManager() {
        return client.getCommandManager();
    }

    /**
     * Get the media manager.
     *
     * @return the media manager
     */
    public MediaManager getMediaManager() {
        return mediaManager;
    }

    /**
     * Check if the client is connected.
     *
     * @return true if connected, false otherwise
     */
    public boolean isConnected() {
        return client.isConnected();
    }

    /**
     * Check if the connection handshake is complete.
     *
     * @return true if connection is fully established, false otherwise
     */
    public boolean isConnectionEstablished() {
        return connectionEstablished;
    }

    /**
     * Mark the connection as established.
     */
    public void markConnectionEstablished() {
        this.connectionEstablished = true;
    }

    /**
     * Set the last error message.
     *
     * @param errorMessage the error message
     */
    public void setLastError(String errorMessage) {
        this.lastErrorMessage = errorMessage;
    }

    /**
     * Get the last error message.
     *
     * @return the last error message, or null if none
     */
    public String getLastError() {
        return lastErrorMessage;
    }

    /**
     * Clear the last error message.
     */
    public void clearLastError() {
        this.lastErrorMessage = null;
    }

    /**
     * Update the client ID.
     *
     * @param clientId the new client ID
     */
    public void updateClientId(int clientId) {
        client.updateClientId(clientId);
    }

    /* ----------------------- Conversation Management ----------------------- */

    /**
     * Generate a conversation ID for a private conversation between two users.
     *
     * @param userId1 the first user ID
     * @param userId2 the second user ID
     * @return the conversation ID
     */
    public static String generatePrivateConversationId(int userId1, int userId2) {
        int min = Math.min(userId1, userId2);
        int max = Math.max(userId1, userId2);
        return "private_" + min + "_" + max;
    }

    /**
     * Generate a conversation ID for a group conversation.
     *
     * @param groupId the group ID
     * @return the conversation ID
     */
    public static String generateGroupConversationId(int groupId) {
        return "group_" + groupId;
    }

    /**
     * Get or create a private conversation with another user.
     *
     * @param otherUserId the other user's ID
     * @return the conversation
     */
    public ConversationClient getOrCreatePrivateConversation(int otherUserId) {
        String conversationId = generatePrivateConversationId(getClientId(), otherUserId);
        ConversationClient conversation = conversationRepository.findById(conversationId);

        if (conversation == null) {
            conversation = new ConversationClient(conversationId, contactRepository.findById(otherUserId), otherUserId, false);
            conversationRepository.add(conversation);
        }

        return conversation;
    }

    /**
     * Get or create a group conversation.
     *
     * @param groupId the group ID
     * @return the conversation
     */
    public ConversationClient getOrCreateGroupConversation(int groupId) {
        String conversationId = generateGroupConversationId(groupId);
        ConversationClient conversation = conversationRepository.findById(conversationId);

        if (conversation == null) {
            if (groupRepository.findById(groupId) == null) {
                throw new IllegalArgumentException("Group with ID " + groupId + " does not exist");
            }
            conversation = new ConversationClient(conversationId, groupId, true, groupRepository.findById(groupId));
            conversationRepository.add(conversation);
        }

        return conversation;
    }

    /* ----------------------- Contact Request Management ----------------------- */

    /**
     * Send a contact request to another user.
     *
     * @param targetUserId the user to send the request to
     * @return the request ID, or null if failed
     */
    public String sendContactRequest(int targetUserId) {
        if (contactRepository.isContact(targetUserId)) {
            System.err.println("[Client] User " + targetUserId + " is already a contact");
            return null;
        }

        if (contactRepository.hasSentRequestTo(targetUserId)) {
            System.err.println("[Client] Contact request to user " + targetUserId + " already sent");
            return null;
        }

        ContactRequestMessage crMsg = (ContactRequestMessage) MessageFactory.create(MessageType.CONTACT_REQUEST, getClientId(), targetUserId);

        Instant expiresAt = Instant.now().plus(ContactClientRepository.DEFAULT_REQUEST_EXPIRATION);
        crMsg.setExpirationTimestamp(expiresAt.toEpochMilli());

        ContactRequest request = ContactClientRepository.createRequest(
                crMsg.getRequestId(), getClientId(), targetUserId
        );
        contactRepository.addSentRequest(request);

        client.sendMessage(crMsg);
        return crMsg.getRequestId();
    }

    /**
     * Respond to a contact request.
     *
     * @param senderId the ID of the user who sent the request
     * @param accept true to accept, false to reject
     */
    public void respondToContactRequest(int senderId, boolean accept) {
        ContactRequest request = contactRepository.getReceivedRequestFrom(senderId);
        if (request == null) {
            System.err.println("[Client] No pending request from user " + senderId);
            return;
        }

        if (request.isExpired()) {
            System.err.println("[Client] Request from user " + senderId + " has expired");
            contactRepository.removeRequest(request.getRequestId());
            return;
        }

        ContactRequestResponseMessage response = (ContactRequestResponseMessage) MessageFactory.create(MessageType.CONTACT_REQUEST_RESPONSE, getClientId(), senderId);
        response.setRequestId(request.getRequestId());
        response.setAccepted(accept);

        request.setStatus(accept ? ContactRequest.Status.ACCEPTED : ContactRequest.Status.REJECTED);
        contactRepository.removeRequest(request.getRequestId());

        if (accept) {
            ContactClient newContact = new ContactClient(senderId, "User #" + senderId);
            contactRepository.add(newContact);
            getOrCreatePrivateConversation(senderId);
        }

        client.sendMessage(response);
    }

    /* ----------------------- Event Subscription ----------------------- */

    /**
     * Subscribe to an event type with an observer, filter, and execution mode.
     *
     * @param eventType the class of the event type to subscribe to
     * @param observer the observer to notify when the event occurs
     * @param filter the filter to apply to events
     * @param mode the execution mode for the observer
     * @param <T> the type of the event
     * @return the event subscription
     */
    public <T extends Event> EventSubscription<T> subscribeToEvent(Class<T> eventType, EventObserver<T> observer, EventFilter<T> filter, ExecutionMode mode) {
        return eventBus.subscribe(eventType, observer, filter, mode);
    }

    /**
     * Subscribe to an event type with an observer and an execution mode.
     *
     * @param eventType the class of the event type to subscribe to
     * @param observer the observer to notify when the event occurs
     * @param mode the execution mode for the observer
     * @param <T> the type of the event
     * @return the event subscription
     */
    public <T extends Event> EventSubscription<T> subscribeToEvent(Class<T> eventType, EventObserver<T> observer, ExecutionMode mode) {
        return eventBus.subscribe(eventType, observer, mode);
    }

    /**
     * Subscribe to an event type with an observer using synchronous execution mode.
     *
     * @param eventType the class of the event type to subscribe to
     * @param observer the observer to notify when the event occurs
     * @param <T> the type of the event
     * @return the event subscription
     */
    public <T extends Event> EventSubscription<T> subscribeToEvent(Class<T> eventType, EventObserver<T> observer) {
        return eventBus.subscribe(eventType, observer, ExecutionMode.SYNC);
    }

    public void unsubscribe(EventSubscription<? extends Event> subscription) {
        eventBus.unsubscribe(subscription);
    }

    /**
     * Publish an event to the event bus.
     *
     * @param event the event to publish
     */
    public void publishEvent(Event event) {
        eventBus.publish(event);
    }

    /* ----------------------- Packet Operations ----------------------- */

    /**
     * Send a file to a recipient without progress callback
     *
     * @param filePath path to the file
     * @param to recipient ID
     * @return true if sending started successfully
     */
    public boolean sendFile(String filePath, int to) {
        return sendFile(filePath, to, null);
    }

    /**
     * Send a file to a recipient with progress callback
     *
     * @param filePath path to the file
     * @param to recipient ID
     * @param progressCallback callback for progress updates (can be null)
     * @return true if sending started successfully
     */
    public boolean sendFile(String filePath, int to, FileProgressCallback progressCallback) {
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                System.err.println("[ClientController] File not found: " + filePath);
                return false;
            }

            long fileSize = Files.size(path);
            String fileName = path.getFileName().toString();

            new Thread(() -> {
                try (InputStream fileStream = Files.newInputStream(path)) {
                    int count;
                    byte[] buffer = new byte[Client.MAX_SIZE_CHUNK_FILE];
                    long bytesSent = 0;
                    int chunkIndex = 0;

                    while ((count = fileStream.read(buffer)) > 0) {
                        MediaMessage mediaMsg = (MediaMessage) MessageFactory.create(MessageType.MEDIA, client.getClientId(), to);
                        mediaMsg.setMediaName(fileName);
                        mediaMsg.setContent(buffer);
                        mediaMsg.setSizeContent(count);

                        boolean sent = client.sendMessage(mediaMsg);

                        if (sent) {
                            bytesSent += count;
                            chunkIndex++;

                            // Notify progress
                            if (progressCallback != null) {
                                progressCallback.onProgress(bytesSent, fileSize, chunkIndex);
                            }
                        } else {
                            System.err.println("[ClientController] Failed to send chunk " + chunkIndex);
                            if (progressCallback != null) {
                                progressCallback.onError("Failed to send chunk " + chunkIndex);
                            }
                            return;
                        }
                    }

                    if (progressCallback != null) {
                        progressCallback.onComplete(fileName, bytesSent, chunkIndex);
                    }
                    System.out.printf("[ClientController] File sent: %s (%d chunks, %d bytes)%n", fileName, chunkIndex, bytesSent);
                } catch (IOException e) {
                    System.err.println("[ClientController] Failed to send file: " + e.getMessage());
                    if (progressCallback != null) {
                        progressCallback.onError(e.getMessage());
                    }
                }
            }, "FileSender-" + fileName).start();

            return true;

        } catch (IOException e) {
            System.err.println("[ClientController] Failed to access file: " + e.getMessage());
            return false;
        }
    }

    /**
     * Send a text message to a recipient using the ACK system.
     *
     * @param content the message content
     * @param toUserId the recipient ID
     * @param replyToMessageId the ID of the message being replied to (optional)
     * @return the message ID, or null if failed
     */
    public String sendTextMessage(String content, int toUserId, String replyToMessageId) {
        TextMessage textMsg = (TextMessage) MessageFactory.create(
                MessageType.TEXT,
                getClientId(),
                toUserId
        );
        textMsg.setContent(content);
        textMsg.setReplyToMessageId(replyToMessageId);

        if (!client.sendMessage(textMsg)) {
            System.err.println("[Client] Failed to send text message to user " + toUserId);
            return null;
        }

        Message msg = new Message(
                textMsg.getMessageId(),
                getClientId(),
                toUserId,
                content,
                textMsg.getTimestamp(),
                replyToMessageId
        );

        if (groupRepository.findById(toUserId) != null) {
            ConversationClient conversation = getOrCreateGroupConversation(toUserId);
            conversation.addMessage(msg);
        } else {
            ConversationClient conversation = getOrCreatePrivateConversation(toUserId);
            conversation.addMessage(msg);
        }

        SendTextMessageCommand command = new SendTextMessageCommand(
                textMsg.getMessageId(),
                msg,
                conversationRepository
        );

        client.getCommandManager().addPendingCommand(command);
        return textMsg.getMessageId();
    }

    public String sendReactionMessage(String content, int toUserId, String reactionToMessageId) {
        ReactionMessage reactMsg = (ReactionMessage) MessageFactory.create(
                MessageType.REACTION,
                getClientId(),
                toUserId
        );
        reactMsg.setReactionToMessageId(reactionToMessageId);
        reactMsg.setContent(content);

        if (!client.sendMessage(reactMsg)) {
            System.err.println("[Client] Failed to send reaction message to user " + toUserId);
            return null;
        }

        Message msg = new Message(
                reactMsg.getMessageId(),
                getClientId(),
                toUserId,
                content,
                reactMsg.getTimestamp(),
                reactionToMessageId
        );

        ConversationClient conversation = getOrCreatePrivateConversation(toUserId);
        conversation.addMessage(msg);

        SendTextMessageCommand command = new SendTextMessageCommand(
                reactMsg.getMessageId(),
                msg,
                conversationRepository
        );

        conversation.addReactionToMessage(reactionToMessageId, content, getClientId());

        client.getCommandManager().addPendingCommand(command);
        return reactMsg.getMessageId();
    }

    /**
     * Send a management message using the ACK system.
     *
     * @param messageType the type of management message
     * @param toUserId the recipient ID (usually 0 for server)
     * @return the created ManagementMessage, or null if failed
     */
    public ManagementMessage sendManagementMessage(MessageType messageType, int toUserId) {
        ManagementMessage mgmtMsg = (ManagementMessage) MessageFactory.create(
                messageType,
                getClientId(),
                toUserId
        );

        SendManagementMessageCommand command = new SendManagementMessageCommand(
                mgmtMsg.getMessageId(),
                messageType
        );

        client.getCommandManager().addPendingCommand(command);

        return mgmtMsg;
    }

    /**
     * Update the user's pseudo using the ACK system.
     *
     * @param newPseudo the new pseudo
     * @return true if the request was sent, false otherwise
     */
    public boolean updatePseudo(String newPseudo) {
        if (newPseudo == null || newPseudo.isEmpty()) {
            System.err.println("[Client] New pseudo cannot be null or empty");
            return false;
        }

        ManagementMessage mgmtMsg = sendManagementMessage(MessageType.UPDATE_PSEUDO, 0);
        if (mgmtMsg == null) {
            return false;
        }

        mgmtMsg.addParam("newPseudo", newPseudo);
        client.sendMessage(mgmtMsg);

        client.getCommandManager().addPendingCommand(new UpdatePseudoCommand(
                mgmtMsg.getMessageId(),
                newPseudo,
                activeUser
        ));

        return true;
    }

    /**
     * Remove a contact using the ACK system.
     *
     * @param contactId the contact ID to remove
     * @return true if the request was sent, false otherwise
     */
    public boolean removeContact(int contactId) {
        if (!contactRepository.isContact(contactId)) {
            System.err.println("[Client] User " + contactId + " is not a contact");
            return false;
        }

        ManagementMessage mgmtMsg = sendManagementMessage(MessageType.REMOVE_CONTACT, 0);
        if (mgmtMsg == null) {
            return false;
        }

        mgmtMsg.addParam("contactId", contactId);
        client.sendMessage(mgmtMsg);

        client.getCommandManager().addPendingCommand(new RemoveContactCommand(
                mgmtMsg.getMessageId(),
                contactId,
                contactRepository
        ));

        return true;
    }

    /**
     * Creat a new group and user is the admin 
     *
     * @param name the desired name of the group 
     * @return true if the request was sent, false otherwise
     */
    public boolean createGroup(String name) {
        if (name == null || name.isEmpty()) {
            System.err.println("[Client] Group name cannot be null or empty");
            return false;
        }

        ManagementMessage mgmtMsg = sendManagementMessage(MessageType.CREATE_GROUP, 0);
        if (mgmtMsg == null) {
            return false;
        }

        mgmtMsg.addParam(KeyInMessage.GROUP_NAME, name);
        client.sendMessage(mgmtMsg);
        client.getCommandManager().addPendingCommand(new CreateGroupCommand(
                mgmtMsg.getMessageId(),
                groupRepository 
        ));

        return true;
    }

    /**
     * Delete a group and user is the admin 
     *
     * @param groupID the group ID 
     * @return true if the request was sent, false otherwise
     */
    public boolean deleteGroup(int groupID) {

        ManagementMessage mgmtMsg = sendManagementMessage(MessageType.DELETE_GROUP, groupID);
        if (mgmtMsg == null) {
            return false;
        }

        mgmtMsg.addParam(KeyInMessage.GROUP_ID, groupID);
        client.sendMessage(mgmtMsg);
        client.getCommandManager().addPendingCommand(new DeleteGroupCommand(
                mgmtMsg.getMessageId(),
                groupID,
                groupRepository 
        ));

        return true;
    }


    /**
     * Rename a group need to be admin 
     *
     * @param name the desired name of the group 
     * @param groupID the group 
     * @return true if the request was sent, false otherwise
     */
    public boolean renameGroup(String name, int groupID) {
        if (name == null || name.isEmpty() ) {
            System.err.println("[Client] Group name cannot be null or empty");
            return false;
        }


        ManagementMessage mgmtMsg = sendManagementMessage(MessageType.UPDATE_GROUP_NAME, groupID);
        if (mgmtMsg == null) {
            return false;
        }

        mgmtMsg.addParam(KeyInMessage.GROUP_NAME, name);
        client.sendMessage(mgmtMsg);
        client.getCommandManager().addPendingCommand(new UpdateGroupNameCommand(
                mgmtMsg.getMessageId(),
                groupID,
                groupRepository, 
                name
        ));

        return true;
    }

    /**
     * Leave a group 
     *
     * @param groupID the id of the group to leave 
     * @return true if the request was sent, false otherwise
     */
    public boolean leaveGroup(int groupID) {

        ManagementMessage mgmtMsg = sendManagementMessage(MessageType.LEAVE_GROUP, groupID);
        if (mgmtMsg == null) {
            return false;
        }

        mgmtMsg.addParam(KeyInMessage.GROUP_ID, groupID);

        client.sendMessage(mgmtMsg);
        client.getCommandManager().addPendingCommand(new LeaveGroupCommand(
                mgmtMsg.getMessageId(),
                groupID,
                groupRepository
        ));

        return true;
    }

    /**
     * Add member to a group, Need to be the admin of the group for this work
     *
     * @param groupID the id of the group 
     * @param newMember the member to add to the group
     * @return true if the request was sent, false otherwise
     */
    public boolean addMemberToGroup(int groupID, int newMember) {
        /* We send the message and the server handle we are not the admin */

        ManagementMessage mgmtMsg = sendManagementMessage(MessageType.ADD_GROUP_MEMBER, groupID);
        if (mgmtMsg == null) {
            return false;
        }

        mgmtMsg.addParam(KeyInMessage.MEMBER_ADD_ID, newMember);

        client.sendMessage(mgmtMsg);

        client.getCommandManager().addPendingCommand(new AddMemberGroupCommand(
                mgmtMsg.getMessageId(),
                groupID,
                groupRepository,
                newMember
        ));
        return true;
    }

    /**
     * Remove a member from a group, Need to be the admin of the group for this work
     *
     * @param groupID the id of the group 
     * @param deleteMember the member to add to the group
     * @return true if the request was sent, false otherwise
     */
    public boolean removeMemberToGroup(int groupID, int deleteMember) {
        /* We send the message and the server handle we are not the admin */

        ManagementMessage mgmtMsg = sendManagementMessage(MessageType.REMOVE_GROUP_MEMBER, groupID);
        if (mgmtMsg == null) {
            return false;
        }

        mgmtMsg.addParam(KeyInMessage.MEMBER_REMOVE_ID, deleteMember);

        client.sendMessage(mgmtMsg);
        client.getCommandManager().addPendingCommand(new RemoveMemberGroupCommand(
                mgmtMsg.getMessageId(),
                groupID,
                groupRepository,
                deleteMember
        ));
        return true;
    }

    public void sendAck(ProtocolMessage originalMessage, MessageStatus ackType) {
        AckMessage ackMsg = (AckMessage) MessageFactory.create(MessageType.MESSAGE_ACK, client.getClientId(), originalMessage.getFrom());
        ackMsg.setAckType(ackType);
        ackMsg.setAcknowledgedMessageId(originalMessage.getMessageId());
        client.sendMessage(ackMsg);
    }

    public interface FileProgressCallback {
        /**
         * Called when a chunk is sent
         *
         * @param bytesSent bytes sent so far
         * @param totalBytes total file size
         * @param chunksSent number of chunks sent
         */
        void onProgress(long bytesSent, long totalBytes, int chunksSent);

        /**
         * Called when file sending completes
         */
        void onComplete(String fileName, long totalBytes, int totalChunks);

        /**
         * Called when an error occurs
         */
        void onError(String errorMessage);
    }
}
