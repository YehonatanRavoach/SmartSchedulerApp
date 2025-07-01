package com.hit.client;

import com.hit.dao.DaoFactory;
import com.hit.dao.IDao;
import com.hit.model.*;
import com.hit.service.TaskAssignmentService;

import java.util.List;
import java.util.Scanner;

/**
 * Console demo for TaskAssignmentService using either file or SQLite backend.
 * Supports bulk assignment, stats, search, and prints all assignments.
 */
public class Main {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        System.out.print("Choose backend [file/sqlite]: ");
        String backend = scanner.nextLine().trim().toLowerCase();
        if (!backend.equals("file") && !backend.equals("sqlite")) {
            System.out.println("Invalid backend. Exiting.");
            return;
        }

        IDao<Task> taskDao = DaoFactory.create(backend, Task.class);
        IDao<TeamMember> memberDao = DaoFactory.create(backend, TeamMember.class);
        IDao<Assignment> assignmentDao = DaoFactory.create(backend, Assignment.class);

        // Seed demo data if empty
        if (taskDao.load().isEmpty()) {
            List<Task> tasks = List.of(
                    new Task("T1", "Build API", 4, 1, List.of("java")),
                    new Task("T2", "Train Model", 5, 2, List.of("ml")),
                    new Task("T3", "Data Query", 3, 3, List.of("sql"))
            );
            taskDao.save(tasks);
        }
        if (memberDao.load().isEmpty()) {
            List<TeamMember> members = List.of(
                    new TeamMember("M1", "Alice", List.of("java", "sql"), 8, 1.0),
                    new TeamMember("M2", "Bob", List.of("ml"), 6, 1.0),
                    new TeamMember("M3", "Charlie", List.of("java", "ml"), 4, 1.0)
            );
            memberDao.save(members);
        }

        TaskAssignmentService service = new TaskAssignmentService(taskDao, memberDao, assignmentDao);

        System.out.print("Choose assignment strategy [greedy/balanced]: ");
        String strategy = scanner.nextLine().trim().toLowerCase();

        // Bulk assignment using the chosen strategy
        boolean assigned = service.assignTasks(strategy);
        if (assigned) {
            System.out.println("\n✅ Assignment completed successfully.");
        } else {
            System.out.println("\n⚠️ No assignments were made.");
        }

        List<Assignment> assignments = service.getAllAssignments();

        System.out.println("\n📋 Assignments:");
        for (Assignment a : assignments) {
            System.out.printf("Assignment: %s-%s, hours: %d%n", a.getTaskId(), a.getMemberId(), a.getAssignedHours());
        }

        // Print stats
        System.out.printf("\nTotal tasks: %d, members: %d%n", service.countTasks(), service.countTeamMembers());
        System.out.printf("Unassigned tasks: %d%n", service.countUnassignedTasks());
        System.out.printf("Average load per member: %.2f%n", service.averageLoad());

        // Search demo
        System.out.print("\nEnter member name to search: ");
        String name = scanner.nextLine();
        List<TeamMember> found = service.searchTeamMembersByName(name);
        System.out.println("🔍 Found members: ");
        for (TeamMember m : found) {
            System.out.printf("- %s (%s)%n", m.getName(), m.getId());
        }

        // Optional: search tasks
        System.out.print("\nEnter task name to search: ");
        String taskName = scanner.nextLine();
        List<Task> foundTasks = service.searchTasksByName(taskName);
        System.out.println("🔍 Found tasks: ");
        for (Task t : foundTasks) {
            System.out.printf("- %s (%s)%n", t.getName(), t.getId());
        }

        System.out.println("\n✅ All checks done.");
    }
}
