package hit.controller;

import Util.GsonFactory;
import com.google.gson.Gson;
import com.hit.controller.ApiResponse;
import com.hit.controller.AssignmentController;
import com.hit.dao.DaoFactory;
import com.hit.dao.IDao;
import com.hit.model.Assignment;
import com.hit.model.Task;
import com.hit.model.TeamMember;
import com.hit.server.Request;
import com.hit.service.TaskAssignmentService;
import org.junit.*;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Integration tests for AssignmentController and the persistence layer.
 * Checks all assignment CRUD, validation, and edge-case flows.
 */
public class AssignmentControllerTest {

    private AssignmentController assignmentController;
    private TaskAssignmentService service;
    private IDao<Task> taskDao;
    private IDao<TeamMember> memberDao;
    private IDao<Assignment> assignmentDao;
    private static final Gson gson = GsonFactory.get();

    @Before
    public void setUp() throws Exception {
        // Use "sqlite" or "file"
        taskDao = DaoFactory.create("sqlite", Task.class);
        memberDao = DaoFactory.create("sqlite", TeamMember.class);
        assignmentDao = DaoFactory.create("sqlite", Assignment.class);
        taskDao.deleteAll();
        memberDao.deleteAll();
        assignmentDao.deleteAll();

        service = new TaskAssignmentService(taskDao, memberDao, assignmentDao);
        assignmentController = new AssignmentController(service);
    }

    @After
    public void tearDown() throws Exception {
        assignmentDao.deleteAll();
        taskDao.deleteAll();
        memberDao.deleteAll();
    }

   //  --- Helpers for creating Task & Member for assignments ---

    private Task createTask(String name) throws Exception {
        Task t = new Task(null, name, 3, 1, List.of("java"));
        return service.createNewTask(t);
    }

    private TeamMember createMember(String name) throws Exception {
        TeamMember m = new TeamMember(null, name, List.of("java"), 8, 1.0);
        return service.createNewTeamMember(m);
    }

    // Helper for bulk assignment
    private ApiResponse<Boolean> assignAll(String strategy) {
        Map<String, Object> body = new HashMap<>();
        body.put("strategy", strategy);
        return assignmentController.assignTasks(new Request(new HashMap<>(), body));
    }

    // Helper for assignment to team member
    private ApiResponse<Boolean> assignToMember(String memberId, String strategy) {
        Map<String, Object> body = new HashMap<>();
        body.put("memberId", memberId);
        body.put("strategy", strategy);
        return assignmentController.assignTasksToTeamMember(new Request(new HashMap<>(), body));
    }

    // --- TESTS ---

    @Test
    public void testAssignAllTasksSuccess() throws Exception {
        Task t = createTask("A1");
        TeamMember m = createMember("B1");

        // Bulk assignment using "greedy"
        ApiResponse<Boolean> resp = assignAll("greedy");
        assertTrue(resp.isSuccess());
        assertTrue(resp.getData());

        // Assignment should exist
        ApiResponse<List<Assignment>> allAssignments = assignmentController.getAllAssignments(new Request());
        assertEquals(1, allAssignments.getData().size());
        Assignment a = allAssignments.getData().getFirst();
        assertEquals(t.getId(), a.getTaskId());
        assertEquals(m.getId(), a.getMemberId());
    }

    @Test
    public void testAssignTasksToTeamMemberSuccess() throws Exception {
        Task t1 = createTask("T1");
        Task t2 = createTask("T2");
        TeamMember m1 = createMember("M1");

        // Assign just for M1
        ApiResponse<Boolean> resp = assignToMember(m1.getId(), "greedy");
        assertTrue(resp.isSuccess());
        assertTrue(resp.getData());

        ApiResponse<List<Assignment>> allAssignments = assignmentController.getAllAssignments(new Request());
        assertEquals(2, allAssignments.getData().size());
        // Both assignments should be to M1
        assertTrue(allAssignments.getData().stream().allMatch(a -> m1.getId().equals(a.getMemberId())));
    }

    @Test
    public void testAssignMissingStrategyDefaultsToGreedy() throws Exception {
        Task t = createTask("X");
        TeamMember m = createMember("Y");

        // Don't supply strategy
        ApiResponse<Boolean> resp = assignAll(null);
        assertTrue(resp.isSuccess());
        assertTrue(resp.getData());
    }

    @Test
    public void testDeleteAssignmentSuccess() throws Exception {
        Task t = createTask("T");
        TeamMember m = createMember("M");
        assignAll("greedy");

        // Find the assignment
        ApiResponse<List<Assignment>> all = assignmentController.getAllAssignments(new Request());
        Assignment found = all.getData().stream()
                .filter(a -> a.getTaskId().equals(t.getId()) && a.getMemberId().equals(m.getId()))
                .findFirst().orElseThrow();

        Request delReq = new Request(Map.of("action", "assignment/delete"),
                Map.of("taskId", t.getId(), "memberId", m.getId()));
        ApiResponse<Boolean> resp = assignmentController.deleteAssignment(delReq);
        assertTrue(resp.isSuccess());
        assertTrue(resp.getData());
    }

    @Test
    public void testDeleteAssignmentNotFound() throws Exception {
        Request delReq = new Request(Map.of("action", "assignment/delete"),
                Map.of("taskId", "fakeT", "memberId", "fakeM"));
        ApiResponse<Boolean> resp = assignmentController.deleteAssignment(delReq);
        assertFalse(resp.isSuccess());
    }

    @Test
    public void testGetAllAssignments() throws Exception {
        createTask("T1");
        createTask("T2");
        createMember("M1");
        createMember("M2");

        assignAll("greedy");

        ApiResponse<List<Assignment>> resp = assignmentController.getAllAssignments(new Request());
        assertTrue(resp.isSuccess());
        assertEquals(2, resp.getData().size());
    }

    @Test
    public void testGetAssignmentsForTeamMember() throws Exception {
        createTask("A");
        createTask("B");
        TeamMember m1 = createMember("ManX");
        assignToMember(m1.getId(), "greedy");

        Request req = new Request(Map.of("action", "assignment/forMember"), Map.of("memberId", m1.getId()));
        ApiResponse<List<Assignment>> resp = assignmentController.getAssignmentsForTeamMember(req);
        assertTrue(resp.isSuccess());
        assertEquals(2, resp.getData().size());
    }

    @Test
    public void testGetAssignmentsForNonexistentMember() throws Exception {
        Request req = new Request(Map.of("action", "assignment/forMember"), Map.of("memberId", "noSuchGuy"));
        ApiResponse<List<Assignment>> resp = assignmentController.getAssignmentsForTeamMember(req);
        assertTrue(resp.isSuccess());
        assertTrue(resp.getData().isEmpty());
    }
}
