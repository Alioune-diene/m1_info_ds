package fr.uga.im2ag.m1info.tchatsapp;

import fr.uga.im2ag.m1info.tchatsapp.client.Client;
import fr.uga.im2ag.m1info.tchatsapp.client.ClientController;
import fr.uga.im2ag.m1info.tchatsapp.client.event.system.ExecutionMode;
import fr.uga.im2ag.m1info.tchatsapp.client.event.types.TextMessageReceivedEvent;
import fr.uga.im2ag.m1info.tchatsapp.client.model.ContactClient;
import fr.uga.im2ag.m1info.tchatsapp.server.ChatServer;
import fr.uga.im2ag.m1info.tchatsapp.common.model.UserInfo;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test d'intégration minimal pour valider la Vague 1 RMI.
 * Scénario : Client A envoie un TextMessage à Client B, Client B le reçoit.
 */
public class RmiIntegrationTest {

    public static void main(String[] args) throws Exception {
        int port = 11099; // port non-standard pour ne pas interférer

        // 1. Démarrer le serveur
        Registry registry = LocateRegistry.createRegistry(port);
        ChatServer server = new ChatServer(port);
        registry.rebind("ChatServer", server);
        System.out.println("[Test] Server started on port " + port);

        // 2. Connecter Client A
        Client clientA = new Client();
        ClientController controllerA = new ClientController(clientA);
        controllerA.connect("localhost", port, "Alice");
        controllerA.initializeHandlers();
        System.out.println("[Test] Client A connected, id=" + clientA.getClientId());

        // 3. Connecter Client B
        Client clientB = new Client();
        ClientController controllerB = new ClientController(clientB);
        controllerB.connect("localhost", port, "Bob");
        controllerB.initializeHandlers();
        System.out.println("[Test] Client B connected, id=" + clientB.getClientId());

        // 4. Ajouter mutuellement comme contacts (côté serveur)
        UserInfo userA = server.getContext().getUserRepository().findById(clientA.getClientId());
        UserInfo userB = server.getContext().getUserRepository().findById(clientB.getClientId());
        userA.addContact(clientB.getClientId());
        userB.addContact(clientA.getClientId());
        System.out.println("[Test] Contacts added mutually");

        // 4b. Ajouter les contacts côté CLIENT aussi
        controllerA.getContactRepository().add(new ContactClient(clientB.getClientId(), "Bob"));
        controllerB.getContactRepository().add(new ContactClient(clientA.getClientId(), "Alice"));

        // 5. S'abonner à l'event de réception côté B
        CountDownLatch latch = new CountDownLatch(1);
        final String[] receivedContent = {null};

        controllerB.subscribeToEvent(
                TextMessageReceivedEvent.class,
                event -> {
                    receivedContent[0] = event.getMessage().getContent();
                    System.out.println("[Test] Client B received: " + receivedContent[0]);
                    latch.countDown();
                },
                ExecutionMode.SYNC
        );

        // 6. Client A envoie un message texte à Client B
        String msgId = controllerA.sendTextMessage("Hello Bob!", clientB.getClientId(), null);
        System.out.println("[Test] Client A sent message, id=" + msgId);

        // 7. Attendre la réception
        boolean received = latch.await(5, TimeUnit.SECONDS);

        // 8. Vérification
        if (received && "Hello Bob!".equals(receivedContent[0])) {
            System.out.println("\n✅ TEST PASSED: Message successfully relayed from A to B via RMI");
        } else {
            System.out.println("\n❌ TEST FAILED: Message not received within timeout");
            System.out.println("   received=" + received + ", content=" + receivedContent[0]);
        }

        // 9. Cleanup
        controllerA.disconnect();
        controllerB.disconnect();
        System.exit(0);
    }
}