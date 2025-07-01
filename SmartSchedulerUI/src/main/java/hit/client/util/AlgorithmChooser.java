package hit.client.util;

import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.Objects;
import java.util.function.Consumer;

/** A modal that lets the user choose “Greedy” or “Balanced.” */
public final class AlgorithmChooser {

    public static void showAndRun(Consumer<String> onSelected) {
        /* ── build UI ───────────────────────────────────────────── */
        ToggleGroup tg = new ToggleGroup();

        ToggleButton greedy   = makeToggle("Greedy",   tg);
        ToggleButton balanced = makeToggle("Balanced", tg);
        greedy.setSelected(true);

        HBox toggles = new HBox(12, greedy, balanced);
        toggles.setAlignment(Pos.CENTER);

        Button ok = new Button("Continue");
        ok.setDefaultButton(true);
        ok.getStyleClass().add("dialog-main-btn");
        ok.setOnAction(e -> {
            String strat = greedy.isSelected() ? "greedy" : "balanced";
            onSelected.accept(strat);
            ((Stage) ok.getScene().getWindow()).close();
        });

        VBox root = new VBox(24, toggles, ok);
        root.setAlignment(Pos.CENTER);
        root.getStyleClass().add("dialog-root");

        /* ── show modal ─────────────────────────────────────────── */
        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setResizable(false);

        Scene scene = new Scene(root);
        scene.getStylesheets().add(Objects.requireNonNull(AlgorithmChooser.class.getResource("/hit/client/assets/style.css")).toExternalForm());
        dlg.setScene(scene);
        dlg.setTitle("Choose algorithm");
        dlg.showAndWait();
    }

    /* helper */
    private static ToggleButton makeToggle(String text, ToggleGroup tg) {
        ToggleButton tb = new ToggleButton(text);
        tb.setToggleGroup(tg);
        tb.getStyleClass().add("algo-toggle");
        return tb;
    }
}
