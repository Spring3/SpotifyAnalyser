package util;

import com.wrapper.spotify.models.LibraryTrack;
import com.wrapper.spotify.models.SimplePlaylist;

import java.time.LocalDateTime;
import java.util.List;

public class Data{

    public Data(){   }

    private String userId;
    private List<SimplePlaylist> playlists;
    private List<LibraryTrack> tracks;
    private LocalDateTime tokenExpTime;

    public String getUserId(){
        return userId;
    }

    public void setUserId(String id){
        userId = id;
    }

    public void setPlaylists(List<SimplePlaylist> playlists){
        this.playlists = playlists;
    }

    public List<SimplePlaylist> getPlaylists(){
        return playlists;
    }

    public LocalDateTime getTokenExpTime(){
        return tokenExpTime;
    }

    public void setTokenExpTime(LocalDateTime tokenExpTime){
        this.tokenExpTime = tokenExpTime;
    }

    public void setTracks(List<LibraryTrack> tracks){
        this.tracks = tracks;
    }

    public List<LibraryTrack> getTracks(){
        return tracks;
    }

    public boolean isTokenExpired(){
        return tokenExpTime == null ? false : LocalDateTime.now().isAfter(tokenExpTime);
    }
}
