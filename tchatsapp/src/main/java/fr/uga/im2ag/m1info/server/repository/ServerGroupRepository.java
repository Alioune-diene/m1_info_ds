package fr.uga.im2ag.m1info.server.repository;

import fr.uga.im2ag.m1info.common.model.GroupInfo;
import fr.uga.im2ag.m1info.common.repository.PersistentRepository;

import java.util.Map;

public class ServerGroupRepository extends PersistentRepository<Integer, GroupInfo> {

    public ServerGroupRepository(Map<Integer, GroupInfo> groups) {
        super(groups, "server_groups");
    }

    public ServerGroupRepository() {
        super("server_groups");
    }

    @Override
    protected Integer getKey(GroupInfo entity) {
        return entity.getGroupId();
    }
}
