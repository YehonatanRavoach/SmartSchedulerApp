package hit.controller;

import Util.GsonFactory;
import com.google.gson.Gson;
import com.hit.controller.ApiResponse;
import com.hit.controller.TaskController;
import com.hit.dao.DaoFactory;
import com.hit.dao.IDao;
import com.hit.model.Assignment;
import com.hit.model.Task;
import com.hit.model.TeamMember;
import com.hit.service.TaskAssignmentService;
import org.junit.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tests for TaskController API logic.
 * Covers task CRUD, search, count, and assignment deletion.
 */
public class TaskControllerTest {

    private TaskController taskController;
    private TaskAssignmentService service;

    // DAOs for clean-up/checking
    private IDao<Task> taskDao;
    private IDao<TeamMember> memberDao;
    private IDao<Assignment> assignmentDao;

    private static final Gson gson = GsonFactory.get();

    @Before
    public void setUp() throws Exception {
        // Use SQLite backend (or swap to "file" for file-based)
        taskDao = DaoFactory.create("sqlite", Task.class);
        memberDao = DaoFactory.create("sqlite", TeamMember.class);
        assignmentDao = DaoFactory.create("sqlite", Assignment.class);

        taskDao.deleteAll();
        memberDao.deleteAll();
        assignmentDao.deleteAll();

        service = new TaskAssignmentService(taskDao, memberDao, assignmentDao);
        taskController = new TaskController(service);
    }

    @After
    public void tearDown() throws Exception {
        if (taskDao != null) taskDao.deleteAll();
        if (memberDao != null) memberDao.deleteAll();
        if (assignmentDao != null) assignmentDao.deleteAll();
    }


    /**
     * Helper: Create a basic request for Task.
     */
    private com.hit.server.Request makeTaskRequest(Map<String, Object> body) {
        Map<String, String> headers = Map.of("action", "task/create");
        return new com.hit.server.Request(headers, body);
    }

    @Test
    public void testCreateTaskSuccess() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Test Task");
        body.put("durationHours", 4);
        body.put("priority", 2);
        body.put("requiredSkills", List.of("java", "sql"));

