package hit.client.controller;

import com.hit.controller.ApiResponse;
import com.hit.model.TeamMember;
import com.hit.server.Request;
import hit.client.network.NetworkClient;
import hit.client.util.AlgorithmChooser;
import hit.client.util.SkillRepository;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.controlsfx.control.CheckComboBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.*;

/**
 * Controller for the Member Form dialog.
 * Handles form population, live validation, async save/delete to server, and state management.
 *
 * Usage:
 *   - Call {@link #setEditMode(boolean)} for edit/create mode.
 *   - Call {@link #setMember(TeamMember)} before show in edit mode.
 *   - "Save" and "Delete" communicate with server via NetworkClient asynchronously.
 */
public class MemberFormController {

    // --- FXML fields ---
    @FXML private Label titleLabel;
    @FXML private FontIcon titleIcon;
    @FXML private AnchorPane rootPane;
    @FXML private VBox cardBox;

    @FXML private TextField nameField;
    @FXML private TextField maxHoursField;
    @FXML private Label remainingLabel;
    @FXML private Label createdAtLabel; // optional (not always shown)
    @FXML private ComboBox<Integer> efficiencyCombo;

    // Skills multi-select
    @FXML private AnchorPane skillsComboBoxAnchor;
    @FXML private FlowPane skillsTagPane;
    @FXML private ScrollPane skillsScrollPane;

    // Buttons
    @FXML private Button cancelButton;
    @FXML private Button saveButton;
    @FXML private Button deleteButton;

    // Error labels
    @FXML private Label nameError, maxHoursError, efficiencyError, skillsError;

    private CheckComboBox<String> skillsComboBox;
    private static final List<String> ALL_SKILLS = SkillRepository.ALL_SKILLS;

    // State
    private boolean editMode = false;
    private String editingMemberId = null;

