package hit.client;

import hit.client.util.ViewNavigator;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects;

/**
 * Main entry-point for the SmartScheduler desktop application.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Create the <strong>central BorderPane</strong> that hosts the dynamic screens.</li>
 *   <li>Inform {@link ViewNavigator} which container to inject into.</li>
 *   <li>Load <code>sidebar.fxml</code> (the navigation column) and dock it at the left.</li>
 *   <li>Show a sized, titled primary {@link Stage}.</li>
 * </ul>
 *
 * <p>All UI files are expected on the class-path under
 * <code>/hit/client/view/</code>.</p>
 */
public final class SmartSchedulerUI extends Application {

    /** Default application window size. */
    private static final double APP_WIDTH  = 900;
    private static final double APP_HEIGHT = 600;

    @Override
    public void start(Stage primaryStage) throws IOException {

        /* 1.  Central area – every screen gets injected into this BorderPane */
        BorderPane centerPane = new BorderPane();
        centerPane.setPrefSize(APP_WIDTH, APP_HEIGHT);

        /* 2.  Tell the navigation helper where to inject future screens      */
        ViewNavigator.setRoot(centerPane);

        /* 3.  Load the sidebar (navigation) – FXML must be on the class-path */
        Parent sidebar = FXMLLoader.load(Objects.requireNonNull(
                getClass().getResource("/hit/client/view/sidebar.fxml")));

        /* 4.  Assemble the root layout: sidebar (left) + changing center     */
        BorderPane root = new BorderPane(centerPane);  // center
        root.setLeft(sidebar);                         // navigation column

        /* 5.  Stage setup                                                   */
        primaryStage.setTitle("SmartScheduler");
        primaryStage.setScene(new Scene(root));
        primaryStage.show();
    }

    /**
     * Standard <code>main</code> delegate – keeps jar/IDE launch simple.
     */
    public static void main(String[] args) {
        launch(args);
    }
}
