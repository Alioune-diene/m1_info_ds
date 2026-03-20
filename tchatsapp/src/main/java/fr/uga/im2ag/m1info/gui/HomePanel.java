package fr.uga.im2ag.m1info.gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Home panel displaying the list of conversations.
 * Provides search functionality and new conversation button.
 * Pure UI component with no business logic.
 */
public class HomePanel extends JPanel {

    /**
     * Data class representing a conversation item in the list.
     */
    public static final class ConversationItem {
        private final String id;
        private final String title;
        private final String lastMessage;

        public ConversationItem(String id, String title, String lastMessage) {
            this.id = id;
            this.title = title;
            this.lastMessage = lastMessage;
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public String getLastMessage() {
            return lastMessage;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    private final JTextField searchField;
    private final DefaultListModel<ConversationItem> listModel;
    private final JList<ConversationItem> conversationList;
    private final JButton newConversationButton;
    private final JButton newContactButton;
    private final JButton pendingContactRequestButton;
    private final JButton viewContactsButton;

    private final JPanel userPanel;
    private final JLabel pseudoLabel;
    private final JPopupMenu userContextMenu;

    private final List<ConversationItem> masterList;
    private ActionListener onNewConversation;
    private ActionListener onNewContact;
    private ActionListener onViewContacts;
    private ActionListener onShowPendingRequest;
    private ActionListener onUpdatePseudo;

    public HomePanel() {
        super(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(12, 12, 12, 12));

        this.searchField = new JTextField();
        this.listModel = new DefaultListModel<>();
        this.conversationList = new JList<>(listModel);
        this.newConversationButton = new JButton("New +");
        this.newContactButton = new JButton("Add a contact");
        this.pendingContactRequestButton = new JButton("Pending contact requests");
        this.viewContactsButton = new JButton("Show contacts");
        this.masterList = new ArrayList<>();

        this.userPanel = new JPanel();
        this.pseudoLabel = new JLabel();
        this.userContextMenu = new JPopupMenu();

        setupLayout();
        setupListeners();
        setupUserPanel();
    }

    private void setupLayout() {
        // Header with title and search
        JPanel header = new JPanel(new BorderLayout(6, 6));
        JLabel title = new JLabel("Conversations");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        header.add(title, BorderLayout.NORTH);

        searchField.setToolTipText("Search for a conversation");
        header.add(searchField, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        // Conversation list
        conversationList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        conversationList.setCellRenderer(new ConversationRenderer());
        JScrollPane scrollPane = new JScrollPane(conversationList);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        add(scrollPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout(6, 6));

        JPanel buttonsPanel = new JPanel(new GridLayout(1, 2, 6, 0));
        buttonsPanel.add(newContactButton);
        buttonsPanel.add(viewContactsButton);
        buttonsPanel.add(pendingContactRequestButton);
        buttonsPanel.add(newConversationButton);
        bottomPanel.add(buttonsPanel, BorderLayout.EAST);

        bottomPanel.add(userPanel, BorderLayout.WEST);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void setupListeners() {
        // Search filter
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyFilter(searchField.getText());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyFilter(searchField.getText());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyFilter(searchField.getText());
            }
        });

        // New conversation button
        newConversationButton.addActionListener(e -> {
            if (onNewConversation != null) {
                onNewConversation.actionPerformed(e);
            }
        });

        // New contact button
        newContactButton.addActionListener(e -> {
            if (onNewContact != null) {
                onNewContact.actionPerformed(e);
            }
        });

        viewContactsButton.addActionListener(e -> {
            if (onViewContacts != null) {
                onViewContacts.actionPerformed(e);
            }
        });

        // Pending contact requests button
        pendingContactRequestButton.addActionListener(e -> {
            if (onShowPendingRequest != null) {
                onShowPendingRequest.actionPerformed(e);
            }
        });
    }

    private void setupUserPanel() {
        userPanel.setLayout(new BorderLayout(8, 0));
        userPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY),
                new EmptyBorder(8, 8, 8, 8)
        ));
        userPanel.setBackground(new Color(220, 220, 220));

        pseudoLabel.setFont(pseudoLabel.getFont().deriveFont(Font.BOLD, 13f));
        userPanel.add(pseudoLabel, BorderLayout.CENTER);

        JMenuItem updatePseudoItem = new JMenuItem("Modifier le pseudo");
        updatePseudoItem.addActionListener(e -> {
            if (onUpdatePseudo != null) {
                onUpdatePseudo.actionPerformed(e);
            }
        });
        userContextMenu.add(updatePseudoItem);

