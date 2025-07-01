package hit.client.controller;

import com.google.gson.reflect.TypeToken;
import com.hit.model.Assignment;
import com.hit.model.Task;
import com.hit.model.TeamMember;
import com.hit.server.Request;
import hit.client.network.NetworkClient;
import hit.client.util.FormNavigator;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller for the Assignments screen.
 * <ul>
 *   <li>Loads all assignments from the backend and displays them as cards.</li>
 *   <li>Supports search (by task, member, skill, ID), algorithm selection, and full refresh.</li>
 *   <li>Allows deleting an assignment, re-assigning tasks (global or per-member), and viewing assignments for a specific member.</li>
 *   <li>UI: Responsive, scalable FlowPane of cards, dynamic updates, warning dialogs for destructive actions.</li>
 * </ul>
 *
 * Usage:
 * - Loads all assignments, tasks, and members at startup.
 * - User can filter assignments by member/task/skill.
 * - User can click "Assign Tasks" to recalculate all assignments (after confirmation).
 * - User can delete assignment cards directly.
 * - User can view assignments for a specific member by searching their ID.
 */
@SuppressWarnings({"unchecked", "CallToPrintStackTrace"})
public class AssignmentController {

    @FXML private FlowPane assignmentsFlow;
    @FXML private TextField searchField;
    @FXML private Button assignBtn;
    @FXML private ToggleButton greedyToggle;
    @FXML private ToggleButton balancedToggle;
    @FXML private Button refreshBtn;
    @FXML private ToggleGroup algorithmToggleGroup;

    // In-memory data
    private List<Assignment> allAssignments = new ArrayList<>();
    private List<Task> allTasks = new ArrayList<>();
    private List<TeamMember> allMembers = new ArrayList<>();

