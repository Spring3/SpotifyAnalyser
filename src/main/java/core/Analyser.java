package core;

import com.wrapper.spotify.Api;
import com.wrapper.spotify.methods.CurrentUserRequest;
import com.wrapper.spotify.models.*;
import db.Mongo;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import ui.UIController;
import util.*;
import util.Writer;

import java.io.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Logger;

/**
 * Created by spring on 6/9/16.
 */
public class Analyser{

    private static final String CLIENT_ID = "62f4b3411b1d4a9c92d9cb621c3daf5a";
    private static final String CLIENT_SECRET = "3c300aa46ff843d2b912c89ad1aa52e9";
    private static final String REDIRECT_URI = "https://tracksdata.herokuapp.com/rest/callback";
    private static Logger logger = Logger.getLogger("log");

    public Analyser(UIController controller){
        userData = new Data();
        uiController = controller;
        writer = Writer.getInstance();
        mongo = Mongo.getInstance();
    }

    private UIController uiController;
    private Api api = Api.builder()
            .clientId(CLIENT_ID)
            .clientSecret(CLIENT_SECRET)
            .redirectURI(REDIRECT_URI)
            .build();

    private final Data userData;
    private final Mongo mongo;
    private final Writer writer;

    public void connect(){
        uiController.setStatus("Connection established");
        writer.writeProperty(Config.URI.getVal(), mongo.getUri());
        writer.writeProperty(Config.PORT.getVal(), mongo.getPort() + "");
        writer.writeProperty(Config.COLLECTION.getVal(), mongo.getColName());

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                ping();
                checkConfig();
                if (writer.getProperty(Config.USERID.getVal()) == null) {
                    Platform.runLater(() -> {
                        authenticate();
                    });

                }else {
                    fillDataBase();
                }
            }
        });
        thread.setDaemon(true);
        thread.start();

    }

    private void checkConfig(){
        if (writer.getProperty(Config.USERID.getVal()) != null){
            LocalDateTime time = (LocalDateTime.parse(writer.getProperty(Config.EXP.getVal())));
            userData.setTokenExpTime(time);
            userData.setUserId(writer.getProperty(Config.USERID.getVal()));
            api.setAccessToken(writer.getProperty(Config.AT.getVal()));
            api.setRefreshToken(writer.getProperty(Config.RT.getVal()));
            if (userData.isTokenExpired()){
                refreshToken();
            }
        }
    }

    private void refreshToken(){
        try {
            System.out.println("Refreshing token...");
            final RefreshAccessTokenCredentials refreshAccessTokenCredentials = api.refreshAccessToken().build().get();

            writer.writeProperty(Config.AT.getVal(), refreshAccessTokenCredentials.getAccessToken());
            api.setAccessToken(refreshAccessTokenCredentials.getAccessToken());
            LocalDateTime tokenExpTime = LocalDateTime.now().plusHours(1);
            writer.writeProperty(Config.EXP.getVal(), tokenExpTime.toString());
            userData.setTokenExpTime(tokenExpTime);
        }
        catch (Exception ex){
            ex.printStackTrace();
        }
    }

    private void authenticate(){

        Stage stage = new Stage();
        final List<String> scopes = Arrays.asList("user-read-private", "user-library-read", "playlist-read-private", "playlist-read-collaborative");
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
                        String appKey = location.substring(location.indexOf("?code=") + 6, location.indexOf("&state"));
                        System.out.println(String.format("App key: %s", appKey));
                        stage.close();
                        getAccessToken(appKey);
                        getCurrentUser();
                        connect();
                    }
                }
            }
        });
    }

    private void getAccessToken(String appKey) {
        try {
            AuthorizationCodeCredentials authorizationCodeCredentials = api.authorizationCodeGrant(appKey).build().get();

            System.out.println(String.format("Access token: %s", authorizationCodeCredentials.getAccessToken()));
            System.out.println(String.format("Expires is: %d", authorizationCodeCredentials.getExpiresIn()));
            System.out.println(String.format("Refresh token: %s", authorizationCodeCredentials.getRefreshToken()));

            userData.setTokenExpTime(LocalDateTime.now().plusHours(1));

            writer.writeProperty(Config.AT.getVal(), authorizationCodeCredentials.getAccessToken());
            writer.writeProperty(Config.RT.getVal(), authorizationCodeCredentials.getRefreshToken());
            writer.writeProperty(Config.EXP.getVal(), userData.getTokenExpTime().toString());

            api.setAccessToken(authorizationCodeCredentials.getAccessToken());
            api.setRefreshToken(authorizationCodeCredentials.getRefreshToken());
        }
        catch (Exception ex){
            ex.printStackTrace();
        }
    }

    private void getCurrentUser(){
        if(userData.isTokenExpired())
            refreshToken();
        final CurrentUserRequest request = api.getMe().build();
        try{
            final User user = request.get();
            System.out.println(String.format("Current user id: %s", user.getId()));
            writer.writeProperty(Config.USERID.getVal(), user.getId());
            userData.setUserId(user.getId());
        }
        catch (Exception ex){
            ex.printStackTrace();
            logger.throwing("Analyser", "getCurrentUser", ex);
        }
    }

    private String getResponseData(HttpResponse response) throws IOException{
        BufferedReader rd = new BufferedReader(
                new InputStreamReader(response.getEntity().getContent()));

        StringBuffer result = new StringBuffer();
        String line = "";
        while ((line = rd.readLine()) != null) {
            result.append(line);
        }
        return result.toString();
    }

    private void ping(){
        final String baseURL = "https://tracksdata.herokuapp.com/rest/init";
        final HttpClient client = HttpClientBuilder.create().build();
        Platform.runLater(() -> {
            uiController.setStatus("Pinging...");
        });
        HttpGet request = new HttpGet(baseURL);
        try{
            client.execute(request);
        }
        catch (Exception ex){
        }
    }

    private void fillDataBase(){
        int from = 0;
        int amount = 50;
        String result = "";
        int received = 0;
        do {
            if (userData.isTokenExpired())
                refreshToken();

            String baseURL = String.format("https://tracksdata.herokuapp.com/rest/fetch?token=%s&rtoken=%s&from=%d&amount=%d",
                    writer.getProperty(Config.AT.getVal()),
                    writer.getProperty(Config.RT.getVal()),
                    from,
                    amount);

            HttpClient client = HttpClientBuilder.create().build();
            HttpGet request = new HttpGet(baseURL);

            try {
                HttpResponse response = client.execute(request);
                result = getResponseData(response);
                result = result.substring(1, result.length() - 1);
                result = result.replace("},", "},\n");
                String[] jsons = result.split("\\n");
                received = jsons.length;
                final int totalReceived = from + received;
                Platform.runLater(() -> {
                    uiController.setStatus(String.format("Received %d tracks", totalReceived));
                });
                System.out.println(String.format("Received elements from %d to %d", from, from + received));
                mongo.save(jsons);
            }catch (Exception ex){
                ex.printStackTrace();
                Platform.runLater(() -> {
                    uiController.setStatus("Error occured when working with db");
                });
                break;
            }
            from += amount;
        }
        while(!result.isEmpty() && !result.equals("[]") && received == amount);
    }

    public void sendData(File json){
        final String baseURL = "https://tracksdata.herokuapp.com/rest/saveMany";
        HttpClient client = HttpClientBuilder.create().build();
        HttpPost request = new HttpPost(baseURL);
        request.setHeader("Content-Type", "application/json");
        try {
            List<String> jsonPacks = new ArrayList<>(packFileAsJson(json, 100));
            for(String pack : jsonPacks) {
                request.setEntity(new StringEntity(pack));
                HttpResponse response = client.execute(request);
                if (response.getStatusLine().getStatusCode() == 200)
                    System.out.println("Exported");
            }
        }
        catch (Exception ex){
            ex.printStackTrace();
        }

    }

    private List<String> packFileAsJson(File json, int portion){
        List<String> result = new ArrayList<>();
        StringBuilder pack = new StringBuilder();
        pack.append("[");
        try {
            BufferedReader rd = new BufferedReader(
                    new FileReader(json));
            int lineCount = 0;
            String line = "";
            while ((line = rd.readLine()) != null) {
                pack.append(line).append(",");
                lineCount ++;
                if (lineCount == portion)
                {
                    String jsonPack = pack.toString();
                    jsonPack = String.format("%s]", jsonPack.substring(0, jsonPack.length() - 1));
                    result.add(jsonPack);
                    pack = new StringBuilder();
                    pack.append("[");
                    lineCount = 0;
                }

            }
        }
        catch (Exception ex){
            ex.printStackTrace();
        }
        String jsonPack = pack.toString();
        jsonPack = String.format("%s]", jsonPack.substring(0, jsonPack.length() - 1));
        result.add(jsonPack);
        return result;
    }


}
