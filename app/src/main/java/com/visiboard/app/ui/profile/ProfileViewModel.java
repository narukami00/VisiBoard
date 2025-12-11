package com.visiboard.app.ui.profile;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.visiboard.app.data.NearbyNote;

import java.util.List;

public class ProfileViewModel extends ViewModel {
    
    private MutableLiveData<String> userName = new MutableLiveData<>();
    private MutableLiveData<String> userEmail = new MutableLiveData<>();
    private MutableLiveData<String> profilePicBase64 = new MutableLiveData<>();
    private MutableLiveData<String> location = new MutableLiveData<>();
    
    private MutableLiveData<Integer> totalNotes = new MutableLiveData<>();
    private MutableLiveData<Integer> totalLikes = new MutableLiveData<>();
    private MutableLiveData<Long> followersCount = new MutableLiveData<>();
    private MutableLiveData<Long> followingCount = new MutableLiveData<>();
    
    private MutableLiveData<String> currentTier = new MutableLiveData<>();
    private MutableLiveData<Integer> tierProgress = new MutableLiveData<>();
    private MutableLiveData<Integer> tierMax = new MutableLiveData<>();
    private MutableLiveData<Integer> tierIconRes = new MutableLiveData<>();
    
    private MutableLiveData<List<NearbyNote>> recentNotes = new MutableLiveData<>();
    
    private boolean isDataLoaded = false;
    private long lastLoadTime = 0;
    private static final long CACHE_DURATION = 2 * 60 * 1000; // 2 minutes
    
    // Getters
    public LiveData<String> getUserName() { return userName; }
    public LiveData<String> getUserEmail() { return userEmail; }
    public LiveData<String> getProfilePicBase64() { return profilePicBase64; }
    public LiveData<String> getLocation() { return location; }
    public LiveData<Integer> getTotalNotes() { return totalNotes; }
    public LiveData<Integer> getTotalLikes() { return totalLikes; }
    public LiveData<Long> getFollowersCount() { return followersCount; }
    public LiveData<Long> getFollowingCount() { return followingCount; }
    public LiveData<String> getCurrentTier() { return currentTier; }
    public LiveData<Integer> getTierProgress() { return tierProgress; }
    public LiveData<Integer> getTierMax() { return tierMax; }
    public LiveData<Integer> getTierIconRes() { return tierIconRes; }
    public LiveData<List<NearbyNote>> getRecentNotes() { return recentNotes; }
    
    // Setters
    public void setUserName(String name) { this.userName.setValue(name); }
    public void setUserEmail(String email) { this.userEmail.setValue(email); }
    public void setProfilePicBase64(String pic) { this.profilePicBase64.setValue(pic); }
    public void setLocation(String loc) { this.location.setValue(loc); }
    public void setTotalNotes(Integer notes) { this.totalNotes.setValue(notes); }
    public void setTotalLikes(Integer likes) { this.totalLikes.setValue(likes); }
    public void setFollowersCount(Long count) { this.followersCount.setValue(count); }
    public void setFollowingCount(Long count) { this.followingCount.setValue(count); }
    public void setCurrentTier(String tier) { this.currentTier.setValue(tier); }
    public void setTierProgress(Integer progress) { this.tierProgress.setValue(progress); }
    public void setTierMax(Integer max) { this.tierMax.setValue(max); }
    public void setTierIconRes(Integer res) { this.tierIconRes.setValue(res); }
    public void setRecentNotes(List<NearbyNote> notes) { this.recentNotes.setValue(notes); }
    
    // State management
    public boolean isDataLoaded() { 
        return isDataLoaded; 
    }
    
    public void setDataLoaded(boolean loaded) { 
        this.isDataLoaded = loaded;
        if (loaded) {
            lastLoadTime = System.currentTimeMillis();
        }
    }
    
    public boolean shouldRefreshData() {
        if (!isDataLoaded) return true;
        return (System.currentTimeMillis() - lastLoadTime) > CACHE_DURATION;
    }
    
    public void invalidateCache() {
        isDataLoaded = false;
        lastLoadTime = 0;
    }
}
