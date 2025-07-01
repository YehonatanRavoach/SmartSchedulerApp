package hit.client.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class HomeController {

    /* New Task */
    @FXML
    private void openNewTask() throws Exception {
        FXMLLoader fx = new FXMLLoader(getClass()
                .getResource("/hit/client/view/task_form.fxml"));
        Stage s = new Stage();
        s.setScene(new Scene(fx.load()));
        hit.client.controller.TaskFormController c = fx.getController();
        c.setEditMode(false);
        s.setTitle("New Task");
        s.show();
    }

    /* New Member */
    @FXML
    private void openNewMember() throws Exception {
        FXMLLoader fx = new FXMLLoader(getClass()
                .getResource("/hit/client/view/member_form.fxml"));
        Stage s = new Stage();
        s.setScene(new Scene(fx.load()));
        hit.client.controller.MemberFormController c = fx.getController();
        c.setEditMode(false);
        s.setTitle("New Member");
        s.show();
    }
}
