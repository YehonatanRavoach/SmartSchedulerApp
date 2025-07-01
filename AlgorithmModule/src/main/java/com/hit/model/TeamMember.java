package com.hit.model;

import java.io.Serializable;
import java.util.List;

public class TeamMember implements Serializable {
    private String id;
    private String name;
    private List<String> skills;
    private int maxHoursPerDay;
    private int remainingHours;
    private double efficiency;

    public TeamMember() {}

    public TeamMember(String id, String name, List<String> skills, int maxHoursPerDay, double efficiency) {
        this.id = id;
        this.name = name;
        this.skills = skills;
        this.maxHoursPerDay = maxHoursPerDay;
        this.remainingHours = maxHoursPerDay;
        this.efficiency = efficiency;
    }

    // Getters & Setters

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<String> getSkills() { return skills; }
    public void setSkills(List<String> skills) { this.skills = skills; }

    public int getMaxHoursPerDay() { return maxHoursPerDay; }
    public void setMaxHoursPerDay(int maxHoursPerDay) { this.maxHoursPerDay = maxHoursPerDay; }

    public int getRemainingHours() { return remainingHours; }
    public void setRemainingHours(int remainingHours) { this.remainingHours = remainingHours; }

    public double getEfficiency() { return efficiency; }
    public void setEfficiency(double efficiency) { this.efficiency = efficiency; }

    @Override
    public String toString() {
        return String.format(
                "TeamMember{id='%s', name='%s', skills=%s, maxHoursPerDay=%d, remainingHours=%d, efficiency=%.2f}",
                id,
                name,
                skills,
                maxHoursPerDay,
                remainingHours,
                efficiency
        );
    }

    public static TeamMember fromRaw(TeamMember raw) {
        return new TeamMember(
                raw.getId(),
                raw.getName(),
                raw.getSkills(),
                raw.getMaxHoursPerDay(),
                raw.getEfficiency()
                // remainingHours automatically initialized in ctor
        );
    }
}
