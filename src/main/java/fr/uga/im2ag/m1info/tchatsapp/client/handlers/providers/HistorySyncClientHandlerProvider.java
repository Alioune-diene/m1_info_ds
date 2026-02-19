package fr.uga.im2ag.m1info.tchatsapp.client.handlers.providers;


import fr.uga.im2ag.m1info.tchatsapp.client.handlers.ClientMessageHandler;
import fr.uga.im2ag.m1info.tchatsapp.client.handlers.HistorySyncClientHandler;
import fr.uga.im2ag.m1info.tchatsapp.common.MessageType;

import java.util.List;
import java.util.Set;

public class HistorySyncClientHandlerProvider implements ClientPacketHandlerProvider {
    @Override
    public Set<MessageType> getHandledTypes() {
        return Set.of(MessageType.HISTORY_SYNC);
    }

    @Override
    public List<ClientMessageHandler> createHandlers() {
        return List.of(new HistorySyncClientHandler());
    }
}