package com.hit.model;

import java.io.Serializable;

public class Assignment implements Serializable {
    private String taskId;
    private String memberId;
    private int assignedHours;

    public Assignment() {}

    public Assignment(String taskId, String memberId, int assignedHours) {
        this.taskId = taskId;
        this.memberId = memberId;
        this.assignedHours = assignedHours;
    }

    // Getters & Setters

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getMemberId() { return memberId; }
    public void setMemberId(String memberId) { this.memberId = memberId; }

    public int getAssignedHours() { return assignedHours; }
    public void setAssignedHours(int assignedHours) { this.assignedHours = assignedHours; }

    @Override
    public String toString() {
        return String.format(
                "Assignment{taskId='%s', memberId='%s', assignedHours=%d}",
                taskId,
                memberId,
                assignedHours
        );
    }


}
