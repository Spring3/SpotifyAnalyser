import javafx.application.Application;
import javafx.stage.Stage;

/**
 * Created by spring on 6/9/16.
 */
public class Main extends Application{

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        Analyser analyser = new Analyser(primaryStage);
        analyser.getInfo();

    }
}
