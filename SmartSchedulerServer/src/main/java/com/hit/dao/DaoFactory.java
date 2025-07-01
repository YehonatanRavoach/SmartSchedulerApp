package com.hit.dao;

import com.hit.model.Assignment;
import com.hit.model.Task;
import com.hit.model.TeamMember;

/**
 * Factory for producing thread-safe DAO implementations.
 */
public class DaoFactory {
    private static SQLiteDaoImpl<Task> taskDaoInstance;
    private static SQLiteDaoImpl<TeamMember> memberDaoInstance;
    private static SQLiteDaoImpl<Assignment> assignmentDaoInstance;

    @SuppressWarnings("unchecked")
    public static synchronized <T> IDao<T> create(String type, Class<T> clazz) {
        return switch (type) {
            case "sqlite" -> {
                if (clazz == Task.class) {
                    if (taskDaoInstance == null)
                        taskDaoInstance = new SQLiteDaoImpl<>(Task.class);
                    yield (IDao<T>) taskDaoInstance;
                }
                if (clazz == TeamMember.class) {
                    if (memberDaoInstance == null)
                        memberDaoInstance = new SQLiteDaoImpl<>(TeamMember.class);
                    yield (IDao<T>) memberDaoInstance;
                }
                if (clazz == Assignment.class) {
                    if (assignmentDaoInstance == null)
                        assignmentDaoInstance = new SQLiteDaoImpl<>(Assignment.class);
                    yield (IDao<T>) assignmentDaoInstance;
                }
                throw new IllegalArgumentException("Unsupported model class: " + clazz);
            }
            case "file" -> new FileDaoImpl<>(clazz);
            default -> throw new IllegalArgumentException("Unsupported DAO type: " + type);
        };
    }
}
