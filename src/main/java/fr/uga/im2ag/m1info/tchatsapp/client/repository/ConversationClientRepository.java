package fr.uga.im2ag.m1info.tchatsapp.client.repository;

import fr.uga.im2ag.m1info.tchatsapp.client.model.ConversationClient;
import fr.uga.im2ag.m1info.tchatsapp.common.repository.AbstractRepository;

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