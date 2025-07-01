package hit.client.controller;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.hit.controller.ApiResponse;
import com.hit.model.Assignment;
import com.hit.model.TeamMember;
import com.hit.model.Task;
import com.hit.server.Request;
import hit.client.network.NetworkClient;
import hit.client.util.GsonFactory;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;

import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller for the Statistics (Dashboard) screen.
 * Loads and displays all statistical information in real-time, using the existing server API.
 * Handles four main tiles: Load Utilization, KPI, Skills Distribution, and Top Overloaded Members.
 * <p>
 * Designed for use with stats.fxml.
 */
@SuppressWarnings("unchecked")
public class StatsController {

    public GridPane statsRoot;
    // ---- Tile 1: Load Utilization ----
    @FXML private Label loadPercentLabel;
    @FXML private Label loadBadge;
    @FXML private Label avgLoadLabel;

    // ---- Tile 2: Key Metrics ----
    @FXML private Label teamMembersCountLabel;
    @FXML private Label tasksCountLabel;
    @FXML private Label unassignedTasksLabel;
    @FXML private Label avgTaskDurationLabel;

    // ---- Tile 3: Skills Distribution ----
    @FXML private PieChart skillsPieChart;
    @FXML private Label skillsNoteLabel;

    // ---- Tile 4: Overloaded Members ----
    @FXML private TableView<OverloadedRow> overloadedTable;
    @FXML private TableColumn<OverloadedRow, String> memberNameCol;
    @FXML private TableColumn<OverloadedRow, Integer> taskCountCol;
    @FXML private Label overloadedNoteLabel;

    // --- Initialization (called automatically by JavaFX after FXML load) ---
    @FXML
    public void initialize() {
        // Prepare table columns (Overloaded Members tile)
        memberNameCol.setCellValueFactory(
                cell -> new SimpleStringProperty(cell.getValue().name()));

        taskCountCol.setCellValueFactory(
                cell -> new SimpleIntegerProperty(cell.getValue().taskCount()).asObject());

        // Start async loading of all stats
        loadAllStats();
    }

    /** Loads all statistics asynchronously from the server and populates all tiles. */
    private void loadAllStats() {
        // Parallel requests, each updating its tile when ready

        loadLoadUtilizationTile();
        loadKeyMetricsTile();
        loadSkillsDistributionTile();
        loadOverloadedMembersTile();
    }

    // --------------- TILE 1: LOAD UTILIZATION ---------------

    /** Loads and updates the Load Utilization tile. */
    @SuppressWarnings("MismatchedReadAndWriteOfArray")
    private void loadLoadUtilizationTile() {
        // Get averageLoad (float), team member count (int), tasks count (int) in parallel
        Request avgLoadReq = new Request(Map.of("action", "member/averageLoad"), null);
        Request memberCountReq = new Request(Map.of("action", "member/count"), null);
        Request taskCountReq = new Request(Map.of("action", "task/count"), null);

        final int[] teamMembers = {0};
        final int[] tasks = {0};
        final double[] avgLoad = {0};

        Runnable tryUpdate = getRunnable(teamMembers, avgLoad);

        NetworkClient.sendRequestAsync(avgLoadReq, (resp, _) -> {
            avgLoad[0] = (resp != null && resp.getData() != null)
                    ? ((Number) resp.getData()).doubleValue() : 0.0;
            tryUpdate.run();
        });

        NetworkClient.sendRequestAsync(memberCountReq, (resp, _) -> {
            teamMembers[0] = (resp != null && resp.getData() != null)
                    ? ((Number) resp.getData()).intValue() : 0;
            tryUpdate.run();
        });

        NetworkClient.sendRequestAsync(taskCountReq, (resp, _) -> {
            tasks[0] = (resp != null && resp.getData() != null)
                    ? ((Number) resp.getData()).intValue() : 0;
            tryUpdate.run();
        });
    }

