package com.hit.algorithm;

import com.hit.model.Assignment;
import com.hit.model.Task;
import com.hit.model.TeamMember;
import org.junit.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class ITaskAssignmentTest {

    // --- Model sanity checks ---

    @Test
    public void testTaskModel() {
        Task t = new Task("T1", "API", 4, 1, List.of("java", "sql"));
        assertEquals("T1", t.getId());
        assertEquals("API", t.getName());
        assertEquals(4, t.getDurationHours());
        assertEquals(1, t.getPriority());
        assertEquals(List.of("java", "sql"), t.getRequiredSkills());
        assertNotNull(t.getCreatedAt());
        assertEquals(t.getDurationHours(), t.getRemainingHours());
        t.setRemainingHours(2);
        assertEquals(2, t.getRemainingHours());
        assertNotNull(t.toString());
    }

    @Test
    public void testTeamMemberModel() {
        TeamMember m = new TeamMember("M1", "Alice", List.of("java", "sql"), 8, 1.0);
        assertEquals("M1", m.getId());
        assertEquals("Alice", m.getName());
        assertEquals(List.of("java", "sql"), m.getSkills());
        assertEquals(8, m.getMaxHoursPerDay());
        assertEquals(8, m.getRemainingHours());
        assertEquals(1.0, m.getEfficiency(), 0.001);
        m.setRemainingHours(5);
        assertEquals(5, m.getRemainingHours());
        assertNotNull(m.toString());
    }

    @Test
    public void testAssignmentModel() {
        Assignment a = new Assignment("T1", "M2", 4);
        assertEquals("T1", a.getTaskId());
        assertEquals("M2", a.getMemberId());
        assertEquals(4, a.getAssignedHours());
        assertNotNull(a.toString());
    }

    // --- Algorithm logic tests ---

    private List<TeamMember> sampleMembers() {
        return List.of(
                new TeamMember("m1", "Alice", List.of("java", "sql"), 8, 1.0),
                new TeamMember("m2", "Bob", List.of("python", "ml"), 6, 1.0),
                new TeamMember("m3", "Charlie", List.of("java", "ml"), 4, 1.0)
        );
    }

    private List<Task> sampleTasks() {
        return List.of(
                new Task("t1", "Build API", 4, 1, List.of("java")),
                new Task("t2", "Train Model", 5, 2, List.of("ml")),
                new Task("t3", "Write SQL Report", 3, 3, List.of("sql"))
        );
    }

    private List<TeamMember> deepCopyMembers() {
        List<TeamMember> copy = new ArrayList<>();
        for (TeamMember m : sampleMembers())
            copy.add(new TeamMember(m.getId(), m.getName(), m.getSkills(), m.getMaxHoursPerDay(), m.getEfficiency()));
        return copy;
    }

    private List<Task> deepCopyTasks() {
        List<Task> copy = new ArrayList<>();
        for (Task t : sampleTasks())
            copy.add(new Task(t.getId(), t.getName(), t.getDurationHours(), t.getPriority(), t.getRequiredSkills()));
        return copy;
    }

    @Test
    public void testGreedyAssignmentSimple() {
        ITaskAssignment strategy = StrategyFactory.getStrategy("greedy");
        List<Assignment> result = strategy.assignTasks(deepCopyTasks(), deepCopyMembers());
        assertFalse(result.isEmpty());
        checkAssignmentsValid(result);
        assertEquals(3, result.size());
        assertEquals(Set.of("t1", "t2", "t3"),
                result.stream().map(Assignment::getTaskId).collect(Collectors.toSet()));
    }

    @Test
    public void testBalancedAssignmentSimple() {
        ITaskAssignment strategy = StrategyFactory.getStrategy("balanced");
        List<Assignment> result = strategy.assignTasks(deepCopyTasks(), deepCopyMembers());
        assertFalse(result.isEmpty());
        checkAssignmentsValid(result);
        assertEquals(3, result.size());
    }

    @Test
    public void testNoMatchingMembers() {
        // All tasks require "go" skill, but no member has it
        List<Task> tasks = List.of(
                new Task("t1", "Go API", 2, 1, List.of("go")),
                new Task("t2", "Go Model", 2, 2, List.of("go"))
        );
        List<TeamMember> members = List.of(
                new TeamMember("m1", "Alice", List.of("java"), 4, 1.0)
        );
        ITaskAssignment strategy = StrategyFactory.getStrategy("greedy");
        List<Assignment> result = strategy.assignTasks(tasks, deepCopyMembers(members));
        assertTrue(result.isEmpty());
    }

    @Test
    public void testPartialAssignmentDueToHours() {
        // Only enough member hours for one task
        List<Task> tasks = List.of(
                new Task("t1", "API", 4, 1, List.of("java")),
                new Task("t2", "Report", 4, 2, List.of("sql"))
        );
        List<TeamMember> members = List.of(
                new TeamMember("m1", "Alice", List.of("java", "sql"), 4, 1.0)
        );
        ITaskAssignment strategy = StrategyFactory.getStrategy("greedy");
        List<Assignment> result = strategy.assignTasks(deepCopyTasks(tasks), deepCopyMembers(members));
        // Should only be able to assign one task
        assertEquals(1, result.size());
        Assignment a = result.getFirst();
        assertTrue(a.getTaskId().equals("t1") || a.getTaskId().equals("t2"));
    }

    @Test
    public void testTaskRequiresMultipleSkillsMemberHasAll() {
        // Task requires two skills, member has both
        Task task = new Task("t1", "Complex Task", 3, 1, List.of("java", "sql"));
        TeamMember member = new TeamMember("m1", "Alice", List.of("java", "sql"), 8, 1.0);
        ITaskAssignment strategy = StrategyFactory.getStrategy("greedy");
        List<Assignment> result = strategy.assignTasks(List.of(task), List.of(member));
        assertFalse(result.isEmpty());
        Assignment a = result.getFirst();
        assertEquals("t1", a.getTaskId());
        assertEquals("m1", a.getMemberId());
    }

    @Test
    public void testNoTasks() {
        ITaskAssignment strategy = StrategyFactory.getStrategy("greedy");
        List<Assignment> result = strategy.assignTasks(Collections.emptyList(), deepCopyMembers());
        assertTrue(result.isEmpty());
    }

    @Test
    public void testNoMembers() {
        ITaskAssignment strategy = StrategyFactory.getStrategy("balanced");
        List<Assignment> result = strategy.assignTasks(deepCopyTasks(), Collections.emptyList());
        assertTrue(result.isEmpty());
    }

    @Test
    public void testMultipleAssignmentsToOneMember() {
        // One member can handle all tasks, verify correct hours tracking
        TeamMember member = new TeamMember("m1", "Superman", List.of("java", "sql", "ml"), 20, 1.0);
        List<Task> tasks = List.of(
                new Task("t1", "API", 4, 1, List.of("java")),
                new Task("t2", "ML", 5, 2, List.of("ml")),
                new Task("t3", "SQL", 3, 3, List.of("sql"))
        );
        ITaskAssignment strategy = StrategyFactory.getStrategy("balanced");
        List<Assignment> result = strategy.assignTasks(deepCopyTasks(tasks), List.of(member));
        assertEquals(3, result.size());
        int totalAssigned = result.stream().mapToInt(Assignment::getAssignedHours).sum();
        assertEquals(12, totalAssigned);
    }

    // --- Helpers ---

    private void checkAssignmentsValid(List<Assignment> assignments) {
        for (Assignment a : assignments) {
            assertNotNull(a.getMemberId());
            assertNotNull(a.getTaskId());
            assertTrue(a.getAssignedHours() > 0);
        }
    }

    private List<TeamMember> deepCopyMembers(List<TeamMember> original) {
        List<TeamMember> copy = new ArrayList<>();
        for (TeamMember m : original)
            copy.add(new TeamMember(m.getId(), m.getName(), m.getSkills(), m.getMaxHoursPerDay(), m.getEfficiency()));
        return copy;
    }

    private List<Task> deepCopyTasks(List<Task> original) {
        List<Task> copy = new ArrayList<>();
        for (Task t : original)
            copy.add(new Task(t.getId(), t.getName(), t.getDurationHours(), t.getPriority(), t.getRequiredSkills()));
        return copy;
    }
}
