package hit.controller;

import com.hit.controller.*;
import com.hit.dao.DaoFactory;
import com.hit.dao.IDao;
import com.hit.model.*;
import com.hit.server.Request;
import com.hit.service.TaskAssignmentService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Comprehensive integration test for the controller layer.
 * Covers edge cases: duplicates, missing/invalid data, multi-assignments, deletes, and updates.
 */
public class ApiControllerLayerTest {
    private TaskController taskController;
    private TeamMemberController memberController;
    private AssignmentController assignmentController;

    private IDao<Task> taskDao;
    private IDao<TeamMember> memberDao;
    private IDao<Assignment> assignmentDao;

    @Before
    public void setup() throws Exception {
        taskDao = DaoFactory.create("sqlite", Task.class);
        memberDao = DaoFactory.create("sqlite", TeamMember.class);
        assignmentDao = DaoFactory.create("sqlite", Assignment.class);

        taskDao.deleteAll();
        memberDao.deleteAll();
        assignmentDao.deleteAll();

        TaskAssignmentService service = new TaskAssignmentService(taskDao, memberDao, assignmentDao);
        taskController = new TaskController(service);
        memberController = new TeamMemberController(service);
        assignmentController = new AssignmentController(service);
    }

    @After
    public void cleanup() throws Exception {
        taskDao.deleteAll();
        memberDao.deleteAll();
        assignmentDao.deleteAll();
    }

