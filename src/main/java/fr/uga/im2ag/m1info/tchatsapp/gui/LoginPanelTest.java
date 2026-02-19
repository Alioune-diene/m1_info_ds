package fr.uga.im2ag.m1info.tchatsapp.gui;
import javax.swing.*;

public class LoginPanelTest {
    public static void main(String[] args) {

        SwingUtilities.invokeLater(() -> {
            JFrame loginFrame = new JFrame("Connexion");
            LoginPanel loginPanel = new LoginPanel();

            loginFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            loginFrame.setContentPane(loginPanel);
            loginFrame.pack();
            loginFrame.setLocationRelativeTo(null); // Centre la fenêtre
            loginFrame.setVisible(true);
        });
    }
}


