package hit.client.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import hit.client.util.SkillChipFactory;
import com.hit.model.TeamMember;

import java.util.List;

/**
 * Controller for rendering a TeamMember as a compact visual card in the members' list.
 *
 * Responsibilities:
 * <ul>
 *   <li>Populating all text fields and formatting numeric values</li>
 *   <li>Rendering a vertical progress bar for workload status</li>
 *   <li>Coloring the efficiency badge according to value</li>
 *   <li>Creating "chips" for each skill (auto-wrapping)</li>
 *   <li>Supporting dynamic card height if there are many skills</li>
 * </ul>
 *
 * Visual style leverages CSS classes:
 * - <b>.task-card</b> for the root (shared style)
 * - <b>.priority-label</b> for the badge
 * - <b>.skill-tag</b> for each chip
 *
 * Usage:
 *   Call {@link #setMember(TeamMember)} to populate the card with data.
 */
public class MemberCardController {

    // --- FXML fields ---
    @FXML private Label      memberNameLabel;
    @FXML private Label      efficiencyLabel;
    @FXML private Rectangle  workloadBar;

    @FXML private Label idLabel;
    @FXML private Label maxHoursLabel;
    @FXML private Label remainingLabel;
    @FXML private FlowPane skillsFlow;

    // --- Internal state ---
    /** The team member currently displayed in this card (can be used for edit/navigation). */
    private TeamMember editingMember;

    // --- Constants ---
    /** Maximum height for workload progress bar (in px). */
    private static final double BAR_MAX_HEIGHT = 160.0;

    /* ─────────────  delegate for “Assign tasks to member”  ─────────── */
    private java.util.function.Consumer<TeamMember> onAssignMember;
    // --- Public API ---

    /**
     * Populates the card UI fields from a TeamMember object.
     * @param member the TeamMember to display
     */
    public void setMember(TeamMember member) {
        this.editingMember = member;
        if (member == null) return;

        // 1. Basic info fields
        memberNameLabel.setText(member.getName());
        idLabel.setText(String.valueOf(member.getId()));
        maxHoursLabel.setText(member.getMaxHoursPerDay() + " h");
        remainingLabel.setText(member.getRemainingHours() + " h");

        // 2. Efficiency badge (color and tooltip)
        int efficiency = (int) member.getEfficiency(); // assuming getEfficiency() returns double
        efficiencyLabel.setText(String.valueOf(efficiency));
        efficiencyLabel.setStyle("-fx-background-color:" + getEfficiencyColor(efficiency) + ';');
        Tooltip.install(
                efficiencyLabel,
                new Tooltip("Efficiency Level: " + efficiency + " (1 = highest, 6 = lowest)")
        );

        // 3. Workload/progress bar (color: green → yellow → red)
        double workloadRatio = getWorkloadRatio(member);
        workloadBar.setHeight(BAR_MAX_HEIGHT * Math.max(0.10, workloadRatio)); // always visible
        workloadBar.setFill(getWorkloadColor(workloadRatio));

        // 4. Skills as chips (auto-wraps for long lists)
        populateSkillChips(member.getSkills());
    }

    // --- Private helpers ---

    /** Adds skill chips for all member skills (with fallback if empty). */
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
     * Returns the workload ratio: 0 = free, 1 = fully loaded.
     */
    private double getWorkloadRatio(TeamMember m) {
        int max = m.getMaxHoursPerDay();
        int rem = m.getRemainingHours();
        if (max <= 0) return 1.0;
        double used = Math.max(0, max - rem);
        return Math.max(0, Math.min(1, used / (double) max));
    }

    /** Utility: clamp double to [lo, hi] */
    private double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Allows the parent controller to plug its own action. */
    public void setOnAssignMember(java.util.function.Consumer<TeamMember> handler) {
        this.onAssignMember = handler;
    }

    /** Called by the MenuItem in FXML. */
    @FXML
    private void onAssignClicked() {
        if (onAssignMember != null && editingMember != null) {
            onAssignMember.accept(editingMember);
        }
    }
    /**
     * Returns a color for the efficiency badge, based on efficiency level (1=best, 6=worst).
     * @param efficiency efficiency level (1–6)
     * @return Hex color string
     */
    private String getEfficiencyColor(int efficiency) {
        return switch (efficiency) {
            case 1 -> "#1abc9c"; // Green (Very efficient)
            case 2 -> "#27ae60"; // Greenish
            case 3 -> "#f1c40f"; // Yellow
            case 4 -> "#f39c12"; // Orange
            case 5 -> "#e67e22"; // Orange-Red
            case 6 -> "#e74c3c"; // Red (Least efficient)
            default -> "#bdc3c7"; // Gray (unknown)
        };
    }

    /**
     * Returns a color for the workload bar (green → yellow → red).
     * @param ratio [0=free, 1=busy]
     */
    private Color getWorkloadColor(double ratio) {
        // 0.0 = green (#28b450), 0.5 = yellow (#f9e100), 1.0 = red (#e74c3c)
        if (ratio <= 0.01) {
            return Color.web("#28b450");
        } else if (ratio < 0.5) {
            // green → yellow
            double k = ratio / 0.5;
            return blend(Color.web("#28b450"), Color.web("#f9e100"), k);
        } else if (ratio < 1.0) {
            // yellow → red
            double k = (ratio - 0.5) / 0.5;
            return blend(Color.web("#f9e100"), Color.web("#e74c3c"), k);
        } else {
            return Color.web("#e74c3c");
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
     * Returns the last team member shown by this controller (can be null if none).
     */
    public TeamMember getEditingMember() {
        return editingMember;
    }
}
