package com.hit.dao;

import java.util.List;
import java.util.function.Predicate;

/**
 * Generic Data Access Object (DAO) interface for persistent storage.
 * Provides standard CRUD operations.
 *
 * @param <T> Entity type (Task, TeamMember, Assignment, etc.)
 */
public interface IDao<T> {
    /**
     * Save (insert or update) a single entity.
     * @param entity The entity to save.
     * @throws Exception On I/O or database error.
     */
    void save(T entity) throws Exception;

    /**
     * Save (insert or update) a batch of entities.
     * @param entities List of entities to save.
     * @throws Exception On I/O or database error.
     */
    void save(List<T> entities) throws Exception;

    /**
     * Load all entities from the data source.
     * @return List of loaded objects.
     * @throws Exception On I/O or format error.
     */
    List<T> load() throws Exception;

    /**
     * Find an entity by its unique string ID.
     * @param id The entity's ID.
     * @return The entity, or null if not found.
     * @throws Exception On error.
     */
    T findById(String id) throws Exception;

    /**
     * Update an existing entity by ID.
     * @param entity The updated entity (must contain the correct ID).
     * @throws Exception On I/O or database error.
     */
    void update(T entity) throws Exception;

    /**
     * Delete an entity by unique string ID.
     * @param id The entity's ID.
     * @throws Exception On error.
     */
    boolean deleteById(String id) throws Exception;

    /**
     * Delete all entities from the data source.
     * @throws Exception On error.
     */
    void deleteAll() throws Exception;

    /**
     * Delete entities matching a predicate.
     * @param predicate Predicate for deletion.
     * @return True if any were deleted.
     * @throws Exception On error.
     */
    boolean deleteIf(Predicate<T> predicate) throws Exception;
}