        JMenuItem copyPseudoItem = new JMenuItem("Copier le pseudo");
        copyPseudoItem.addActionListener(e -> {
            StringSelection stringSelection = new StringSelection(pseudoLabel.getText());
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);
        });
        userContextMenu.add(copyPseudoItem);

        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showContextMenu(e);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showContextMenu(e);
                }
            }

            private void showContextMenu(MouseEvent e) {
                userContextMenu.show(e.getComponent(), e.getX(), e.getY());
            }
        };

        pseudoLabel.addMouseListener(mouseAdapter);
    }

    private void applyFilter(String query) {
        String q = (query == null) ? "" : query.trim().toLowerCase();
        listModel.clear();

        for (ConversationItem item : masterList) {
            if (q.isEmpty() || item.getTitle().toLowerCase().contains(q)) {
                listModel.addElement(item);
            }
        }
    }

    // ----------------------- Public API -----------------------

    /**
     * Set the displayed pseudo in the user panel
     * @param pseudo the user's pseudo
     */
    public void setUserPseudo(String pseudo) {
        pseudoLabel.setText(pseudo);
    }

    /**
     * Set the list of conversations to display.
     *
     * @param conversations the conversations to display
     */
    public void setConversations(List<ConversationItem> conversations) {
        masterList.clear();
        if (conversations != null) {
            masterList.addAll(conversations);
        }
        applyFilter(searchField.getText());
    }

    /**
     * Get the currently selected conversation.
     *
     * @return the selected conversation, or null if none selected
     */
    public ConversationItem getSelectedConversation() {
        return conversationList.getSelectedValue();
    }

    /**
     * Clear the current conversation selection.
     */
    public void clearSelection() {
        conversationList.clearSelection();
    }

    /**
     * Add a listener for conversation selection changes.
     *
     * @param listener the list selection listener
     */
    public void addConversationSelectionListener(ListSelectionListener listener) {
        conversationList.addListSelectionListener(listener);
    }

    /**
     * Set the callback for the new conversation button.
     *
     * @param listener the action listener
     */
    public void setOnNewConversation(ActionListener listener) {
        this.onNewConversation = listener;
    }

    /**
     * Set the callback for the new contact button.
     *
     * @param listener the action listener
     */
    public void setOnNewContact(ActionListener listener) {
        this.onNewContact = listener;
    }

    /**
     * Set the callback for the contact view button.
     *
     * @param listener the action listener
     */
    public void setOnViewContacts(ActionListener listener) {
        this.onViewContacts = listener;
    }

    public void setOnShowPendingRequest(ActionListener l) {
        this.onShowPendingRequest = l;
    }

    /**
     * Set listener for update pseudo action
     */
    public void setOnUpdatePseudo(ActionListener listener) {
        this.onUpdatePseudo = listener;
    }

    // ----------------------- Cell Renderer -----------------------

    /**
     * Custom renderer for conversation items.
     */
    private static final class ConversationRenderer extends JPanel implements ListCellRenderer<ConversationItem> {
        private static final int MAX_PREVIEW_CHARS = 77;

        private final JLabel titleLabel;
        private final JLabel previewLabel;

        ConversationRenderer() {
            setLayout(new BorderLayout());
            setBorder(new EmptyBorder(10, 10, 10, 10));

            titleLabel = new JLabel();
            titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));

            previewLabel = new JLabel();
            previewLabel.setForeground(new Color(0, 0, 0, 160));

            JPanel centerPanel = new JPanel(new BorderLayout());
            centerPanel.setOpaque(false);
            centerPanel.add(titleLabel, BorderLayout.NORTH);
            centerPanel.add(previewLabel, BorderLayout.SOUTH);
            add(centerPanel, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends ConversationItem> list,
                                                      ConversationItem value,
                                                      int index,
                                                      boolean isSelected,
                                                      boolean cellHasFocus) {
            titleLabel.setText(value.getTitle());

            String preview = (value.getLastMessage() == null) ? "" : value.getLastMessage();
            if (preview.length() > MAX_PREVIEW_CHARS) {
                preview = preview.substring(0, MAX_PREVIEW_CHARS - 3) + "...";
            }
            previewLabel.setText(preview);

            if (isSelected) {
                setBackground(list.getSelectionBackground());
                setForeground(list.getSelectionForeground());
            } else {
                setBackground(list.getBackground());
                setForeground(list.getForeground());
            }
            setOpaque(true);

            return this;
        }
    }
}