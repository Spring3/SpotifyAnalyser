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
        for (Track track : tracks) {
            if (i == index)
                return track;
            i++;
        }
        return null;
    }

    public synchronized List<Track> getTracks(int start, int amount){
        int i = 0;
        List<Track> result = new ArrayList<>(amount);
        for (Track track : tracks) {
            if (i >= start && i < start + amount)
                result.add(track);
            else if (i >= start + amount)
                break;
            i++;
        }
        return result;
    }

    public boolean isTokenExpired(){
        return tokenExpTime == null ? false : LocalDateTime.now().isAfter(tokenExpTime);
    }
}
