package fr.uga.im2ag.m1info.tchatsapp.server;

import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.ProtocolMessage;
import fr.uga.im2ag.m1info.tchatsapp.common.rmi.IChatClient;
import fr.uga.im2ag.m1info.tchatsapp.common.repository.GroupRepository;
import fr.uga.im2ag.m1info.tchatsapp.server.repository.UserRepository;

import java.rmi.RemoteException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class ChatServerContext {
    private static final Logger LOG = Logger.getLogger(ChatServerContext.class.getName());

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final IdGenerator idGenerator;

    private final ConcurrentHashMap<Integer, IChatClient> connectedClients = new ConcurrentHashMap<>();

    public ChatServerContext() {
        this.userRepository = new UserRepository();
        this.groupRepository = new GroupRepository();
        this.idGenerator = new SequentialIdGenerator();
    }

    // ========================= Repositories =========================

    public UserRepository getUserRepository() {
        return userRepository;
    }

    public GroupRepository getGroupRepository() {
        return groupRepository;
    }

    // ========================= ID Generation =========================

    public int generateClientId() {
        return idGenerator.generateId();
    }

    // ========================= Client Registration =========================

    public boolean isClientRegistered(int clientId) {
        return userRepository.findById(clientId) != null;
    }

    public boolean isClientConnected(int clientId) {
        return connectedClients.containsKey(clientId);
    }

    public void registerClient(int clientId, IChatClient callback) {
        connectedClients.put(clientId, callback);
        LOG.info("Client " + clientId + " connected (callback registered)");
    }

    public void unregisterClient(int clientId) {
        connectedClients.remove(clientId);
        LOG.info("Client " + clientId + " disconnected (callback removed)");
    }

    // ========================= Message Sending =========================

    public void sendToClient(ProtocolMessage message, int clientId) {
        IChatClient callback = connectedClients.get(clientId);
        if (callback != null) {
            try {
                callback.receiveMessage(message);
            } catch (RemoteException e) {
                LOG.warning("Failed to send to client " + clientId + ": " + e.getMessage());
                handleClientDisconnection(clientId);
            }
        } else {
            LOG.info("Client " + clientId + " is offline, message not delivered");
        }
    }

    public void sendToRecipient(ProtocolMessage message) {
        int recipientId = message.getTo();

        if (isGroupId(recipientId)) {
            // Broadcast aux membres du groupe
            var group = groupRepository.findById(recipientId);
            if (group != null) {
                for (int memberId : group.getMembersId()) {
                    if (memberId != message.getFrom()) {
                        sendToClient(message, memberId);
                    }
                }
            }
        } else {
            sendToClient(message, recipientId);
        }
    }

    public boolean isGroupId(int id) {
        return groupRepository.findById(id) != null;
    }

    // ========================= Disconnection Handling =========================

    private void handleClientDisconnection(int clientId) {
        connectedClients.remove(clientId);
        LOG.warning("Client " + clientId + " forcefully disconnected (RemoteException)");
    }
}
