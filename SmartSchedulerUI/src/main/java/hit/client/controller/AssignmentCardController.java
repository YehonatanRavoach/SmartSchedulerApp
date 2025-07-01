package hit.client.controller;

import com.hit.model.Assignment;
import com.hit.model.Task;
import com.hit.model.TeamMember;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;

/**
 * Controller for a single <b>Assignment Card</b>.
 *
 * <p>The card is intentionally “dumb”: it knows how to <em>display</em> an
 * {@link Assignment} (IDs + hours) and raises callbacks when the user clicks
 * the Task ID, Member ID or the delete button.
 * All real work (navigation, network calls, confirmations) is handled by the
 * owning <code>AssignmentsController</code>.</p>
 *
 * <h2>Integrator’s guide</h2>
 * <pre>{@code
 * AssignmentCardController c = loader.getController();
 * c.setAssignment(a, task, member);
 * c.setOnTaskClicked( () -> openTask(task) );
 * c.setOnMemberClicked( () -> openMember(member) );
 * c.setOnDeleteClicked( () -> deleteAssignment(a) );
 * }</pre>
 *
 * <ul>
 *   <li>Only the IDs are rendered in the UI to keep the list compact.</li>
 *   <li>The full Task / Member names are shown as tool-tips.</li>
 *   <li>All setters are <em>optional</em> – if you do not set a callback the
 *       click is simply ignored.</li>
 * </ul>
 */
public class AssignmentCardController {

    /* ----------  FXML fields  ---------- */

    @FXML private Hyperlink taskIdLink;      // e.g. [T5678]
    @FXML private Hyperlink memberIdLink;    // e.g. [M314]
    @FXML private Label     assignedHoursLabel;
    @FXML private Button    deleteBtn;

    /* ----------  Backing model  ---------- */

    private Assignment assignment;
    private Task       task;
    private TeamMember member;

    /* ----------  Delegates installed by parent  ---------- */

    private Runnable onTaskClicked;
    private Runnable onMemberClicked;
    private Runnable onDeleteClicked;

    /* ---------------------------------------------------------------------- *
     *  Public API                                                             *
     * ---------------------------------------------------------------------- */

    /**
     * Populates the card with data and tool-tips.
     *
     * @param assignment assignment record (may be {@code null})
     * @param task       matching task (nullable)
     * @param member     matching team-member (nullable)
     */
    public void setAssignment(Assignment assignment, Task task, TeamMember member) {
        this.assignment = assignment;
        this.task       = task;
        this.member     = member;

        /* --- render IDs only --- */
        taskIdLink.setText(   task   != null ? task.getId()   : "?");
        memberIdLink.setText( member != null ? member.getId() : "?");
        assignedHoursLabel.setText(
                assignment != null ? String.valueOf(assignment.getAssignedHours()) : "?");

        /* --- tool-tips with full names --- */
        Tooltip.install(taskIdLink,
                new Tooltip(task   != null ? task.getName()   : taskIdLink.getText()));
        Tooltip.install(memberIdLink,
                new Tooltip(member != null ? member.getName() : memberIdLink.getText()));
    }

    /* ------------ delegate setters ------------ */

    public void setOnTaskClicked  (Runnable r) { this.onTaskClicked   = r; }
    public void setOnMemberClicked(Runnable r) { this.onMemberClicked = r; }
    public void setOnDeleteClicked(Runnable r) { this.onDeleteClicked = r; }

    /* ----------  FXML event handlers ---------- */

    @FXML private void onTaskClicked()   { if (onTaskClicked   != null) onTaskClicked.run(); }
    @FXML private void onMemberClicked() { if (onMemberClicked != null) onMemberClicked.run(); }
    @FXML private void onDeleteClicked() { if (onDeleteClicked != null) onDeleteClicked.run(); }

    /* ----------  Optional getters  ---------- */

    public Assignment getAssignment() { return assignment; }
    public Task       getTask()       { return task; }
    public TeamMember getMember()     { return member; }
}
