package fr.uga.im2ag.m1info.tchatsapp.server.repository;

import fr.uga.im2ag.m1info.tchatsapp.common.repository.AbstractRepository;
import fr.uga.im2ag.m1info.tchatsapp.common.model.UserInfo;

import java.util.Map;

public class UserRepository extends AbstractRepository<Integer, UserInfo> {
    public UserRepository(Map<Integer, UserInfo> users) {
        super(users);
    }

    public UserRepository() {
        super();
    }

    @Override
    protected Integer getKey(UserInfo entity) {
        return entity.getId();
    }
}
