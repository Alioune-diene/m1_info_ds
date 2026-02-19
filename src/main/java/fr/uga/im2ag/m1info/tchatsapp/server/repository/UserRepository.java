package fr.uga.im2ag.m1info.tchatsapp.server.repository;

import fr.uga.im2ag.m1info.tchatsapp.common.repository.AbstractRepository;
import fr.uga.im2ag.m1info.tchatsapp.server.model.UserInfo;

import java.util.Map;

public class UserRepository extends AbstractRepository<Integer, UserInfo> {
    public UserRepository(Map<Integer, UserInfo> users) {
        super(users, "userRepository");
    }

    public UserRepository() {
        super("userRepository");
    }

    @Override
    protected Integer getKey(UserInfo entity) {
        return entity.getId();
    }
}
