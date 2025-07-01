package hit.client.util;

import com.hit.model.Task;
import com.hit.model.TeamMember;
import hit.client.controller.TaskFormController;
import hit.client.controller.MemberFormController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Central utility for opening modal forms (Task / Member) from anywhere.
 * All controllers can call the static methods – no more copy-pasting FXML loaders.
 *
 * All opened forms are draggable, even when undecorated or transparent.
 */
@SuppressWarnings("CallToPrintStackTrace")
public final class FormNavigator {

    private FormNavigator() {} // Utility – no instances

    /**
     * Opens the Task form as a draggable transparent modal dialog.
     * @param task Task to edit (null for creation)
     * @param editMode true = edit mode, false = create mode
     */
    public static void openTaskForm(Task task, boolean editMode) {
        try {
            FXMLLoader fx = new FXMLLoader(
                    FormNavigator.class.getResource("/hit/client/view/task_form.fxml"));
            Parent root = fx.load();

            TaskFormController c = fx.getController();
            c.setEditMode(editMode);
            c.setTask(task);

            Stage dlg = buildDraggableModalStage(root, "Task");
            dlg.showAndWait();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Opens the Member form as a draggable transparent modal dialog.
     * @param member Member to edit (null for create)
     * @param editMode true = edit mode, false = create mode
     */
    public static void openMemberForm(TeamMember member, boolean editMode) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    FormNavigator.class.getResource("/hit/client/view/member_form.fxml"));
            Parent root = loader.load();

            MemberFormController c = loader.getController();
            c.setEditMode(editMode);
            c.setMember(member);

            Stage stage = buildDraggableModalStage(root, "Member");
            stage.showAndWait();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Creates a draggable transparent modal stage.
     * Any mouse press & drag on the root will move the window.
     * @param root FXML root node
     * @param title Window title (optional may not be visible in transparent style)
     * @return Configured Stage
     */
    private static Stage buildDraggableModalStage(Parent root, String title) {
        Stage s = new Stage(StageStyle.TRANSPARENT);
        Scene scene = new Scene(root);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        s.setScene(scene);
        s.initModality(Modality.APPLICATION_MODAL);
        s.setResizable(false);
        s.setTitle(title);

        // --- Add drag support ---
        final Delta dragDelta = new Delta();
        root.setOnMousePressed(e -> {
            dragDelta.x = s.getX() - e.getScreenX();
            dragDelta.y = s.getY() - e.getScreenY();
        });
        root.setOnMouseDragged(e -> {
            s.setX(e.getScreenX() + dragDelta.x);
            s.setY(e.getScreenY() + dragDelta.y);
        });

        return s;
    }

    /** Helper class for window dragging. */
    private static class Delta {
        double x, y;
    }
}
