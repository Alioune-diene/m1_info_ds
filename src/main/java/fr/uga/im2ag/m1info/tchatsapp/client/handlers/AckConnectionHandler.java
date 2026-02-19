package fr.uga.im2ag.m1info.tchatsapp.client.handlers;

import fr.uga.im2ag.m1info.tchatsapp.client.ClientController;
import fr.uga.im2ag.m1info.tchatsapp.client.event.types.ConnectionEstablishedEvent;
import fr.uga.im2ag.m1info.tchatsapp.common.MessageType;
import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.ManagementMessage;
import fr.uga.im2ag.m1info.tchatsapp.common.messagefactory.ProtocolMessage;

public class AckConnectionHandler extends ClientMessageHandler {
    @Override
    public void handle(ProtocolMessage message, ClientController context) {
        if (!(message instanceof ManagementMessage ackMsg)) {
            throw new IllegalArgumentException("Invalid message type for AckConnectionHandler");
        }

        int clientId = ackMsg.getParamAsType("clientId", Integer.class);
        String pseudo = ackMsg.getParamAsType("pseudo", String.class);
        Boolean isNewUser = ackMsg.getParamAsType("newUser", Boolean.class);

        context.updateClientId(clientId);

        if (pseudo != null) {
            context.getActiveUser().setPseudo(pseudo);
        }

        context.markConnectionEstablished();

        if (Boolean.TRUE.equals(isNewUser)) {
            System.out.println("[Client] New account created successfully!");
            System.out.println("\tClient ID: " + context.getClientId());
            if (pseudo != null) {
                System.out.println("\tPseudo: " + pseudo);
            }
        } else {
            System.out.println("[Client] Connected successfully!");
            System.out.println("\tClient ID: " + context.getClientId());
            if (pseudo != null) {
                System.out.println("\tPseudo: " + pseudo);
            }
        }

        publishEvent(new ConnectionEstablishedEvent(
                this,
                context.getClientId(),
                pseudo != null ? pseudo : "Unknown",
                Boolean.TRUE.equals(isNewUser)
        ), context);
    }

    @Override
    public boolean canHandle(MessageType messageType) {
        return messageType == MessageType.ACK_CONNECTION;
    }
}