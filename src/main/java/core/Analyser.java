package core;

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
import util.Config;
import util.Data;
import util.Security;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Key;
import java.text.Normalizer;
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
        properties = new Properties();
        userData = new Data();
        security = Security.getInstance();
        uiController = controller;
    }

    private Properties properties;
    private double totalTracks;
    private int sendingAmount;
    private UIController uiController;
    private Api api = Api.builder()
            .clientId(CLIENT_ID)
            .clientSecret(CLIENT_SECRET)
            .redirectURI(REDIRECT_URI)
            .build();

    private final Data userData;
    private final Security security;


    public void getInfo(){
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                ping();
                checkConfig();
                if (getProperty(Config.USERID.getVal()) == null) {
                    Platform.runLater(() -> {
                        authenticate();
                        return;
                    });

                }else {
                    getUserPlaylists();
                    getUserSavedTracks();
                    analyse();
                }
            }
        });
        thread.setDaemon(true);
        thread.start();

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

    private Path checkPropertyFile(){
        try {
            Path propFile = Paths.get("config.properties");
            if (!Files.exists(propFile, LinkOption.NOFOLLOW_LINKS)) {
                Files.createFile(propFile);
            }
            return propFile;
        }
        catch (IOException ex){
            ex.printStackTrace();
            logger.throwing("Analyser", "checkPropFile", ex);
        }
        return null;
    }

    public void clearProperties(){
        try {
            Path propFile = checkPropertyFile();
            Files.delete(propFile);
        }
        catch (Exception ex){
            ex.printStackTrace();
            logger.throwing("Analyser", "checkProperties", ex);
        }
    }

    private void writeProperty(String key, String value){
        try {
            Path propFile = checkPropertyFile();
            OutputStream output = new FileOutputStream(propFile.toFile().getPath());
            Key pubKey = Security.generateKey();
            String encValue = security.encrypt(pubKey, value);
            properties.setProperty(key, String.format("%s|%s", Base64.getEncoder().encodeToString(pubKey.getEncoded()), encValue));
            properties.store(output, null);
            output.close();
        }
        catch (Exception ex){
            ex.printStackTrace();
            logger.throwing("Analyser", "writeProperty", ex);
        }
    }

    private String getProperty(String key){
        try {
            Path propFile = checkPropertyFile();
            InputStream input = new FileInputStream(propFile.toFile().getPath());
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
            logger.throwing("Analyser", "getProperty", ex);
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

        Stage stage = new Stage();
        final List<String> scopes = Arrays.asList("user-read-private", "user-library-read", "playlist-read-private", "playlist-read-collaborative");
        final String state = "Initialized";
        final String authorizeURL= api.createAuthorizeURL(scopes, state);
        final WebView web = new WebView();
        web.getEngine().load(authorizeURL);
        stage.setScene(new Scene(web));
        stage.show();

        web.getEngine().getLoadWorker().stateProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == Worker.State.SUCCEEDED){
                if (web.getEngine().getLocation().contains("?code=")){
                    String location = web.getEngine().getLocation();
                    String appKey = location.substring(location.indexOf("?code=") + 6, location.indexOf("&state"));
                    System.out.println(String.format("App key: %s", appKey));
                    stage.close();
                    getAccessToken(appKey);
                    getCurrentUser();
                    getInfo();
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
                logger.throwing("Analyser", "getAccessToken", throwable);
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
            logger.throwing("Analyser", "getCurrentUser", ex);
        }
    }

    private void getUserPlaylists(){
        Platform.runLater(()-> {
            uiController.setStatus("Searching for playlists...");
        });

        if(userData.isTokenExpired())
            refreshToken();
        final UserPlaylistsRequest request = api.getPlaylistsForUser(userData.getUserId()).build();
        final SettableFuture<Page<SimplePlaylist>> responseFuture = request.getAsync();

        try{
            Futures.addCallback(responseFuture, new FutureCallback<Page<SimplePlaylist>>() {
                @Override
                public void onSuccess(Page<SimplePlaylist> playlistPage) {

                    Platform.runLater(()-> {
                        uiController.setStatus(String.format("Found %d playlists", playlistPage.getItems().size()));
                    });
                    System.out.println(String.format("Found %d playlists", playlistPage.getItems().size()));
                    getTracksFromPlaylists(playlistPage);
                }

                @Override
                public void onFailure(Throwable throwable) {

                }
            });
        }
        catch (Exception ex){
            ex.printStackTrace();
            logger.throwing("Analyser", "getUserPlaylists", ex);
        }
    }

    private void getUserSavedTracks(){
        Platform.runLater(()-> {
            uiController.setStatus("Looking through the saved tracks... Amazing!");
        });
        if (userData.isTokenExpired())
            refreshToken();
        int offset = 0;
        Set<Track> tracks = new HashSet<>(100);
        boolean hasMore = true;
        try {
            while (hasMore) {
                GetMySavedTracksRequest request = api.getMySavedTracks().limit(50).offset(offset).build();
                Page<LibraryTrack> libraryTracks = request.get();
                offset += 50;
                for(LibraryTrack track : libraryTracks.getItems()){
                    tracks.add(track.getTrack());
                }
                if (offset > libraryTracks.getTotal())
                    hasMore = false;
            }
            Platform.runLater(()-> {
                uiController.setStatus(String.format("Found %d tracks", tracks.size()));
            });
            System.out.println(String.format("Found %d saved tracks", tracks.size()));
            for(Track track : tracks){
                userData.addTrack(track);
            }
        }
        catch (Exception ex){
            ex.printStackTrace();
            logger.throwing("Analyser", "getUserSavedTracks", ex);
        }

    }

    private void analyse(){
        Platform.runLater(()-> {
            uiController.setStatus("Sending collected tracks info");
        });
        totalTracks = userData.getTracks().size();
        int amount = 100;  // tracks per request
        String json = "";
        StringBuilder builder = new StringBuilder();
        if (userData.getTracks().size() <= amount){
            for(Track track : userData.getTracks())
                builder.append(track.getId()).append(",");
            sendingAmount = userData.getTracks().size();
            json = getTrackInfo(builder.substring(0, builder.length() - 1));
            String newJSON = repackJson(new ArrayList<>(userData.getTracks()), json);
            System.out.println(String.format("Sending %d tracks", sendingAmount));
            sendTrackInfo(newJSON);
        }
        else{
            int tracksLeft = userData.getTracks().size();
            int start = 0;
            while(tracksLeft > 0){
                List<Track> tracks = userData.getTracks(start, amount);
                for(Track track : tracks){
                    builder.append(track.getId()).append(",");
                }
                sendingAmount = tracks.size();
                String part = builder.toString();
                json = getTrackInfo(part.substring(0, part.length() - 1));
                String newJSON = repackJson(tracks, json);
                System.out.println(String.format("Sending %d tracks", sendingAmount));
                sendTrackInfo(newJSON);
                start += amount;
                tracksLeft -= amount;

                builder = new StringBuilder();
            }
        }
    }

    private String repackJson(List<Track> tracks, String json){
        StringBuilder resultJSON = new StringBuilder();
        resultJSON.append("[");
        String[] separateJSONs = json.substring(1, json.length() - 1).split("},");
        for(int i = 0; i < separateJSONs.length; i ++){
            separateJSONs[i] = separateJSONs[i].replace("{", "").trim();
            String[] jsonProperties = separateJSONs[i].split(",");
            StringBuilder jsonBuilder = new StringBuilder();
            StringBuilder artistsBuilder = new StringBuilder();
            Track currentTrack = tracks.get(i);
            if (currentTrack.getId().equals("null"))
                continue;
            for(SimpleArtist author : currentTrack.getArtists())
            {
                artistsBuilder.append(author.getName()).append(",");
            }

            artistsBuilder.deleteCharAt(artistsBuilder.length() - 1).toString();
            jsonBuilder.append("{").append("\"artist\":").append(String.format("\"%s\"", artistsBuilder.toString().replace("\"", "'"))).append(",")
                                   .append("\"title\":").append(String.format("\"%s\"", currentTrack.getName().replace("\"", "'"))).append(",")
                                   .append(jsonProperties[12].trim()).append(",") // id
                                   .append("\"features\": {");
            for(int k = 0; k < 11; k ++) {
                jsonBuilder.append(jsonProperties[k].trim());
                if (k < 10)
                    jsonBuilder.append(",");
                else{
                    jsonBuilder.append("}},");
                }
            }
            resultJSON.append(jsonBuilder.toString());
        }
        resultJSON.deleteCharAt(resultJSON.length() - 1);
        resultJSON.append("]");
        return resultJSON.toString();
    }

    private String getTrackInfo(String tracksIds){
        final HttpClient client = HttpClientBuilder.create().build();
        String baseURL = String.format("https://api.spotify.com/v1/audio-features/?ids=%s", tracksIds);
        HttpGet requestTrackInfo = new HttpGet(baseURL);
        //synchronized (Analyser.class) {
            if (userData.isTokenExpired())
                refreshToken();
        //}
        requestTrackInfo.addHeader("Authorization", String.format("Bearer  %s", getProperty(Config.AT.getVal())));
        try {
            HttpResponse response = client.execute(requestTrackInfo);
            BufferedReader rd = new BufferedReader(
                    new InputStreamReader(response.getEntity().getContent()));

            StringBuilder result = new StringBuilder();
            String line = "";
            while ((line = rd.readLine()) != null) {
                result.append(line);
            }
            String json = result.toString();
            // 22 - to remove header of the json
            json = json.substring(22, json.length() - 1);
            return json;
        }
        catch (Exception ex){
            ex.printStackTrace();
            logger.throwing("Analyser", "getTrackInfo", ex);
        }
        return "";
    }

    private void sendTrackInfo(String json){
        final String baseURL = "https://tracksdata.herokuapp.com/rest/saveMany";
        final HttpClient client = HttpClientBuilder.create().build();
        HttpPost request = new HttpPost(baseURL);
        request.setHeader("Content-Type", "application/json; charset=UTF-8");
        try {
            json = new String(json.getBytes(), "UTF-8");
            json = Normalizer.normalize(json, Normalizer.Form.NFKD);
            StringEntity entity = new StringEntity(json, "UTF-8");
            entity.setContentEncoding("UTF-8");
            entity.setContentType("application/json; charset=UTF-8");
            request.setEntity(entity);
            HttpResponse response = client.execute(request);
            if (response.getStatusLine().getStatusCode() == 200){
                System.out.println("Sent");
            }
            else{
                System.out.println("Duplicate");
            }
            Platform.runLater(()-> {
                double progress = sendingAmount / totalTracks;
                System.out.println(progress);
                uiController.updateProgress(progress);
            });
        }
        catch (Exception ex){
            ex.printStackTrace();
            logger.throwing("Analyser", "sendTrackInfo", ex);
        }
    }

    private void ping(){
        final String baseURL = "https://tracksdata.herokuapp.com/rest/init";
        final HttpClient client = HttpClientBuilder.create().build();
        HttpGet request = new HttpGet(baseURL);
        try{
            client.execute(request);
        }
        catch (Exception ex){
        }
    }

    private void getTracksFromPlaylists(Page<SimplePlaylist> playlists){
        Set<PlaylistTrack> tracks = new HashSet<>(300);
        for(SimplePlaylist playlist : playlists.getItems()){
            boolean hasMore = true;
            int offset = 0;
            while(hasMore) {
                if (userData.isTokenExpired())
                    refreshToken();
                final PlaylistTracksRequest request = api.getPlaylistTracks(playlist.getOwner().getId(), playlist.getId()).offset(offset).build();
                try {
                    Page<PlaylistTrack> playlistPage = request.get();
                    tracks.addAll(playlistPage.getItems());
                    offset += 100;
                    if (offset > playlistPage.getTotal())
                        hasMore = false;
                } catch (Exception ex) {
                    ex.printStackTrace();
                    logger.throwing("Analyser", "getTracksFromPlaylists", ex);
                }
            }
        }
        for(PlaylistTrack track : tracks){
            userData.addTrack(track.getTrack());
        }
        System.out.println(String.format("Total tracks in playlists: %d", tracks.size()));
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = properties != null ? properties.hashCode() : 0;
        temp = Double.doubleToLongBits(totalTracks);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + sendingAmount;
        result = 31 * result + (uiController != null ? uiController.hashCode() : 0);
        result = 31 * result + (api != null ? api.hashCode() : 0);
        result = 31 * result + (userData != null ? userData.hashCode() : 0);
        result = 31 * result + (security != null ? security.hashCode() : 0);
        return result;
    }
}
