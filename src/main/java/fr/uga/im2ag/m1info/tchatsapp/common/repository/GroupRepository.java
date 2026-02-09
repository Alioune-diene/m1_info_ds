package fr.uga.im2ag.m1info.tchatsapp.common.repository;

import fr.uga.im2ag.m1info.tchatsapp.common.model.GroupInfo;

import java.util.Map;

public class GroupRepository extends AbstractRepository<Integer, GroupInfo> {
    public GroupRepository(Map<Integer, GroupInfo> groups) {
        super(groups, "groupRepository");
    }

    public GroupRepository() {
        super("groupRepository");
    }

    @Override
    protected Integer getKey(GroupInfo entity) {
        return entity.getGroupId();
    }
}