    @Test
    public void testFullApiFlow_AllEdgeCases() throws Exception {
        // --- 1. Create task with invalid fields ---
        Map<String, Object> badTask = Map.of("name", "", "durationHours", -1, "priority", 20, "requiredSkills", List.of());
        ApiResponse<Task> badTaskResp = taskController.createNewTask(new Request(new HashMap<>(), badTask));
        assertFalse(badTaskResp.isSuccess());

        // --- 2. Create valid tasks ---
        Map<String, Object> t1 = Map.of("name", "Task1", "durationHours", 2, "priority", 1, "requiredSkills", List.of("java"));
        Map<String, Object> t2 = Map.of("name", "Task2", "durationHours", 3, "priority", 2, "requiredSkills", List.of("sql", "ml"));
        Map<String, Object> t3 = Map.of("name", "Task3", "durationHours", 5, "priority", 2, "requiredSkills", List.of("python"));

        ApiResponse<Task> tResp1 = taskController.createNewTask(new Request(new HashMap<>(), t1));
        assertTrue("Failed to create t1: " + tResp1.getMessage(), tResp1.isSuccess());
        String taskId1 = tResp1.getData().getId();

        ApiResponse<Task> tResp2 = taskController.createNewTask(new Request(new HashMap<>(), t2));
        assertTrue("Failed to create t2: " + tResp2.getMessage(), tResp2.isSuccess());
        String taskId2 = tResp2.getData().getId();

        ApiResponse<Task> tResp3 = taskController.createNewTask(new Request(new HashMap<>(), t3));
        assertTrue("Failed to create t3: " + tResp3.getMessage(), tResp3.isSuccess());
        String taskId3 = tResp3.getData().getId();

        // --- 3. Create member with invalid data ---
        Map<String, Object> badMember = Map.of("name", "", "skills", List.of(""), "maxHoursPerDay", 0, "efficiency", 0.0);
        ApiResponse<TeamMember> badMemberResp = memberController.createNewTeamMember(new Request(new HashMap<>(), badMember));
        assertFalse(badMemberResp.isSuccess());

        // --- 4. Create valid members ---
        Map<String, Object> m1 = Map.of("name", "Alice", "skills", List.of("java", "sql"), "maxHoursPerDay", 8, "efficiency", 1.0);
        Map<String, Object> m2 = Map.of("name", "Bob", "skills", List.of("python", "ml"), "maxHoursPerDay", 6, "efficiency", 1.1);
        Map<String, Object> m3 = Map.of("name", "Dan", "skills", List.of("devops", "cloud"), "maxHoursPerDay", 4, "efficiency", 1.0);

        ApiResponse<TeamMember> mResp1 = memberController.createNewTeamMember(new Request(new HashMap<>(), m1));
        assertTrue("Failed to create m1: " + mResp1.getMessage(), mResp1.isSuccess());
        String memberId1 = mResp1.getData().getId();

        ApiResponse<TeamMember> mResp2 = memberController.createNewTeamMember(new Request(new HashMap<>(), m2));
        assertTrue("Failed to create m2: " + mResp2.getMessage(), mResp2.isSuccess());
        String memberId2 = mResp2.getData().getId();

        ApiResponse<TeamMember> mResp3 = memberController.createNewTeamMember(new Request(new HashMap<>(), m3));
        assertTrue("Failed to create m3: " + mResp3.getMessage(), mResp3.isSuccess());
        String memberId3 = mResp3.getData().getId();

        // --- 5. Duplicate member creation ---
        Map<String, Object> dupMember = new HashMap<>(m1);
        dupMember.put("id", memberId1);
        int countBefore = memberController.countTeamMembers(new Request()).getData();
        ApiResponse<TeamMember> dupResp = memberController.createNewTeamMember(new Request(new HashMap<>(), dupMember));
        assertFalse("Duplicate member creation should fail", dupResp.isSuccess());
        int countAfter = memberController.countTeamMembers(new Request()).getData();
        assertEquals(countBefore, countAfter);

        // --- 6. Assign all tasks using strategy (should handle overlapping skills) ---
        Map<String, Object> assignBody = Map.of("strategy", "greedy");
        ApiResponse<Boolean> assigned = assignmentController.assignTasks(new Request(new HashMap<>(), assignBody));
        // Success may be false only if there are no valid assignments
        ApiResponse<List<Assignment>> allAssignments = assignmentController.getAllAssignments(new Request());
        if (assigned.isSuccess()) {
            assertFalse(allAssignments.getData().isEmpty());
        }

        // --- 7. Re-assign (should not crash, should reset/re-assign) ---
        assignmentController.assignTasks(new Request(new HashMap<>(), assignBody));
        ApiResponse<List<Assignment>> afterReassign = assignmentController.getAllAssignments(new Request());
        assertNotNull(afterReassign.getData());

        // --- 8. Assign when no tasks ---
        taskDao.deleteAll();
        ApiResponse<Boolean> assignNoTasks = assignmentController.assignTasks(new Request(new HashMap<>(), assignBody));
        assertFalse(assignNoTasks.isSuccess());
        assertEquals(0, assignmentController.getAllAssignments(new Request()).getData().size());

        // Restore for next test (create only if not exists!)
        ApiResponse<Task> newTask = taskController.createNewTask(new Request(new HashMap<>(), t1));
        assertTrue(newTask.isSuccess());
        ApiResponse<TeamMember> newMember = memberController.createNewTeamMember(new Request(new HashMap<>(), m1));
        assertTrue(newMember.isSuccess());

        // --- 9. Assign when no members ---
        memberDao.deleteAll();
        ApiResponse<Boolean> assignNoMembers = assignmentController.assignTasks(new Request(new HashMap<>(), assignBody));
        assertFalse(assignNoMembers.isSuccess());
        assertEquals(0, assignmentController.getAllAssignments(new Request()).getData().size());

        // --- 10. Assign when no skill match (should yield zero assignments) ---
        Map<String, Object> tNoMatch = Map.of("name", "Impossible Task", "durationHours", 2, "priority", 1, "requiredSkills", List.of("go"));
        ApiResponse<Task> impossibleTaskResp = taskController.createNewTask(new Request(new HashMap<>(), tNoMatch));
        assertTrue(impossibleTaskResp.isSuccess());
        Map<String, Object> mNoMatch = Map.of("name", "NoGoDev", "skills", List.of("perl"), "maxHoursPerDay", 5, "efficiency", 1.0);
        ApiResponse<TeamMember> noGoMemberResp = memberController.createNewTeamMember(new Request(new HashMap<>(), mNoMatch));
        assertTrue(noGoMemberResp.isSuccess());
        ApiResponse<Boolean> assignNoSkill = assignmentController.assignTasks(new Request(new HashMap<>(), assignBody));
        assertFalse(assignNoSkill.isSuccess());
        assertEquals(0, assignmentController.getAllAssignments(new Request()).getData().size());

        // --- 11. Update and delete logic ---
        // Update member with invalid efficiency
        ApiResponse<TeamMember> memberForUpdateResp = memberController.createNewTeamMember(new Request(new HashMap<>(), m2));
        assertTrue(memberForUpdateResp.isSuccess());
        String memberForUpdateId = memberForUpdateResp.getData().getId();

        Map<String, Object> updateBody = new HashMap<>(m2);
        updateBody.put("id", memberForUpdateId);
        updateBody.put("efficiency", 10.0);
        updateBody.put("strategy", "greedy");
        ApiResponse<Boolean> updateBad = memberController.updateTeamMember(new Request(new HashMap<>(), updateBody));
        assertFalse(updateBad.isSuccess());

        // Valid update
        updateBody.put("efficiency", 1.1);
        ApiResponse<Boolean> updateOk = memberController.updateTeamMember(new Request(new HashMap<>(), updateBody));
        assertTrue("Valid update should succeed: " + updateOk.getMessage(), updateOk.isSuccess());

        // Delete non-existent member
        Map<String, Object> delM = Map.of("id", "fakeid");
        ApiResponse<Boolean> delMemResp = memberController.deleteTeamMember(new Request(new HashMap<>(), delM));
        assertFalse(delMemResp.isSuccess());

        // Delete real member
        delM = Map.of("id", memberForUpdateId);
        delMemResp = memberController.deleteTeamMember(new Request(new HashMap<>(), delM));
        assertTrue(delMemResp.isSuccess());
    }
}
