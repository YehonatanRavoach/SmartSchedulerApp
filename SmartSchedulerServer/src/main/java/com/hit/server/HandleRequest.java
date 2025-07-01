package com.hit.server;

import Util.GsonFactory;
import com.google.gson.Gson;
import com.hit.controller.ApiResponse;

import java.io.*;
import java.net.Socket;
import java.util.Map;
import java.util.Scanner;
import java.util.function.Function;

/**
 * Handles a single client request: reads JSON from socket, dispatches to the correct API handler,
 * and sends an ApiResponse as JSON. Intended for use in a thread pool.
 * Always returns a valid JSON response (error or success).
 */
public class HandleRequest implements Runnable {
    private final Socket socket;
    private final Map<String, Function<Request, ApiResponse<?>>> actionMap;
    private static final int REQUEST_TIMEOUT = 10_000; // ms
    private static final Gson gson = GsonFactory.get();

    /**
     * Constructs a handler for one client connection.
     * @param socket    The connected client socket.
     * @param actionMap Map of action string → handler function.
     */
    public HandleRequest(Socket socket, Map<String, Function<Request, ApiResponse<?>>> actionMap) {
        this.socket = socket;
        this.actionMap = actionMap;
    }

    /**
     * Main logic for handling a single client request.
     * <ul>
     *     <li>Reads one line (JSON) from a client.</li>
     *     <li>Parses to {@link Request} object (if possible).</li>
     *     <li>Dispatches to correct handler in {@code actionMap}.</li>
     *     <li>Sends {@link ApiResponse} (always as JSON) to a client.</li>
     *     <li>Handles all errors gracefully, always responds.</li>
     * </ul>
     */
    @Override
    public void run() {
        String clientAddress = socket.getRemoteSocketAddress().toString();
        System.out.println("[Thread: " + Thread.currentThread().getName() + "] Connected: " + clientAddress);

        try (
                Scanner reader = new Scanner(new InputStreamReader(socket.getInputStream()));
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true)
        ) {
            long startTime = System.currentTimeMillis();

            if (reader.hasNextLine()) {
                String json = reader.nextLine();
                try {
                    Request req = gson.fromJson(json, Request.class);

                    String action = (req.getHeaders() != null) ? req.getHeaders().get("action") : null;
                    ApiResponse<?> resp;
                    if (action != null && actionMap.containsKey(action)) {
                        try {
                            resp = actionMap.get(action).apply(req);
                        } catch (Exception ex) {
                            resp = ApiResponse.error("Internal error: " + ex.getMessage());
                        }
                    } else {
                        resp = ApiResponse.error("Unknown or missing action: " + action);
                    }
                    writer.println(gson.toJson(resp));
                } catch (Exception parseEx) {
                    // Handles malformed JSON: respond with error as JSON!
                    ApiResponse<?> errorResp = ApiResponse.error("Invalid JSON: " + parseEx.getMessage());
                    writer.println(gson.toJson(errorResp));
                }
            } else {
                writer.println(gson.toJson(ApiResponse.error("No request received.")));
            }

            long duration = System.currentTimeMillis() - startTime;
            if (duration > REQUEST_TIMEOUT) {
                System.err.println("Request from " + clientAddress + " exceeded timeout (" + duration + "ms)");
            }
        } catch (Exception e) {
            // Fatal error (very rare), tries to notify a client if possible
            try {
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
                ApiResponse<?> errorResp = ApiResponse.error("Fatal server error: " + e.getMessage());
                writer.println(gson.toJson(errorResp));
            } catch (Exception ignored) {}
            System.err.println("Error handling client " + clientAddress + ": " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {}
            System.out.println("[Thread: " + Thread.currentThread().getName() + "] Disconnected: " + clientAddress);
        }
    }
}
