package com.hit.server;

import com.hit.dao.DaoFactory;
import com.hit.dao.IDao;
import com.hit.model.Assignment;
import com.hit.model.Task;
import com.hit.model.TeamMember;
import com.hit.service.TaskAssignmentService;

public class ServerDriver {
    public static void main(String[] args) {
        try {
            // Create DAOs for each model (thread-safe singleton)
            IDao<Task> taskDao = DaoFactory.create("sqlite", Task.class);
            IDao<TeamMember> memberDao = DaoFactory.create("sqlite", TeamMember.class);
            IDao<Assignment> assignmentDao = DaoFactory.create("sqlite", Assignment.class);

            // Create the main service (inject DAOs)
            TaskAssignmentService service = new TaskAssignmentService(taskDao, memberDao, assignmentDao);

            // Start the server: port 34567, pool size 10 (change as needed)
            Server server = new Server(34567, 10, service);
            Thread serverThread = new Thread(server);
            serverThread.start();

            // Add graceful shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(server::shutdown));

            System.out.println("Server started successfully. Listening on port 34567.");
        } catch (Exception e) {
            System.err.println("Failed to start server: " + e.getMessage());
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
        }
    }
}
