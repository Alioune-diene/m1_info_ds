package fr.uga.im2ag.m1info.tchatsapp.gui;

import fr.uga.im2ag.m1info.tchatsapp.gui.ConversationPanel.MessageItem;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class ConversationPanelTest {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Conversation");
            f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            ConversationPanel p = new ConversationPanel();
            p.setConversationTitle("LeBoss");

            List<MessageItem> demo = new ArrayList<>();
            demo.add(new MessageItem(false, "PedroSanchez", "Salut !"));
            demo.add(new MessageItem(true, null, "667 ?"));
            demo.add(new MessageItem(false, "PedroSanchez", "Test de longueur voir si ça coupe bien les messages où il faut lààààà et bien entre les mots, pas comme un chien en plein mot"));
            demo.add(new MessageItem(true, null, "Alright"));
            demo.add(new MessageItem(false, "PedroSanchez", "MangemortSquad"));

            p.setMessages(demo);

            p.setOnBack(e -> System.out.println("GoBackHomeSweeeeeeeetHome"));
            p.setOnSend((text, replyId) -> p.appendMessage(new MessageItem(true, null, text)));

            f.setContentPane(p);
            f.setSize(520, 560);
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
    }

}
