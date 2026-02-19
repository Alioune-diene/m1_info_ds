package fr.uga.im2ag.m1info.tchatsapp.gui;

import fr.uga.im2ag.m1info.tchatsapp.client.model.ContactClient;
import fr.uga.im2ag.m1info.tchatsapp.client.repository.ContactClientRepository;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CreateConversationDialog extends JDialog {

    public static final class Result {
        private final String conversationName;
        private final Set<Integer> participantIds;
        private final boolean isGroup;

        public Result(String conversationName, Set<Integer> participantIds, boolean isGroup) {
            this.conversationName = conversationName;
            this.participantIds = participantIds;
            this.isGroup = isGroup;
        }

        public String getConversationName() { return conversationName; }
        public Set<Integer> getParticipantIds() { return participantIds; }
        public boolean isGroup() { return isGroup; }
    }

    // Pojo to add contacts in JLsit
    private static final class ContactItem {
        final int id;
        final String pseudo;
        ContactItem(int id, String pseudo) {
            this.id = id; this.pseudo = pseudo;
        }
        
        public int getId() { return id; }
        public String getPseudo() { return pseudo; }

        @Override
        public String toString() { return pseudo + ", id = " + id; }
    }

    private final JRadioButton rbPrivate = new JRadioButton("Conversation privée", true);
    private final JRadioButton rbGroup   = new JRadioButton("Conversation de groupe");
    private final JTextField nameField   = new JTextField(); // Name of the group (auto for priavte)
    private final JList<ContactItem> contactList = new JList<>();
    private final JButton btnOk = new JButton("Créer");
    private final JButton btnCancel = new JButton("Annuler");

    private final List<ContactItem> items = new ArrayList<>();
    private final JTextField manualIdField = new JTextField(8);
    private final JButton addIdButton = new JButton("Ajouter ID");

    private Result result;

    /** Create a conversation dialog which will allow to create a new conversation from the selected participants
     * 
     * @param owner the main Frame
     * @param contactRepo contact repository of the client
     * @param currentUserId the id of the client
     */

    public CreateConversationDialog(Frame owner,
                                    ContactClientRepository contactRepo,
                                    int currentUserId) {
        super(owner, "Nouvelle conversation", true);



        setLayout(new BorderLayout(8, 8));
        JPanel content = new JPanel(new GridBagLayout());
        content.setBorder(new EmptyBorder(12, 12, 12, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6,6,6,6);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;

        // Conversation type
        ButtonGroup bg = new ButtonGroup();
        bg.add(rbPrivate);
        bg.add(rbGroup);
        JPanel typePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        typePanel.add(rbPrivate);
        typePanel.add(rbGroup);

        c.gridx = 0; 
        c.gridy = 0;
        c.gridwidth = 2;
        content.add(new JLabel("Conversation type :"), c);
        c.gridy++;
        content.add(typePanel, c);

        // Name of the conv
        c.gridy++;
        content.add(new JLabel("Name of the conv, optional for private ones :"), c);
        c.gridy++;
        nameField.setColumns(20);
        content.add(nameField, c);

        // contact list
        c.gridy++;
        content.add(new JLabel("Choisissez les participants :"), c);
        c.gridy++;
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1.0;

        // load contacts from repo
        items.clear();
        for (ContactClient contact : contactRepo.findAll()) {
            if (contact.getContactId() == currentUserId) {
                continue; // we don't show the client itself if it has been added by mistake
            }
            items.add(new ContactItem(contact.getContactId(), contact.getPseudo()));
        }
        contactList.setListData(items.toArray(new ContactItem[0]));

        contactList.setListData(items.toArray(new ContactItem[0]));
        contactList.setVisibleRowCount(8);
        contactList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scroll = new JScrollPane(contactList);
        content.add(scroll, c);

        // add member by ID (for groups, but we don't enforce here)
        c.gridy++; 
        c.gridwidth = 2;
        c.weighty = 0;
        c.fill = GridBagConstraints.HORIZONTAL;

        JPanel addIdPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        addIdPanel.add(new JLabel("Add member by ID :"));
        manualIdField.setColumns(8);
        addIdPanel.add(manualIdField);
        addIdButton.setFocusable(false);
        addIdPanel.add(addIdButton);
        content.add(addIdPanel, c);

        // 4) boottom panel
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(btnCancel);
        buttons.add(btnOk);

        add(content, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);

        // private vs group conv
        rbPrivate.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                contactList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            }
        });
        rbGroup.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                contactList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            }
        });

        addIdButton.addActionListener(e -> onAddManualId(currentUserId));
        manualIdField.addActionListener(e -> onAddManualId(currentUserId)); // Enter key


        btnCancel.addActionListener(e -> {
            result = null;
            dispose();
        });

        btnOk.addActionListener(e -> onValidate(currentUserId));

        pack();
        setLocationRelativeTo(owner);
    }

    private void onValidate(int currentUserId) {
        boolean isGroup = rbGroup.isSelected();
        List<ContactItem> selected = contactList.getSelectedValuesList();

        if (!isGroup) {
            // private conv
            if (selected.size() != 1) {
                JOptionPane.showMessageDialog(this,
                        "Please select only one ocntact for private conv",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
        } else {
            // group conv
            if (selected.size() < 2) {
                JOptionPane.showMessageDialog(this,
                        "Please select at least 2 contacts to create group conv",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            if (!isGroup) {
                // default name of priv conv = pseudo of contact
                name = selected.get(0).getPseudo();
            } else {
                // Group : if no name provided, some pseudos, and "..." if > 3
                StringBuilder sb = new StringBuilder("Group : ");
                int count = 0;
                for (ContactItem ci : selected) {
                    if (count > 0) sb.append(", ");
                    sb.append(ci.pseudo);
                    count++;
                    if (count >= 3) {
                        sb.append("...");
                        break;
                    }
                }
                name = sb.toString();
                }
        }

        // Participants = selected contacts
        Set<Integer> ids = new HashSet<>();
        if (selected.size()==1) ids.add(selected.get(0).getId());
        else { 
            for (ContactItem ci : selected) {
                ids.add(ci.id);
            }
        }
        result = new Result(name, ids, isGroup);
        dispose();
    }

    private void onAddManualId(int currentUserId) {

        if (!rbGroup.isSelected()) {
            JOptionPane.showMessageDialog(
                    this,
                    "Adding by ID is reserved for group conv",
                    "Information",
                    JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        String txt = manualIdField.getText().trim();
        if (txt.isEmpty()) {
            return;
        }

        int id;
        try {
            id = Integer.parseInt(txt);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "invalid ID, please enter an INT",
                    "Erreur",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        // Check if already present
        for (ContactItem ci : items) {
            if (ci.getId() == id) {
                // Already in the list: just select it and exit
                int index = items.indexOf(ci);
                if (index >= 0) {
                    contactList.addSelectionInterval(index, index);
                }
                manualIdField.setText("");
                return;
            }
        }

        // Not present: create a temporary entry "User #id"
        ContactItem newItem = new ContactItem(id, "User #" + id);
        items.add(newItem);

        // Refresh the JList data
        contactList.setListData(items.toArray(new ContactItem[0]));

        // Select the newly added item
        int newIndex = items.size() - 1;
        contactList.addSelectionInterval(newIndex, newIndex);

        manualIdField.setText("");
    }


    public Result showDialog() {
        setVisible(true); // block until dispose
        return result;
    }
}
