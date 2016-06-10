import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import com.wrapper.spotify.Api;
import com.wrapper.spotify.methods.CurrentUserRequest;
import com.wrapper.spotify.methods.UserPlaylistsRequest;
import com.wrapper.spotify.models.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.security.Key;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Created by spring on 6/9/16.
 */
public class Analyser {

    private static final String CLIENT_ID = "";
    private static final String CLIENT_SECRET = "";
    private static final String REDIRECT_URI = "";

    public Analyser(Stage stage){
        this.stage = stage;
        properties = new Properties();
        api = Api.builder()
                .clientId(CLIENT_ID)
                .clientSecret(CLIENT_SECRET)
                .redirectURI(REDIRECT_URI)
                .build();
    }

    private Properties properties;
    private Stage stage;
    private Api api;
    private final Data userData = Data.getInstance();
    private final Security security = Security.getInstance();

    public void getInfo(){
        checkConfig();
        if (getProperty(Config.USERID.getVal()) == null)
            authenticate();
        getCurrentUser();
        getUserPlaylists();
    }

    private void checkConfig(){
        if (getProperty(Config.USERID.getVal()) != null){
            LocalDateTime time = (LocalDateTime.parse(getProperty(Config.EXP.getVal())));
            userData.setTokenExpTime(time);
            if (userData.isTokenExpired()){
                api.setAccessToken(getProperty(Config.AT.getVal()));
                api.setRefreshToken(getProperty(Config.RT.getVal()));
                refreshToken();
            }
        }
        checkClientId();
    }

    private void refreshToken(){
        try {
            final RefreshAccessTokenCredentials refreshAccessTokenCredentials = api.refreshAccessToken().build().get();

            writeProperty(Config.AT.getVal(), refreshAccessTokenCredentials.getAccessToken());
            LocalDateTime tokenExpTime = LocalDateTime.now().plusHours(1);
            writeProperty(Config.EXP.getVal(), tokenExpTime.toString());
            userData.setTokenExpTime(tokenExpTime);
        }
        catch (Exception ex){ }


    }

    private void checkClientId() {

        if (getProperty(Config.CLIENTID.getVal()) == null) {
            String clientId = generateString(16);
            writeProperty(Config.CLIENTID.getVal(), clientId);
            System.out.println(clientId);
        }
        userData.setClientId(getProperty(Config.CLIENTID.getVal()));
        System.out.println(userData.getClientId());
    }

    private void writeProperty(String key, String value){
        try {
            OutputStream output = new FileOutputStream(getClass().getClassLoader().getResource("config.properties").getFile());
            Key pubKey = Security.generateKey();
            String encValue = security.encrypt(pubKey, value);
            properties.setProperty(key, String.format("%s|%s", Base64.getEncoder().encodeToString(pubKey.getEncoded()), encValue));
            properties.store(output, null);
            output.close();
        }
        catch (Exception ex){

        }
    }

    private String getProperty(String key){
        try {
            InputStream input = new FileInputStream(getClass().getClassLoader().getResource("config.properties").getFile());
            properties.load(input);
            String result = properties.getProperty(key);
            if (result == null) {
                input.close();
                return result;
            }
            String pubKey = result.substring(0, result.indexOf("|"));
            byte[] pubkeyBytes = Base64.getDecoder().decode(pubKey);
            result = result.substring(result.indexOf("|") + 1, result.length());
            input.close();
            return security.decrypt(new SecretKeySpec(pubkeyBytes, 0, pubkeyBytes.length, "DES"), result);
        }
        catch (Exception ex){

        }
        return null;
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

    private void authenticate(){

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
                        stage.hide();
                        getAccessToken(appKey);
                    }
                }
            }
        });

    }

    private void getAccessToken(String appKey){
        SettableFuture<AuthorizationCodeCredentials> authorizationCodeCredentialsSettableFuture = api.authorizationCodeGrant(appKey).build().getAsync();

        Futures.addCallback(authorizationCodeCredentialsSettableFuture, new FutureCallback<AuthorizationCodeCredentials>() {
            @Override
            public void onSuccess(AuthorizationCodeCredentials authorizationCodeCredentials) {
                System.out.println(String.format("Access token: %s", authorizationCodeCredentials.getAccessToken()));
                System.out.println(String.format("Expires is: %d", authorizationCodeCredentials.getExpiresIn()));
                System.out.println(String.format("Refresh token: %s", authorizationCodeCredentials.getRefreshToken()));

                userData.setTokenExpTime(LocalDateTime.now().plusHours(1));

                writeProperty(Config.AT.getVal(), authorizationCodeCredentials.getAccessToken());
                writeProperty(Config.RT.getVal(), authorizationCodeCredentials.getRefreshToken());
                writeProperty(Config.EXP.getVal(), userData.getTokenExpTime().toString());

                api.setAccessToken(authorizationCodeCredentials.getAccessToken());
                api.setRefreshToken(authorizationCodeCredentials.getRefreshToken());
            }

            @Override
            public void onFailure(Throwable throwable) {

            }
        });
    }

    private void getCurrentUser(){
        if(userData.isTokenExpired())
            refreshToken();
        final CurrentUserRequest request = api.getMe().build();
        try{
            final User user = request.get();
            System.out.println(String.format("Current user id: %s", user.getId()));
            writeProperty(Config.USERID.getVal(), user.getId());
            userData.setUserId(user.getId());
        }
        catch (Exception ex){        }
    }

    private void getUserPlaylists(){
        final UserPlaylistsRequest request = api.getPlaylistsForUser(userData.getUserId()).build();

        try{
            final Page<SimplePlaylist> playlistPage = request.get();
            userData.setPlaylists(playlistPage.getItems());
        }
        catch (Exception ex){   }
    }

    private void getUserSavedTracks(){

    }

    public Data getUserData(){
        return userData;
    }

    public void analyse(){

    }
}
