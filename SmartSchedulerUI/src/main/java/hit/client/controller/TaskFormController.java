package hit.client.controller;

import com.hit.model.Task;
import com.hit.server.Request;
import hit.client.network.NetworkClient;
import hit.client.util.AlgorithmChooser;
import hit.client.util.SkillRepository;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.controlsfx.control.CheckComboBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Controller for the Task Form dialog.
 * Handles form population, live validation, async save/delete to server, and state management.
 * <p>
 * Usage:
 *   - Call {@link #setEditMode(boolean)} for edit/create mode.
 *   - Call {@link #setTask(Task)} before show in edit mode.
 *   - "Save" and "Delete" communicate with server via NetworkClient asynchronously.
 */
public class TaskFormController {

    // --- FXML fields ---
    @FXML private Label titleLabel;
    @FXML private FontIcon titleIcon;
    @FXML private AnchorPane rootPane;
    @FXML private VBox cardBox;

    @FXML private TextField taskNameField;
    @FXML private TextField durationField;
    @FXML private ComboBox<Integer> priorityCombo;
    @FXML private Label createdAtLabel;
    @FXML private Label remainingLabel;

    // Skills multi-select
    @FXML private AnchorPane skillsComboBoxAnchor;
    @FXML private FlowPane skillsTagPane;
    @FXML private ScrollPane skillsScrollPane;

    // Buttons
    @FXML private Button cancelButton;
    @FXML private Button saveButton;
    @FXML private Button deleteButton;

    // Error labels
    @FXML private Label taskNameError, durationError, priorityError, skillsError;

    private CheckComboBox<String> skillsComboBox;
    private static final List<String> ALL_SKILLS = SkillRepository.ALL_SKILLS;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy  HH:mm");

    // State
    private boolean editMode = false;
    private String editingTaskId = null;

    @FXML
    public void initialize() {
        priorityCombo.getItems().setAll(1, 2, 3, 4);

        // ControlsFX skills multiselect
        skillsComboBox = new CheckComboBox<>();
        skillsComboBox.getItems().addAll(ALL_SKILLS);
        AnchorPane.setLeftAnchor(skillsComboBox, 0.0);
        AnchorPane.setRightAnchor(skillsComboBox, 0.0);
        AnchorPane.setTopAnchor(skillsComboBox, 0.0);
        AnchorPane.setBottomAnchor(skillsComboBox, 0.0);
        skillsComboBoxAnchor.getChildren().add(skillsComboBox);

        skillsComboBox.getCheckModel().getCheckedItems().addListener((ListChangeListener<String>) _ -> updateSkillsTagPane());
        updateSkillsTagPane();

        clearErrorLabels();
        setEditMode(false);

        cardBox.getStyleClass().add("create-bg");

        // --- Live-validation listeners ---
        taskNameField.textProperty().addListener((obs, oldVal, newVal) -> clearFieldError(taskNameField, taskNameError));
        durationField.textProperty().addListener((obs, oldVal, newVal) -> clearFieldError(durationField, durationError));
        priorityCombo.valueProperty().addListener((obs, oldVal, newVal) -> clearFieldError(priorityCombo, priorityError));
        skillsComboBox.getCheckModel().getCheckedItems().addListener((ListChangeListener<String>) change -> clearFieldError(skillsComboBox, skillsError));
    }

    /**
     * Configures the form for edit mode (true) or create mode (false).
     */
    public void setEditMode(boolean isEdit) {
        this.editMode = isEdit;
        deleteButton.setVisible(isEdit);
        deleteButton.setManaged(isEdit);

        if (isEdit) {
            titleLabel.setText("Update task");
            titleIcon.setIconLiteral("mdi2p-pencil");
            cardBox.getStyleClass().removeAll("create-bg");
            cardBox.getStyleClass().add("edit-bg");
        } else {
            titleLabel.setText("Create a new task");
            titleIcon.setIconLiteral("mdi2p-plus-circle");
            cardBox.getStyleClass().removeAll("edit-bg");
            cardBox.getStyleClass().add("create-bg");
            editingTaskId = null;
        }
    }

    /**
     * Populates the form fields from an existing Task.
     * Only used in edit mode!
     */
    public void setTask(Task task) {
        if (task == null) return;
        editingTaskId = task.getId();
        taskNameField.setText(task.getName());
        durationField.setText(String.valueOf(task.getDurationHours()));
        remainingLabel.setText(task.getRemainingHours() + " h");
        createdAtLabel.setText(task.getCreatedAt() == null
                ? ""
                : DATE_FMT.format(task.getCreatedAt().atZone(ZoneId.systemDefault())));
        priorityCombo.setValue(task.getPriority());

        skillsComboBox.getCheckModel().clearChecks();
        if (task.getRequiredSkills() != null) {
            for (String skill : task.getRequiredSkills()) {
                if (ALL_SKILLS.contains(skill))
                    skillsComboBox.getCheckModel().check(skill);
            }
        }
        updateSkillsTagPane();
    }

    /** Returns the currently selected skills. */
    public List<String> getSelectedSkills() {
        return skillsComboBox.getCheckModel().getCheckedItems();
    }

    /** Updates the visible skill chips in the FlowPane. */
    private void updateSkillsTagPane() {
        skillsTagPane.getChildren().clear();
        ObservableList<String> selected = skillsComboBox.getCheckModel().getCheckedItems();
        if (selected.isEmpty()) {
            Label emptyTag = new Label("No skills selected");
            emptyTag.getStyleClass().add("skill-tag-empty");
            skillsTagPane.getChildren().add(emptyTag);
        } else {
            for (String skill : selected) {
                Label tag = new Label(skill);
                tag.getStyleClass().add("skill-tag");
                skillsTagPane.getChildren().add(tag);
            }
        }
    }

    private void clearErrorLabels() {
        taskNameError.setVisible(false); taskNameError.setManaged(false);
        durationError.setVisible(false); durationError.setManaged(false);
        priorityError.setVisible(false); priorityError.setManaged(false);
        skillsError.setVisible(false); skillsError.setManaged(false);

        removeErrorStyle(taskNameField);
        removeErrorStyle(durationField);
        removeErrorStyle(priorityCombo);
        removeErrorStyle(skillsComboBox);
    }

    @FXML
    private void onSave() {
        // 1. Clear previous error states
        clearErrorLabels();
        boolean valid = true;

        // 2. Validate fields
        String name = taskNameField.getText();
        String nameError = getTaskNameError(name);
        if (nameError != null) {
            showError(taskNameError, taskNameField);
            showAlert("Invalid Task Name", nameError);
            valid = false;
        }

        String durStr = durationField.getText().trim();
        int duration = -1;
        try {
            duration = Integer.parseInt(durStr);
            if (duration < 1 || duration > 1000) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            showError(durationError, durationField);
            valid = false;
        }

        Integer priority = priorityCombo.getValue();
        if (priority == null) {
            showError(priorityError, priorityCombo);
            valid = false;
        }

        List<String> skills = getSelectedSkills();
        if (skills.isEmpty()) {
            showError(skillsError, skillsComboBox);
            valid = false;
        }

        if (!valid) return;

        // 3. Confirmation and algorithm selection in edit mode
        if (editMode) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Update Task");
            confirm.setHeaderText("Assignment recalculation required");
            confirm.setContentText("Editing this task will cause all assignments to be recalculated. Continue?");
            int finalDuration = duration;
            confirm.showAndWait().ifPresent(result -> {
                if (result == ButtonType.OK) {
                    // Show algorithm chooser dialog
                    AlgorithmChooser.showAndRun(selectedAlgorithm -> {
                        // Save task with the selected algorithm
                        saveTaskToServer(name, finalDuration, skills, priority, selectedAlgorithm);
                    });
                }
            });
        } else {
            saveTaskToServer(name, duration, skills, priority, null);
        }
    }

    /**
     * Saves the task to the server via NetworkClient.
     * - In edit mode: updates by ID.
     * - In create mode: creates a new task.
     * Handles UI error messages and closes the window on success.
     */
    private void saveTaskToServer(String name, int duration, List<String> skills, Integer priority, String algorithm) {
        // Build request body
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("durationHours", duration);
        body.put("priority", priority);
        body.put("requiredSkills", skills);

        String action;
        if (editMode) {
            if (editingTaskId == null || editingTaskId.isEmpty()) {
                showAlert("Error", "Task ID is missing for update.");
                return;
            }
            body.put("id", editingTaskId);
            if (algorithm != null) body.put("algorithm", algorithm);
            action = "task/update";
        } else {
            action = "task/create";
        }
        Request req = new Request(Map.of("action", action), body);

        NetworkClient.sendRequestAsync(req, (resp, err) -> {
            if (err != null) {
                showAlert("Network Error", "Could not save task: " + err.getMessage());
            } else if (resp != null && resp.isSuccess()) {
                closeWindow();
                // Optionally notify parent controller to refresh
            } else {
                String msg = (resp != null && resp.getMessage() != null) ? resp.getMessage() : "Unknown error";
                showAlert("Failed to save task", msg);
            }
        });
    }

    /**
     * Handles the "Delete" action.
     * Shows a confirmation, sends request async, closes on success.
     */
    @FXML
    private void onDelete() {
        if (editingTaskId == null || editingTaskId.isEmpty()) {
            showAlert("Delete Error", "No task selected for deletion.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Task");
        confirm.setHeaderText("Are you sure you want to delete this task?");
        confirm.setContentText("This action cannot be undone.");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }
        Request req = new Request(Map.of("action", "task/delete"), Map.of("id", editingTaskId));
        NetworkClient.sendRequestAsync(req, (resp, err) -> {
            if (err != null) {
                showAlert("Network Error", "Delete failed: " + err.getMessage());
            } else if (resp != null && resp.isSuccess()) {
                closeWindow();

            } else {
                String msg = (resp != null) ? resp.getMessage() : "No response from server";
                showAlert("Delete Failed", msg);
            }
        });
    }

    @FXML
    private void onCancel() {
        closeWindow();
    }

    private void closeWindow() {
        ((Stage) cancelButton.getScene().getWindow()).close();
    }

    /** Returns an error message for invalid task names, or null if valid. */
    private String getTaskNameError(String name) {
        if (name == null || name.trim().isEmpty())
            return "Task name cannot be empty.";
        if (name.trim().length() < 2)
            return "Task name is too short.";
        if (!name.matches(".*[a-zA-Z0-9א-ת].*"))
            return "Task name must contain at least one letter or digit.";
        if (!name.trim().equals(name))
            return "Task name cannot start or end with spaces.";
        return name.matches(".*[?\"].*") ? "Task name contains invalid characters." : null;
    }

    /** Shows an error Alert dialog. */
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(Label errorLabel, Control field) {
        errorLabel.setVisible(true); errorLabel.setManaged(true);
        field.getStyleClass().add("input-error");
    }
    private void removeErrorStyle(Control field) {
        field.getStyleClass().remove("input-error");
    }

    /** Removes red border and error label from a field. */
    private void clearFieldError(Control field, Label errorLabel) {
        removeErrorStyle(field);
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }
}
