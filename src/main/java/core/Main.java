package core;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.logging.FileHandler;
import java.util.logging.Logger;

/**
 * Created by spring on 6/9/16.
 */
public class Main extends Application{

    private static Logger logger = Logger.getLogger("log");

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        try {
            logger.addHandler(new FileHandler("logs"));
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/main.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
            primaryStage.setScene(scene);
            primaryStage.setTitle("Bliss");
            primaryStage.setOnCloseRequest( event -> Platform.exit());
            primaryStage.setResizable(false);
            primaryStage.show();
        }
        catch (Exception ex){
            ex.printStackTrace();
            logger.throwing("Main", "start", ex);
        }

    }
}
