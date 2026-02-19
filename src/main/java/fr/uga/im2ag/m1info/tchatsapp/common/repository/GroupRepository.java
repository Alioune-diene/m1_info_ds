package fr.uga.im2ag.m1info.tchatsapp.common.repository;

import fr.uga.im2ag.m1info.tchatsapp.common.model.GroupInfo;

import java.util.Map;

public class GroupRepository extends AbstractRepository<Integer, GroupInfo> {
    public GroupRepository(Map<Integer, GroupInfo> groups) {
        super(groups);
    }

    public GroupRepository() {
        super();
    }

    @Override
    protected Integer getKey(GroupInfo entity) {
        return entity.getGroupId();
    }
}

