package com.hit.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

public class Task implements Serializable {
    private String id;
    private String name;
    private int durationHours;
    private int remainingHours;
    private int priority;
    private List<String> requiredSkills;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAt;

    public Task() {
        this.createdAt = Instant.now();
    }

    public Task(String id, String name, int durationHours, int priority, List<String> requiredSkills) {
        this.id = id;
        this.name = name;
        this.durationHours = durationHours;
        this.remainingHours = durationHours;
        this.priority = priority;
        this.requiredSkills = requiredSkills;
        this.createdAt = Instant.now();
    }

    // Getters & Setters

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getDurationHours() { return durationHours; }
    public void setDurationHours(int durationHours) { this.durationHours = durationHours; }

    public int getRemainingHours() { return remainingHours; }
    public void setRemainingHours(int remainingHours) { this.remainingHours = remainingHours; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public List<String> getRequiredSkills() { return requiredSkills; }
    public void setRequiredSkills(List<String> requiredSkills) { this.requiredSkills = requiredSkills; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return String.format(
                "Task{id='%s', name='%s', duration=%d, remaining=%d, priority=%d, requiredSkills=%s, createdAt=%s}",
                id,
                name,
                durationHours,
                remainingHours,
                priority,
                requiredSkills,
                createdAt != null ? createdAt.toString() : "null"
        );
    }

    public static Task fromRaw(Task raw) {
        return new Task(
                raw.getId(),
                raw.getName(),
                raw.getDurationHours(),
                raw.getPriority(),
                raw.getRequiredSkills()
                // remainingHours automatically initialized in durationHours
        );
    }


}
