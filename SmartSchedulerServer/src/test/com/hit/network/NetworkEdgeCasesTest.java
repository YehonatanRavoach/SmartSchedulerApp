package hit.network;

import Util.GsonFactory;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.hit.controller.ApiResponse;
import com.hit.server.Request;
import org.junit.*;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.Map;
import java.util.concurrent.*;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

/**
 * Tests for network protocol edge cases and error scenarios.
 * Covers connection handling, malformed requests, timeouts,
 * concurrency, and protocol robustness.
 */
public class NetworkEdgeCasesTest {

    private static final int PORT = 34567;
    private static final Gson gson = GsonFactory.get();

    /**
     * Ensures the server is running and responsive before all tests.
     */
    @Before
    public void ensureServerUp() throws Exception {
        boolean up = false;
        for (int i = 0; i < 5 && !up; i++) {
            try (Socket _ = new Socket("localhost", PORT)) { up = true; }
            catch (IOException e) { Thread.sleep(400); }
        }
        if (!up) throw new IllegalStateException("Server is not up for edge-case tests.");
    }

    /**
     * Simulates abrupt client disconnection after sending partial data.
     * Server must not crash or hang.
     */
    @Test(timeout = 2000)
    public void testConnectionDropMidRequest() throws Exception {
        Socket s = new Socket("localhost", PORT);
        OutputStream out = s.getOutputStream();
        out.write("{\"headers\":{\"action\":\"task/create\"},\"body\":".getBytes());
        out.flush();
        s.close();
        Thread.sleep(100);
        ApiResponse<Object> resp = sendValidPingRequest();
        assertTrue(resp.isSuccess());
    }

    /**
     * Attempts to connect to a non-listening port and expects IOException.
     */
    @Test
    public void testInvalidPortConnectionFails() {
        int badPort = PORT + 1337;
        try (Socket ignored = new Socket("localhost", badPort)) {
            fail("Expected IOException was not thrown");
        } catch (IOException e) {
            // Expected, test passes
        }
    }


    /**
     * Sends a request with an extremely large body to verify server robustness.
     */
    @Test(timeout = 5000)
    public void testLargeRequestBody() throws Exception {
        String huge = "B".repeat(100_000);

        Request req = new Request(Map.of("action", "task/create"), Map.of(
                "name", huge,
                "durationHours", 1,
                "priority", 1,
                "requiredSkills", java.util.List.of("java")
        ));

        try {
            ApiResponse<Object> resp = sendRequest(req);
             assertNotNull("No response from server for large request", resp);
             assertFalse("Large request should fail", resp.isSuccess());
        } catch (SocketException se) {
            System.out.println("Socket closed by server as expected due to large request");
        }
    }


