package com.hit.server;

import com.hit.controller.*;
import com.hit.service.TaskAssignmentService;

import java.net.*;
import java.util.concurrent.*;
import java.util.Map;
import java.util.HashMap;
import java.util.function.Function;

/**
 * Multi-threaded server for handling API requests from clients.
 * Initializes controllers and a single actionMap, and passes them to HandleRequest via a thread pool.
 */
public class Server implements Runnable {
    private final int port;
    private final ExecutorService pool;
    private final TaskAssignmentService service;
    private final TaskController taskController;
    private final TeamMemberController teamMemberController;
    private final AssignmentController assignmentController;
    private final Map<String, Function<Request, ApiResponse<?>>> actionMap;
    private volatile boolean running = true;

    /**
     * Server constructor.
     * @param port      Port number to listen on.
     * @param poolSize  Maximum thread pool size.
     * @param service   Shared, thread-safe application service.
     */
    public Server(int port, int poolSize, TaskAssignmentService service) {
        this.port = port;
        this.pool = new ThreadPoolExecutor(
                poolSize, poolSize,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>()
        );
        this.service = service;
        this.taskController = new TaskController(service);
        this.teamMemberController = new TeamMemberController(service);
        this.assignmentController = new AssignmentController(service);
        this.actionMap = buildActionMap();
    }

    /**
     * Builds the action map that links action strings to controller methods.
     * All handlers expect a Request, and return ApiResponse<?>.
     */
    private Map<String, Function<Request, ApiResponse<?>>> buildActionMap() {
        Map<String, Function<Request, ApiResponse<?>>> map = new HashMap<>();

        // ---- Tasks ----
        map.put("task/create", taskController::createNewTask);
        map.put("task/update", taskController::updateTask);
        map.put("task/delete", taskController::deleteTask);
        map.put("task/getAll", taskController::getAllTasks);
        map.put("task/search", taskController::searchTasksByName);
        map.put("task/count", taskController::countTasks);
        map.put("task/countUnassigned", taskController::countUnassignedTasks);

        // ---- Team Members ----
        map.put("member/create", teamMemberController::createNewTeamMember);
        map.put("member/update", teamMemberController::updateTeamMember);
        map.put("member/delete", teamMemberController::deleteTeamMember);
        map.put("member/getAll", teamMemberController::getAllTeamMembers);
        map.put("member/search", teamMemberController::searchTeamMembersByName);
        map.put("member/count", teamMemberController::countTeamMembers);
        map.put("member/averageLoad", teamMemberController::averageLoad);

        // ---- Assignments ----
        map.put("assignment/assignAll", assignmentController::assignTasks);
        map.put("assignment/assignForMember", assignmentController::assignTasksToTeamMember);
        map.put("assignment/delete", assignmentController::deleteAssignment);
        map.put("assignment/getAll", assignmentController::getAllAssignments);
        map.put("assignment/forMember", assignmentController::getAssignmentsForTeamMember);

        return map;
    }

    /**
     * Main server loop. Accepts clients, delegates to HandleRequest in thread pool.
     */
    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setSoTimeout(2000); // Accept timeout for shutdown support
            System.out.println("Server started on port " + port);
            while (running) {
                try {
                    Socket socket = serverSocket.accept();
                    socket.setSoTimeout(10_000); // Client inactivity timeout (ms)
                    pool.execute(new HandleRequest(socket, actionMap));
                    printPoolStatus();
                } catch (SocketTimeoutException ste) {
                    // Allows shutdown checks (non-blocking accept)
                }
            }
        } catch (Exception e) {
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
        } finally {
            shutdown();
        }
    }

    /**
     * Shuts down the server gracefully, waiting for all tasks to finish.
     */
    public void shutdown() {
        running = false;
        pool.shutdown();
        try {
            if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
            System.out.println("Server shut down.");
        } catch (InterruptedException ie) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Prints current thread pool statistics (active, queued, completed).
     */
    private void printPoolStatus() {
        if (pool instanceof ThreadPoolExecutor tpe) {
            System.out.printf("Active: %d, Queued: %d, Completed: %d%n",
                    tpe.getActiveCount(), tpe.getQueue().size(), tpe.getCompletedTaskCount());
        }
    }
}
