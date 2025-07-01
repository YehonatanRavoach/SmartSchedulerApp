package hit.controller;

import Util.GsonFactory;
import com.google.gson.Gson;
import com.hit.controller.ApiResponse;
import com.hit.controller.TeamMemberController;
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
 * Integration tests for TeamMemberController logic and DAO layer.
 * Checks all CRUD, validation, and statistics flows.
 */
public class TeamMemberControllerTest {

    private TeamMemberController teamMemberController;
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
        teamMemberController = new TeamMemberController(service);
    }

    @After
    public void tearDown() throws Exception {
        if (taskDao != null) taskDao.deleteAll();
        if (memberDao != null) memberDao.deleteAll();
        if (assignmentDao != null) assignmentDao.deleteAll();
    }


    /**
     * Helper: build request for creating or updating a member.
     */
    private com.hit.server.Request makeMemberRequest(Map<String, Object> body) {
        Map<String, String> headers = Map.of("action", "member/create");
        return new com.hit.server.Request(headers, body);
    }

    @Test
    public void testCreateMemberSuccess() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Alice");
        body.put("skills", List.of("java", "sql"));
        body.put("maxHoursPerDay", 8);
        body.put("efficiency", 1.0);

        ApiResponse<TeamMember> resp = teamMemberController.createNewTeamMember(makeMemberRequest(body));
        assertTrue(resp.isSuccess());
        assertNotNull(resp.getData());
        assertEquals("Alice", resp.getData().getName());
        assertNotNull(resp.getData().getId());
    }

    @Test
    public void testCreateMemberMissingName() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("skills", List.of("sql"));
        body.put("maxHoursPerDay", 6);
        body.put("efficiency", 0.8);

        ApiResponse<TeamMember> resp = teamMemberController.createNewTeamMember(makeMemberRequest(body));
        assertFalse(resp.isSuccess());
        assertTrue(resp.getMessage().toLowerCase().contains("name"));
    }

    @Test
    public void testGetAllMembers() throws Exception {
        for (int i = 1; i <= 2; i++) {
            Map<String, Object> body = new HashMap<>();
            body.put("name", "User" + i);
            body.put("skills", List.of("java"));
            body.put("maxHoursPerDay", 4 + i);
            body.put("efficiency", 1.0);
            teamMemberController.createNewTeamMember(makeMemberRequest(body));
        }
        ApiResponse<List<TeamMember>> resp = teamMemberController.getAllTeamMembers(new com.hit.server.Request());
        assertTrue(resp.isSuccess());
        assertEquals(2, resp.getData().size());
    }

    @Test
    public void testUpdateMemberSuccess() throws Exception {
        // Create member
        Map<String, Object> body = new HashMap<>();
        body.put("name", "ToUpdate");
        body.put("skills", List.of("python"));
        body.put("maxHoursPerDay", 5);
        body.put("efficiency", 1.0);
        ApiResponse<TeamMember> createResp = teamMemberController.createNewTeamMember(makeMemberRequest(body));
        String id = createResp.getData().getId();

        // Update
        body.put("id", id);
        body.put("name", "UpdatedName");
        com.hit.server.Request updateReq = new com.hit.server.Request(Map.of("action", "member/update"), body);
        ApiResponse<Boolean> updateResp = teamMemberController.updateTeamMember(updateReq);
        assertTrue(updateResp.isSuccess());
        assertTrue(updateResp.getData());

        // Confirm update
        TeamMember updated = service.getTeamMemberById(id);
        assertEquals("UpdatedName", updated.getName());
    }

    @Test
    public void testUpdateMemberNotFound() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("id", "notexist");
        body.put("name", "Ghost");
        com.hit.server.Request updateReq = new com.hit.server.Request(Map.of("action", "member/update"), body);
        ApiResponse<Boolean> resp = teamMemberController.updateTeamMember(updateReq);
        assertFalse(resp.isSuccess());
    }

    @Test
    public void testDeleteMemberSuccess() throws Exception {
        // Create member
        Map<String, Object> body = new HashMap<>();
        body.put("name", "ToDelete");
        body.put("skills", List.of("java"));
        body.put("maxHoursPerDay", 4);
        body.put("efficiency", 1.0);
        ApiResponse<TeamMember> createResp = teamMemberController.createNewTeamMember(makeMemberRequest(body));
        String id = createResp.getData().getId();

        // Delete
        com.hit.server.Request deleteReq = new com.hit.server.Request(Map.of("action", "member/delete"), Map.of("id", id));
        ApiResponse<Boolean> delResp = teamMemberController.deleteTeamMember(deleteReq);
        assertTrue(delResp.isSuccess());
        assertTrue(delResp.getData());

        // Confirm delete
        assertNull(service.getTeamMemberById(id));
    }

    @Test
    public void testDeleteMemberNotFound() throws Exception {
        com.hit.server.Request deleteReq = new com.hit.server.Request(Map.of("action", "member/delete"), Map.of("id", "notexist"));
        ApiResponse<Boolean> resp = teamMemberController.deleteTeamMember(deleteReq);
        assertFalse(resp.isSuccess());
    }

    @Test
    public void testSearchMembersByName() throws Exception {
        // Add members
        Map<String, Object> body1 = new HashMap<>();
        body1.put("name", "AlphaMan");
        body1.put("skills", List.of("sql"));
        body1.put("maxHoursPerDay", 5);
        body1.put("efficiency", 1.0);
        teamMemberController.createNewTeamMember(makeMemberRequest(body1));

        Map<String, Object> body2 = new HashMap<>();
        body2.put("name", "BetaAlpha");
        body2.put("skills", List.of("java"));
        body2.put("maxHoursPerDay", 6);
        body2.put("efficiency", 1.0);
        teamMemberController.createNewTeamMember(makeMemberRequest(body2));

        com.hit.server.Request searchReq = new com.hit.server.Request(Map.of("action", "member/search"), Map.of("name", "alpha"));
        ApiResponse<List<TeamMember>> resp = teamMemberController.searchTeamMembersByName(searchReq);
        assertTrue(resp.isSuccess());
        assertEquals(2, resp.getData().size());
    }

    @Test
    public void testCountMembers() throws Exception {
        memberDao.deleteAll();
        for (int i = 1; i <= 3; i++) {
            Map<String, Object> body = new HashMap<>();
            body.put("name", "M" + i);
            body.put("skills", List.of("java"));
            body.put("maxHoursPerDay", 3 + i);
            body.put("efficiency", 1.0);
            teamMemberController.createNewTeamMember(makeMemberRequest(body));
        }
        ApiResponse<Integer> resp = teamMemberController.countTeamMembers(new com.hit.server.Request());
        assertTrue(resp.isSuccess());
        assertEquals(Integer.valueOf(3), resp.getData());
    }

    @Test
    public void testAverageLoadWhenNoMembers() throws Exception {
        memberDao.deleteAll();
        ApiResponse<Double> resp = teamMemberController.averageLoad(new com.hit.server.Request());
        assertTrue(resp.isSuccess());
        assertEquals(Double.valueOf(0.0), resp.getData());
    }
}
