package fr.uga.im2ag.m1info.server.repository;

import fr.uga.im2ag.m1info.common.model.StoredMessage;
import fr.uga.im2ag.m1info.common.repository.RepositoryWriter;
import fr.uga.im2ag.m1info.server.model.ConversationServerData;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class ConversationServerRepository {
    private static final Logger LOG = Logger.getLogger(ConversationServerRepository.class.getName());

    private static final int MAX_MESSAGES = Integer.parseInt(
            System.getProperty("tchatsapp.history.max", "100")
    );

    private final ConcurrentHashMap<String, ConversationServerData> storage = new ConcurrentHashMap<>();
    private final RepositoryWriter<ConversationServerData> writer;

    public ConversationServerRepository() {
        this.writer = new RepositoryWriter<>("conversationServerRepository");
        Set<ConversationServerData> cached = writer.readData();
        for (ConversationServerData data : cached) {
            storage.put(data.getConversationId(), data);
        }
        LOG.info("ConversationServerRepository loaded " + storage.size() + " conversations from cache.");
    }

    public void addMessage(String conversationId, StoredMessage message) {
        storage.compute(conversationId, (key, existing) -> {
            ConversationServerData data = (existing != null) ? existing
                    : new ConversationServerData(key);
            data.addMessage(message, MAX_MESSAGES);
            return data;
        });
        persist(conversationId);
    }

    public ConversationServerData findById(String conversationId) {
        return storage.get(conversationId);
    }

    public Collection<ConversationServerData> findAll() {
        return Collections.unmodifiableCollection(storage.values());
    }

    private void persist(String conversationId) {
        ConversationServerData updated = storage.get(conversationId);
        if (updated == null) return;

        Set<ConversationServerData> allData = writer.readData();
        allData.removeIf(d -> d.getConversationId().equals(conversationId));
        allData.add(updated);
        writer.writeData(allData);
    }
}
