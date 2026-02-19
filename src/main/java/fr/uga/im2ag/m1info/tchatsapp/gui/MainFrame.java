package fr.uga.im2ag.m1info.tchatsapp.gui;

import fr.uga.im2ag.m1info.tchatsapp.client.Client;
import fr.uga.im2ag.m1info.tchatsapp.client.ClientController;
import fr.uga.im2ag.m1info.tchatsapp.client.event.types.FileTransferProgressEvent;
import fr.uga.im2ag.m1info.tchatsapp.client.media.MediaManager;
import fr.uga.im2ag.m1info.tchatsapp.client.model.*;
import fr.uga.im2ag.m1info.tchatsapp.client.repository.ContactClientRepository;
import fr.uga.im2ag.m1info.tchatsapp.client.repository.ConversationClientRepository;
import fr.uga.im2ag.m1info.tchatsapp.common.model.GroupInfo;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

public class MainFrame extends JFrame {
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 1099;

    private static final String CARD_LOGIN = "login";
    private static final String CARD_HOME = "home";
    private static final String CARD_CONVERSATION = "conversation";

    private ClientController controller;
    private GuiEventHandler eventHandler;

    // UI components
    private final JPanel cards;
    private final CardLayout cardLayout;
    private final LoginPanel loginPanel;
    private final HomePanel homePanel;
    private final ConversationPanel conversationPanel;

    // State
    private String currentConversationId;
    private volatile boolean awaitingConnection;
    private HashMap <String, Set<Integer>> pendingGroupParticipants;

