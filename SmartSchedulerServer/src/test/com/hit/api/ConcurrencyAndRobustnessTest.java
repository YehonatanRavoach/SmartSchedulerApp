package hit.api;

import Util.GsonFactory;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.hit.controller.ApiResponse;
import com.hit.server.Request;
import org.junit.*;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * Tests for concurrent and robust usage of the system under load.
 * Uses multiple threads to verify correct, robust, and race-condition-free behavior.
 */
@SuppressWarnings("ResultOfMethodCallIgnored")
public class ConcurrencyAndRobustnessTest {

    private static final int PORT = 34567;
    private static final Gson gson = GsonFactory.get();

    // Util: Sends a single API request (blocks)
    private static ApiResponse<Object> sendRequest(Request req) throws Exception {
        try (Socket socket = new Socket("localhost", PORT);
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            writer.println(gson.toJson(req));
            String jsonResp = reader.readLine();
            return gson.fromJson(jsonResp, new TypeToken<ApiResponse<Object>>(){}.getType());
        }
    }

    @Test
    public void testConcurrentTaskCreation() throws Exception {
        int numThreads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);

        List<Future<ApiResponse<Object>>> futures = new ArrayList<>();
        for (int i = 0; i < numThreads; i++) {
            int idx = i;
            futures.add(pool.submit(() -> {
                try {
                    Map<String, String> headers = Map.of("action", "task/create");
                    Map<String, Object> body = new HashMap<>();
                    body.put("name", "ConcurrentTask_" + idx);
                    body.put("durationHours", 1 + idx % 5);
                    body.put("priority", (idx % 3) + 1); // must be 1..MAX_PRIORITY
                    body.put("requiredSkills", List.of("java"));
                    return sendRequest(new Request(headers, body));
                } finally {
                    latch.countDown();
                }
            }));
        }
        latch.await(10, TimeUnit.SECONDS);
        pool.shutdown();

