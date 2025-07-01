package hit.client.controller;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.hit.model.TeamMember;
import com.hit.server.Request;
import hit.client.network.NetworkClient;
import hit.client.util.AlgorithmChooser;
import hit.client.util.FormNavigator;
import hit.client.util.GsonFactory;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller for the main Members list screen.
 * <ul>
 *   <li>Manages a scrollable, responsive grid of Member cards (2–3 per row, dynamic height).</li>
 *   <li>Supports dynamic filtering (search by name, skills, max hours, ID), sorting by efficiency (highest first), refresh from server, and UI animations.</li>
 *   <li>Handles opening the Member Form for editing, with full context passing.</li>
 * </ul>
 *
 * Usage:
 * - Loads members from the server on a screen load.
 * - Typing in the search bar filters members by any of the configured fields.
 * - Clicking "Refresh" or calling {@link #onRefresh()} reloads members from the server.
 * - Clicking a card opens an editable MemberForm.
 */
@SuppressWarnings("CallToPrintStackTrace")
public class MembersController {
    @FXML private FlowPane membersFlow;
    @FXML private ScrollPane scrollPane;
    @FXML private TextField searchField;

    /** Holds all members loaded from the backend. */
    private List<TeamMember> allMembers = new ArrayList<>();

    /** The current filtered list for display. */
    private List<TeamMember> filteredMembers = new ArrayList<>();

    // Server config
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 34567;
    private final Gson gson = GsonFactory.get();

    @FXML
    public void initialize() {
        loadMembersFromServer();

        // Live filtering as user types in the search field
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilterAndRefresh());
    }

    /**
     * Loads members from the backend server via Socket/JSON (action: "member/getAll").
     * Updates UI asynchronously.
     */
    private void loadMembersFromServer() {
        new Thread(() -> {
            try {
                Request req = new Request(Map.of("action", "member/getAll"), Map.of());
                try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
                     PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
                     BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                    writer.println(gson.toJson(req));
                    String jsonResp = reader.readLine();

                    // The server returns: ApiResponse<List<TeamMember>>
                    Map<?, ?> resp = gson.fromJson(jsonResp, Map.class);
                    Object dataObj = resp.get("data");
                    List<TeamMember> loadedMembers = gson.fromJson(gson.toJson(dataObj), new TypeToken<List<TeamMember>>(){}.getType());

                    if (loadedMembers != null) {
                        Platform.runLater(() -> {
                            this.allMembers = loadedMembers;
                            applyFilterAndRefresh();
                        });
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                Platform.runLater(() -> {
                    this.allMembers = Collections.emptyList();
                    applyFilterAndRefresh();
                });
            }
        }).start();
    }

    /**
     * Applies the search filter and updates the displayed member cards.
     * The filter checks for a match in name, skills, max hours, or ID.
     */
    private void applyFilterAndRefresh() {
        String filter = searchField.getText();
        if (filter == null || filter.trim().isEmpty()) {
            filteredMembers = new ArrayList<>(allMembers);
        } else {
            String lower = filter.trim().toLowerCase();
            filteredMembers = allMembers.stream()
                    .filter(member -> matchesFilter(member, lower))
                    .collect(Collectors.toList());
        }
        refreshMemberCards();
    }

    /**
     * Checks if a member matches the search filter.
     * Supports matching by name, any skill, max hours, or ID.
     *
     * @param member TeamMember to check
     * @param filter Lower-case search text
     * @return true if the member matches, false otherwise
     */
    private boolean matchesFilter(TeamMember member, String filter) {
        // Match by name
        if (member.getName() != null && member.getName().toLowerCase().contains(filter))
            return true;
        // Match by ID
        if (member.getId() != null && member.getId().toLowerCase().contains(filter))
            return true;
        // Match by any skill
        if (member.getSkills() != null && member.getSkills().stream()
                .anyMatch(skill -> skill != null && skill.toLowerCase().contains(filter)))
            return true;
        // Match by max hours (as text)
        return String.valueOf(member.getMaxHoursPerDay()).contains(filter);
    }

    /**
     * Refreshes the UI with the current filtered list of members, sorted by efficiency (the lowest value = best).
     */
    private void refreshMemberCards() {
        membersFlow.getChildren().clear();
        List<TeamMember> sorted = filteredMembers.stream()
                .sorted(Comparator.comparingDouble(TeamMember::getEfficiency) // 1 = best
                        .thenComparing(TeamMember::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
        for (TeamMember member : sorted) {
            Node card = createMemberCard(member);
            membersFlow.getChildren().add(card);
        }
    }

    /**
     * Loads a MemberCard FXML, binds data, and hooks up UI events/animations.
     * @param member The member to display
     * @return Node representing the card
     */
    private Node createMemberCard(TeamMember member) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/hit/client/view/MemberCard.fxml"));
            Region card = loader.load();
            MemberCardController controller = loader.getController();
            controller.setMember(member);

            controller.setOnAssignMember(this::assignTasksForMember);
            // Animation on hover
            card.setOnMouseEntered(_ -> card.setStyle("-fx-effect: dropshadow(gaussian, #5c7aff33, 16, 0.18, 0, 4); -fx-translate-y: -5;"));
            card.setOnMouseExited(_ -> card.setStyle(""));

            // Open edit form on click
            card.setOnMouseClicked(_ -> openMemberForm(member));
            card.setCursor(javafx.scene.Cursor.HAND);

            return card;
        } catch (Exception ex) {
            ex.printStackTrace();
            return new javafx.scene.control.Label("Error loading card: " + ex.getMessage());
        }
    }

    /* -----------------------------------------------------------
     * Assign *all suitable* tasks to a single member
     * --------------------------------------------------------- */
    private void assignTasksForMember(TeamMember m) {
        if (m == null) return;

        AlgorithmChooser.showAndRun(strategy -> {
            Request req = new Request(
                    Map.of("action", "assignment/assignForMember"),
                    Map.of("memberId", m.getId(), "strategy", strategy));

            NetworkClient.sendRequestAsync(req, (resp, err) -> {
                if (err != null || resp == null || !resp.isSuccess()) {
                    showError("Failed to assign tasks to member",
                            err != null ? err.getMessage()
                                    : (resp != null ? resp.getMessage() : ""));
                } else {
                    loadMembersFromServer();
                }
            });
        });
    }


    /**
     * Opens the Member editing form as a floating modal, with all values populated.
     *
     * @param member TeamMember to edit
     */
    private void openMemberForm(TeamMember member) {
        FormNavigator.openMemberForm(member, true);
        loadMembersFromServer();
    }

    @FXML
    private void onAddMember() {
        FormNavigator.openMemberForm(null, false);
        loadMembersFromServer();
    }

    /** Refresh button handler. Reloads from the server. */
    @FXML
    private void onRefresh() {
        loadMembersFromServer();
    }

    /** Utility to show error dialogs with context. */
    private void showError(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error: " + title);
            alert.setHeaderText(title);
            alert.setContentText(message != null ? message : "Unknown error.");
            alert.showAndWait();
        });
    }

}
