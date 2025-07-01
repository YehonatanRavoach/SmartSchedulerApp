package hit.network;

import Util.GsonFactory;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.hit.controller.ApiResponse;
import com.hit.dao.DaoFactory;
import com.hit.dao.IDao;
import com.hit.model.Assignment;
import com.hit.model.Task;
import com.hit.model.TeamMember;
import com.hit.server.Request;
import com.hit.server.Server;
import com.hit.service.TaskAssignmentService;
import org.junit.*;

import java.io.*;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class NetworkEndToEndTest {
    private static Thread serverThread;
    private static final int PORT = 34567;
    private static Server server;
    private static final Gson gson = GsonFactory.get();

    @BeforeClass
    public static void startServer() throws Exception {
        IDao<Task> taskDao = DaoFactory.create("sqlite", Task.class);
        IDao<TeamMember> memberDao = DaoFactory.create("sqlite", TeamMember.class);
        IDao<Assignment> assignmentDao = DaoFactory.create("sqlite", Assignment.class);

        taskDao.deleteAll();
        memberDao.deleteAll();
        assignmentDao.deleteAll();

        TaskAssignmentService service = new TaskAssignmentService(taskDao, memberDao, assignmentDao);
        server = new Server(PORT, 2, service);

        serverThread = new Thread(server);
        serverThread.start();
        Thread.sleep(1000);
    }

    @AfterClass
    public static void stopServer() throws Exception {
        if (server != null) server.shutdown();
        if (serverThread != null) serverThread.join(2000);
    }

    private ApiResponse<Object> sendRequest(Request req) throws Exception {
        try (Socket socket = new Socket("localhost", PORT);
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String jsonReq = gson.toJson(req);
            writer.println(jsonReq);
            String jsonResp = reader.readLine();
            return gson.fromJson(jsonResp, new TypeToken<ApiResponse<?>>(){}.getType());
        }
    }

    @Test
    public void testFullNetworkFlow() throws Exception {
        // 1. Create a valid task and member
        Map<String, Object> taskBody = new HashMap<>();
        taskBody.put("name", "NetTask2");
        taskBody.put("durationHours", 2);
        taskBody.put("priority", 1);
        taskBody.put("requiredSkills", java.util.List.of("java"));
        Request createTaskReq = new Request(Map.of("action", "task/create"), taskBody);
        ApiResponse<Object> createTaskResp = sendRequest(createTaskReq);
        assertTrue("Task creation failed", createTaskResp.isSuccess());

        Map<String, Object> memberBody = new HashMap<>();
        memberBody.put("name", "NetAlice");
        memberBody.put("skills", java.util.List.of("java"));
        memberBody.put("maxHoursPerDay", 3);
        memberBody.put("efficiency", 1.0);
        Request createMemberReq = new Request(Map.of("action", "member/create"), memberBody);
        ApiResponse<Object> createMemberResp = sendRequest(createMemberReq);
        assertTrue("Member creation failed", createMemberResp.isSuccess());

        String taskId = ((Map<?, ?>) createTaskResp.getData()).get("id").toString();
        String memberId = ((Map<?, ?>) createMemberResp.getData()).get("id").toString();

        // 3. Try duplicate member by name+skills (should succeed because only ID must be unique)
        // (if you require unique name+skills, check this logic)
        ApiResponse<Object> dupMemberResp = sendRequest(createMemberReq);
        assertTrue("Member with same name/skills but different ID is allowed", dupMemberResp.isSuccess());

        // 4. Assign tasks to all members (bulk)
        ApiResponse<Object> assignAllResp = sendRequest(
                new Request(Map.of("action", "assignment/assignAll"),
                        Map.of("strategy", "greedy")));
        assertTrue("Assign all should succeed", assignAllResp.isSuccess());

        // 5. Assign to specific member (should not throw idempotent)
        ApiResponse<Object> assignForMemberResp = sendRequest(
                new Request(Map.of("action", "assignment/assignForMember"),
                        Map.of("memberId", memberId, "strategy", "greedy")));
        // Succeeds if a member exists, even if no assignments made (will say so in a message)
        assertTrue(assignForMemberResp.isSuccess() || assignForMemberResp.getMessage().toLowerCase().contains("no assignments"));

        // 6. Try to assign to non-existent member (should fail)
        ApiResponse<Object> assignNonExist = sendRequest(
                new Request(Map.of("action", "assignment/assignForMember"),
                        Map.of("memberId", "M999999")));
        assertFalse("Assign for non-existent member should fail", assignNonExist.isSuccess());

        // 7. Try to create a member with missing name (should fail)
        Map<String, Object> badMember = new HashMap<>();
        badMember.put("skills", java.util.List.of("python"));
        badMember.put("maxHoursPerDay", 4);
        badMember.put("efficiency", 0.9);
        ApiResponse<Object> badMemberResp = sendRequest(
                new Request(Map.of("action", "member/create"), badMember));
        assertFalse("Should not allow member creation without name", badMemberResp.isSuccess());

        // 8. Try to create a task with blank name (should fail)
        Map<String, Object> badTask = new HashMap<>();
        badTask.put("name", "   ");
        badTask.put("durationHours", 1);
        badTask.put("priority", 1);
        badTask.put("requiredSkills", java.util.List.of("sql"));
        ApiResponse<Object> badTaskResp = sendRequest(
                new Request(Map.of("action", "task/create"), badTask));
        assertFalse("Should not allow task creation with blank name", badTaskResp.isSuccess());

        // 9. Update a non-existing task (should fail)
        Map<String, Object> updateBody = new HashMap<>();
        updateBody.put("id", "T99999");
        updateBody.put("name", "ShouldFail");
        updateBody.put("durationHours", 3);
        updateBody.put("priority", 2);
        updateBody.put("requiredSkills", java.util.List.of("java"));
        ApiResponse<Object> updateTaskResp = sendRequest(
                new Request(Map.of("action", "task/update"), updateBody));
        assertFalse("Update of non-existing task should fail", updateTaskResp.isSuccess());

        // 10. Delete already deleted member (should fail)
        ApiResponse<Object> delMemberResp = sendRequest(
                new Request(Map.of("action", "member/delete"), Map.of("id", memberId)));
        assertTrue("Delete member failed", delMemberResp.isSuccess());
        // Try again (should fail)
        ApiResponse<Object> delMemberAgainResp = sendRequest(
                new Request(Map.of("action", "member/delete"), Map.of("id", memberId)));
        assertFalse("Second deletion should fail", delMemberAgainResp.isSuccess());

        // 11. Try unknown action
        ApiResponse<Object> unknownResp = sendRequest(
                new Request(Map.of("action", "doesnotexist"), Map.of()));
        assertFalse("Unknown action should fail", unknownResp.isSuccess());
    }
}
