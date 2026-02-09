package fr.uga.im2ag.m1info.tchatsapp.common.repository;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An abstract implementation of the Repository interface providing basic CRUD operations
 * and caching functionality.
 *
 * @param <K> the type of the identifier for the entities
 * @param <V> the type of entities to be managed by the repository
 */
public abstract class AbstractRepository<K, V> implements Repository<K, V> {
    protected final Map<K, V> storage;
    private final RepositoryWriter<V> writer;

    protected AbstractRepository(Map<K, V> storage, String filePath) {
        this.storage = storage;
        this.writer = new RepositoryWriter<>(filePath);
        loadFromCache();
    }

    protected AbstractRepository(String filePath) {
        this(new ConcurrentHashMap<>(), filePath);
    }

    @Override
    public void add(V entity) {
        storage.put(getKey(entity), entity);
        writer.writeData(entity);
    }

    @Override
    public void update(K id, V entity) {
        storage.put(id, entity);
        writer.updateData(e -> getKey(e).equals(id), entity);
    }

    @Override
    public void delete(K id) {
        storage.remove(id);
        writer.removeData(e -> getKey(e).equals(id));
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

    private void loadFromCache() {
        Set<V> cachedObjects = writer.readData();
        for (V object : cachedObjects) {
            storage.put(getKey(object), object);
        }
    }
}