    /**
     * Simulates a slow client; server should timeout and respond gracefully.
     */
    @Test(timeout = 15_000)
    public void testServerTimeoutOnLongRequest() throws Exception {
        try (Socket s = new Socket("localhost", PORT)) {
            OutputStream out = s.getOutputStream();
            String partial = "{\"headers\":{\"action\":\"task/create\"},\"body\":{\"name\":\"slow\",\"durationHours\":1,";
            out.write(partial.getBytes());
            out.flush();
            Thread.sleep(12_000); // Exceeding server timeout
            out.write("\"priority\":1,\"requiredSkills\":[\"java\"]}}".getBytes());
            out.flush();

            BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream()));
            try {
                String response = reader.readLine();

                if (response == null) {
                    // Success: the server closed the socket due to timeout without response
                    System.out.println("Server closed connection due to timeout (no response, as expected)");
                } else {
                    // Got a response — should be a failure
                    ApiResponse<Object> resp = gson.fromJson(response, new TypeToken<ApiResponse<Object>>(){}.getType());
                    assertFalse(resp.isSuccess());
                }
            } catch (SocketException ex) {
                // This is also an expected outcome when the server closes the socket
                System.out.println("SocketException: Connection closed by server as expected (" + ex.getMessage() + ")");
            }
        }
    }



    /**
     * Sends multiple requests on the same connection. A server is expected
     * to handle only the first and close the connection after response.
     */
    @Test(timeout = 4000)
    public void testMultipleRequestsSameConnection() throws Exception {
        try (Socket s = new Socket("localhost", PORT)) {
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(s.getOutputStream()), true);
            BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream()));

            Request req1 = new Request(Map.of("action", "task/count"), Map.of());
            writer.println(gson.toJson(req1));
            String resp1 = reader.readLine();

            Request req2 = new Request(Map.of("action", "member/count"), Map.of());
            writer.println(gson.toJson(req2));
            try {
                String resp2 = reader.readLine();
                // Resp2 might be null (connection closed) or trigger exception.
                // Both are valid for a one-request-per-connection server.
                if (resp2 != null) {
                    System.out.println("Second response received (may indicate keep-alive): " + resp2);
                } else {
                    System.out.println("No second response — server closed connection as expected.");
                }
            } catch (SocketException ex) {
                System.out.println("SocketException on second request: connection closed by server (expected).");
            }
            assertNotNull(resp1);
        }
    }


    /**
     * Sends a malformed JSON payload; expects error response or no crash.
     */
    @Test(timeout = 2000)
    public void testMalformedJsonPayload() throws Exception {
        try (Socket s = new Socket("localhost", PORT)) {
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(s.getOutputStream()), true);
            BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream()));
            String badJson = "{\"headers\":{\"action\":\"task/create\"},\"body\":{name:UNQUOTED}}";
            writer.println(badJson);
            String resp = reader.readLine();
            assertNotNull(resp);
            ApiResponse<Object> apiResp = gson.fromJson(resp, new TypeToken<ApiResponse<Object>>(){}.getType());
            assertFalse(apiResp.isSuccess());
        }
    }

    /**
     * Sends a request with invalid header types (non-string keys/values).
     */
    @Test(timeout = 2000)
    public void testInvalidHeaderTypes() throws Exception {
        String badJson = "{\"headers\":{\"action\":100},\"body\":{\"name\":\"A\",\"durationHours\":1,\"priority\":1,\"requiredSkills\":[\"java\"]}}";
        try (Socket s = new Socket("localhost", PORT)) {
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(s.getOutputStream()), true);
            BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream()));
            writer.println(badJson);
            String resp = reader.readLine();
            assertNotNull(resp);
            ApiResponse<Object> apiResp = gson.fromJson(resp, new TypeToken<ApiResponse<Object>>(){}.getType());
            assertFalse(apiResp.isSuccess());
        }
    }

    /**
     * Floods the server with a burst of valid requests in parallel to test high-frequency handling.
     */
    @Test(timeout = 10_000)
    public void testHighFrequencyRequests() throws Exception {
        try (ExecutorService exec = Executors.newFixedThreadPool(10)) {
            int numRequests = 20;
            CountDownLatch latch = new CountDownLatch(numRequests);
            for (int i = 0; i < numRequests; i++) {
                exec.submit(() -> {
                    try {
                        Request req = new Request(Map.of("action", "task/count"), Map.of());
                        ApiResponse<Object> resp = sendRequest(req);
                        assertNotNull(resp);
                    } catch (Exception e) {
                        fail("Request failed: " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }
            boolean completed = latch.await(7, TimeUnit.SECONDS);
            assertTrue("Not all requests finished in time", completed);
            // ExecutorService is auto-shutdown by try-with-resources (Java 21+)
        }
    }


    /**
     * Sends a request with Unicode and unusual characters to check UTF-8 safety.
     */
    @Test(timeout = 3000)
    public void testRequestWithUnicodeAndEncoding() throws Exception {
        String emojiName = "Emoji_😀_שָׁלוֹם";
        Request req = new Request(Map.of("action", "task/create"), Map.of(
                "name", emojiName,
                "durationHours", 1,
                "priority", 1,
                "requiredSkills", java.util.List.of("java")
        ));
        ApiResponse<Object> resp = sendRequest(req);
        assertTrue(resp.getMessage().contains("name") || resp.isSuccess());
    }

    // ---------- Helpers ----------

    /**
     * Sends a basic "ping" request to ensure a server is alive.
     * @return ApiResponse
     */
    private ApiResponse<Object> sendValidPingRequest() throws Exception {
        Request req = new Request(Map.of("action", "task/count"), Map.of());
        return sendRequest(req);
    }

    /**
     * Sends a request and returns the ApiResponse parsed from the server.
     * @param req Request object
     * @return ApiResponse
     */
    private ApiResponse<Object> sendRequest(Request req) throws Exception {
        try (Socket socket = new Socket("localhost", PORT);
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            writer.println(gson.toJson(req));
            String jsonResp = reader.readLine();
            return gson.fromJson(jsonResp, new TypeToken<ApiResponse<Object>>(){}.getType());
        }
    }
}
