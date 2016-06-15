package util;

import com.wrapper.spotify.models.Track;

import java.time.LocalDateTime;
import java.util.*;

public class Data{

    public Data(){
        tracks = Collections.synchronizedSet(new HashSet<>());
    }

    private String userId;
    private Set<Track> tracks;
    private LocalDateTime tokenExpTime;

    public String getUserId(){
        return userId;
    }

    public void setUserId(String id){
        userId = id;
    }

    public LocalDateTime getTokenExpTime(){
        return tokenExpTime;
    }

    public void setTokenExpTime(LocalDateTime tokenExpTime){
        this.tokenExpTime = tokenExpTime;
    }

    public void setTracks(Set<Track> tracks){
        this.tracks = tracks;
    }

    public Set<Track> getTracks(){
        return tracks;
    }

    public synchronized Track getTrack(int index){
        int i = 0;
        for (Iterator<Track> it = tracks.iterator(); it.hasNext(); ) {
            Track track = it.next();
            if (i == index)
                return track;
            i++;
        }
        return null;
    }

    public boolean isTokenExpired(){
        return tokenExpTime != null && LocalDateTime.now().isAfter(tokenExpTime);
    }
}
