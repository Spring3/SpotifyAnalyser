package ui;

import core.Analyser;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;

/**
 * Created by spring on 6/11/16.
 */
public class UIController {

    public UIController(){

    }

    @FXML
    Label lbl_status;
    @FXML
    Label lbl_success;
    @FXML
    ProgressBar progress;
    @FXML
    Button btn_analyse;
    @FXML
    Button btn_switchAcc;
    private Analyser analyser;
    private double currentProgress;

    public void setStatus(String status){
        lbl_status.setText(status);
    }

    public void showSuccess(boolean show){
        lbl_success.setVisible(show);
    }

    public synchronized void updateProgress(double progress){
        currentProgress += progress;
        this.progress.setProgress(currentProgress);
        if (currentProgress == 1 || 1 - currentProgress < 0.01){
            showSuccess(true);
            setStatus("Thank you! See you around!");
            btn_switchAcc.setVisible(true);
        }
    }

    public void setAnalyser(Analyser instance){
        analyser = instance;
    }

    public void btn_begin(ActionEvent actionEvent) {
        analyser = new Analyser(this);
        analyser.getInfo();
        btn_analyse.setVisible(false);
        lbl_status.setVisible(true);
        progress.setVisible(true);
    }

    public void btn_logout(ActionEvent actionEvent) {
        analyser.clearProperties();
        analyser = new Analyser(this);
        lbl_status.setVisible(true);
        lbl_success.setVisible(false);
        progress.setProgress(0);
        progress.setVisible(true);
        btn_switchAcc.setVisible(false);
        analyser.getInfo();
    }
}
