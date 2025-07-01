module SmartSchedulerUI {

    /* ---------- Dependencies ---------- */
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires org.controlsfx.controls;
    requires com.google.gson;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.core;
    requires org.kordamp.ikonli.materialdesign2;
    requires eu.hansolo.tilesfx;
    requires AlgorithmModule;
    requires ServerJAR;

    /* ---------- Public API exported to the outside world ---------- */
    exports hit.client.util;               // e.g. ViewNavigator
    exports hit.client.network;            // anything the server/adapters use

    /* ---------- JavaFX controllers opened for reflection ---------- */
    opens hit.client.controller to javafx.fxml, com.google.gson;
    exports hit.client;
    /* ---------- (Optional) internal packages opened to Gson etc. -- */
    opens hit.client.util   to com.google.gson;
}
