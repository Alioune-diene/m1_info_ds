package fr.uga.im2ag.m1info.server.repository;

import fr.uga.im2ag.m1info.common.model.UserInfo;
import fr.uga.im2ag.m1info.common.repository.PersistentRepository;

import java.util.Map;

public class UserRepository extends PersistentRepository<Integer, UserInfo> {

    public UserRepository(Map<Integer, UserInfo> users) {
        super(users, "server_users");
    }

    public UserRepository() {
        super("server_users");
    }

    @Override
    protected Integer getKey(UserInfo entity) {
        return entity.getId();
    }
}
