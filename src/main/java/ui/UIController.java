package ui;

import core.Analyser;
import db.Mongo;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import util.Config;
import util.Writer;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ResourceBundle;

/**
 * Created by spring on 6/11/16.
 */
public class UIController implements Initializable {

    public UIController(){

    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        Writer writer = Writer.getInstance();
        String uri = writer.getProperty(Config.URI.getVal());
        String port = writer.getProperty(Config.PORT.getVal());
        String collection = writer.getProperty(Config.COLLECTION.getVal());
        if (null != uri)
            txt_uri.setText(uri);
        if (null != port)
            txt_port.setText(port);
        if (null != collection)
            txt_collection.setText(collection);
    }

    @FXML
    Label lbl_status;
    @FXML
    Button btn_analyse;
    @FXML
    TextField txt_uri;
    @FXML
    TextField txt_port;
    @FXML
    TextField txt_collection;
    @FXML
    TextField txt_json;
    @FXML
    Button btn_send;
    @FXML
    Button btn_chooser;

    private Analyser analyser;

    public void setStatus(String status){
        lbl_status.setText(status);
    }

    public void btn_begin(ActionEvent actionEvent) {
        if (!txt_uri.getText().isEmpty() && !txt_port.getText().isEmpty() && !txt_collection.getText().isEmpty()) {
            int port = Integer.parseInt(txt_port.getText());
            Mongo mongo = Mongo.getInstance();
            mongo.setUri(txt_uri.getText());
            mongo.setPort(port);
            mongo.setColName(txt_collection.getText());
            mongo.connect();
            analyser = new Analyser(this);
            analyser.connect();
            btn_analyse.setVisible(false);
            txt_uri.setVisible(false);
            txt_port.setVisible(false);
            txt_collection.setVisible(false);
            lbl_status.setVisible(true);
            setStatus("Getting jsons from remote server");

        }

    }

    public void send(ActionEvent actionEvent) {
        if (!txt_json.getText().isEmpty()){
            Path path = Paths.get(txt_json.getText());
            if (Files.exists(path)){
                if (analyser == null)
                    analyser = new Analyser(this);
                analyser.sendData(path.toFile());
            }
        }
    }

    public void choose(ActionEvent actionEvent){
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open json file");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        File selectedFile = fileChooser.showOpenDialog(null);
        txt_json.setText(selectedFile.getPath());
    }
}
