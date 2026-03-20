package fr.uga.im2ag.m1info.client.repository;

import fr.uga.im2ag.m1info.client.model.ConversationClient;
import fr.uga.im2ag.m1info.common.repository.AbstractRepository;

import java.util.Map;

public class ConversationClientRepository extends AbstractRepository<String, ConversationClient> {


    public ConversationClientRepository(Map<String, ConversationClient> conversations) {
        super(conversations);
    }

    public ConversationClientRepository() {
        super();
    }

    @Override
    protected String getKey(ConversationClient entity) {
        return entity.getConversationId();
    }
}