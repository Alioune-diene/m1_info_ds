package fr.uga.im2ag.m1info.tchatsapp.gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionListener;

/**
 * A reusable login panel for COO TchatApp.
 * <p>
 * Responsibilities:
 *  - Renders username + password fields and a "Login" button
 *  - Exposes getters for fields
 *  - No logic inside
 */
// TODO: Avoir une vraie distinction entre la création de compte et la connexion
public class LoginPanel extends JPanel {

    private final JTextField usernameField;
    private final JPasswordField passwordField;
    private final JButton loginButton;
    private final JLabel errorLabel;

    public LoginPanel() {
        super(new GridBagLayout());
        setBorder(new EmptyBorder(24, 24, 24, 24));

        this.usernameField = new JTextField();
        this.passwordField = new JPasswordField();
        this.loginButton = new JButton("Se connecter");
        this.errorLabel = new JLabel(" ");

        setupLayout();
    }

    private void setupLayout() {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(8, 8, 8, 8);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;

        // Title
        JLabel title = new JLabel("Connexion");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.CENTER;
        add(title, c);

        // Username label
        c.gridy++;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        add(new JLabel("Identifiant (0 pour créer un nouveau compte)"), c);

        // Username field
        c.gridy++;
        usernameField.setColumns(18);
        add(usernameField, c);

        // Password label
        c.gridy++;
        add(new JLabel("Mot de passe"), c);

        // Password field
        c.gridy++;
        passwordField.setColumns(18);
        passwordField.setEnabled(false);
        add(passwordField, c);

        // Login button
        c.gridy++;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.NONE;
        loginButton.setPreferredSize(new Dimension(180, 36));
        add(loginButton, c);

        // Error label
        c.gridy++;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        errorLabel.setForeground(new Color(160, 20, 20));
        add(errorLabel, c);
    }

    // ----------------------- Public API -----------------------

    public String getUsername() {
        return usernameField.getText();
    }

    public char[] getPassword() {
        return passwordField.getPassword();
    }

    public JButton getLoginButton() {
        return loginButton;
    }

    public void showError(String msg) {
        errorLabel.setText(msg);
    }

    public void clearError() {
        errorLabel.setText(" ");
    }

    /**
     * Add a listener for the submit action (button click or Enter in password field).
     *
     * @param listener the action listener
     */
    public void addSubmitListener(ActionListener listener) {
        loginButton.addActionListener(listener);
        usernameField.addActionListener(listener);
    }
}