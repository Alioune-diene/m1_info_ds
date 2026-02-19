package fr.uga.im2ag.m1info.tchatsapp.server.repository;

import fr.uga.im2ag.m1info.tchatsapp.common.model.UserInfo;
import fr.uga.im2ag.m1info.tchatsapp.common.repository.PersistentRepository;

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