        ApiResponse<Task> resp = taskController.createNewTask(makeTaskRequest(body));
        assertTrue(resp.isSuccess());
        assertNotNull(resp.getData());
        assertEquals("Test Task", resp.getData().getName());
        assertNotNull(resp.getData().getId());
    }

    @Test
    public void testCreateTaskMissingFields() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("durationHours", 4); // No name
        ApiResponse<Task> resp = taskController.createNewTask(makeTaskRequest(body));
        assertFalse(resp.isSuccess());
        assertTrue(resp.getMessage().toLowerCase().contains("name"));
    }

    @Test
    public void testGetAllTasks() throws Exception {
        // Add two tasks
        for (int i = 1; i <= 2; i++) {
            Map<String, Object> body = new HashMap<>();
            body.put("name", "Task" + i);
            body.put("durationHours", i + 1);
            body.put("priority", i);
            body.put("requiredSkills", List.of("java"));
            taskController.createNewTask(makeTaskRequest(body));
        }
        ApiResponse<List<Task>> resp = taskController.getAllTasks(new com.hit.server.Request());
        assertTrue(resp.isSuccess());
        assertEquals(2, resp.getData().size());
    }

    @Test
    public void testGetTaskByIdSuccess() throws Exception {
        // Create a task
        Map<String, Object> body = new HashMap<>();
        body.put("name", "FindMe");
        body.put("durationHours", 2);
        body.put("priority", 1);
        body.put("requiredSkills", List.of("sql"));
        ApiResponse<Task> createResp = taskController.createNewTask(makeTaskRequest(body));
        String id = createResp.getData().getId();

        Task found = service.getTaskById(id);
        assertNotNull(found);
        assertEquals("FindMe", found.getName());
    }

    @Test
    public void testGetTaskByIdNotFound() throws Exception {
        Task found = service.getTaskById("notexist");
        assertNull(found);
    }

    @Test
    public void testUpdateTaskSuccess() throws Exception {
        // Create
        Map<String, Object> body = new HashMap<>();
        body.put("name", "ToUpdate");
        body.put("durationHours", 1);
        body.put("priority", 1);
        body.put("requiredSkills", List.of("java"));
        ApiResponse<Task> createResp = taskController.createNewTask(makeTaskRequest(body));
        String id = createResp.getData().getId();

        // Update
        body.put("id", id);
        body.put("name", "UpdatedName");
        com.hit.server.Request updateReq = new com.hit.server.Request(Map.of("action", "task/update"), body);
        ApiResponse<Boolean> updateResp = taskController.updateTask(updateReq);
        assertTrue(updateResp.isSuccess());
        assertTrue(updateResp.getData());

        // Confirm
        Task found = service.getTaskById(id);
        assertEquals("UpdatedName", found.getName());
    }

    @Test
    public void testUpdateTaskNotFound() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("id", "not-exist");
        body.put("name", "ShouldNotWork");
        com.hit.server.Request updateReq = new com.hit.server.Request(Map.of("action", "task/update"), body);
        ApiResponse<Boolean> updateResp = taskController.updateTask(updateReq);
        assertFalse(updateResp.isSuccess());
    }

    @Test
    public void testDeleteTaskSuccess() throws Exception {
        // Create
        Map<String, Object> body = new HashMap<>();
        body.put("name", "ToDelete");
        body.put("durationHours", 1);
        body.put("priority", 1);
        body.put("requiredSkills", List.of("sql"));
        ApiResponse<Task> createResp = taskController.createNewTask(makeTaskRequest(body));
        String id = createResp.getData().getId();

        // Delete
        com.hit.server.Request deleteReq = new com.hit.server.Request(Map.of("action", "task/delete"), Map.of("id", id));
        ApiResponse<Boolean> delResp = taskController.deleteTask(deleteReq);
        assertTrue(delResp.isSuccess());
        assertTrue(delResp.getData());

        // Confirm
        assertNull(service.getTaskById(id));
    }

    @Test
    public void testDeleteTaskNotFound() throws Exception {
        com.hit.server.Request deleteReq = new com.hit.server.Request(Map.of("action", "task/delete"), Map.of("id", "not-exist"));
        ApiResponse<Boolean> delResp = taskController.deleteTask(deleteReq);
        assertFalse(delResp.isSuccess());
    }

    @Test
    public void testSearchTasksByName() throws Exception {
        // Create tasks
        Map<String, Object> body1 = new HashMap<>();
        body1.put("name", "Alpha");
        body1.put("durationHours", 2);
        body1.put("priority", 1);
        body1.put("requiredSkills", List.of("java"));
        taskController.createNewTask(makeTaskRequest(body1));

        Map<String, Object> body2 = new HashMap<>();
        body2.put("name", "BetaAlpha");
        body2.put("durationHours", 3);
        body2.put("priority", 2);
        body2.put("requiredSkills", List.of("java"));
        taskController.createNewTask(makeTaskRequest(body2));

        com.hit.server.Request searchReq = new com.hit.server.Request(Map.of("action", "task/search"), Map.of("name", "alpha"));
        ApiResponse<List<Task>> searchResp = taskController.searchTasksByName(searchReq);
        assertTrue(searchResp.isSuccess());
        assertEquals(2, searchResp.getData().size());
    }

    @Test
    public void testCountTasks() throws Exception {
        // Ensure DB is empty
        taskDao.deleteAll();
        // Add three tasks
        for (int i = 1; i <= 3; i++) {
            Map<String, Object> body = new HashMap<>();
            body.put("name", "Task_" + i);
            body.put("durationHours", i);
            body.put("priority", i);
            body.put("requiredSkills", List.of("java"));
            taskController.createNewTask(makeTaskRequest(body));
        }
        ApiResponse<Integer> resp = taskController.countTasks(new com.hit.server.Request());
        assertTrue(resp.isSuccess());
        assertEquals(Integer.valueOf(3), resp.getData());
    }

    @Test
    public void testCountUnassignedTasks() throws Exception {
        // For this test, no assignments so all tasks are unassigned
        taskDao.deleteAll();
        for (int i = 1; i <= 2; i++) {
            Map<String, Object> body = new HashMap<>();
            body.put("name", "Unassigned" + i);
            body.put("durationHours", i);
            body.put("priority", i);
            body.put("requiredSkills", List.of("sql"));
            taskController.createNewTask(makeTaskRequest(body));
        }
        ApiResponse<Integer> resp = taskController.countUnassignedTasks(new com.hit.server.Request());
        assertTrue(resp.isSuccess());
        assertEquals(Integer.valueOf(2), resp.getData());
    }
}
