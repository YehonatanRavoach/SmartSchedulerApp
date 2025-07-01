package com.hit.dao;

import com.hit.model.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Thread-safe SQLite implementation of IDao<T> for persistent storage.
 * Provides real CRUD operations using SQL queries.
 *
 * @param <T> Entity type.
 */
public class SQLiteDaoImpl<T> implements IDao<T> {
    private static final String DB_URL = "jdbc:sqlite:src/main/resources/DataSource.db";
    private final Class<T> clazz;

    /**
     * @param clazz The entity class type.
     */
    public SQLiteDaoImpl(Class<T> clazz) {
        this.clazz = clazz;
        createTableIfNotExists();
    }

    private void createTableIfNotExists() {
        synchronized (this) {
            try (Connection conn = DriverManager.getConnection(DB_URL);
                 Statement stmt = conn.createStatement()) {
                String sql = switch (clazz.getSimpleName()) {
                    case "Task" -> """
                        CREATE TABLE IF NOT EXISTS tasks (
                            id TEXT PRIMARY KEY,
                            name TEXT,
                            durationHours INTEGER,
                            priority INTEGER,
                            remainingHours INTEGER,
                            requiredSkill TEXT
                        );""";
                    case "TeamMember" -> """
                        CREATE TABLE IF NOT EXISTS members (
                            id TEXT PRIMARY KEY,
                            name TEXT,
                            skills TEXT,
                            maxHoursPerDay INTEGER,
                            remainingHours INTEGER,
                            efficiency REAL
                        );""";
                    case "Assignment" -> """
                        CREATE TABLE IF NOT EXISTS assignments (
                            taskId TEXT,
                            memberId TEXT,
                            assignedHours INTEGER,
                            PRIMARY KEY (taskId, memberId)
                        );""";
                    default -> throw new IllegalArgumentException("Unsupported type");
                };
                stmt.executeUpdate(sql);
            } catch (SQLException e) {
                throw new RuntimeException("Failed to create table", e);
            }
        }
    }

    @Override
    public synchronized void save(T entity) throws Exception {
        save(List.of(entity));
    }

