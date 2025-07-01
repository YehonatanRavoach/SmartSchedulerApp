package com.hit.algorithm;

import com.hit.model.Assignment;
import com.hit.model.Task;
import com.hit.model.TeamMember;

import java.util.*;

/**
 * A simple greedy strategy that assigns each task to the first available
 * qualified team member with enough available hours.
 * Prioritizes task urgency and early matching.
 */
public class GreedyEarliestFit implements ITaskAssignment {


    /**
     * Greedily assigns tasks to matching members by scanning all available members
     * and picking the first one who fits the skill and time constraints.
     */
    @Override
    public List<Assignment> assignTasks(List<Task> tasks, List<TeamMember> members) {
        List<Assignment> assignments = new ArrayList<>();

        // Build skill index for fast lookup
        Map<String, List<TeamMember>> skillIndex = new HashMap<>();
        for (TeamMember member : members)
            for (String skill : member.getSkills()) {
                skillIndex
                        .computeIfAbsent(skill, _ -> new ArrayList<>())
                        .add(member);
            }

        // Use a stable priority queue (by priority, then creation time)
        PriorityQueue<Task> taskQueue = new PriorityQueue<>(
                Comparator.comparingInt(Task::getPriority)
                        .thenComparing(Task::getCreatedAt)
        );
        taskQueue.addAll(tasks);

        while (!taskQueue.isEmpty()) {
            Task task = taskQueue.poll();
            boolean assigned = false;

            // Combine all relevant members with ANY required skill
            Set<TeamMember> relevantMembers = new HashSet<>();
            for (String skill : task.getRequiredSkills()) {
                List<TeamMember> skilled = skillIndex.get(skill);
                if (skilled != null)  relevantMembers.addAll(skilled);
            }

            for (TeamMember member : relevantMembers) {
                if (member.getRemainingHours() > 0) {

                    int assignableHours = Math.min(task.getRemainingHours(), member.getRemainingHours());
                    if (assignableHours > 0) {
                        assignments.add(new Assignment(task.getId(), member.getId(), assignableHours));

                        task.setRemainingHours(task.getRemainingHours() - assignableHours);
                        member.setRemainingHours(member.getRemainingHours() - assignableHours);

                        assigned = true;
                    }

                    if (task.getRemainingHours() == 0) break;
                }
            }

            if (task.getRemainingHours() > 0 && assigned) {
                taskQueue.add(task); // Try again in the next round
            }
        }

        return assignments;
    }
}
