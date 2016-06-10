import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import com.wrapper.spotify.Api;
import com.wrapper.spotify.models.AuthorizationCodeCredentials;
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
    private String appKey;

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
        final String authorizeURL= api.createAuthorizeURL(scopes, state);
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
                        appKey = location.substring(location.indexOf("?code=") + 6, location.indexOf("&state"));
                        System.out.println(String.format("App key: %s", appKey));
                        stage.hide();
                        getAccessToken();
                    }
                }
            }
        });

    }

    private void getAccessToken(){
        SettableFuture<AuthorizationCodeCredentials> authorizationCodeCredentialsSettableFuture = api.authorizationCodeGrant(appKey).build().getAsync();

        Futures.addCallback(authorizationCodeCredentialsSettableFuture, new FutureCallback<AuthorizationCodeCredentials>() {
            @Override
            public void onSuccess(AuthorizationCodeCredentials authorizationCodeCredentials) {
                System.out.println(String.format("Access token: %s", authorizationCodeCredentials.getAccessToken()));
                System.out.println(String.format("Refresh token: %s", authorizationCodeCredentials.getRefreshToken()));

                api.setAccessToken(authorizationCodeCredentials.getAccessToken());
                api.setRefreshToken(authorizationCodeCredentials.getRefreshToken());
            }

            @Override
            public void onFailure(Throwable throwable) {

            }
        });
    }

    public void analyse(){

    }
}
