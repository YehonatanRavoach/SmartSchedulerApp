package hit.api;

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

/**
 * Tests for API validation, error cases, and invalid input handling.
 */
public class ValidationAndErrorTest {
    private static Thread serverThread;
    private static final int PORT = 34567;
    private static Server server;
    private static final Gson gson = GsonFactory.get();

    @BeforeClass
    public static void startServer() throws Exception {
        IDao<Task> taskDao = DaoFactory.create("sqlite", Task.class);
        IDao<TeamMember> memberDao = DaoFactory.create("sqlite", TeamMember.class);
        IDao<Assignment> assignmentDao = DaoFactory.create("sqlite", Assignment.class);

        // Clean state
        taskDao.deleteAll();
        memberDao.deleteAll();
        assignmentDao.deleteAll();

        TaskAssignmentService service = new TaskAssignmentService(taskDao, memberDao, assignmentDao);
        server = new Server(PORT, 2, service);

        serverThread = new Thread(server);
        serverThread.start();
        Thread.sleep(1000); // Give server time to start
    }

    @AfterClass
    public static void stopServer() throws Exception {
        if (server != null) server.shutdown();
        if (serverThread != null) serverThread.join(2000);
    }

    /**
     * Helper to send a request and receive a response (one-shot, blocking).
     */
    private ApiResponse<Object> sendRequest(Request req) throws Exception {
        try (Socket socket = new Socket("localhost", PORT);
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String jsonReq = gson.toJson(req);
            writer.println(jsonReq);
            String jsonResp = reader.readLine();
            return gson.fromJson(jsonResp, new TypeToken<ApiResponse<Object>>(){}.getType());
        }
    }

    /**
     * Helper to send raw, invalid JSON (simulates a bad client).
     */
    private String sendRawJson(String rawJson) throws Exception {
        try (Socket socket = new Socket("localhost", PORT);
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            writer.println(rawJson);
            return reader.readLine();
        }
    }

    @Test
    public void testInvalidActionReturnsError() throws Exception {
        // Action does not exist
        Request req = new Request(Map.of("action", "not/a/real/action"), new HashMap<>());
        ApiResponse<Object> resp = sendRequest(req);
        assertFalse(resp.isSuccess());
        assertTrue(resp.getMessage().toLowerCase().contains("unknown"));
    }

    @Test
    public void testInvalidJsonFormatReturnsError() throws Exception {
        // Malformed JSON
        String badJson = "{ this is: [not, valid, json}";
        String response = sendRawJson(badJson);
        // Should not throw, should get back an error as JSON
        assertNotNull(response);
        ApiResponse<Object> resp = gson.fromJson(response, new TypeToken<ApiResponse<Object>>(){}.getType());
        assertFalse(resp.isSuccess());
        assertTrue(resp.getMessage().toLowerCase().contains("json"));
    }

    @Test
    public void testAssignmentWithMissingFieldsFails() throws Exception {
        // Missing memberId
        Map<String, Object> body = new HashMap<>();
        body.put("taskId", "t1");
        Request req = new Request(Map.of("action", "assignment/assign"), body);
        ApiResponse<Object> resp = sendRequest(req);
        assertFalse(resp.isSuccess());
        assertTrue(resp.getMessage().toLowerCase().contains("missing"));
    }

    @Test
    public void testTaskWithInvalidFieldTypesFails() throws Exception {
        // "priority" should be int, not string
        Map<String, Object> body = new HashMap<>();
        body.put("name", "TypeFail");
        body.put("durationHours", 2);
        body.put("priority", "shouldBeInt"); // invalid!
        body.put("requiredSkills", java.util.List.of("java"));
        Request req = new Request(Map.of("action", "task/create"), body);
        ApiResponse<Object> resp = sendRequest(req);
        assertFalse(resp.isSuccess());
    }

    @Test
    public void testMemberWithInvalidFieldTypesFails() throws Exception {
        // "efficiency" should be double, not string
        Map<String, Object> body = new HashMap<>();
        body.put("name", "BadType");
        body.put("skills", java.util.List.of("sql"));
        body.put("maxHoursPerDay", 6);
        body.put("efficiency", "shouldBeDouble"); // invalid!
        Request req = new Request(Map.of("action", "member/create"), body);
        ApiResponse<Object> resp = sendRequest(req);
        assertFalse(resp.isSuccess());
    }

    @Test
    public void testRequestWithoutHeadersFails() throws Exception {
        // Send request without headers (action missing)
        Request req = new Request(null, Map.of("name", "no-header"));
        ApiResponse<Object> resp = sendRequest(req);
        assertFalse(resp.isSuccess());
    }

    @Test
    public void testRequestWithoutBodyFails() throws Exception {
        // Send request with null body (should still fail gracefully)
        Request req = new Request(Map.of("action", "task/create"), null);
        ApiResponse<Object> resp = sendRequest(req);
        assertFalse(resp.isSuccess());
    }
}
