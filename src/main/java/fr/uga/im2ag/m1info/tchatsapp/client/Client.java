/*
 * Copyright (c) 2025.  Jerome David. Univ. Grenoble Alpes.
 * This file is part of TchatsApp.
 *
 * TchatsApp is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * TchatsApp is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with TchatsApp. If not, see <https://www.gnu.org/licenses/>.
 */

package fr.uga.im2ag.m1info.tchatsapp.client;

import fr.uga.im2ag.m1info.tchatsapp.client.command.PendingCommandManager;
import fr.uga.im2ag.m1info.tchatsapp.common.MessageProcessor;
import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.*;
import fr.uga.im2ag.m1info.tchatsapp.common.rmi.IChatClient;
import fr.uga.im2ag.m1info.tchatsapp.common.rmi.IChatServer;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.logging.Logger;

public class Client implements IChatClient {
    private static final Logger LOG = Logger.getLogger(Client.class.getName());

    public static final int MAX_SIZE_CHUNK_FILE = 8192;

    private int clientId;
    private IChatServer serverStub;
    private MessageProcessor processor; // Sera alimenté par ClientController
    private boolean connected;
    private final PendingCommandManager commandManager;

    public Client() {
        this(0);
    }

    public Client(int clientId) {
        this.clientId = clientId;
        this.commandManager = new PendingCommandManager();
        this.connected = false;
    }

    /**
     * Get the pending command manager.
     *
     * @return the pending command manager
     */
    public PendingCommandManager getCommandManager() {
        return commandManager;
    }

    // ========================= Connexion RMI =========================

    /**
     * Connexion au serveur via RMI.
     *
     * @param host le hostname du serveur
     * @param port le port du registry RMI
     * @param username le pseudo (pour création de compte si clientId == 0)
     * @return true si connecté
     */
    public boolean connect(String host, int port, String username) throws Exception {
        Registry registry = LocateRegistry.getRegistry(host, port);
        serverStub = (IChatServer) registry.lookup("ChatServer");

        // Exporter cet objet pour recevoir les callbacks
        IChatClient clientStub = (IChatClient) UnicastRemoteObject.exportObject(this, 0);

        if (clientId == 0) {
            // Nouveau client → registerClient
            clientId = serverStub.registerClient(username, clientStub);
            if (clientId <= 0) {
                throw new RemoteException("Registration failed");
            }
        } else {
            // Client existant → reconnexion
            if (!serverStub.connectClient(clientId, clientStub)) {
                throw new RemoteException("Connection failed for id " + clientId);
            }
        }

        connected = true;
        LOG.info("Connected to server as client " + clientId);
        return true;
    }

    /**
     * Déconnexion propre.
     */
    public void disconnect() {
        try {
            if (serverStub != null && connected) {
                serverStub.disconnect(clientId);
            }
            UnicastRemoteObject.unexportObject(this, true);
        } catch (Exception e) {
            LOG.warning("Error during disconnect: " + e.getMessage());
        } finally {
            connected = false;
            serverStub = null;
        }
    }

    // ========================= Callback RMI (réception) =========================

    @Override
    public void receiveMessage(ProtocolMessage message) throws RemoteException {
        if (processor != null) {
            processor.process(message);
        } else {
            LOG.warning("Received message but no processor set: " + message.getMessageType());
        }
    }

    // ========================= Envoi =========================

    /**
     * Envoie un ProtocolMessage au serveur.
     */
    public boolean sendMessage(ProtocolMessage message) {
        try {
            serverStub.processMessage(message);
            return true;
        } catch (RemoteException e) {
            LOG.severe("Failed to send message: " + e.getMessage());
            return false;
        }
    }

    // ========================= Accessors =========================

    public int getClientId() {
        return clientId;
    }

    void updateClientId(int clientId) {
        this.clientId = clientId;
    }

    public void setMessageProcessor(MessageProcessor p) {
        this.processor = p;
    }

    public boolean isConnected() {
        return connected;
    }

    public IChatServer getServerStub() {
        return serverStub;
    }
}
