package fr.uga.im2ag.m1info.tchatsapp.common.repository;

import java.util.Set;

/**
 * A generic repository interface defining basic CRUD operations.
 *
 * @param <ID> the type of the identifier for the entities
 * @param <T>  the type of entities to be managed by the repository
 */
public interface Repository<ID, T> {
    void add(T entity);
    void update(ID id,T entity);
    void delete(ID id);
    T findById(ID id);
    Set<T> findAll();
}
