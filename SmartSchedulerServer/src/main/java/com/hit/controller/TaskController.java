package com.hit.controller;

import Util.GsonFactory;
import com.google.gson.Gson;
import com.hit.model.Task;
import com.hit.server.Request;
import com.hit.service.TaskAssignmentService;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Handles API logic for Task-related actions.
 * Now robust against duplicate creation and edge cases.
 */
public class TaskController {
    private final TaskAssignmentService service;
    private static final Gson gson = GsonFactory.get();

    // --- Validation constants ---
    private static final int MAX_TASK_NAME_LENGTH = 100;
    private static final int MAX_SKILL_NAME_LENGTH = 20;
    private static final int MAX_TASK_DURATION = 1000;
    private static final int MAX_PRIORITY = 4;

    public TaskController(TaskAssignmentService service) {
        this.service = service;
    }

    /**
     * Create a new Task.
     * Validates all Task fields and prevents duplicate creation (by name or ID).
     */
    public ApiResponse<Task> createNewTask(Request req) {
        try {
            Task raw = parseTaskFromRequest(req);
            Task task = Task.fromRaw(raw);
            String validationError = validateTaskFields(task);
            if (validationError != null)
                return ApiResponse.error(validationError);

            // Defensive: prevent duplicate by ID (if passed forcibly)
            if (task.getId() != null && !task.getId().isBlank()) {
                Task existsById = service.getTaskById(task.getId());
                if (existsById != null)
                    return ApiResponse.error("Task already exists with this ID.");
            }

            Task created = service.createNewTask(task);
            return ApiResponse.success(created, "Task created.");
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error("Failed to create task: " + e.getMessage());
        }
    }

    /**
     * Update an existing Task and re-assign all tasks using a user-supplied strategy.
     * Will only update if the task already exists (by id).
     * @param req Request containing Task data, id, and "strategy"
     * @return ApiResponse with success flag or error
     */
    public ApiResponse<Boolean> updateTask(Request req) {
        try {
            String id = getIdFromBody(req);
            Task raw = parseTaskFromRequest(req);
            Task task = Task.fromRaw(raw);

            if (id == null || id.isBlank())
                return ApiResponse.error("Missing task id.");

            // Ensure the task exists before updating
            Task existing = service.getTaskById(id);
            if (existing == null)
                return ApiResponse.error("Task not found.");

            // Set the existing ID to prevent accidental creation
            task.setId(id);

            // Validate fields before update
            String validationError = validateTaskFields(task);
            if (validationError != null)
                return ApiResponse.error(validationError);

            String strategy = getStrategyFromBody(req);

            boolean updated = service.updateTask(id, task, strategy);
            return updated
                    ? ApiResponse.success(true, "Task updated and assignments recalculated.")
                    : ApiResponse.error("Task not found.");
        } catch (Exception e) {
            return ApiResponse.error("Failed to update task: " + e.getMessage());
        }
    }

    public ApiResponse<Boolean> deleteTask(Request req) {
        try {
            String id = getIdFromBody(req);
            if (id == null || id.isBlank())
                return ApiResponse.error("Missing task id.");
            boolean deleted = service.deleteTask(id);
            return deleted
                    ? ApiResponse.success(true, "Task deleted.")
                    : ApiResponse.error("Task not found.");
        } catch (Exception e) {
            return ApiResponse.error("Failed to delete task: " + e.getMessage());
        }
    }

    public ApiResponse<List<Task>> getAllTasks(Request req) {
        try {
            return ApiResponse.success(service.getAllTasks(), "All tasks.");
        } catch (Exception e) {
            return ApiResponse.error("Failed to get tasks: " + e.getMessage());
        }
    }

    public ApiResponse<List<Task>> searchTasksByName(Request req) {
        try {
            String name = getNameFromBody(req);
            if (name == null || name.isBlank())
                return ApiResponse.error("Missing task name.");
            return ApiResponse.success(service.searchTasksByName(name), "Search result.");
        } catch (Exception e) {
            return ApiResponse.error("Failed to search tasks: " + e.getMessage());
        }
    }

    public ApiResponse<Integer> countTasks(Request req) {
        try {
            return ApiResponse.success(service.countTasks(), "Task count.");
        } catch (Exception e) {
            return ApiResponse.error("Failed to count tasks: " + e.getMessage());
        }
    }

    public ApiResponse<Integer> countUnassignedTasks(Request req) {
        try {
            return ApiResponse.success(service.countUnassignedTasks(), "Unassigned task count.");
        } catch (Exception e) {
            return ApiResponse.error("Failed to count unassigned tasks: " + e.getMessage());
        }
    }

    // --- Helpers ---

    private String validateTaskFields(Task task) {
        if (task == null)
            return "Task data is missing.";

        String name = task.getName();
        if (name == null || name.isBlank())
            return "Invalid or missing task name.";
        if (name.length() > MAX_TASK_NAME_LENGTH)
            return "Task name too long. Maximum length is " + MAX_TASK_NAME_LENGTH + " characters.";

        double duration = task.getDurationHours();
        if (duration <= 0 || duration > MAX_TASK_DURATION)
            return "Duration hours must be between 1 and " + MAX_TASK_DURATION + ".";

        int priority = task.getPriority();
        if (priority < 1 || priority > MAX_PRIORITY)
            return "Priority must be between 1 and " + MAX_PRIORITY + ".";

        List<String> skills = task.getRequiredSkills();
        if (skills == null || skills.isEmpty())
            return "At least one required skill must be specified.";
        Set<String> uniqueSkills = new HashSet<>();
        for (String skill : skills) {
            if (skill == null || skill.isBlank())
                return "Required skills must be non-empty strings.";
            if (skill.length() > MAX_SKILL_NAME_LENGTH)
                return "Skill name too long (max " + MAX_SKILL_NAME_LENGTH + ").";
            String normalized = skill.trim().toLowerCase();
            if (!uniqueSkills.add(normalized))
                return "Duplicate skills are not allowed.";
        }
        return null;
    }

    protected Task parseTaskFromRequest(Request req) {
        return gson.fromJson(gson.toJson(req.getBody()), Task.class);
    }

    protected String getIdFromBody(Request req) {
        Object obj = req.getBody().get("id");
        return obj != null ? obj.toString() : null;
    }

    protected String getNameFromBody(Request req) {
        Object obj = req.getBody().get("name");
        return obj != null ? obj.toString() : null;
    }

    protected String getStrategyFromBody(Request req) {
        Object obj = req.getBody().get("strategy");
        return obj != null ? obj.toString() : null;
    }
}
