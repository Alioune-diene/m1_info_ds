package fr.uga.im2ag.m1info.common.repository;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An abstract implementation of the Repository interface providing basic CRUD operations
 *
 * @param <K> the type of the identifier for the entities
 * @param <V> the type of entities to be managed by the repository
 */
public abstract class AbstractRepository<K, V> implements Repository<K, V> {
    protected final Map<K, V> storage;

    protected AbstractRepository(Map<K, V> storage) {
        this.storage = storage;
    }

    protected AbstractRepository() {
        this(new ConcurrentHashMap<>());
    }

    @Override
    public void add(V entity) {
        storage.put(getKey(entity), entity);
    }

    @Override
    public void update(K id, V entity) {
        storage.put(id, entity);
    }

    @Override
    public void delete(K id) {
        storage.remove(id);
    }

    @Override
    public V findById(K id) {
        return storage.get(id);
    }

    @Override
    public Set<V> findAll() {
        return Set.copyOf(storage.values());
    }

    protected abstract K getKey(V entity);
}
