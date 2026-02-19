package fr.uga.im2ag.m1info.tchatsapp.server;

import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.ProtocolMessage;
import fr.uga.im2ag.m1info.tchatsapp.common.rmi.IChatClient;
import fr.uga.im2ag.m1info.tchatsapp.common.rmi.IChatServer;
import fr.uga.im2ag.m1info.tchatsapp.server.handlers.ServerHandlerContext;
import fr.uga.im2ag.m1info.tchatsapp.server.model.UserInfo;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
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
        return true;
    }

    @Override
    public void disconnect(int clientId) throws RemoteException {
        context.unregisterClient(clientId);
        LOG.info("Client disconnected: id=" + clientId);
    }

    public ChatServerContext getContext() {
        return context;
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
