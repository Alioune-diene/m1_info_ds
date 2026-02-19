package fr.uga.im2ag.m1info.tchatsapp.gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class ReactionSelectorDialog extends JDialog {

    private static final String[] REACTIONS = {
            "LIKE",
            "LOVE",
            "LAUGH",
            "WOW",
            "SAD",
            "ANGRY"
    };

    private String selectedReaction = null;

    public ReactionSelectorDialog(Window parent) {
        super(parent, "Choisir une réaction", ModalityType.APPLICATION_MODAL);
        initComponents();
        pack();
        setLocationRelativeTo(parent);
    }

    private void initComponents() {
        setLayout(new BorderLayout(8, 8));
        getRootPane().setBorder(new EmptyBorder(12, 12, 12, 12));

        JLabel titleLabel = new JLabel("Choisissez une réaction :");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        add(titleLabel, BorderLayout.NORTH);

        JPanel reactionsPanel = new JPanel(new GridLayout(2, 3, 8, 8));

        for (String reaction : REACTIONS) {
            JButton reactionButton = new JButton(reaction);
            reactionButton.setFont(reactionButton.getFont().deriveFont(Font.BOLD, 12f));
            reactionButton.setPreferredSize(new Dimension(80, 50));
            reactionButton.setFocusPainted(false);
            reactionButton.addActionListener(e -> {
                selectedReaction = reaction;
                dispose();
            });
            reactionsPanel.add(reactionButton);
        }

        add(reactionsPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancelButton = new JButton("Annuler");
        cancelButton.addActionListener(e -> {
            selectedReaction = null;
            dispose();
        });
        buttonPanel.add(cancelButton);

        add(buttonPanel, BorderLayout.SOUTH);
    }

    public String getSelectedReaction() {
        return selectedReaction;
    }
}