    /** Called automatically after FXML loading. Loads data and hooks up UI events. */
    @FXML
    public void initialize() {
        loadAllData();

        // Live search/filter
        searchField.textProperty().addListener((obs, oldVal, newVal) -> filterAndShowAssignments());

        // Toggle group logic (ensure one selected)
        algorithmToggleGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null) { // prevent no toggle
                algorithmToggleGroup.selectToggle(greedyToggle);
            }
        });

        // Default: Greedy
        greedyToggle.setSelected(true);
    }

    /** Loads all assignments, tasks, and members from the backend. */
    private void loadAllData() {
        loadAssignmentsFromServer();
        loadTasksFromServer();
        loadMembersFromServer();
    }

    private void loadAssignmentsFromServer() {
        Request req = new Request(Map.of("action", "assignment/getAll"), Map.of());
        NetworkClient.sendRequestAsyncTyped(
                req,
                new TypeToken<com.hit.controller.ApiResponse<List<Assignment>>>(){}.getType(),
                (resp, err) -> {
                    if (err != null || resp == null || !resp.isSuccess()) {
                        showError("Failed to load assignments.", err != null ? err.getMessage() : (resp != null ? resp.getMessage() : ""));
                        this.allAssignments = new ArrayList<>();
                    } else {
                        this.allAssignments = resp.getData() != null ? (List<Assignment>) resp.getData() : new ArrayList<>();
                    }
                    filterAndShowAssignments();
                }
        );
    }

    private void loadTasksFromServer() {
        Request req = new Request(Map.of("action", "task/getAll"), Map.of());
        NetworkClient.sendRequestAsyncTyped(
                req,
                new TypeToken<com.hit.controller.ApiResponse<List<Task>>>(){}.getType(),
                (resp, err) -> {
                    if (resp != null && resp.isSuccess() && resp.getData() != null) {
                        this.allTasks = (List<Task>) resp.getData();
                    } else {
                        this.allTasks = new ArrayList<>();
                    }
                    Platform.runLater(this::filterAndShowAssignments);
                }
        );
    }

    private void loadMembersFromServer() {
        Request req = new Request(Map.of("action", "member/getAll"), Map.of());
        NetworkClient.sendRequestAsyncTyped(
                req,
                new TypeToken<com.hit.controller.ApiResponse<List<TeamMember>>>(){}.getType(),
                (resp, _) -> {
                    if (resp != null && resp.isSuccess() && resp.getData() != null) {
                        this.allMembers = (List<TeamMember>) resp.getData();
                    } else {
                        this.allMembers = new ArrayList<>();
                    }
                    Platform.runLater(this::filterAndShowAssignments);
                }
        );
    }

    /** Applies the current filter and displays matching assignment cards. */
    private void filterAndShowAssignments() {
        String filter = searchField.getText() != null ? searchField.getText().trim().toLowerCase() : "";
        List<Assignment> filtered = allAssignments.stream()
                .filter(a -> matchesAssignment(a, filter))
                .collect(Collectors.toList());
        showAssignments(filtered);
    }

    /** Checks if an assignment matches the filter string by task/member name, skill, or ID. */
    private boolean matchesAssignment(Assignment a, String filter) {
        if (filter.isEmpty()) return true;
        Task task = findTaskById(a.getTaskId());
        TeamMember member = findMemberById(a.getMemberId());

        // Task name/id/skill
        if (task != null) {
            if (containsIgnoreCase(task.getName(), filter) || containsIgnoreCase(task.getId(), filter))
                return true;
            if (task.getRequiredSkills() != null && task.getRequiredSkills().stream()
                    .anyMatch(skill -> containsIgnoreCase(skill, filter)))
                return true;
        }
        // Member name/id/skill
        if (member != null) {
            if (containsIgnoreCase(member.getName(), filter) || containsIgnoreCase(member.getId(), filter))
                return true;
            if (member.getSkills() != null && member.getSkills().stream()
                    .anyMatch(skill -> containsIgnoreCase(skill, filter)))
                return true;
        }
        // Assigned hours
        return String.valueOf(a.getAssignedHours()).contains(filter);
    }

    private boolean containsIgnoreCase(String val, String filter) {
        return val != null && val.toLowerCase().contains(filter);
    }

    /** Displays assignment cards in the UI (clears and adds new). */
    private void showAssignments(List<Assignment> assignments) {
        assignmentsFlow.getChildren().clear();
        for (Assignment a : assignments) {
            Task t = findTaskById(a.getTaskId());
            TeamMember m = findMemberById(a.getMemberId());
            Node card = createAssignmentCard(a, t, m);
            assignmentsFlow.getChildren().add(card);
        }
    }

    /** Finds a Task by ID from local cache. */
    private Task findTaskById(String taskId) {
        if (taskId == null) return null;
        return allTasks.stream().filter(t -> taskId.equals(t.getId())).findFirst().orElse(null);
    }
    /** Finds a TeamMember by ID from local cache. */
    private TeamMember findMemberById(String memberId) {
        if (memberId == null) return null;
        return allMembers.stream().filter(m -> memberId.equals(m.getId())).findFirst().orElse(null);
    }

    /** Creates an AssignmentCard for a given assignment/task/member (supports delete/edit actions). */
    /** Creates a single Assignment card and wires the callbacks */
    private Node createAssignmentCard(Assignment a,
                                               Task t,
                                               TeamMember m) {

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/hit/client/view/AssignmentCard.fxml"));
            Region card = loader.load();

            AssignmentCardController c = loader.getController();

            /* populate data */
            c.setAssignment(a, t, m);

            /* optional delegates */
            c.setOnTaskClicked   (() -> openTaskFormAndRefresh(t));
            c.setOnMemberClicked (() -> openMemberFormAndRefresh(m));
            c.setOnDeleteClicked (() -> onDeleteAssignment(a));


            /* double-click opens a small info dialog – UX sugar */
            card.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                    showAssignmentDetails(a, t, m);
                }
            });

            return card;

        } catch (Exception ex) {
            ex.printStackTrace();
            return new Label("Error loading assignment card: " + ex.getMessage());
        }
    }


    /** Handles refresh button. */
    @FXML
    private void onRefresh() {
        loadAllData();
    }

    /** Handles delete assignment (after confirmation dialog). */
    private void onDeleteAssignment(Assignment a) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Assignment");
        alert.setHeaderText("Are you sure you want to delete this assignment?");
        alert.setContentText("This will remove the assignment for Task [" + a.getTaskId() +
                "] and Member [" + a.getMemberId() + "].");
        Optional<ButtonType> res = alert.showAndWait();
        if (res.isPresent() && res.get() == ButtonType.OK) {
            Request req = new Request(Map.of("action", "assignment/delete"),
                    Map.of("taskId", a.getTaskId(), "memberId", a.getMemberId()));
            NetworkClient.sendRequestAsync(req, (resp, err) -> {
                if (err != null || resp == null || !resp.isSuccess()) {
                    showError("Failed to delete assignment.",
                            err != null ? err.getMessage() : (resp != null ? resp.getMessage() : ""));
                } else {
                    loadAssignmentsFromServer();
                }
            });
        }
    }

    /** Handles clicking the "Assign Tasks" button — warns, then triggers backend assignment for all. */
    @FXML
    private void onAssignClicked() {
        String selectedStrategy = greedyToggle.isSelected() ? "greedy" : "balanced";
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Reassign All Tasks");
        alert.setHeaderText("All existing assignments will be cleared and recalculated.");
        alert.setContentText("Proceed with " + selectedStrategy.toUpperCase() + " algorithm?");
        Optional<ButtonType> res = alert.showAndWait();
        if (res.isPresent() && res.get() == ButtonType.OK) {
            Request req = new Request(Map.of("action", "assignment/assignAll"),
                    Map.of("strategy", selectedStrategy));
            NetworkClient.sendRequestAsync(req, (resp, err) -> {
                if (err != null || resp == null || !resp.isSuccess()) {
                    showError("Failed to assign tasks.",
                            err != null ? err.getMessage() : (resp != null ? resp.getMessage() : ""));
                } else {
                    loadAssignmentsFromServer();
                }
            });
        }
    }
    /** Finds and shows only assignments for a given member ID. */
    public void filterAssignmentsForMember(String memberId) {
        searchField.setText(memberId);
        // The filter method will handle it
    }

    /** Shows assignments for a specific task ID. */
    public void filterAssignmentsForTask(String taskId) {
        searchField.setText(taskId);
    }

    private void openTaskFormAndRefresh(Task task) {
        FormNavigator.openTaskForm(task, true);   // blocks until the modal closes
        loadAllData();                            // pull fresh data
    }

    private void openMemberFormAndRefresh(TeamMember member) {
        FormNavigator.openMemberForm(member, true);
        loadAllData();
    }


    /** Opens assignment details modal (optional, for UX). */
    private void showAssignmentDetails(Assignment a, Task t, TeamMember m) {
        // You can create a modal for assignment details/edit here if needed
        Alert info = new Alert(Alert.AlertType.INFORMATION);
        info.setTitle("Assignment Details");
        info.setHeaderText("Assignment Information");
        StringBuilder sb = new StringBuilder();
        sb.append("Task: ").append(t != null ? t.getName() : a.getTaskId())
                .append("\nMember: ").append(m != null ? m.getName() : a.getMemberId())
                .append("\nAssigned Hours: ").append(a.getAssignedHours());
        info.setContentText(sb.toString());
        info.showAndWait();
    }

    /** Utility to show error dialogs with context. */
    private void showError(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error: " + title);
            alert.setHeaderText(title);
            alert.setContentText(message != null ? message : "Unknown error.");
            alert.showAndWait();
        });
    }
}