    public MainFrame() {
        super("TchatApp");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleApplicationClose();
            }
        });

        // Initialize UI components
        this.cardLayout = new CardLayout();
        this.cards = new JPanel(cardLayout);
        this.loginPanel = new LoginPanel();
        this.homePanel = new HomePanel();
        this.conversationPanel = new ConversationPanel();

        // Setup card layout
        cards.add(loginPanel, CARD_LOGIN);
        cards.add(homePanel, CARD_HOME);
        cards.add(conversationPanel, CARD_CONVERSATION);
        setContentPane(cards);

        // Initial size for login
        loginPanel.setPreferredSize(new Dimension(420, 360));
        pack();
        setLocationRelativeTo(null);

        // Setup UI event listeners
        setupUIListeners();
    }

    // ----------------------- UI Setup -----------------------

    private void setupUIListeners() {
        loginPanel.addSubmitListener(e -> handleLogin());
        homePanel.addConversationSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                HomePanel.ConversationItem selected = homePanel.getSelectedConversation();
                if (selected != null) {
                    handleConversationSelection(selected);
                }
            }
        });
        homePanel.setOnNewConversation(e -> handleNewConversationRequest());
        homePanel.setOnNewContact(e -> handleNewContactRequest());
        homePanel.setOnViewContacts(e -> generateContactView());
        homePanel.setOnShowPendingRequest(e -> showPendingContactRequests());
        homePanel.setOnUpdatePseudo(e -> handleUpdatePseudoRequest());

        conversationPanel.setOnFileSend(this::handleFileSend);
        conversationPanel.setOnFileDownload(this::handleFileDownload);
        conversationPanel.setOnFileOpen(this::handleFileOpen);
    }

    

    private void setupEventHandlerCallbacks() {
        eventHandler.setOnConnectionEstablished(event -> {
            if (awaitingConnection) {
                awaitingConnection = false;
                onConnectionSuccess(event.getClientId(), event.getPseudo(), event.isNewUser());
            }
        });

        eventHandler.setOnGroupCreated(event -> {
            int groupId = event.getGroupInfo().getGroupId();
            String groupName = event.getGroupInfo().getGroupName();
            if (pendingGroupParticipants.containsKey(groupName)) {
                for (Integer participantId : pendingGroupParticipants.get(groupName)){
                    controller.addMemberToGroup(groupId, participantId);
                }
                controller.getOrCreateGroupConversation(groupId);
                pendingGroupParticipants.remove(groupName);
            }
            controller.getOrCreateGroupConversation(groupId);
            refreshHomeConversations();
        });

        eventHandler.setOnGroupMemberChanged(event -> {
            int groupId = event.getGroupId();
            if (controller.getGroupRepository().findById(groupId) != null && currentConversationId != null) {
                ConversationClient open = controller.getConversationRepository().findById(currentConversationId);
                if (open != null && open.getPeerId() == event.getGroupId()) {
                    refreshMessages(open);
                }
            }    
        });

        eventHandler.setOnContactRequestResponse(event -> {
            showContactRequestResponseDialog(event.isAccepted(), event.getOtherUserId());
        });

        eventHandler.setOnContactRequestReceived(event -> {
            Boolean choice = showContactRequestReceivedDialog(event.getSenderId());
            if (choice != null) {
                controller.respondToContactRequest(event.getSenderId(), choice);
            } 
        });

        eventHandler.setOnUserPseudoUpdated(event -> {
            String newPseudo = event.getNewPseudo();
            setTitle("TchatApp - " + newPseudo);
            homePanel.setUserPseudo(newPseudo);
            homePanel.repaint();
        });

        eventHandler.setOnContactUpdated(event -> {
            refreshHomeConversations();
        });

        eventHandler.setOnTextMessageReceived(event -> {
            refreshHomeConversations();
            String convId = event.getConversationId();
            if (currentConversationId != null && currentConversationId.equals(convId)) {
                ConversationClient conv = controller.getConversationRepository().findById(convId);
                refreshMessages(conv);
            }
        });

        eventHandler.setOnMediaMessageReceived(event -> {
            refreshHomeConversations();
            String convId = event.getConversationId();
            if (currentConversationId != null && currentConversationId.equals(convId)) {
                ConversationClient conv = controller.getConversationRepository().findById(convId);
                refreshMessages(conv);
            }
        });

        eventHandler.setOnReactionMessageReceived(event -> {
            refreshHomeConversations();
            String convId = event.getConversationId();
            if (currentConversationId != null && currentConversationId.equals(convId)) {
                ConversationClient conv = controller.getConversationRepository().findById(convId);
                refreshMessages(conv);
            }
        });

        eventHandler.setOnMessageStatusChanged(event -> {

        });

        eventHandler.setOnContactAdded(event -> {
            int contactId = event.getContactId();
            controller.getOrCreatePrivateConversation(contactId);
            refreshHomeConversations();
        });

        eventHandler.setOnContactRemoved(event -> {

        });

        eventHandler.setOnUpdateGroupName(event -> {
            int groupId = event.getGroupId();
            GroupInfo group;
            if ((group = controller.getGroupRepository().findById(groupId)) != null){
                refreshHomeConversations();
                ConversationClient open = controller.getConversationRepository().findById(currentConversationId);
                if (open != null && open.getPeerId() == event.getGroupId()) {
                    refreshMessages(open);
                }
            }  
        });

        eventHandler.setOnFileTransferProgress(this::handleFileTransferProgress);
    }

    // ----------------------- Login Flow -----------------------

    // TODO: Better gestion for invalid id
    private void handleLogin() {
        loginPanel.clearError();

        String raw = loginPanel.getUsername().trim();
        if (raw.isEmpty()) {
            loginPanel.showError("Veuillez entrer un identifiant numérique.");
            return;
        }

        int clientId;
        try {
            clientId = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            loginPanel.showError("Identifiant invalide : veuillez entrer un nombre.");
            return;
        }

        // Ask for pseudo if creating new account
        String pseudo = "";
        if (clientId == 0) {
            pseudo = askPseudo();
            if (pseudo == null) {
                return; // User cancelled
            }
        }

        // Initialize client and controller
        initializeClient(clientId);

        // Start connection
        awaitingConnection = true;
        loginPanel.getLoginButton().setEnabled(false);
        loginPanel.showError("Connexion en cours...");

        final String finalPseudo = pseudo;
        new Thread(() -> {
            try {
                boolean socketConnected = controller.connect(DEFAULT_HOST, DEFAULT_PORT, finalPseudo);
                if (!socketConnected) {
                    SwingUtilities.invokeLater(() -> {
                        awaitingConnection = false;
                        onConnectionError("Impossible d'établir la connexion au serveur.");
                    });
                }
                // If socket connected, wait for ConnectionEstablishedEvent or ErrorEvent
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    awaitingConnection = false;
                    onConnectionError("Erreur de connexion : " + e.getMessage());
                });
            }
        }, "Connection-Thread").start();
    }

    private void initializeClient(int clientId) {
        this.controller = new ClientController(new Client(clientId));
        this.controller.initializeHandlers();

        // Initialize event handler
        this.eventHandler = new GuiEventHandler(controller);
        setupEventHandlerCallbacks();
        eventHandler.registerSubscriptions();

        // Initialize group member pending for a group created on the request of the user
        this.pendingGroupParticipants = new HashMap<>();
    }
    private void onConnectionSuccess(int clientId, String pseudo, boolean isNewUser) {

        loginPanel.clearError();
        loginPanel.getLoginButton().setEnabled(true);

        if (isNewUser) {
            JOptionPane.showMessageDialog(
                    this,
                    "Compte créé avec succès !\nVotre identifiant : " + clientId,
                    "Bienvenue",
                    JOptionPane.INFORMATION_MESSAGE
            );
        }

        setTitle("TchatApp - " + pseudo);
        homePanel.setUserPseudo(pseudo);
        showHome();
    }

    private void onConnectionError(String errorMessage) {
        loginPanel.showError(errorMessage);
        loginPanel.getLoginButton().setEnabled(true);

        // Cleanup if client was initialized
        if (eventHandler != null) {
            eventHandler.unsubscribeAll();
            eventHandler = null;
        }
        if (controller != null) {
            controller.disconnect();
            controller = null;
        }
    }

    private String askPseudo() {
        String pseudo;
        do {
            pseudo = JOptionPane.showInputDialog(
                    this,
                    "Choisissez votre pseudo :",
                    "Nouveau compte",
                    JOptionPane.QUESTION_MESSAGE
            );
            if (pseudo == null) {
                return null; // User cancelled
            }
            pseudo = pseudo.trim();
        } while (pseudo.isEmpty());
        return pseudo;
    }

    // ----------------------- Navigation -----------------------

    private void showLogin() {
        cardLayout.show(cards, CARD_LOGIN);
        setSize(420, 360);
    }

    private void showHome() {
        cardLayout.show(cards, CARD_HOME);
        setSize(900, 600);
        homePanel.clearSelection();   // <--- important pour pouvoir réouvrir la conv !!!
    }

    private void showConversation() {
        cardLayout.show(cards, CARD_CONVERSATION);
    }

    // ----------------------- Home Panel Actions -----------------------

    /**
     * Handle user request to update their pseudo
     */
    private void handleUpdatePseudoRequest() {
        String currentPseudo = controller.getActiveUser().getPseudo();

        String newPseudo;
        do {
            newPseudo = JOptionPane.showInputDialog(this, "Nouveau pseudo :", currentPseudo);
            if (newPseudo == null) { return; }
            newPseudo = newPseudo.trim();

            if (newPseudo.isEmpty()) {
                JOptionPane.showMessageDialog(
                        this,
                        "Le pseudo ne peut pas être vide.",
                        "Erreur",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        } while (newPseudo.isEmpty());
        controller.updatePseudo(newPseudo);
    }

    private void handleNewConversationRequest() {

        ContactClientRepository contactRepo = controller.getContactRepository();
        if (contactRepo == null) {
            JOptionPane.showMessageDialog(this,
                    "No contact repo available",
                    "Erreur",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        int currentUserId = controller.getClientId();

        CreateConversationDialog dialog = new CreateConversationDialog(this, contactRepo, currentUserId);
        CreateConversationDialog.Result res = dialog.showDialog();
        if (res == null) {
            return;
        }

        if (res.isGroup()) {
            res.getParticipantIds().add(currentUserId);
            controller.createGroup(res.getConversationName());

            pendingGroupParticipants.put(res.getConversationName(), res.getParticipantIds());
        }
        else {
            int otherParticipantId = res.getParticipantIds().iterator().next();
            ConversationClient conv = controller.getOrCreatePrivateConversation(otherParticipantId);
            ContactClient contact = controller.getContactRepository().findById(otherParticipantId);
            conv.setConversationName(contact.getPseudo());
        }
        // MaJ HP
        refreshHomeConversations();
    }

    private void refreshHomeConversations() {
        ConversationClientRepository convoRepo = controller.getConversationRepository();

        Set<ConversationClient> allConv = convoRepo.findAll();

        ArrayList<HomePanel.ConversationItem> items = new ArrayList<>();

        for (ConversationClient conv : allConv) {
            Message last = conv.getLastMessage();
            String preview = "";
            if (last != null) {preview = last.getContent();}

            HomePanel.ConversationItem item =
                    new HomePanel.ConversationItem(
                            conv.getConversationId(),
                            conv.getConversationName(),
                            preview
                    );

            items.add(item);
        }

        items.sort((a, b) -> {
            ConversationClient ca = convoRepo.findById(a.getId());
            ConversationClient cb = convoRepo.findById(b.getId());
            Message ma = ca.getLastMessage();
            Message mb = cb.getLastMessage();
            Instant ta = (ma == null ? Instant.MIN : ma.getTimestamp());
            Instant tb = (mb == null ? Instant.MIN : mb.getTimestamp());
            return tb.compareTo(ta); // plus récent d’abord
        });

        homePanel.setConversations(items);
    }

    private void handleNewContactRequest() {
        String idToAdd;
        try {
            do {
                idToAdd = JOptionPane.showInputDialog(this, "Envoyer une demande de contact à :", "Nouveau contact", JOptionPane.QUESTION_MESSAGE);
                if (idToAdd == null) { return; }
                idToAdd = idToAdd.trim();
            } while (idToAdd.isEmpty());

            int otherUserId = Integer.parseInt(idToAdd);
            controller.sendContactRequest(otherUserId);
        } catch (NumberFormatException e) {
            showErrorDialog("Identifiant invalide", "Veuillez entrer un nombre valide.");
        }
    }

    private void generateContactView() {
        JDialog dialog = new JDialog(this, "Your contacts", true);
        dialog.setLayout(new BorderLayout(8, 8));

        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));

        Set<ContactClient> contacts = controller.getContactRepository().findAll();

        if (contacts.isEmpty()) {
            listPanel.add(new JLabel("You have no contacts."));
        } else {
            for (ContactClient contact : contacts) {
                String label = "- " + contact.getPseudo()
                            + " (" + contact.getContactId() + ")";
                listPanel.add(new JLabel(label));
            }
        }

        JScrollPane scroll = new JScrollPane(listPanel);
        scroll.setPreferredSize(new Dimension(300, 200));

        dialog.add(scroll, BorderLayout.CENTER);

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dialog.dispose());
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(closeBtn);

        dialog.add(buttonPanel, BorderLayout.SOUTH);

        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void showPendingContactRequests() {

        ContactClientRepository repo = controller.getContactRepository();
        repo.cleanupExpiredRequests();

        Set<ContactRequest> received = repo.getPendingReceivedRequests();
        Set<ContactRequest> sent = repo.getPendingSentRequests();

        if (received.isEmpty() && sent.isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    "no pending contact requests",
                    "pending contact requests",
                    JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        JDialog dialog = new JDialog(this, "Pending contact requests", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout(10, 10));

        JPanel receivedListPanel = new JPanel();
        receivedListPanel.setLayout(new BoxLayout(receivedListPanel, BoxLayout.Y_AXIS));

        for (ContactRequest request : received) {
            int senderId = request.getSenderId();

            String pseudo = "User #" + senderId;
            ContactClient contact = repo.findById(senderId);
            if (contact != null && contact.getPseudo() != null && !contact.getPseudo().isEmpty()) {
                pseudo = contact.getPseudo();
            }

            JPanel item = new JPanel(new FlowLayout(FlowLayout.LEFT));
            item.add(new JLabel(pseudo + " (" + senderId + ")"));

            JButton acceptBtn = new JButton("Accept");
            JButton denyBtn = new JButton("Deny");

            acceptBtn.addActionListener(ev -> {
                controller.respondToContactRequest(senderId, true);
                dialog.dispose();
                refreshHomeConversations();
            });

            denyBtn.addActionListener(ev -> {
                controller.respondToContactRequest(senderId, false);
                dialog.dispose();
                refreshHomeConversations();
            });

            item.add(acceptBtn);
            item.add(denyBtn);

            receivedListPanel.add(item);
        }

        JScrollPane receivedScroll = new JScrollPane(receivedListPanel);

        JPanel receivedPanel = new JPanel(new BorderLayout(4, 4));
        receivedPanel.setBorder(BorderFactory.createTitledBorder("Received"));
        receivedPanel.add(receivedScroll, BorderLayout.CENTER);

        JPanel sentListPanel = new JPanel();
        sentListPanel.setLayout(new BoxLayout(sentListPanel, BoxLayout.Y_AXIS));

        for (ContactRequest request : sent) {
            int receiverId = request.getReceiverId();

            String pseudo = "User #" + receiverId;
            ContactClient contact = repo.findById(receiverId);
            if (contact != null && contact.getPseudo() != null && !contact.getPseudo().isEmpty()) {
                pseudo = contact.getPseudo();
            }

            JPanel item = new JPanel(new FlowLayout(FlowLayout.LEFT));
            item.add(new JLabel(pseudo + " (" + receiverId + ")"));
            sentListPanel.add(item);
        }

        JScrollPane sentScroll = new JScrollPane(sentListPanel);

        JPanel sentPanel = new JPanel(new BorderLayout(4, 4));
        sentPanel.setBorder(BorderFactory.createTitledBorder("Sent"));
        sentPanel.add(sentScroll, BorderLayout.CENTER);

        JPanel center = new JPanel(new GridLayout(1, 2, 8, 8));
        center.add(receivedPanel);
        center.add(sentPanel);
        dialog.add(center, BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dialog.dispose());
        south.add(closeBtn);
        dialog.add(south, BorderLayout.SOUTH);

        dialog.setSize(600, 400);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }




    // ----------------------- Conversation Panel Actions -----------------------


    public void handleConversationSelection(HomePanel.ConversationItem conversation) {
        CardLayout cl = (CardLayout) cards.getLayout();
        ConversationClient conv = controller.getConversationRepository()
                                         .findById(conversation.getId());

        currentConversationId = conv.getConversationId();
        conversationPanel.setConversationTitle(conv.getConversationName());

        refreshMessages(conv);

        conversationPanel.setOnSend((text, replyId) -> {
            if (text == null || text.trim().isEmpty()) {
                return;
            }
            String trimmed = text.trim();

            int toUserId = conv.getPeerId();
            controller.sendTextMessage(trimmed, toUserId, replyId);
            refreshMessages(conv);
        });

        conversationPanel.setOnReact((messageId, reaction) -> {
            if (currentConversationId != null) {
                int recipientId = conv.getPeerId();
                controller.sendReactionMessage(reaction, recipientId, messageId);
            }
        });

        conversationPanel.setOnOption(() -> {
            if (conv.isGroupConversation()) {
                showGroupOptions(conv);
            }
        });
        conversationPanel.setOnBack(e -> showHome());
        cl.show(cards, "conversation");
    }

    private void showGroupOptions(ConversationClient conv) {

        // show members
        JPopupMenu menu = new JPopupMenu();
        JMenuItem viewMembers = new JMenuItem("Show members");
        viewMembers.addActionListener(e -> showGroupMembersDialog(conv));
        menu.add(viewMembers);

        // add memebr by id
        JMenuItem addMemberItem = new JMenuItem("Add a member");
        addMemberItem.addActionListener(e -> handleAddMemberToGroup(conv));
        menu.add(addMemberItem);

        // remove member by id
        JMenuItem removeMemberItem = new JMenuItem("Remove a member");
        removeMemberItem.addActionListener(e -> handleRemoveMemberFromGroup(conv));
        menu.add(removeMemberItem);

        // rename group
        JMenuItem renameItem = new JMenuItem("Rename group");
        renameItem.addActionListener(e -> handleRenameGroup(conv));
        menu.add(renameItem);

        menu.show(conversationPanel, conversationPanel.getWidth() - 10, 35);

        
    }

    private void handleRenameGroup(ConversationClient conv) {
        if (!ensureAdminOrWarn(conv)) {
            return;
        }

        String input = JOptionPane.showInputDialog(
                this,
                "New group name ?",
                "Renaming group",
                JOptionPane.QUESTION_MESSAGE
        );

        if (input == null) return; // cancel
        input = input.trim();
        if (input.isEmpty()) return;
        controller.renameGroup(input, conv.getPeerId());
    }

    private void handleRemoveMemberFromGroup(ConversationClient conv) {
        if (!ensureAdminOrWarn(conv)) {
            return;
        }

        String input = JOptionPane.showInputDialog(
                this,
                "ID of member to be removed :",
                "Remove a member",
                JOptionPane.QUESTION_MESSAGE
        );

        if (input == null) return; // cancel
        input = input.trim();
        if (input.isEmpty()) return;

        try {
            int memberId = Integer.parseInt(input);
            controller.removeMemberToGroup(conv.getPeerId(), memberId);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Invalid ID, choose an int",
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void showGroupMembersDialog(ConversationClient conv) {
        ContactClientRepository contacts = controller.getContactRepository();

        StringBuilder sb = new StringBuilder();
        for (var entry : controller.getGroupRepository().findById(conv.getPeerId()).getMembers().entrySet()) {
            sb.append("- ").append(entry.getValue()).append(" (").append(entry.getKey()).append(")").append("\n");
        }

        JOptionPane.showMessageDialog(
                this,
                sb.toString(),
                "Membres du groupe : " + conv.getConversationName(),
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    private void handleAddMemberToGroup(ConversationClient conv) {
        if (!ensureAdminOrWarn(conv)) {
            return;
        }

        String input = JOptionPane.showInputDialog(
                this,
                "ID of member to be added :",
                "Add a member",
                JOptionPane.QUESTION_MESSAGE
        );

        if (input == null) return; // cancel
        input = input.trim();
        if (input.isEmpty()) return;

        try {
            int memberId = Integer.parseInt(input);
            controller.addMemberToGroup(conv.getPeerId(), memberId);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Invalid ID, choose an int",
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void handleFileSend(File file) {
        if (currentConversationId == null) {
            JOptionPane.showMessageDialog(this,
                    "Aucune conversation sélectionnée",
                    "Erreur",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        int recipientId = controller.getConversationRepository().findById(currentConversationId).getPeerId();

        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setString("Envoi en cours... 0%");

        JPanel progressPanel = new JPanel(new BorderLayout(10, 10));
        progressPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        progressPanel.add(new JLabel("Envoi de : " + file.getName()), BorderLayout.NORTH);
        progressPanel.add(progressBar, BorderLayout.CENTER);

        JDialog progressDialog = new JDialog(this, "Envoi de fichier", false);
        progressDialog.setContentPane(progressPanel);
        progressDialog.pack();
        progressDialog.setLocationRelativeTo(this);
        progressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        progressDialog.setVisible(true);

        ClientController.FileProgressCallback callback = new ClientController.FileProgressCallback() {
            @Override
            public void onProgress(long bytesSent, long totalBytes, int chunksSent) {
                SwingUtilities.invokeLater(() -> {
                    int percentage = (int) ((bytesSent * 100) / totalBytes);
                    progressBar.setValue(percentage);
                    progressBar.setString(String.format("Envoi en cours... %d%% (%d chunks)",
                            percentage, chunksSent));
                });
            }

            @Override
            public void onComplete(String fileName, long totalBytes, int totalChunks) {
                SwingUtilities.invokeLater(() -> {
                    progressDialog.dispose();

                    try {
                        String mediaId = java.util.UUID.randomUUID().toString();
                        VirtualMedia virtualMedia = new VirtualMedia(
                                mediaId,
                                fileName,
                                totalBytes,
                                Instant.now(),
                                controller.getClientId()
                        );

                        ConversationClient conversation = controller.getConversationRepository().findById(currentConversationId);
                        if (conversation != null) {
                            // TODO: Use real info
                            Message message = new Message(
                                    java.util.UUID.randomUUID().toString(),
                                    controller.getClientId(),
                                    recipientId,
                                    "[Media Message]",
                                    Instant.now(),
                                    null
                            );
                            message.setAttachedMedia(virtualMedia);

                            conversation.addMessage(message);
                            controller.getConversationRepository().update(currentConversationId, conversation);

                            if (currentConversationId != null) {
                                List<ConversationPanel.MessageItem> messages = loadMessages(conversation);
                                conversationPanel.setMessages(messages);
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("[MainFrame] Error creating local media message: " + e.getMessage());
                        e.printStackTrace();
                    }

                    String sizeStr = formatFileSize(totalBytes);
                    JOptionPane.showMessageDialog(
                            MainFrame.this,
                            String.format("Fichier '%s' envoyé avec succès!\nTaille: %s\nChunks: %d",
                                    fileName, sizeStr, totalChunks),
                            "Envoi terminé",
                            JOptionPane.INFORMATION_MESSAGE
                    );
                });
            }

            @Override
            public void onError(String errorMessage) {
                SwingUtilities.invokeLater(() -> {
                    progressDialog.dispose();
                    JOptionPane.showMessageDialog(
                            MainFrame.this,
                            "Erreur lors de l'envoi du fichier:\n" + errorMessage,
                            "Erreur",
                            JOptionPane.ERROR_MESSAGE
                    );
                });
            }
        };

        controller.sendFile(String.valueOf(file.toPath()), recipientId, callback);
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private void handleFileDownload(String mediaId) {
        MediaManager mediaManager = controller.getMediaManager();
        VirtualMedia media = mediaManager.getVirtualMedia(mediaId);

        if (media == null) {
            JOptionPane.showMessageDialog(this, "Fichier introuvable", "Erreur", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Enregistrer le fichier");
        fileChooser.setSelectedFile(new File(media.getFileName()));

        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File destination = fileChooser.getSelectedFile();
            Path destPath = destination.toPath();

            boolean success = mediaManager.saveMediaTo(mediaId, destPath);

            if (success) {
                JOptionPane.showMessageDialog(this, "Fichier enregistré: " + destination.getAbsolutePath(), "Succès", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "Erreur lors de l'enregistrement du fichier", "Erreur", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void handleFileOpen(String mediaId) {
        MediaManager mediaManager = controller.getMediaManager();
        Path filePath = mediaManager.getMediaPath(mediaId);

        if (filePath == null || !Files.exists(filePath)) {
            JOptionPane.showMessageDialog(this, "Fichier introuvable", "Erreur", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().open(filePath.toFile());
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Impossible d'ouvrir le fichier: " + e.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            JOptionPane.showMessageDialog(this, "Ouverture de fichier non supportée sur ce système", "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleFileTransferProgress(FileTransferProgressEvent event) {
        if (event.isComplete()) {
            System.out.printf("[MainFrame] File transfer complete: %s (%s)%n", event.getFileName(), event.getFormattedProgress());
        } else {
            System.out.printf("[MainFrame] File transfer progress: %s - %s (%d chunks)%n", event.getFileName(), event.getFormattedProgress(), event.getChunksReceived());
        }
    }


    public List<ConversationPanel.MessageItem> loadMessages(ConversationClient conversation){
        List<Message> messagesFromConv = conversation.getMessagesFrom(null, -1, true, true);
        ContactClientRepository contactClientRepository = controller.getContactRepository();
        List<ConversationPanel.MessageItem> messageItems = new ArrayList<>();
        for (Message message : messagesFromConv) {
            boolean isOwnMessage = message.getFromUserId() == controller.getClientId();
            String pseudo;
            if (isOwnMessage) {
                pseudo = "Vous";
            } else {
                ContactClient contact = contactClientRepository.findById(message.getFromUserId());
                if (contact != null) {
                    pseudo = contact.getPseudo();
                } else {
                    String groupPseudo = controller.getGroupRepository().findById(conversation.getPeerId()).getMemberName(message.getFromUserId());
                    pseudo = (groupPseudo != null) ? groupPseudo : "User " + message.getFromUserId();
                }
            }
            messageItems.add(new ConversationPanel.MessageItem(
                    isOwnMessage,
                    pseudo,
                    message.getContent(),
                    message.getMessageId(),
                    message.getReplyToMessageId(),
                    message.getAttachedMedia(),
                    message.getReactions()
            ));
        }
        return messageItems;
    }

    // ----------------------- Application Lifecycle -----------------------

    private void handleApplicationClose() {
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Voulez-vous vraiment quitter ?",
                "Confirmation",
                JOptionPane.YES_NO_OPTION
        );

        if (confirm == JOptionPane.YES_OPTION) {
            cleanup();
            dispose();
            System.exit(0);
        }
    }

    private void cleanup() {
        if (eventHandler != null) {
            eventHandler.unsubscribeAll();
        }
        if (controller != null) {
            controller.disconnect();
        }
    }

    // ----------------------- Utility -----------------------

    private void showErrorDialog(String title, String message) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }

    private void showContactRequestResponseDialog (boolean isAccepted, int otherUserId) {

        String user = "User " + otherUserId;
        String message = isAccepted
                ? user + " has accepted your contact request"
                : user + " has denied your contact request";

        String title = isAccepted
                ? "Contact request accepted!"
                : "Contact request denied";

        JOptionPane.showMessageDialog(
                this,
                message,
                title,
                JOptionPane.INFORMATION_MESSAGE
        );

    }

    private Boolean showContactRequestReceivedDialog (int senderID) {
        String [] options = {"Add user as contact", "Deny contact request", "Decide later"};
        int selection = JOptionPane.showOptionDialog(this,
                                                    "Contact request from " + senderID + "received",
                                                    "Contact Request received",
                                                    JOptionPane.DEFAULT_OPTION,
                                                    JOptionPane.INFORMATION_MESSAGE,
                                                    null,
                                                    options,
                                                    options[0]
        );
        if (selection == JOptionPane.CLOSED_OPTION || selection == 2) {
        // null means do nothing, keep request pending
            return null;
        }
        return (selection == 0);
    }

    private void refreshMessages(ConversationClient conv) {
        List<ConversationPanel.MessageItem> messageItems = loadMessages(conv);
        conversationPanel.setMessages(messageItems);
    }

    private boolean isGroupAdmin(ConversationClient conv) {
        GroupInfo group = controller.getGroupRepository().findById(conv.getPeerId());
        if (group == null) {return false; }
        return group.getAdminId() == controller.getClientId();
    }

    private boolean ensureAdminOrWarn(ConversationClient conv) {
        if (isGroupAdmin(conv)) {
            return true;
        }
        JOptionPane.showMessageDialog(
                this,
                "Admin only, fool",
                "You're not supposed to do that",
                JOptionPane.WARNING_MESSAGE
        );
        return false;
    }

    // ----------------------- Entry Point -----------------------

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}