    private Runnable getRunnable(int[] teamMembers, double[] avgLoad) {
        final int[] readyCount = {0};

        // Assume 6 as "full" load
        return () -> {
            readyCount[0]++;
            if (readyCount[0] == 3 && teamMembers[0] > 0) {
                double loadPercent = Math.min((avgLoad[0] / 6.0) * 100, 100); // Assume 6 as "full" load
                Platform.runLater(() -> {
                    loadPercentLabel.setText(String.format(Locale.US, "%.0f%%", loadPercent));
                    avgLoadLabel.setText(String.format(Locale.US, "Avg. %.2f assignments per member", avgLoad[0]));
                    if (loadPercent >= 80) {
                        loadBadge.setText("HIGH");
                        loadBadge.getStyleClass().setAll("load-badge-high");
                    } else if (loadPercent >= 50) {
                        loadBadge.setText("MEDIUM");
                        loadBadge.getStyleClass().setAll("load-badge-medium");
                    } else {
                        loadBadge.setText("LOW");
                        loadBadge.getStyleClass().setAll("load-badge-low");
                    }
                });
            }
        };
    }

    // --------------- TILE 2: KEY METRICS ---------------

    /** Loads and updates the KPI tile (team members, tasks, unassigned, avg duration). */
    private void loadKeyMetricsTile() {
        // 1. Team members count
        Request memberCountReq = new Request(Map.of("action", "member/count"), null);
        NetworkClient.sendRequestAsync(memberCountReq, (resp, _) -> {
            int count = (resp != null && resp.getData() != null)
                    ? ((Number) resp.getData()).intValue() : 0;
            Platform.runLater(() -> teamMembersCountLabel.setText(String.valueOf(count)));
        });

        // 2. Tasks count
        Request taskCountReq = new Request(Map.of("action", "task/count"), null);
        NetworkClient.sendRequestAsync(taskCountReq, (resp, _) -> {
            int count = (resp != null && resp.getData() != null)
                    ? ((Number) resp.getData()).intValue() : 0;
            Platform.runLater(() -> tasksCountLabel.setText(String.valueOf(count)));
        });

        // 3. Unassigned tasks
        Request unassignedReq = new Request(Map.of("action", "task/countUnassigned"), null);
        NetworkClient.sendRequestAsync(unassignedReq, (resp, _) -> {
            int count = (resp != null && resp.getData() != null)
                    ? ((Number) resp.getData()).intValue() : 0;
            Platform.runLater(() -> unassignedTasksLabel.setText(String.valueOf(count)));
        });

        // 4. Average task duration (computed on a client)
        Request allTasksReq = new Request(Map.of("action", "task/getAll"), null);
        Type tasksListType = new TypeToken<ApiResponse<List<Task>>>(){}.getType();

        NetworkClient.sendRequestAsyncTyped(allTasksReq, tasksListType, (resp, _) -> {
            double avg = 0.0;
            Gson gson = GsonFactory.get();
            if (resp != null && resp.getData() instanceof List<?> list && !list.isEmpty()) {
                List<Task> tasks;
                Object first = list.getFirst();
                // If objects are already Task
                if (first instanceof Task) {
                    tasks = (List<Task>) list;
                } else {
                    // Fallback: convert from Map to Task using Gson
                    tasks = list.stream()
                            .map(obj -> gson.fromJson(gson.toJson(obj), Task.class))
                            .collect(Collectors.toList());
                }
                avg = tasks.stream().mapToInt(Task::getDurationHours).average().orElse(0.0);
            }
            double finalAvg = avg;
            Platform.runLater(() -> avgTaskDurationLabel.setText(String.format(Locale.US, "%.1fh", finalAvg)));
        });


    }

    // --------------- TILE 3: SKILLS DISTRIBUTION ---------------

