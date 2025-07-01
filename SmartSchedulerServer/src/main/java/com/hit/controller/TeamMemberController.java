package com.hit.controller;

import Util.GsonFactory;
import com.google.gson.Gson;
import com.hit.model.TeamMember;
import com.hit.server.Request;
import com.hit.service.TaskAssignmentService;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Handles API logic for TeamMember-related actions.
 * Now robust against duplicate creation and covers edge cases.
 */
public class TeamMemberController {
    private final TaskAssignmentService service;
    private static final Gson gson = GsonFactory.get();

    // --- Validation constants ---
    private static final int MAX_MEMBER_NAME_LENGTH = 100;
    private static final int MAX_SKILL_NAME_LENGTH = 40;
    private static final int MAX_HOURS_PER_DAY = 24;
    private static final double MIN_EFFICIENCY = 1.0;
    private static final double MAX_EFFICIENCY = 6.0;

    public TeamMemberController(TaskAssignmentService service) {
        this.service = service;
    }

    /**
     * Create a new TeamMember.
     * Validates all TeamMember fields and prevents duplicate creation.
     * @param req Request containing TeamMember data
     * @return ApiResponse with created TeamMember or error
     */
    public ApiResponse<TeamMember> createNewTeamMember(Request req) {
        try {
            TeamMember raw = parseMemberFromRequest(req);
            TeamMember m = TeamMember.fromRaw(raw);

            String validationError = validateMemberFields(m);
            if (validationError != null)
                return ApiResponse.error(validationError);

            // Defensive: prevent duplicate only by ID (if passed forcibly, but the generator always overrides)
            if (m.getId() != null && !m.getId().isBlank()) {
                TeamMember existsById = service.getTeamMemberById(m.getId());
                if (existsById != null)
                    return ApiResponse.error("Member already exists with this ID.");
            }

            TeamMember created = service.createNewTeamMember(m);
            return ApiResponse.success(created, "Member created.");
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error("Failed to create member: " + e.getMessage());
        }
    }


    /**
     * Update an existing TeamMember and re-assign all tasks using a user-supplied strategy.
     * Requires "strategy" in request body.
     * @param req Request containing TeamMember data, id, and "strategy"
     * @return ApiResponse with success flag or error
     */
    public ApiResponse<Boolean> updateTeamMember(Request req) {
        try {
            String id = getIdFromBody(req);
            TeamMember raw = parseMemberFromRequest(req);
            TeamMember m = TeamMember.fromRaw(raw); // Converts from raw/gson to a valid object

            if (id == null || id.isBlank())
                return ApiResponse.error("Missing member id.");

            // Make sure the member exists before updating
            TeamMember existing = service.getTeamMemberById(id);
            if (existing == null)
                return ApiResponse.error("Member not found.");

            // Use the existing ID (prevents accidental create)
            m.setId(id);

            // Run the standard validation
            String validationError = validateMemberFields(m);
            if (validationError != null)
                return ApiResponse.error(validationError);

            String strategy = getStrategyFromBody(req);

            boolean updated = service.updateTeamMember(id, m, strategy);
            return updated
                    ? ApiResponse.success(true, "Member updated and assignments recalculated.")
                    : ApiResponse.error("Member not found."); // This should almost never happen here
        } catch (Exception e) {
            return ApiResponse.error("Failed to update member: " + e.getMessage());
        }
    }

    /**
     * Delete a TeamMember by id.
     * @param req Request containing TeamMember id
     * @return ApiResponse with success flag or error
     */
    public ApiResponse<Boolean> deleteTeamMember(Request req) {
        try {
            String id = getIdFromBody(req);
            if (id == null || id.isBlank())
                return ApiResponse.error("Missing member id.");
            boolean deleted = service.deleteTeamMember(id);
            return deleted
                    ? ApiResponse.success(true, "Member deleted.")
                    : ApiResponse.error("Member not found.");
        } catch (Exception e) {
            return ApiResponse.error("Failed to delete member: " + e.getMessage());
        }
    }

    public ApiResponse<List<TeamMember>> getAllTeamMembers(Request req) {
        try {
            return ApiResponse.success(service.getAllTeamMembers(), "All members.");
        } catch (Exception e) {
            return ApiResponse.error("Failed to get members: " + e.getMessage());
        }
    }

    public ApiResponse<List<TeamMember>> searchTeamMembersByName(Request req) {
        try {
            String name = getNameFromBody(req);
            if (name == null || name.isBlank())
                return ApiResponse.error("Missing member name.");
            return ApiResponse.success(service.searchTeamMembersByName(name), "Search result.");
        } catch (Exception e) {
            return ApiResponse.error("Failed to search members: " + e.getMessage());
        }
    }

    public ApiResponse<Integer> countTeamMembers(Request req) {
        try {
            return ApiResponse.success(service.countTeamMembers(), "Member count.");
        } catch (Exception e) {
            return ApiResponse.error("Failed to count members: " + e.getMessage());
        }
    }

    public ApiResponse<Double> averageLoad(Request req) {
        try {
            return ApiResponse.success(service.averageLoad(), "Average load.");
        } catch (Exception e) {
            return ApiResponse.error("Failed to get average load: " + e.getMessage());
        }
    }

    // --- Helpers ---

    private String validateMemberFields(TeamMember m) {
        if (m == null)
            return "Member data is missing.";

        String name = m.getName();
        if (name == null || name.isBlank())
            return "Invalid or missing member name.";
        if (name.length() > MAX_MEMBER_NAME_LENGTH)
            return "Member name too long. Maximum is " + MAX_MEMBER_NAME_LENGTH + " characters.";

        List<String> skills = m.getSkills();
        if (skills == null || skills.isEmpty())
            return "At least one skill must be specified.";
        Set<String> uniqueSkills = new HashSet<>();
        for (String skill : skills) {
            if (skill == null || skill.isBlank())
                return "Each skill must be a non-empty string.";
            if (skill.length() > MAX_SKILL_NAME_LENGTH)
                return "Skill name too long (max " + MAX_SKILL_NAME_LENGTH + ").";
            String normalized = skill.trim().toLowerCase();
            if (!uniqueSkills.add(normalized))
                return "Duplicate skills are not allowed.";
        }

        int maxHoursPerDay = m.getMaxHoursPerDay();
        if (maxHoursPerDay < 1 || maxHoursPerDay > MAX_HOURS_PER_DAY)
            return "Max hours per day must be between 1 and " + MAX_HOURS_PER_DAY + ".";

        double efficiency = m.getEfficiency();
        if (efficiency < MIN_EFFICIENCY || efficiency > MAX_EFFICIENCY)
            return "Efficiency must be between " + MIN_EFFICIENCY + " and " + MAX_EFFICIENCY + ".";

        return null;
    }

    protected TeamMember parseMemberFromRequest(Request req) {
        return gson.fromJson(gson.toJson(req.getBody()), TeamMember.class);
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
