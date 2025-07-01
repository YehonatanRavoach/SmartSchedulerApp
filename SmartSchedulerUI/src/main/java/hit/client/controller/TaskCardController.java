package hit.client.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import hit.client.util.SkillChipFactory;
import com.hit.model.Task;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Controller for rendering a Task as a compact visual card in the tasks list.
 *
 * Responsibilities:
 * <ul>
 *   <li>Populating all text fields and formatting date values</li>
 *   <li>Rendering a vertical progress bar for allocation status</li>
 *   <li>Coloring the priority badge according to urgency (P1-P4)</li>
 *   <li>Creating "chips" for each skill (auto-wrapping)</li>
 *   <li>Supporting dynamic card height if there are many skills</li>
 * </ul>
 *
 * Visual style leverages CSS classes:
 * - <b>.task-card</b> for the root
 * - <b>.priority-label</b> for the badge
 * - <b>.skill-tag</b> for each chip
 *
 * Usage:
 *   Call {@link #setTask(Task)} to populate the card with data.
 */
public class TaskCardController {

    // --- FXML fields ---

    @FXML private Label      taskNameLabel;
    @FXML private Label      priorityLabel;
    @FXML private Rectangle  allocationBar;

    @FXML private Label idLabel;
    @FXML private Label durationLabel;
    @FXML private Label remainingLabel;
    @FXML private Label createdAtLabel;
    @FXML private FlowPane skillsFlow;

    // --- Internal state ---
    /** The task currently displayed in this card (can be used for edit/navigation). */
    private Task editingTask;

    // --- Constants ---

    /** Maximum height for allocation progress bar (in px). */
    private static final double BAR_MAX_HEIGHT = 160.0;

    /** Date formatter for createdAt field. */
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy  HH:mm")
                    .withZone(ZoneId.systemDefault());

    // --- Public API ---

    /**
     * Populates the card UI fields from a Task object.
     * @param task the Task to display
     */
    public void setTask(Task task) {
        this.editingTask = task;
        if (task == null) return;

        // 1. Basic info fields
        taskNameLabel.setText(task.getName());
        idLabel.setText(String.valueOf(task.getId()));
        durationLabel.setText(task.getDurationHours() + " h");
        remainingLabel.setText(task.getRemainingHours() + " h");
        createdAtLabel.setText(DATE_FMT.format(task.getCreatedAt()));

        // 2. Priority badge (color and tooltip)
        int pr = clamp(task.getPriority(), 1, 4);
        String prColor = getPriorityColor(pr);
        priorityLabel.setText("P" + pr);
        priorityLabel.setStyle("-fx-background-color:" + prColor + ';');
        Tooltip.install(priorityLabel, new Tooltip("Priority " + pr + " (1 = Urgent, 4 = Low)"));

        // 3. Allocation/progress bar (color: red → yellow → green)
        double ratio = getAllocatedRatio(task);
        allocationBar.setHeight(BAR_MAX_HEIGHT * Math.max(0.10, ratio)); // always visible
        allocationBar.setFill(getProgressColor(ratio));

        // 4. Skills as chips (auto-wraps for long lists)
        populateSkillChips(task.getRequiredSkills());
    }

    // --- Private helpers ---

    /** Adds skill chips for all required skills (with fallback if empty). */
    private void populateSkillChips(List<String> skills) {
        skillsFlow.getChildren().clear();
        if (skills == null || skills.isEmpty()) {
            Label dash = new Label("—");
            dash.getStyleClass().add("secondary-text");
            skillsFlow.getChildren().add(dash);
            return;
        }
        for (String s : skills) {
            skillsFlow.getChildren().add(SkillChipFactory.create(s));
        }
    }

    /**
     * Returns the allocated progress ratio: 0 = not assigned, 1 = fully assigned.
     */
    private double getAllocatedRatio(Task t) {
        int total = t.getDurationHours();
        int rem = t.getRemainingHours();
        if (total <= 0) return 1.0;
        double done = Math.max(0, total - rem);
        return Math.max(0, Math.min(1, done / (double) total));
    }

    /** Utility: clamp int to [lo, hi] */
    private int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /**
     * Returns a color for the priority badge.
     * @param pr Priority 1-4
     * @return Hex color string
     */
    private String getPriorityColor(int pr) {
        return switch (pr) {
            case 1 -> "#e74c3c";  // Red
            case 2 -> "#f39c12";  // Orange
            case 3 -> "#f1c40f";  // Yellow
            case 4 -> "#2ecc71";  // Green
            default -> "#95a5a6"; // Gray
        };
    }

    /**
     * Returns a color for the progress bar (red → yellow → green).
     * @param ratio [0=none, 1=all]
     */
    private Color getProgressColor(double ratio) {
        // 0.0 = red (#e74c3c), 0.5 = yellow (#f9e100), 1.0 = green (#28b450)
        if (ratio <= 0.01) {
            return Color.web("#e74c3c");
        } else if (ratio < 0.5) {
            // red → yellow
            double k = ratio / 0.5;
            return blend(Color.web("#e74c3c"), Color.web("#f9e100"), k);
        } else if (ratio < 1.0) {
            // yellow → green
            double k = (ratio - 0.5) / 0.5;
            return blend(Color.web("#f9e100"), Color.web("#28b450"), k);
        } else {
            return Color.web("#28b450");
        }
    }

    /**
     * Utility: blend two colors by ratio [0-1].
     * @param a start color
     * @param b end color
     * @param k ratio (0=start, 1=end)
     */
    private static Color blend(Color a, Color b, double k) {
        double r = a.getRed()   * (1 - k) + b.getRed()   * k;
        double g = a.getGreen() * (1 - k) + b.getGreen() * k;
        double b_ = a.getBlue() * (1 - k) + b.getBlue()  * k;
        return new Color(r, g, b_, 0.75);
    }

    /**
     * Returns the last task shown by this controller (can be null if none).
     */
    public Task getEditingTask() {
        return editingTask;
    }
}
