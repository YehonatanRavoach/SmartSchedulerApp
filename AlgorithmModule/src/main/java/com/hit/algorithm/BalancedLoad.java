package com.hit.algorithm;

import com.hit.model.Assignment;
import com.hit.model.Task;
import com.hit.model.TeamMember;

import java.util.*;

/**
 * A load-balancing strategy that assigns tasks as evenly as possible
 * across team members, while considering skills and availability.
 * Prioritizes fairness in load distribution.
 */
public class BalancedLoad implements ITaskAssignment {

    /**
     * Assigns tasks by sorting skilled team members by their current load,
     * then allocating work to the least-loaded suitable member.
     */
    @Override
    public List<Assignment> assignTasks(List<Task> tasks, List<TeamMember> members) {
        List<Assignment> assignments = new ArrayList<>();

        // Build skill index
        Map<String, List<TeamMember>> skillIndex = new HashMap<>();
        Map<String, Integer> memberLoadMap = new HashMap<>();

        for (TeamMember member : members) {
            memberLoadMap.put(member.getId(), 0);
            for (String skill : member.getSkills()) {
                skillIndex.computeIfAbsent(skill, _ -> new ArrayList<>()).add(member);
            }
        }

        // Stable task priority
        PriorityQueue<Task> taskQueue = new PriorityQueue<>(
                Comparator.comparingInt(Task::getPriority)
                        .thenComparing(Task::getCreatedAt)
        );
        taskQueue.addAll(tasks);

        while (!taskQueue.isEmpty()) {
            Task task = taskQueue.poll();
            boolean assigned = false;

            // Filter relevant members
            Set<TeamMember> skilledMembers = new HashSet<>();
            for (String skill : task.getRequiredSkills()) {
                List<TeamMember> skilled = skillIndex.get(skill);
                if (skilled != null) skilledMembers.addAll(skilled);
            }

            List<TeamMember> candidates = new ArrayList<>();
            for (TeamMember member : skilledMembers) {
                if (member.getRemainingHours() > 0) {
                    candidates.add(member);
                }
            }

            // Sort candidates by normalized load
            candidates.sort(Comparator.comparingDouble(member ->
                    memberLoadMap.get(member.getId()) / (double) member.getMaxHoursPerDay()
            ));

            for (TeamMember member : candidates) {
                int assignableHours = Math.min(task.getRemainingHours(), member.getRemainingHours());
                if (assignableHours > 0) {
                    assignments.add(new Assignment(task.getId(), member.getId(), assignableHours));

                    task.setRemainingHours(task.getRemainingHours() - assignableHours);
                    member.setRemainingHours(member.getRemainingHours() - assignableHours);
                    memberLoadMap.put(member.getId(), memberLoadMap.get(member.getId()) + assignableHours);

                    assigned = true;
                }
                if (task.getRemainingHours() == 0) break;
            }

            if (task.getRemainingHours() > 0 && assigned) {
                taskQueue.add(task); // Requeue for further assignment
            }

        }

        return assignments;
    }
}