    @Override
    public synchronized void save(List<T> entities) throws Exception {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            conn.setAutoCommit(false);
            if (clazz == Task.class) {
                try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT OR REPLACE INTO tasks (id, name, durationHours, priority, remainingHours, requiredSkill)
                    VALUES (?, ?, ?, ?, ?, ?)
                """)) {
                    for (T obj : entities) {
                        Task t = (Task) obj;
                        ps.setString(1, t.getId());
                        ps.setString(2, t.getName());
                        ps.setInt(3, t.getDurationHours());
                        ps.setInt(4, t.getPriority());
                        ps.setInt(5, t.getRemainingHours());
                        ps.setString(6, String.join(",", t.getRequiredSkills()));
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            } else if (clazz == TeamMember.class) {
                try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT OR REPLACE INTO members (id, name, skills, maxHoursPerDay, remainingHours, efficiency)
                    VALUES (?, ?, ?, ?, ?, ?)
                """)) {
                    for (T obj : entities) {
                        TeamMember m = (TeamMember) obj;
                        ps.setString(1, m.getId());
                        ps.setString(2, m.getName());
                        ps.setString(3, String.join(",", m.getSkills()));
                        ps.setInt(4, m.getMaxHoursPerDay());
                        ps.setInt(5, m.getRemainingHours());
                        ps.setDouble(6, m.getEfficiency());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            } else if (clazz == Assignment.class) {
                try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT OR REPLACE INTO assignments (taskId, memberId, assignedHours)
                    VALUES (?, ?, ?)
                """)) {
                    for (T obj : entities) {
                        Assignment a = (Assignment) obj;
                        ps.setString(1, a.getTaskId());
                        ps.setString(2, a.getMemberId());
                        ps.setInt(3, a.getAssignedHours());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
            conn.commit();
        }
    }

    @Override
    public synchronized List<T> load() throws Exception {
        String sql = switch (clazz.getSimpleName()) {
            case "Task" -> "SELECT * FROM tasks";
            case "TeamMember" -> "SELECT * FROM members";
            case "Assignment" -> "SELECT * FROM assignments";
            default -> throw new IllegalArgumentException("Unsupported type");
        };

        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            List<T> result = new ArrayList<>();
            while (rs.next()) {
                result.add(mapResultSet(rs));
            }
            return result;
        }
    }

    @Override
    public synchronized T findById(String id) throws Exception {
        String sql = switch (clazz.getSimpleName()) {
            case "Task" -> "SELECT * FROM tasks WHERE id = ?";
            case "TeamMember" -> "SELECT * FROM members WHERE id = ?";
            case "Assignment" -> {
                String[] parts = id.split("-");
                if (parts.length != 2) throw new IllegalArgumentException("Invalid Assignment id: " + id);
                yield "SELECT * FROM assignments WHERE taskId = ? AND memberId = ?";
            }
            default -> throw new IllegalArgumentException("Unsupported type");
        };

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (clazz == Assignment.class) {
                String[] parts = id.split("-");
                ps.setString(1, parts[0]);
                ps.setString(2, parts[1]);
            } else {
                ps.setString(1, id);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
                return null;
            }
        }
    }

    @Override
    public synchronized void update(T entity) throws Exception {
        String id = getId(entity);
        if (findById(id) == null) {
            throw new IllegalArgumentException("Entity not found for update: " + id);
        }
        save(entity);
    }


    @Override
    public synchronized boolean deleteById(String id) throws Exception {
        String sql = switch (clazz.getSimpleName()) {
            case "Task" -> "DELETE FROM tasks WHERE id = ?";
            case "TeamMember" -> "DELETE FROM members WHERE id = ?";
            case "Assignment" -> {
                String[] parts = id.split("-");
                if (parts.length != 2) throw new IllegalArgumentException("Invalid Assignment id: " + id);
                yield "DELETE FROM assignments WHERE taskId = ? AND memberId = ?";
            }
            default -> throw new IllegalArgumentException("Unsupported type");
        };

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (clazz == Assignment.class) {
                String[] parts = id.split("-");
                ps.setString(1, parts[0]);
                ps.setString(2, parts[1]);
            } else {
                ps.setString(1, id);
            }
            int affectedRows = ps.executeUpdate();
            return affectedRows > 0;
        }
    }


    @Override
    public synchronized void deleteAll() throws Exception {
        String table = switch (clazz.getSimpleName()) {
            case "Task" -> "tasks";
            case "TeamMember" -> "members";
            case "Assignment" -> "assignments";
            default -> throw new IllegalArgumentException("Unsupported type");
        };

        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM " + table);
        }
    }

    @Override
    public synchronized boolean deleteIf(Predicate<T> predicate) throws Exception {
        List<T> all = load();
        boolean deleted = false;
        for (T obj : all) {
            if (predicate.test(obj)) {
                deleteById(getId(obj));
                deleted = true;
            }
        }
        return deleted;
    }

    // Helper to extract ID for all supported types
    private String getId(T obj) {
        if (obj instanceof Task t) return t.getId();
        if (obj instanceof TeamMember m) return m.getId();
        if (obj instanceof Assignment a) return a.getTaskId() + "-" + a.getMemberId();
        throw new IllegalArgumentException("Unknown type");
    }

    @SuppressWarnings("unchecked")
    private T mapResultSet(ResultSet rs) throws Exception {
        if (clazz == Task.class) {
            Task t = new Task(
                    rs.getString("id"),
                    rs.getString("name"),
                    rs.getInt("durationHours"),
                    rs.getInt("priority"),
                    List.of(rs.getString("requiredSkill").split(","))
            );
            t.setRemainingHours(rs.getInt("remainingHours"));
            return (T) t;
        } else if (clazz == TeamMember.class) {
            TeamMember m = new TeamMember(
                    rs.getString("id"),
                    rs.getString("name"),
                    List.of(rs.getString("skills").split(",")),
                    rs.getInt("maxHoursPerDay"),
                    rs.getDouble("efficiency")
            );
            m.setRemainingHours(rs.getInt("remainingHours"));
            return (T) m;
        } else if (clazz == Assignment.class) {
            return (T) new Assignment(
                    rs.getString("taskId"),
                    rs.getString("memberId"),
                    rs.getInt("assignedHours")
            );
        }
        throw new IllegalArgumentException("Unsupported type");
    }
}