    /** Loads and updates the skills distribution pie chart. */
    private void loadSkillsDistributionTile() {
        Request allMembersReq = new Request(Map.of("action", "member/getAll"), null);
        Type membersListType = new TypeToken<ApiResponse<List<TeamMember>>>(){}.getType();
        NetworkClient.sendRequestAsyncTyped(allMembersReq, membersListType, (resp, _) -> {
            Gson gson = GsonFactory.get();
            List<TeamMember> members = new ArrayList<>();
            if (resp != null && resp.getData() instanceof List<?> list && !list.isEmpty()) {
                Object first = list.getFirst();
                if (first instanceof TeamMember) {
                    members = (List<TeamMember>) list;
                } else {
                    members = list.stream()
                            .map(obj -> gson.fromJson(gson.toJson(obj), TeamMember.class))
                            .collect(Collectors.toList());
                }
            }
            // Now you can safely iterate:
            Map<String, Integer> skillCounts = new HashMap<>();
            for (TeamMember member : members) {
                if (member.getSkills() != null) {
                    for (String skill : member.getSkills()) {
                        skillCounts.put(skill, skillCounts.getOrDefault(skill, 0) + 1);
                    }
                }
            }
            List<PieChart.Data> pieData = skillCounts.entrySet().stream()
                    .map(e -> new PieChart.Data(e.getKey(), e.getValue()))
                    .collect(Collectors.toList());
            Platform.runLater(() -> {
                skillsPieChart.setData(FXCollections.observableArrayList(pieData));
                if (pieData.isEmpty()) {
                    skillsNoteLabel.setText("No skills data found.");
                } else {
                    skillsNoteLabel.setText(String.format("Total unique skills: %d", pieData.size()));
                }
            });
        });

    }

    // --------------- TILE 4: TOP OVERLOADED MEMBERS ---------------

    /** Loads and updates the table of most overloaded members. */
    private void loadOverloadedMembersTile() {
        Request assignmentsReq = new Request(Map.of("action", "assignment/getAll"), null);
        Request membersReq = new Request(Map.of("action", "member/getAll"), null);
        Type assignmentsListType = new TypeToken<ApiResponse<List<Assignment>>>(){}.getType();
        Type membersListType = new TypeToken<ApiResponse<List<TeamMember>>>(){}.getType();

        final List<Assignment>[] assignments = new List[]{null};
        final List<TeamMember>[] members = new List[]{null};

        Runnable tryUpdate = () -> {
            if (assignments[0] != null && members[0] != null) {
                Map<String, Long> tasksPerMember = assignments[0].stream()
                        .collect(Collectors.groupingBy(Assignment::getMemberId, Collectors.counting()));

                List<OverloadedRow> rows = members[0].stream()
                        .filter(m -> tasksPerMember.containsKey(m.getId()))
                        .map(m -> new OverloadedRow(m.getName(), tasksPerMember.get(m.getId()).intValue()))
                        .sorted(Comparator.comparingInt(OverloadedRow::taskCount).reversed())
                        .limit(3)
                        .collect(Collectors.toList());

                Platform.runLater(() -> {
                    overloadedTable.getItems().setAll(rows);
                    if (rows.isEmpty()) {
                        overloadedNoteLabel.setText("No overloaded members found.");
                    } else {
                        overloadedNoteLabel.setText("Shows up to top 3 members with highest load");
                    }
                });
            }
        };

        NetworkClient.sendRequestAsyncTyped(assignmentsReq, assignmentsListType, (resp, _) -> {
            assignments[0] = (resp != null && resp.getData() != null) ? (List<Assignment>) resp.getData() : Collections.emptyList();
            tryUpdate.run();
        });
        NetworkClient.sendRequestAsyncTyped(membersReq, membersListType, (resp, _) -> {
            members[0] = (resp != null && resp.getData() != null) ? (List<TeamMember>) resp.getData() : Collections.emptyList();
            tryUpdate.run();
        });
    }

    // ----------- Helper class for overloaded members table -----------
        public record OverloadedRow(String name, int taskCount) {
        public String getName()      { return name; }
        public int    getTaskCount() { return taskCount; }
    }
}
