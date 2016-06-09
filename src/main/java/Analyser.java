import com.wrapper.spotify.Api;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Random;

/**
 * Created by spring on 6/9/16.
 */
public class Analyser {

    public Analyser(Stage stage){
        this.stage = stage;
        api = Api.builder()
                .clientId(CLIENT_ID)
                .clientSecret(CLIENT_SECRET)
                .redirectURI(REDIRECT_URI)
                .build();
    }

    private static final String CLIENT_ID = "";
    private static final String CLIENT_SECRET = "";
    private static final String REDIRECT_URI = "";
    private Properties properties;
    private InputStream input;
    private OutputStream output;
    private Stage stage;
    private Api api;
    private String accessToken;

    public void generateClientId() {
        properties = new Properties();
        try {
            input = new FileInputStream(getClass().getClassLoader().getResource("config.properties").getFile());
            properties.load(input);
            if (properties.getProperty("clientId") == null){
                input.close();
                output = new FileOutputStream(getClass().getClassLoader().getResource("config.properties").getFile());
                properties.setProperty("clientId", generateString(16));
                properties.store(output, null);
                output.close();
            }
        }
        catch (IOException ex){
            ex.printStackTrace();
        }

    }

    private String generateString(final int length){
        char[] chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890".toCharArray();
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < length; i++) {
            char c = chars[random.nextInt(chars.length)];
            sb.append(c);
        }
        return sb.toString();
    }

    public void authenticate(){

        final List<String> scopes = Arrays.asList("user-read-private");
        final String state = "Initialized";
        String authorizeURL= api.createAuthorizeURL(scopes, state);
        final WebView web = new WebView();
        web.getEngine().load(authorizeURL);
        stage.setScene(new Scene(web));
        stage.show();

        web.getEngine().getLoadWorker().stateProperty().addListener(new ChangeListener<Worker.State>() {
            @Override
            public void changed(ObservableValue<? extends Worker.State> observable, Worker.State oldValue, Worker.State newValue) {
                if (newValue == Worker.State.SUCCEEDED){
                    if (web.getEngine().getLocation().contains("?code=")){
                        String location = web.getEngine().getLocation();
                        accessToken = location.substring(location.indexOf("?code=") + 6, location.indexOf("&state"));
                        System.out.println(String.format("Access token: %s", accessToken));
                        stage.hide();
                    }
                }
            }
        });
        /*
        final ClientCredentialsGrantRequest request = api.clientCredentialsGrant().build();
        final SettableFuture<ClientCredentials> responseFuture = request.getAsync();

        Futures.addCallback(responseFuture, new FutureCallback<ClientCredentials>() {
            public void onSuccess(ClientCredentials clientCredentials) {
                System.out.println(String.format("Access token expires in %d seconds", clientCredentials.getExpiresIn()));
                api.setAccessToken(clientCredentials.getAccessToken());

            }

            public void onFailure(Throwable throwable) {

            }
        });
        */
    }

    public void analyse(){

    }
}
