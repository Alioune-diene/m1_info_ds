package fr.uga.im2ag.m1info.tchatsapp.common.repository;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public abstract class PersistentRepository<K, V> extends AbstractRepository<K, V> {
    private final RepositoryWriter<V> writer;

    protected PersistentRepository(Map<K, V> storage, String filePath) {
        super(storage);
        this.writer = new RepositoryWriter<>(filePath);
        loadFromDisk();
    }

    protected PersistentRepository(String filePath) {
        this(new ConcurrentHashMap<>(), filePath);
    }

    @Override
    public void add(V entity) {
        super.add(entity);
        writer.writeData(entity);
    }

    @Override
    public void update(K id, V entity) {
        super.update(id, entity);
        writer.updateData(e -> getKey(e).equals(id), entity);
    }

    @Override
    public void delete(K id) {
        super.delete(id);
        writer.removeData(e -> getKey(e).equals(id));
    }

    private void loadFromDisk() {
        Set<V> cached = writer.readData();
        for (V entity : cached) {
            storage.put(getKey(entity), entity);
        }
    }
}
