package hit.client.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.BorderPane;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;

/**
 * One-liner helper that swaps the <CENTER> of the root BorderPane.
 * Keeps navigation logic out of the UI controllers.
 */
public final class ViewNavigator {

    /* ------------------------------------------------------------------ */
    /* 1)  Screens enum                                                    */
    /* ------------------------------------------------------------------ */
    public enum Screen { DASHBOARD, TASKS, MEMBERS, ASSIGNMENTS, STATS }

    /* Map screen → fxml path (add routes in one place) */
    private static final Map<Screen, String> ROUTES = Map.of(
            Screen.DASHBOARD,    "/hit/client/view/home.fxml",
            Screen.TASKS,        "/hit/client/view/tasks.fxml",
            Screen.MEMBERS,      "/hit/client/view/members.fxml",
            Screen.ASSIGNMENTS,  "/hit/client/view/assignments.fxml",
            Screen.STATS,        "/hit/client/view/stats.fxml"
    );

    /* ------------------------------------------------------------------ */
    /* 2)  Hosting BorderPane injection                                   */
    /* ------------------------------------------------------------------ */
    private static BorderPane root;          // <BorderPane> from Main.fxml

    /** Call once from your Main / RootController */
    public static void setRoot(BorderPane bp) { root = bp; }

    /* ------------------------------------------------------------------ */
    /* 3)  Public navigation API                                          */
    /* ------------------------------------------------------------------ */
    @SuppressWarnings("CallToPrintStackTrace")
    public static void goTo(Screen target) {
        if (root == null) throw new IllegalStateException("ViewNavigator root not set");

        String fxml = ROUTES.get(target);
        if (fxml == null) throw new IllegalArgumentException("No route for " + target);

        try {
            Parent content = FXMLLoader.load(Objects.requireNonNull(ViewNavigator.class.getResource(fxml)));
            root.setCenter(content);
        } catch (IOException e) {
            e.printStackTrace();
            // optional: show toast / dialog
        }
    }

    /* prevent instantiation */
    private ViewNavigator() {}
}
