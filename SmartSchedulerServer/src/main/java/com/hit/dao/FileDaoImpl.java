package com.hit.dao;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Thread-safe file-based implementation of IDao<T> using Java serialization.
 * Suitable for small to medium datasets.
 *
 * @param <T> Entity type.
 */
public class FileDaoImpl<T> implements IDao<T> {

    private final String filePath;

    /**
     * @param clazz The entity class type.
     */
    public FileDaoImpl(Class<T> clazz) {
        this.filePath = switch (clazz.getSimpleName()) {
            case "Task" -> "src/main/resources/tasks.txt";
            case "TeamMember" -> "src/main/resources/members.txt";
            case "Assignment" -> "src/main/resources/assignments.txt";
            default -> throw new IllegalArgumentException("Unsupported type: " + clazz);
        };
    }

    @Override
    public synchronized void save(T entity) throws Exception {
        List<T> all = new ArrayList<>(load());
        String entityId = getId(entity);
        boolean updated = false;
        for (int i = 0; i < all.size(); i++) {
            if (getId(all.get(i)).equals(entityId)) {
                all.set(i, entity);
                updated = true;
                break;
            }
        }
        if (!updated) {
            all.add(entity);
        }
        save(all);
    }

    @Override
    public synchronized void save(List<T> entities) throws Exception {
        // Always write a mutable copy to file to avoid immutability bugs!
        List<T> mutable = (entities == null) ? new ArrayList<>() : new ArrayList<>(entities);
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filePath))) {
            oos.writeObject(mutable);
        }
    }

    @Override
    public synchronized List<T> load() throws Exception {
        File file = new File(filePath);
        if (!file.exists() || file.length() == 0) return new ArrayList<>();
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            Object obj = ois.readObject();
            // Always return as mutable ArrayList
            if (obj instanceof List<?> list) {
                return new ArrayList<>((List<T>) list);
            }
            return new ArrayList<>();
        }
    }

    @Override
    public synchronized T findById(String id) throws Exception {
        for (T obj : load()) {
            if (getId(obj).equals(id)) return obj;
        }
        return null;
    }

    @Override
    public synchronized void update(T entity) throws Exception {
        List<T> all = new ArrayList<>(load());
        String entityId = getId(entity);
        boolean updated = false;
        for (int i = 0; i < all.size(); i++) {
            if (getId(all.get(i)).equals(entityId)) {
                all.set(i, entity);
                updated = true;
                break;
            }
        }
        if (updated) {
            save(all);
        } else {
            throw new IllegalArgumentException("Entity not found for update: " + entityId);
        }
    }

    @Override
    public synchronized boolean deleteById(String id) throws Exception {
        List<T> all = new ArrayList<>(load());
        boolean changed = all.removeIf(obj -> getId(obj).equals(id));
        if (changed) {
            save(all);
        }
        return changed;
    }


    @Override
    public synchronized void deleteAll() throws Exception {
        try (PrintWriter pw = new PrintWriter(filePath)) {
            // Truncate file
        }
    }

    @Override
    public synchronized boolean deleteIf(Predicate<T> predicate) throws Exception {
        List<T> all = new ArrayList<>(load());
        boolean changed = all.removeIf(predicate);
        if (changed) save(all);
        return changed;
    }

    // Helper to extract ID for all supported types
    private String getId(T obj) {
        if (obj instanceof com.hit.model.Task t) return t.getId();
        if (obj instanceof com.hit.model.TeamMember m) return m.getId();
        if (obj instanceof com.hit.model.Assignment a) return a.getTaskId() + "-" + a.getMemberId();
        throw new IllegalArgumentException("Unknown type");
    }
}