        int successCount = 0;
        for (Future<ApiResponse<Object>> fut : futures) {
            ApiResponse<Object> resp = fut.get();
            assertNotNull(resp);
            if (resp.isSuccess()) successCount++;
        }
        assertEquals(numThreads, successCount);
    }

    @Test
    public void testConcurrentAssignmentsBulk() throws Exception {
        // Create N tasks and N members, then assign all using "assignment/assignAll" concurrently
        int numTasks = 10;
        int numMembers = 10;

        // Create tasks
        AtomicReference<List<String>> taskIds = new AtomicReference<>(new ArrayList<>());
        for (int i = 0; i < numTasks; i++) {
            Map<String, Object> body = Map.of(
                    "name", "BulkAssignTask_" + i,
                    "durationHours", 2,
                    "priority", 1,
                    "requiredSkills", List.of("java")
            );
            ApiResponse<Object> resp = sendRequest(new Request(Map.of("action", "task/create"), body));
            assertTrue(resp.isSuccess());
            taskIds.get().add(((Map<?, ?>) resp.getData()).get("id").toString());
        }

        // Create members
        AtomicReference<List<String>> memberIds = new AtomicReference<>(new ArrayList<>());
        for (int i = 0; i < numMembers; i++) {
            Map<String, Object> mbody = Map.of(
                    "name", "BulkMember_" + i,
                    "skills", List.of("java"),
                    "maxHoursPerDay", 8,
                    "efficiency", 1.0
            );
            ApiResponse<Object> mResp = sendRequest(new Request(Map.of("action", "member/create"), mbody));
            assertTrue(mResp.isSuccess());
            memberIds.get().add(((Map<?, ?>) mResp.getData()).get("id").toString());
        }

        // Run bulk assignment concurrently several times (simulate "race" on strategy selection)
        int numAssignments = 5;
        ExecutorService pool = Executors.newFixedThreadPool(numAssignments);
        CountDownLatch latch = new CountDownLatch(numAssignments);
        List<Future<ApiResponse<Object>>> futures = new ArrayList<>();
        for (int i = 0; i < numAssignments; i++) {
            String strategy = (i % 2 == 0) ? "greedy" : "balanced";
            futures.add(pool.submit(() -> {
                try {
                    Request req = new Request(
                            Map.of("action", "assignment/assignAll"),
                            Map.of("strategy", strategy)
                    );
                    return sendRequest(req);
                } finally {
                    latch.countDown();
                }
            }));
        }
        //noinspection ResultOfMethodCallIgnored
        latch.await(10, TimeUnit.SECONDS);
        pool.shutdown();

        int success = 0;
        for (Future<ApiResponse<Object>> fut : futures) {
            ApiResponse<Object> resp = fut.get();
            assertNotNull(resp);
            assertTrue(resp.getMessage(), resp.isSuccess());
            if (resp.isSuccess()) success++;
        }
        assertEquals(numAssignments, success);
    }

    @Test
   public void testConcurrentAssignmentsForSingleMember() throws Exception {
       Map<String, Object> tBody = Map.of(
                "name", "MemberAssignTask",
                "durationHours", 4,
                "priority", 1,
                "requiredSkills", List.of("java")
        );
        ApiResponse<Object> tResp = sendRequest(new Request(Map.of("action", "task/create"), tBody));
        assertTrue(tResp.isSuccess());
        String taskId = ((Map<?, ?>) tResp.getData()).get("id").toString();

        Map<String, Object> mBody = Map.of(
                "name", "SoloMember",
                "skills", List.of("java"),
                "maxHoursPerDay", 8,
                "efficiency", 1.0
        );
        ApiResponse<Object> mResp = sendRequest(new Request(Map.of("action", "member/create"), mBody));
        assertTrue(mResp.isSuccess());
        String memberId = ((Map<?, ?>) mResp.getData()).get("id").toString();

       int numThreads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        List<Future<ApiResponse<Object>>> futures = new ArrayList<>();
        for (int i = 0; i < numThreads; i++) {
            futures.add(pool.submit(() -> {
                try {
                    Request req = new Request(
                            Map.of("action", "assignment/assignForMember"),
                            Map.of("memberId", memberId, "strategy", "greedy")
                    );
                    return sendRequest(req);
                } finally {
                    latch.countDown();
                }
            }));
        }
        latch.await(10, TimeUnit.SECONDS);
        pool.shutdown();

        for (Future<ApiResponse<Object>> fut : futures) {
            ApiResponse<Object> resp = fut.get();
            assertNotNull(resp);
        }

       ApiResponse<Object> allAssignmentsResp = sendRequest(new Request(Map.of("action", "assignment/getAll"), Map.of()));
        assertTrue(allAssignmentsResp.isSuccess());
        List<?> assignments = (List<?>) allAssignmentsResp.getData();
        long countForMemberAndTask = assignments.stream()
                .filter(a -> memberId.equals(((Map<?, ?>) a).get("memberId")) && taskId.equals(((Map<?, ?>) a).get("taskId")))
                .count();

        assertTrue("At most one assignment per member-task expected.", countForMemberAndTask <= 1);
    }



    @Test
    public void testConcurrentDeletes() throws Exception {
        // Create many tasks and then delete them all concurrently
        int N = 10;
        List<String> taskIds = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            Map<String, Object> body = Map.of("name", "DelTask_" + i, "durationHours", 1, "priority", 1, "requiredSkills", List.of("java"));
            ApiResponse<Object> resp = sendRequest(new Request(Map.of("action", "task/create"), body));
            assertTrue(resp.isSuccess());
            taskIds.add(((Map<?, ?>) resp.getData()).get("id").toString());
        }

        ExecutorService pool = Executors.newFixedThreadPool(N);
        CountDownLatch latch = new CountDownLatch(N);
        List<Future<ApiResponse<Object>>> futures = new ArrayList<>();
        for (String id : taskIds) {
            futures.add(pool.submit(() -> {
                try {
                    Request req = new Request(Map.of("action", "task/delete"), Map.of("id", id));
                    return sendRequest(req);
                } finally {
                    latch.countDown();
                }
            }));
        }
        latch.await(10, TimeUnit.SECONDS);
        pool.shutdown();

        int success = 0;
        for (Future<ApiResponse<Object>> fut : futures) {
            ApiResponse<Object> resp = fut.get();
            assertNotNull(resp);
            if (resp.isSuccess()) success++;
        }
        assertEquals(N, success);
    }

    @Test
    public void testServerHandlesMultipleClients() throws Exception {
        int numClients = 15;
        ExecutorService pool = Executors.newFixedThreadPool(numClients);
        CountDownLatch latch = new CountDownLatch(numClients);

        List<Future<ApiResponse<Object>>> futures = new ArrayList<>();
        for (int i = 0; i < numClients; i++) {
            int idx = i;
            futures.add(pool.submit(() -> {
                try {
                    Map<String, Object> body = Map.of(
                            "name", "MultiClientTask_" + idx,
                            "durationHours", 2,
                            "priority", 2,
                            "requiredSkills", List.of("java")
                    );
                    Request req = new Request(Map.of("action", "task/create"), body);
                    return sendRequest(req);
                } finally {
                    latch.countDown();
                }
            }));
        }
        latch.await(10, TimeUnit.SECONDS);
        pool.shutdown();

        int created = 0;
        for (Future<ApiResponse<Object>> fut : futures) {
            ApiResponse<Object> resp = fut.get();
            assertNotNull(resp);
            if (resp.isSuccess()) created++;
        }
        assertEquals(numClients, created);
    }

    @Test
    public void testBulkLoadPerformance() throws Exception {
        // Create 100 tasks and measure elapsed time (should be < X seconds)
        int N = 100;
        long start = System.currentTimeMillis();
        for (int i = 0; i < N; i++) {
            Map<String, Object> body = Map.of("name", "PerfTask_" + i, "durationHours", 1, "priority", 1, "requiredSkills", List.of("sql"));
            ApiResponse<Object> resp = sendRequest(new Request(Map.of("action", "task/create"), body));
            assertTrue(resp.isSuccess());
        }
        long elapsed = System.currentTimeMillis() - start;
        System.out.println("Bulk load: " + N + " tasks in " + elapsed + "ms");
        assertTrue("Bulk load should complete quickly", elapsed < 6000); // 6 seconds is generous for local DB
    }
}
