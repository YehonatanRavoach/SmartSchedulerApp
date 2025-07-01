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
import java.util.*;

import static org.junit.Assert.*;

/**
 * Robust full business flow integration test: create, update, assign, delete.
 * Covers edge cases and validates system integrity after every operation.
 */
public class IntegrationFullFlowTest {
    private static Thread serverThread;
    private static final int PORT = 34568;
    private static Server server;
    private static final Gson gson = GsonFactory.get();

    @Before
    public void startServer() throws Exception {
        IDao<Task> taskDao = DaoFactory.create("sqlite", Task.class);
        IDao<TeamMember> memberDao = DaoFactory.create("sqlite", TeamMember.class);
        IDao<Assignment> assignmentDao = DaoFactory.create("sqlite", Assignment.class);

        taskDao.deleteAll();
        memberDao.deleteAll();
        assignmentDao.deleteAll();

        TaskAssignmentService service = new TaskAssignmentService(taskDao, memberDao, assignmentDao);
        server = new Server(PORT, 4, service);

        serverThread = new Thread(server);
        serverThread.start();
        Thread.sleep(800); // Let the server start
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
            writer.println(gson.toJson(req));
            String jsonResp = reader.readLine();
            return gson.fromJson(jsonResp, new TypeToken<ApiResponse<Object>>(){}.getType());
        }
    }

    @Test
    public void testFullBusinessFlowWithEdgeCases() throws Exception {
        // 1. Create a task with invalid data
        Map<String, Object> badTask = Map.of("name", "", "durationHours", 0, "priority", 50, "requiredSkills", List.of());
        ApiResponse<Object> badTaskResp = sendRequest(new Request(Map.of("action", "task/create"), badTask));
        assertFalse("Task with invalid fields should not be created", badTaskResp.isSuccess());

        // 2. Create valid tasks
        Map<String, Object> t1 = Map.of("name", "TaskA", "durationHours", 2, "priority", 1, "requiredSkills", List.of("java"));
        Map<String, Object> t2 = Map.of("name", "TaskB", "durationHours", 4, "priority", 2, "requiredSkills", List.of("python"));

        String taskIdA = ((Map<?, ?>) sendRequest(new Request(Map.of("action", "task/create"), t1)).getData()).get("id").toString();
        String taskIdB = ((Map<?, ?>) sendRequest(new Request(Map.of("action", "task/create"), t2)).getData()).get("id").toString();

        // 3. Create member with invalid data
        Map<String, Object> badMember = Map.of("name", "", "skills", List.of(""), "maxHoursPerDay", 0, "efficiency", 0.0);
        ApiResponse<Object> badMemberResp = sendRequest(new Request(Map.of("action", "member/create"), badMember));
        assertFalse("Member with invalid fields should not be created", badMemberResp.isSuccess());

        // 4. Create valid members
        Map<String, Object> m1 = Map.of("name", "Tom", "skills", List.of("java", "sql"), "maxHoursPerDay", 8, "efficiency", 1.0);
        Map<String, Object> m2 = Map.of("name", "Jerry", "skills", List.of("python"), "maxHoursPerDay", 6, "efficiency", 1.1);

        String memberId1 = ((Map<?, ?>) sendRequest(new Request(Map.of("action", "member/create"), m1)).getData()).get("id").toString();
        String memberId2 = ((Map<?, ?>) sendRequest(new Request(Map.of("action", "member/create"), m2)).getData()).get("id").toString();

        // 5. Prevent duplicate member by ID
        Map<String, Object> dupMember = new HashMap<>(m1);
        dupMember.put("id", memberId1);
        ApiResponse<Object> dupResp = sendRequest(new Request(Map.of("action", "member/create"), dupMember));
        assertFalse("Duplicate member creation by ID should fail", dupResp.isSuccess());

        // 6. Assign TaskA to Tom (success)
        ApiResponse<Object> assignResp = sendRequest(
                new Request(Map.of("action", "assignment/assignForMember"),
                        Map.of("taskId", taskIdA, "memberId", memberId1)));
        assertTrue("Assignment should succeed", assignResp.isSuccess());

        // 7. Assign TaskB to Jerry (success)
        ApiResponse<Object> assignResp2 = sendRequest(
                new Request(Map.of("action", "assignment/assignForMember"),
                        Map.of("taskId", taskIdB, "memberId", memberId2)));
        assertTrue("Assignment should succeed", assignResp2.isSuccess());


        // 8. Delete TaskA, then check assignment removed
        ApiResponse<Object> delTaskA = sendRequest(new Request(Map.of("action", "task/delete"), Map.of("id", taskIdA)));
        assertTrue("Deleting task should succeed", delTaskA.isSuccess());

        ApiResponse<Object> allAssignAfterDelete = sendRequest(new Request(Map.of("action", "assignment/getAll"), Map.of()));
        List<?> assigns = (List<?>) allAssignAfterDelete.getData();
        if (assigns != null) {
            for (Object a : assigns) {
                Map<?, ?> aMap = (Map<?, ?>) a;
                assertNotEquals("Assignment for deleted task should not exist", taskIdA, aMap.get("taskId"));
            }
        }

        // 9. Try to update member with invalid efficiency
        Map<String, Object> updateBody = new HashMap<>(m1);
        updateBody.put("id", memberId1);
        updateBody.put("efficiency", 10.0);
        ApiResponse<Object> updateBad = sendRequest(new Request(Map.of("action", "member/update"), updateBody));
        assertFalse("Invalid update should fail", updateBad.isSuccess());

        // 10. Valid update (change maxHoursPerDay)
        updateBody.put("efficiency", 1.0);
        updateBody.put("maxHoursPerDay", 10);
        ApiResponse<Object> updateGood = sendRequest(new Request(Map.of("action", "member/update"), updateBody));
        assertTrue("Valid update should succeed", updateGood.isSuccess());

        // 11. Delete non-existent member
        ApiResponse<Object> delFake = sendRequest(new Request(Map.of("action", "member/delete"), Map.of("id", "FAKE_ID")));
        assertFalse("Deleting non-existent member should fail", delFake.isSuccess());

        // 12. Delete real member and verify all assignments removed
        ApiResponse<Object> delM1 = sendRequest(new Request(Map.of("action", "member/delete"), Map.of("id", memberId1)));
        assertTrue(delM1.isSuccess());
        ApiResponse<Object> assignsAfterMemberDelete = sendRequest(new Request(Map.of("action", "assignment/getAll"), Map.of()));
        List<?> assigns2 = (List<?>) assignsAfterMemberDelete.getData();
        if (assigns2 != null) {
            for (Object a : assigns2) {
                Map<?, ?> aMap = (Map<?, ?>) a;
                assertNotEquals("Assignment for deleted member should not exist", memberId1, aMap.get("memberId"));
            }
        }
    }
}
