package hit.client.controller;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.hit.model.Task;
import com.hit.server.Request;
import hit.client.util.FormNavigator;
import hit.client.util.GsonFactory;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller for the main Tasks list screen.
 * <ul>
 *   <li>Manages a scrollable, responsive grid of Task cards (2–3 per row, dynamic height).</li>
 *   <li>Supports dynamic filtering (search by name, skills, priority, ID, duration, remaining hours), sorting by priority (lowest=most urgent), and UI animations.</li>
 *   <li>Handles opening the Task Form for editing and for adding new tasks, with full context passing.</li>
 * </ul>
 *
 * Usage:
 * - Automatically loads tasks from the server on a screen load.
 * - Typing in the search bar filters tasks dynamically.
 * - Clicking "Refresh" reloads tasks from the server.
 * - Clicking a card opens an editable TaskForm.
 * - Clicking the FAB ("+") opens the TaskForm for creating a new task.
 */
@SuppressWarnings("CallToPrintStackTrace")
public class TasksController {

    public Button refreshBtn;
    @FXML private FlowPane tasksFlow;
    @FXML private ScrollPane scrollPane;
    @FXML private TextField searchField;

    /** List of all tasks loaded from the server. */
    private List<Task> allTasks = new ArrayList<>();
    /** List of tasks currently filtered for display. */
    private List<Task> filteredTasks = new ArrayList<>();

    // Socket config
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 34567;
    private final Gson gson = GsonFactory.get();

    @FXML
    public void initialize() {
        loadTasksFromServer();

        // Live filtering as user types in the search field
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilterAndRefresh());
    }

    /**
     * Loads tasks from the backend server via Socket/JSON (action: "task/getAll").
     * The UI is updated asynchronously.
     */
    private void loadTasksFromServer() {
        new Thread(() -> {
            try {
                Request req = new Request(Map.of("action", "task/getAll"), Map.of());
                try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
                     PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
                     BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                    writer.println(gson.toJson(req));
                    String jsonResp = reader.readLine();

                    // The server returns: ApiResponse<List<Task>>
                    Map<?, ?> resp = gson.fromJson(jsonResp, Map.class);
                    Object dataObj = resp.get("data");
                    List<Task> loadedTasks = gson.fromJson(gson.toJson(dataObj), new TypeToken<List<Task>>(){}.getType());

                    if (loadedTasks != null) {
                        Platform.runLater(() -> {
                            this.allTasks = loadedTasks;
                            applyFilterAndRefresh();
                        });
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                Platform.runLater(() -> {
                    this.allTasks = Collections.emptyList();
                    applyFilterAndRefresh();
                });
            }
        }).start();
    }

    /**
     * Applies the search filter and updates the displayed task cards.
     * The filter checks for a match in name, required skills, priority, id, duration, or remaining hours.
     */
    private void applyFilterAndRefresh() {
        String filter = searchField.getText();
        if (filter == null || filter.trim().isEmpty()) {
            filteredTasks = new ArrayList<>(allTasks);
        } else {
            String lower = filter.trim().toLowerCase();
            filteredTasks = allTasks.stream()
                    .filter(task -> matchesFilter(task, lower))
                    .collect(Collectors.toList());
        }
        refreshTaskCards();
    }

    /**
     * Checks if a task matches the search filter.
     * Supports matching by name, required skills, priority, id, duration, or remaining hours.
     *
     * @param task   Task to check
     * @param filter Lower-case search text
     * @return true if the task matches, false otherwise
     */
    private boolean matchesFilter(Task task, String filter) {
        // Match by name
        if (task.getName() != null && task.getName().toLowerCase().contains(filter))
            return true;
        // Match by id
        if (task.getId() != null && task.getId().toLowerCase().contains(filter))
            return true;
        // Match by required skills
        if (task.getRequiredSkills() != null && task.getRequiredSkills().stream()
                .anyMatch(skill -> skill != null && skill.toLowerCase().contains(filter)))
            return true;
        // Match by priority (as text)
        if (String.valueOf(task.getPriority()).contains(filter))
            return true;
        // Match by duration (as text)
        if (String.valueOf(task.getDurationHours()).contains(filter))
            return true;
        // Match by remaining hours (as text)
        return String.valueOf(task.getRemainingHours()).contains(filter);
    }

    /**
     * Refreshes the UI with the current filtered list of tasks, sorted by priority (lowest = most urgent), then name.
     */
    private void refreshTaskCards() {
        tasksFlow.getChildren().clear();
        List<Task> sorted = filteredTasks.stream()
                .sorted(Comparator.comparingInt(Task::getPriority)
                        .thenComparing(Task::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
        for (Task task : sorted) {
            Node card = createTaskCard(task);
            tasksFlow.getChildren().add(card);
        }
    }

    /**
     * Loads a TaskCard FXML, binds data, and hooks up UI events/animations.
     * @param task The task to display
     * @return Node representing the card
     */
    private Node createTaskCard(Task task) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/hit/client/view/TaskCard.fxml"));
            Region card = loader.load();
            TaskCardController controller = loader.getController();
            controller.setTask(task);

            // Animation on hover
            card.setOnMouseEntered(_ -> card.setStyle("-fx-effect: dropshadow(gaussian, #5c7aff33, 16, 0.18, 0, 4); -fx-translate-y: -5;"));
            card.setOnMouseExited(_ -> card.setStyle(""));

            // Open edit form on click
            card.setOnMouseClicked(_ -> openTaskForm(task));
            card.setCursor(javafx.scene.Cursor.HAND);

            return card;
        } catch (Exception ex) {
            ex.printStackTrace();
            return new javafx.scene.control.Label("Error loading card: " + ex.getMessage());
        }
    }

    /**
     * Opens the Task editing form as a floating modal, with all values populated.
     * @param task Task to edit
     */
    private void openTaskForm(Task task) {
        FormNavigator.openTaskForm(task, true);
        loadTasksFromServer();
    }

    /**
     * Opens the Task creation form as a floating modal (FAB "+" button).
     */
    @FXML
    private void onAddTask() {
        FormNavigator.openTaskForm(null, false);
        loadTasksFromServer();
    }

    /**
     * Refresh button handler. Reloads from the server.
     */
    @FXML
    private void onRefresh() {
        loadTasksFromServer();
    }

    /**
     * For programmatic usage. Allows parent to set tasks directly (rare).
     * @param tasks List of tasks to display
     */
    public void setTasks(List<Task> tasks) {
        this.allTasks = tasks != null ? new ArrayList<>(tasks) : new ArrayList<>();
        applyFilterAndRefresh();
    }
}