    @FXML
    public void initialize() {
        efficiencyCombo.getItems().setAll(1,2,3,4,5,6);

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
        nameField.textProperty().addListener((obs, oldVal, newVal) -> clearFieldError(nameField, nameError));
        maxHoursField.textProperty().addListener((obs, oldVal, newVal) -> clearFieldError(maxHoursField, maxHoursError));
        efficiencyCombo.valueProperty().addListener((obs, oldVal, newVal) -> clearFieldError(efficiencyCombo, efficiencyError));
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
            titleLabel.setText("Update team member");
            titleIcon.setIconLiteral("mdi2a-account-edit");
            cardBox.getStyleClass().removeAll("create-bg");
            cardBox.getStyleClass().add("edit-bg");
        } else {
            titleLabel.setText("Create a new member");
            titleIcon.setIconLiteral("mdi2a-account-plus");
            cardBox.getStyleClass().removeAll("edit-bg");
            cardBox.getStyleClass().add("create-bg");
            editingMemberId = null;
            remainingLabel.setText(""); // clear on creation
        }
    }

    /**
     * Populates the form fields from an existing TeamMember.
     * Only used in edit mode!
     */
    public void setMember(TeamMember member) {
        if (member == null) return;
        editingMemberId = member.getId();
        nameField.setText(member.getName());
        maxHoursField.setText(String.valueOf(member.getMaxHoursPerDay()));
        remainingLabel.setText(member.getRemainingHours() + " h");
        efficiencyCombo.setValue((int) member.getEfficiency());

        skillsComboBox.getCheckModel().clearChecks();
        if (member.getSkills() != null) {
            for (String skill : member.getSkills()) {
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
        var selected = skillsComboBox.getCheckModel().getCheckedItems();
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
        nameError.setVisible(false); nameError.setManaged(false);
        maxHoursError.setVisible(false); maxHoursError.setManaged(false);
        efficiencyError.setVisible(false); efficiencyError.setManaged(false);
        skillsError.setVisible(false); skillsError.setManaged(false);

        removeErrorStyle(nameField);
        removeErrorStyle(maxHoursField);
        removeErrorStyle(efficiencyCombo);
        removeErrorStyle(skillsComboBox);
    }

    @FXML
    private void onSave() {
        // 1. Clear previous error state
        clearErrorLabels();

        // 2. Validate fields
        String name = nameField.getText();
        Integer efficiency = efficiencyCombo.getValue();
        List<String> skills = getSelectedSkills();

        // Name validation
        String nameErr = getNameError(name);
        if (nameErr != null) {
            showError(nameError, nameField);
            showAlert("Invalid name", nameErr);
            return;
        }

        // Max hours validation
        int maxHours;
        try {
            maxHours = Integer.parseInt(maxHoursField.getText().trim());
            if (maxHours < 1 || maxHours > 12) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            showError(maxHoursError, maxHoursField);
            showAlert("Invalid hours", "Max-Hours must be between 1-12.");
            return;
        }

        // Efficiency validation
        if (efficiency == null || efficiency < 1 || efficiency > 6) {
            showError(efficiencyError, efficiencyCombo);
            showAlert("Invalid efficiency", "Choose a value between 1 (best) and 6 (worst).");
            return;
        }

        // Skills validation
        if (skills.isEmpty()) {
            showError(skillsError, skillsComboBox);
            showAlert("Missing skills", "Please add at least one skill.");
            return;
        }

        int eff = efficiency; // fix for variable

        if (editMode) {
            // Show confirmation dialog
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Update Member");
            confirm.setHeaderText("Assignment recalculation required");
            confirm.setContentText("Editing this member will cause all assignments to be recalculated. Continue?");
            confirm.showAndWait().ifPresent(result -> {
                if (result == ButtonType.OK) {
                    // Show algorithm chooser dialog
                    AlgorithmChooser.showAndRun(strategy -> {
                        // You can pass 'strategy' to saveMemberToServer if needed
                        saveMemberToServer(name, maxHours, eff, skills);
                    });
                }
            });
        } else {
            saveMemberToServer(name, maxHours, eff, skills);
        }
    }


    /**
     * Saves the member to the server via NetworkClient.
     * - In edit mode: updates by ID.
     * - In create mode: creates a new member.
     * Handles UI error messages and closes the window on success.
     */
    private void saveMemberToServer(String name, int maxHours, int efficiency, List<String> skills) {
        // Build request body
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("maxHoursPerDay", maxHours);
        body.put("efficiency", efficiency);
        body.put("skills", skills);

        String action;
        if (editMode) {
            if (editingMemberId == null || editingMemberId.isEmpty()) {
                showAlert("Error", "Member ID is missing for update.");
                return;
            }
            body.put("id", editingMemberId);
            action = "member/update";
        } else {
            action = "member/create";
        }
        Request req = new Request(Map.of("action", action), body);

        NetworkClient.sendRequestAsync(req, (resp, err) -> {
            if (err != null) {
                showAlert("Network Error", "Could not save member: " + err.getMessage());
            } else if (resp != null && resp.isSuccess()) {
                closeWindow();
                // Optionally notify parent controller to refresh
            } else {
                String msg = (resp != null && resp.getMessage() != null) ? resp.getMessage() : "Unknown error";
                showAlert("Failed to save member", msg);
            }
        });
    }

    /**
     * Handles the "Delete" action.
     * Shows a confirmation, sends request async, closes on success.
     */
    @FXML
    private void onDelete() {
        if (editingMemberId == null || editingMemberId.isEmpty()) {
            showAlert("Delete Error", "No member selected for deletion.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Member");
        confirm.setHeaderText("Are you sure you want to delete this member?");
        confirm.setContentText("This action cannot be undone.");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }
        Request req = new Request(Map.of("action", "member/delete"), Map.of("id", editingMemberId));
        NetworkClient.sendRequestAsync(req, (resp, err) -> {
            if (err != null) {
                showAlert("Network Error", "Delete failed: " + err.getMessage());
            } else if (resp != null && resp.isSuccess()) {
                closeWindow();
                // Optionally: notify parent controller to refresh members' list
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

    /** Returns an error message for invalid names, or null if valid. */
    private String getNameError(String name) {
        if (name == null || name.trim().isEmpty())
            return "Name cannot be empty.";
        if (name.trim().length() < 2)
            return "Name is too short.";
        if (!name.matches(".*[a-zA-Z0-9א-ת].*"))
            return "Name must contain at least one letter or digit.";
        if (!name.trim().equals(name))
            return "Name cannot start or end with spaces.";
        return name.matches(".*[?\"].*") ? "Name contains invalid characters." : null;
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
