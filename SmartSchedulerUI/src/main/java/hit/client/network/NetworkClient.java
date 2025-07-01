package hit.client.network;

import com.google.gson.Gson;
import com.hit.controller.ApiResponse;
import com.hit.server.Request;
import hit.client.util.GsonFactory;
import javafx.application.Platform;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

/**
 * Central network client for all server communications.
 * Handles socket connection, (de)serialization, error management,
 * and runs all requests asynchronously in a thread pool.
 *
 * Usage:
 *   - For most use-cases: call NetworkClient.sendRequestAsync(request, (resp, err) -> {...});
 *   - For blocking (background worker, not UI): call NetworkClient.sendRequestSync(request);
 *   - No socket logic or Gson handling should appear in controllers!
 *
 * Supports automatic resource cleanup and one place for future extensions.
 */
public class NetworkClient {
    // ==== Connection Settings ====
    private static final String HOST = "localhost";   // Server host (could be configurable)
    private static final int PORT = 34567;            // Server port

    // ==== Thread Pool for Async Requests ====
    private static final ExecutorService pool =
            Executors.newFixedThreadPool(4, r -> {
                Thread t = new Thread(r, "NetworkClient-Worker");
                t.setDaemon(true);
                return t;
            });

    // ==== Singleton Gson Instance ====
    private static final Gson gson = GsonFactory.get();

    /**
     * Sends a request to the server synchronously (blocking).
     * Should be used ONLY from background threads (never from UI thread!).
     *
     * @param req The request to send
     * @return The server's ApiResponse (or null on network failure)
     * @throws IOException if socket connection fails
     */
    public static ApiResponse<?> sendRequestSync(Request req) throws IOException {
        try (Socket socket = new Socket(HOST, PORT);
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            writer.println(gson.toJson(req));
            String jsonResp = reader.readLine();
            // Note: using ApiResponse.class deserializes data as LinkedTreeMap. See generic method below for better typing.
            return gson.fromJson(jsonResp, ApiResponse.class);
        }
    }

    /**
     * Sends a request asynchronously in a background thread.
     * The callback receives (response, exception), always called on the **JavaFX Application Thread**.
     * Suitable for use from any controller/UI code.
     *
     * @param req      The request to send
     * @param callback A BiConsumer: (ApiResponse, Exception). If err != null, the response will be null.
     */
    public static void sendRequestAsync(Request req, BiConsumer<ApiResponse<?>, Throwable> callback) {
        pool.submit(() -> {
            ApiResponse<?> resp = null;
            Throwable error = null;
            try {
                resp = sendRequestSync(req);
            } catch (Throwable t) {
                error = t;
            }
            // Always call the callback on the JavaFX UI thread (safe for UI updates)
            ApiResponse<?> finalResp = resp;
            Throwable finalError = error;
            Platform.runLater(() -> callback.accept(finalResp, finalError));
        });
    }

    /**
     * Advanced: Send a request with type-safe result (avoids generic Map for data).
     * Usage:
     *   sendRequestAsyncTyped(request, new TypeToken<ApiResponse<List<Task>>>(){}.getType(), (resp, err) -> {...});
     */
    public static <T> void sendRequestAsyncTyped(Request req, java.lang.reflect.Type responseType, BiConsumer<ApiResponse<T>, Throwable> callback) {
        pool.submit(() -> {
            ApiResponse<T> resp = null;
            Throwable error = null;
            try (Socket socket = new Socket(HOST, PORT);
                 PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                writer.println(gson.toJson(req));
                String jsonResp = reader.readLine();
                resp = gson.fromJson(jsonResp, responseType);
            } catch (Throwable t) {
                error = t;
            }
            ApiResponse<T> finalResp = resp;
            Throwable finalError = error;
            Platform.runLater(() -> callback.accept(finalResp, finalError));
        });
    }

    /**
     * Properly shuts down the network client thread pool.
     * Call on application exit (optional, for clean shutdown).
     */
    public static void shutdown() {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
        }
    }

    // ==== Private constructor prevents instantiation ====
    private NetworkClient() {}
}
