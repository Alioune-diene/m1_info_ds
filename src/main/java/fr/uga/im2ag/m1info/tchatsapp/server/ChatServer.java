package fr.uga.im2ag.m1info.tchatsapp.server;

import fr.uga.im2ag.m1info.tchatsapp.common.MessageType;
import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.HistorySyncMessage;
import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.MessageFactory;
import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.ProtocolMessage;
import fr.uga.im2ag.m1info.tchatsapp.common.model.GroupInfo;
import fr.uga.im2ag.m1info.tchatsapp.common.model.StoredMessage;
import fr.uga.im2ag.m1info.tchatsapp.common.rmi.IChatClient;
import fr.uga.im2ag.m1info.tchatsapp.common.rmi.IChatServer;
import fr.uga.im2ag.m1info.tchatsapp.server.handlers.ServerHandlerContext;
import fr.uga.im2ag.m1info.tchatsapp.common.model.UserInfo;
import fr.uga.im2ag.m1info.tchatsapp.server.model.ConversationServerData;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class ChatServer extends UnicastRemoteObject implements IChatServer {
    private static final Logger LOG = Logger.getLogger(ChatServer.class.getName());

    private final ChatServerContext context;
    private final ServerMessageRouter router;
    private final ExecutorService workers;

    public ChatServer(int port) throws RemoteException {
        super(port);
        this.context = new ChatServerContext();
        this.workers = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors()
        );

        ServerHandlerContext handlerContext = ServerHandlerContext.builder().build();
        this.router = ServerMessageRouter.createWithServiceLoader(context, handlerContext);

        LOG.info("ChatServer initialized on port " + port);
    }

    @Override
    public void processMessage(ProtocolMessage message) throws RemoteException {
        workers.submit(() -> {
            try {
                router.process(message);
            } catch (Exception e) {
                LOG.severe("Error processing message: " + e.getMessage());
            }
        });
    }

    @Override
    public int registerClient(String pseudo, IChatClient callback) throws RemoteException {
        int clientId = context.generateClientId();

        UserInfo user = new UserInfo(clientId, pseudo);
        context.getUserRepository().add(user);
        context.registerClient(clientId, callback);

        LOG.info("Client registered: id=" + clientId + ", pseudo=" + pseudo);
        return clientId;
    }

    @Override
    public boolean connectClient(int clientId, IChatClient callback) throws RemoteException {
        if (!context.isClientRegistered(clientId)) {
            LOG.warning("Connection refused: unknown client id " + clientId);
            return false;
        }

        context.registerClient(clientId, callback);
        LOG.info("Client reconnected: id=" + clientId);
        workers.submit(() -> sendHistoryToClient(clientId));
        return true;
    }

    @Override
    public void disconnect(int clientId) throws RemoteException {
        context.unregisterClient(clientId);
        LOG.info("Client disconnected: id=" + clientId);
    }

    @Override
    public Set<UserInfo> getContacts(int clientId) throws RemoteException {
        UserInfo user = context.getUserRepository().findById(clientId);
        if (user == null) {
            LOG.warning("User not found for client id " + clientId);
            return Set.of();
        }

        return user.getContacts().stream()
                .map(contactId -> {
                    UserInfo contact = context.getUserRepository().findById(contactId);
                    if (contact != null) {
                        return new UserInfo(contact.getId(), contact.getUsername(), new HashSet<>(), contact.getLastLogin());
                    } else {
                        LOG.warning("Contact not found for id " + contactId);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

    }

    @Override
    public String getClientPseudo(int clientId) throws RemoteException {
        UserInfo user = context.getUserRepository().findById(clientId);
        if (user == null) {
            LOG.warning("User not found for client id " + clientId);
            return null;
        }

        return user.getUsername();
    }

    @Override
    public Set<GroupInfo> getGroups(int clientId) throws RemoteException {
        UserInfo user = context.getUserRepository().findById(clientId);
        if (user == null) {
            LOG.warning("User not found for client id " + clientId);
            return Set.of();
        }

        return context.getGroupRepository().findAll().stream()
                .filter(group -> group.hasMember(clientId))
                .collect(java.util.stream.Collectors.toSet());
    }

    public ChatServerContext getContext() {
        return context;
    }

    private void sendHistoryToClient(int clientId) {
        UserInfo user = context.getUserRepository().findById(clientId);
        if (user == null) return;

        List<StoredMessage> allMessages = new ArrayList<>();
        Set<String> processedConvIds = new HashSet<>();

        for (int contactId : user.getContacts()) {
            String convId = ChatServerContext.privateConversationId(clientId, contactId);
            if (processedConvIds.add(convId)) {
                ConversationServerData conv = context.getConversationRepository().findById(convId);
                if (conv != null) allMessages.addAll(conv.getMessages());
            }
        }

        for (GroupInfo group : context.getGroupRepository().findAll()) {
            if (group.hasMember(clientId)) {
                String convId = ChatServerContext.groupConversationId(group.getGroupId());
                if (processedConvIds.add(convId)) {
                    ConversationServerData conv = context.getConversationRepository().findById(convId);
                    if (conv != null) allMessages.addAll(conv.getMessages());
                }
            }
        }

        if (allMessages.isEmpty()) return;

        allMessages.sort(Comparator.comparing(StoredMessage::getTimestamp));

        HistorySyncMessage syncMsg = (HistorySyncMessage) MessageFactory.create(MessageType.HISTORY_SYNC, 0, clientId);
        syncMsg.setMessages(allMessages);

        context.sendToClient(syncMsg, clientId);
        LOG.info("Sent history to client " + clientId + " (" + allMessages.size() + " messages)");
    }

    // ========================= Main =========================

    public static void main(String[] args) throws Exception {
        int port = 1099;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }

        Registry registry = LocateRegistry.createRegistry(port);
        ChatServer server = new ChatServer(port);
        registry.rebind("ChatServer", server);

        LOG.info("=== ChatServer started on port " + port + " ===");
        System.out.println("ChatServer is running. Press Ctrl+C to stop.");

        // Keep alive
        Thread.currentThread().join();
    }
}
