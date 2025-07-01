package com.hit.controller;

import com.hit.model.Assignment;
import com.hit.server.Request;
import com.hit.service.TaskAssignmentService;

import java.util.List;

/**
 * Handles API logic for Assignment-related actions.
 * Now robust against edge cases and duplicate assignments.
 */
public class AssignmentController {
    private final TaskAssignmentService service;
    private static final int MAX_ID_LENGTH = 40;

    public AssignmentController(TaskAssignmentService service) {
        this.service = service;
    }

    /**
     * Assign tasks to all members using a specified strategy.
     * Strategy name must be passed in the request body as "strategy".
     */
    public ApiResponse<Boolean> assignTasks(Request req) {
        try {
            String strategy = getStrategyFromBody(req);
            boolean success = service.assignTasks(strategy);
            return success
                    ? ApiResponse.success(true, "Tasks assigned to all members.")
                    : ApiResponse.error("No assignments were made.");
        } catch (Exception e) {
            return ApiResponse.error("Failed to assign tasks: " + e.getMessage());
        }
    }

    /**
     * Assign tasks to a single team member using a specified strategy.
     * Requires "memberId" and "strategy" in the request body.
     * Will not create duplicate assignments for the same (task, member) pair.
     */
    public ApiResponse<Boolean> assignTasksToTeamMember(Request req) {
        try {
            String memberId = getMemberIdFromBody(req);
            String strategy = getStrategyFromBody(req);
            if (!isValidId(memberId))
                return ApiResponse.error("Missing or invalid memberId.");

            // Defensive: check that member exists in the system
            if (service.getTeamMemberById(memberId) == null)
                return ApiResponse.error("Member not found.");

            boolean success = service.assignTasksToTeamMember(memberId, strategy);
            return success
                    ? ApiResponse.success(true, "Tasks assigned to member.")
                    : ApiResponse.error("No assignments were made for member.");
        } catch (Exception e) {
            return ApiResponse.error("Failed to assign tasks to member: " + e.getMessage());
        }
    }

    /**
     * Delete an assignment by taskId and memberId.
     * Validates both IDs before deletion.
     * Will not throw on redundant delete (idempotent).
     */
    public ApiResponse<Boolean> deleteAssignment(Request req) {
        try {
            String taskId = getTaskIdFromBody(req);
            String memberId = getMemberIdFromBody(req);
            String validationError = validateAssignmentIds(taskId, memberId);
            if (validationError != null)
                return ApiResponse.error(validationError);

            boolean deleted = service.deleteAssignment(taskId, memberId);
            return deleted
                    ? ApiResponse.success(true, "Assignment deleted.")
                    : ApiResponse.error("Assignment not found or already deleted.");
        } catch (Exception e) {
            return ApiResponse.error("Failed to delete assignment: " + e.getMessage());
        }
    }

    /**
     * Get all assignments in the system.
     */
    public ApiResponse<List<Assignment>> getAllAssignments(Request req) {
        try {
            return ApiResponse.success(service.getAllAssignments(), "All assignments.");
        } catch (Exception e) {
            return ApiResponse.error("Failed to get assignments: " + e.getMessage());
        }
    }

    /**
     * Get all assignments for a specific team member.
     * Validates memberId before filtering.
     */
    public ApiResponse<List<Assignment>> getAssignmentsForTeamMember(Request req) {
        try {
            String memberId = getMemberIdFromBody(req);
            if (!isValidId(memberId))
                return ApiResponse.error("Missing or invalid memberId.");

            // Defensive: check member exists (avoid confusion)
            if (service.getTeamMemberById(memberId) == null)
                return ApiResponse.error("Member not found.");

            List<Assignment> filtered = service.getAllAssignments().stream()
                    .filter(a -> memberId.equals(a.getMemberId()))
                    .toList();
            return ApiResponse.success(filtered, "Assignments for member.");
        } catch (Exception e) {
            return ApiResponse.error("Failed to get assignments for member: " + e.getMessage());
        }
    }

    // --- Helpers ---

    /** Checks if a string id is valid (non-null, non-blank, and not too long). */
    private boolean isValidId(String id) {
        return id != null && !id.isBlank() && id.length() <= MAX_ID_LENGTH;
    }

    private String validateAssignmentIds(String taskId, String memberId) {
        if (!isValidId(taskId))
            return "Missing or invalid taskId.";
        if (!isValidId(memberId))
            return "Missing or invalid memberId.";
        return null;
    }

    protected String getTaskIdFromBody(Request req) {
        Object obj = req.getBody().get("taskId");
        return obj != null ? obj.toString() : null;
    }

    protected String getMemberIdFromBody(Request req) {
        Object obj = req.getBody().get("memberId");
        return obj != null ? obj.toString() : null;
    }

    protected String getStrategyFromBody(Request req) {
        Object obj = req.getBody().get("strategy");
        return obj != null ? obj.toString() : null;
    }
}
