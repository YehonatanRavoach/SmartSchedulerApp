package hit.client.controller;

import hit.client.util.ViewNavigator;
import javafx.fxml.FXML;
import javafx.scene.control.ToggleButton;

/**
 * Controller for <b>sidebar.fxml</b>.
 *
 * Pure responsibilities -- no business logic:
 *     • listen to the five ToggleButtons
 *     • delegate navigation to {@link ViewNavigator}
 *
 *  *If you rename / add buttons in FXML just add another handler + enum value.*
 */
public final class SidebarController {

    /* ---------- FXML wires ---------- */
    @FXML private ToggleButton dashboardBtn;
    @FXML private ToggleButton tasksBtn;
    @FXML private ToggleButton membersBtn;
    @FXML private ToggleButton assignmentsBtn;
    @FXML private ToggleButton statsBtn;        //  ← you asked for onStats()

    /* ---------- Lifecycle ---------- */
    @FXML
    private void initialize() {
        dashboardBtn.setSelected(true);
        ViewNavigator.goTo(ViewNavigator.Screen.DASHBOARD);
    }

    /* ---------- Event handlers ---------- */
    @FXML private void onDashboard()   { ViewNavigator.goTo(ViewNavigator.Screen.DASHBOARD); }
    @FXML private void onTasks()       { ViewNavigator.goTo(ViewNavigator.Screen.TASKS); }
    @FXML private void onMembers()     { ViewNavigator.goTo(ViewNavigator.Screen.MEMBERS); }
    @FXML private void onAssignments() { ViewNavigator.goTo(ViewNavigator.Screen.ASSIGNMENTS); }
    @FXML private void onStats()       { ViewNavigator.goTo(ViewNavigator.Screen.STATS); }
}
