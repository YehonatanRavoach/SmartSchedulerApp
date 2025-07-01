package hit.service;

import com.hit.dao.IDao;
import com.hit.dao.DaoFactory;
import com.hit.model.*;
import com.hit.service.TaskAssignmentService;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class TaskAssignmentServiceTest {

    private IDao<Task> taskDaoFile;
    private IDao<TeamMember> memberDaoFile;
    private IDao<Assignment> assignmentDaoFile;

    private IDao<Task> taskDaoSQL;
    private IDao<TeamMember> memberDaoSQL;
    private IDao<Assignment> assignmentDaoSQL;

    @Before
    public void setup() throws Exception {
        // Create DAOs for both file and SQLite backends
        taskDaoFile = DaoFactory.create("file", Task.class);
        memberDaoFile = DaoFactory.create("file", TeamMember.class);
        assignmentDaoFile = DaoFactory.create("file", Assignment.class);

        taskDaoSQL = DaoFactory.create("sqlite", Task.class);
        memberDaoSQL = DaoFactory.create("sqlite", TeamMember.class);
        assignmentDaoSQL = DaoFactory.create("sqlite", Assignment.class);

        // Clear all data for a clean start
        taskDaoFile.deleteAll();
        memberDaoFile.deleteAll();
        assignmentDaoFile.deleteAll();
        taskDaoSQL.deleteAll();
        memberDaoSQL.deleteAll();
        assignmentDaoSQL.deleteAll();

        // Seed tasks and team members (same data for both backends)
        List<Task> sampleTasks = List.of(
                new Task("T1", "Build API", 4, 1, List.of("java")),
                new Task("T2", "Train Model", 5, 2, List.of("ml")),
                new Task("T3", "Data Query", 3, 3, List.of("sql"))
        );

        List<TeamMember> sampleMembers = List.of(
                new TeamMember("M1", "Alice", List.of("java", "sql"), 8, 1.0),
                new TeamMember("M2", "Bob", List.of("ml"), 6, 1.0),
                new TeamMember("M3", "Charlie", List.of("java", "ml"), 4, 1.0)
        );

        taskDaoFile.save(sampleTasks);
        memberDaoFile.save(sampleMembers);
        taskDaoSQL.save(sampleTasks);
        memberDaoSQL.save(sampleMembers);
    }

    @Test
    public void testFileBackendFlow() throws Exception {
        TaskAssignmentService service = new TaskAssignmentService(taskDaoFile, memberDaoFile, assignmentDaoFile);
        System.out.println("📂 Testing File Backend:");
        runFullFlow(service, assignmentDaoFile, taskDaoFile, memberDaoFile);
    }

    @Test
    public void testSQLiteBackendFlow() throws Exception {
        TaskAssignmentService service = new TaskAssignmentService(taskDaoSQL, memberDaoSQL, assignmentDaoSQL);
        System.out.println("🗃️ Testing SQLite Backend:");
        runFullFlow(service, assignmentDaoSQL, taskDaoSQL, memberDaoSQL);
    }

    /**
     * This method runs a full flow on the provided TaskAssignmentService and DAOs.
     * All reads are fresh from the DAO; nothing is cached.
     */
    private void runFullFlow(TaskAssignmentService service, IDao<Assignment> assignmentDao,
                             IDao<Task> taskDao, IDao<TeamMember> memberDao) throws Exception {
        // Assign all tasks using strategy (test both "greedy" and "balanced")
        boolean assigned = service.assignTasks("greedy");
        assertTrue("Assignment failed!", assigned);

        // Verify assignments exist
        List<Assignment> allAssignments = assignmentDao.load();
        assertEquals("All tasks should be assigned.", 3, allAssignments.size());
        assertTrue(allAssignments.stream().anyMatch(a -> a.getTaskId().equals("T1") && a.getMemberId() != null));

        // Get all tasks and members from DAO
        List<Task> allTasks = taskDao.load();
        assertEquals(3, allTasks.size());
        assertTrue(allTasks.stream().anyMatch(t -> t.getName().equals("Train Model")));

        List<TeamMember> allMembers = memberDao.load();
        assertEquals(3, allMembers.size());

        // Update a Task (change name) and verify update & that assignments are recalculated
        Task toUpdate = allTasks.get(0);
        toUpdate.setName("API Builder");
        boolean updated = service.updateTask(toUpdate.getId(), toUpdate, "greedy");
        assertTrue(updated);

        Task updatedTask = service.getTaskById(toUpdate.getId());
        assertEquals("API Builder", updatedTask.getName());

        // Search by task name (should find at least one)
        List<Task> searchResults = service.searchTasksByName("API");
        assertFalse(searchResults.isEmpty());

        // Test statistics functions
        assertEquals(3, service.countTasks());
        assertEquals(3, service.countTeamMembers());

        // Assignment deletion test (and hour restoration)
        List<Assignment> beforeDelete = assignmentDao.load();
        Assignment toDelete = beforeDelete.get(0);

        // Store hours for test
        Task taskBefore = service.getTaskById(toDelete.getTaskId());
        TeamMember memberBefore = service.getTeamMemberById(toDelete.getMemberId());
        double taskHoursBefore = taskBefore.getRemainingHours();
        double memberHoursBefore = memberBefore.getRemainingHours();

        boolean deleted = service.deleteAssignment(toDelete.getTaskId(), toDelete.getMemberId());
        assertTrue("Assignment was not deleted!", deleted);

        List<Assignment> assignmentsAfterDelete = assignmentDao.load();
        assertEquals("One assignment should be deleted", 2, assignmentsAfterDelete.size());

        // Test that hours were restored (if model implements it)
        Task taskAfter = service.getTaskById(toDelete.getTaskId());
        TeamMember memberAfter = service.getTeamMemberById(toDelete.getMemberId());
        if (taskAfter != null && memberAfter != null) {
            assertEquals(taskHoursBefore + toDelete.getAssignedHours(), taskAfter.getRemainingHours(), 0.001);
            assertEquals(memberHoursBefore + toDelete.getAssignedHours(), memberAfter.getRemainingHours(), 0.001);
        }

        // Delete all assignments
        assignmentDao.deleteAll();
        assertTrue(assignmentDao.load().isEmpty());

        // Delete a team member and verify
        boolean memberDeleted = service.deleteTeamMember("M3");
        assertTrue(memberDeleted);
        assertEquals(2, service.getAllTeamMembers().size());

        // Delete a task and verify
        boolean taskDeleted = service.deleteTask("T2");
        assertTrue(taskDeleted);
        assertEquals(2, service.getAllTasks().size());

        // Cleanup
        assignmentDao.deleteAll();
        taskDao.deleteAll();
        memberDao.deleteAll();

        System.out.println("✅ Test passed for backend: " + assignmentDao.getClass().getSimpleName());
    }
}
