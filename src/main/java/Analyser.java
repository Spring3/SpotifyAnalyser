import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import com.wrapper.spotify.Api;
import com.wrapper.spotify.methods.CurrentUserRequest;
import com.wrapper.spotify.methods.GetMySavedTracksRequest;
import com.wrapper.spotify.methods.PlaylistTracksRequest;
import com.wrapper.spotify.methods.UserPlaylistsRequest;
import com.wrapper.spotify.models.*;
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
    }

    private Properties properties;
    private Stage stage;
    private Api api = Api.builder()
            .clientId(CLIENT_ID)
            .clientSecret(CLIENT_SECRET)
            .redirectURI(REDIRECT_URI)
            .build();

    private final Data userData = Data.getInstance();
    private final Security security = Security.getInstance();

    public void getInfo(){
        checkConfig();
        if (getProperty(Config.USERID.getVal()) == null) {
            authenticate();
        }
        else {
            getUserPlaylists();
            getUserSavedTracks();
            analyse();
        }
    }

    private void checkConfig(){
        if (getProperty(Config.USERID.getVal()) != null){
            LocalDateTime time = (LocalDateTime.parse(getProperty(Config.EXP.getVal())));
            userData.setTokenExpTime(time);
            userData.setUserId(getProperty(Config.USERID.getVal()));
            api.setAccessToken(getProperty(Config.AT.getVal()));
            api.setRefreshToken(getProperty(Config.RT.getVal()));
            if (userData.isTokenExpired()){
                refreshToken();
            }
        }
        checkClientId();
    }

    private void refreshToken(){
        try {
            System.out.println("Refreshing token...");
            final RefreshAccessTokenCredentials refreshAccessTokenCredentials = api.refreshAccessToken().build().get();

            writeProperty(Config.AT.getVal(), refreshAccessTokenCredentials.getAccessToken());
            api.setAccessToken(refreshAccessTokenCredentials.getAccessToken());
            LocalDateTime tokenExpTime = LocalDateTime.now().plusHours(1);
            writeProperty(Config.EXP.getVal(), tokenExpTime.toString());
            userData.setTokenExpTime(tokenExpTime);
        }
        catch (Exception ex){
            ex.printStackTrace();
        }
    }

    private void checkClientId() {

        if (getProperty(Config.CLIENTID.getVal()) == null) {
            String clientId = generateString(16);
            writeProperty(Config.CLIENTID.getVal(), clientId);
            System.out.println(clientId);
        }
        userData.setClientId(getProperty(Config.CLIENTID.getVal()));
        System.out.println("Client id: " + userData.getClientId());
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
            ex.printStackTrace();
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
            ex.printStackTrace();
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
                        getCurrentUser();
                        getInfo();
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
        catch (Exception ex){
            ex.printStackTrace();
        }
    }

    private void getUserPlaylists(){
        if(userData.isTokenExpired())
            refreshToken();
        final UserPlaylistsRequest request = api.getPlaylistsForUser(userData.getUserId()).build();

        try{
            final Page<SimplePlaylist> playlistPage = request.get();
            userData.setPlaylists(playlistPage.getItems());
            System.out.println(String.format("Found %d playlists", playlistPage.getItems().size()));
        }
        catch (Exception ex){
            ex.printStackTrace();
        }
    }

    private void getUserSavedTracks(){
        if (userData.isTokenExpired())
            refreshToken();
        int offset = 0;
        List<LibraryTrack> tracks = new ArrayList<>(50);
        boolean hasMore = true;
        try {
            while (hasMore) {
                GetMySavedTracksRequest request = api.getMySavedTracks().limit(50).offset(offset).build();
                Page<LibraryTrack> libraryTracks = request.get();
                offset += 50;
                tracks.addAll(libraryTracks.getItems());
                if (offset > libraryTracks.getTotal())
                    hasMore = false;
            }
            System.out.println(String.format("Found %d saved tracks", tracks.size()));
            userData.setTracks(tracks);
        }
        catch (Exception ex){
            ex.printStackTrace();
        }

    }

    public void analyse(){
        List<PlaylistTrack> tracks = getTracksFromPlaylists();



        Thread thread = new Thread(() ->{
            for(LibraryTrack track : userData.getTracks()){
                getTrackInfo(track.getTrack().getId());
            }
            userData.getTracks().clear();
        });

        thread.setDaemon(true);
        thread.start();


        for(PlaylistTrack track : tracks){
            getTrackInfo(track.getTrack().getId());
        }

        Platform.exit();
    }

    private synchronized void getTrackInfo(String trackId){
        final HttpClient client = HttpClientBuilder.create().build();
        String baseURL = String.format("https://api.spotify.com/v1/audio-features/%s", trackId);
        HttpGet requestTrackInfo = new HttpGet(baseURL);
        synchronized (Analyser.class) {
            if (userData.isTokenExpired())
                refreshToken();
        }
        requestTrackInfo.addHeader("Authorization", String.format("Bearer  %s", getProperty(Config.AT.getVal())));
        try {
            HttpResponse response = client.execute(requestTrackInfo);
            BufferedReader rd = new BufferedReader(
                    new InputStreamReader(response.getEntity().getContent()));

            StringBuffer result = new StringBuffer();
            String line = "";
            while ((line = rd.readLine()) != null) {
                result.append(line);
            }
            sendTrackInfo(result.toString());
        }
        catch (Exception ex){
            ex.printStackTrace();
        }
    }

    private void sendTrackInfo(String json){
        final String baseURL = "https://tracksdata.herokuapp.com/rest/save";
        final HttpClient client = HttpClientBuilder.create().build();
        HttpPost request = new HttpPost(baseURL);
        request.setHeader("Content-Type", "application/json");
        try {
            request.setEntity(new StringEntity(json));
            HttpResponse response = client.execute(request);
            if (response.getStatusLine().getStatusCode() == 200){
                System.out.println("Sent");
            }
            else{
                System.out.println("Duplicate");
            }
        }
        catch (Exception ex){
            ex.printStackTrace();
        }
    }

    private List<PlaylistTrack> getTracksFromPlaylists(){
        List<PlaylistTrack> tracks = new ArrayList<>(300);
        for(SimplePlaylist playlist : userData.getPlaylists()){
            boolean hasMore = true;
            int offset = 0;
            while(hasMore) {
                final PlaylistTracksRequest request = api.getPlaylistTracks(playlist.getOwner().getId(), playlist.getId()).offset(offset).build();
                try {
                    Page<PlaylistTrack> playlistPage = request.get();
                    tracks.addAll(playlistPage.getItems());
                    offset += 100;
                    if (offset > playlistPage.getTotal())
                        hasMore = false;
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
        System.out.println(String.format("Total tracks in playlists: %d", tracks.size()));
        return tracks;
    }
}
