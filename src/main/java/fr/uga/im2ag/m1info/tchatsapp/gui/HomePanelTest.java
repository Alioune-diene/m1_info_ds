package fr.uga.im2ag.m1info.tchatsapp.gui;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class HomePanelTest {
    public static void main(String[] args) {

        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Accueil");
            HomePanel home = new HomePanel();

            List<HomePanel.ConversationItem> items =
            new ArrayList<>(java.util.Arrays.asList(
                    new HomePanel.ConversationItem("c1", "Alice", "À ce soir ! TestTestTestTestTestTestTestTestTestTestTestTestTestTest"),
                    new HomePanel.ConversationItem("c2", "Projet M1", "Alioune on t'entend paaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaas"),
                    new HomePanel.ConversationItem("c3", "Bob", "Ok bb"),
                    new HomePanel.ConversationItem("c4", "Famille", "MAIS C4ETAIT SUR EN FAIT")
            ));

            home.setConversations(items);

            home.setOnNewConversation(ev -> {
                System.out.println("Créer une nouvelle conversatio");
                // Call backend
            });

            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setContentPane(home);
            f.setSize(900, 600);
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
    }
}
