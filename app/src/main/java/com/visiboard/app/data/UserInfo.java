package com.visiboard.app.data;

import com.google.firebase.firestore.PropertyName;

public class UserInfo {
    private String userId;
    private String name;
    private String email;
    private String profilePic;
    private String currentTier;
    private String lastKnownLocation;
    private int followersCount;
    private int followingCount;

    public UserInfo() {
        // Required empty constructor for Firestore
    }

    public UserInfo(String userId, String name, String email, String profilePic, 
                   String currentTier, String lastKnownLocation, int followersCount, int followingCount) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.profilePic = profilePic;
        this.currentTier = currentTier;
        this.lastKnownLocation = lastKnownLocation;
        this.followersCount = followersCount;
        this.followingCount = followingCount;
    }

    // Getters and Setters
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getProfilePic() { return profilePic; }
    public void setProfilePic(String profilePic) { this.profilePic = profilePic; }

    public String getCurrentTier() { return currentTier; }
    public void setCurrentTier(String currentTier) { this.currentTier = currentTier; }

    public String getLastKnownLocation() { return lastKnownLocation; }
    public void setLastKnownLocation(String lastKnownLocation) { this.lastKnownLocation = lastKnownLocation; }

    public int getFollowersCount() { return followersCount; }
    public void setFollowersCount(int followersCount) { this.followersCount = followersCount; }

    public int getFollowingCount() { return followingCount; }
    public void setFollowingCount(int followingCount) { this.followingCount = followingCount; }

    // New field for Legends feature
    private int totalLikes;
    public int getTotalLikes() { return totalLikes; }
    public void setTotalLikes(int totalLikes) { this.totalLikes = totalLikes; }
    
    // Privacy
    private boolean isPrivate;
    @PropertyName("isPrivate")
    public boolean isPrivate() { return isPrivate; }
    @PropertyName("isPrivate")
    public void setPrivate(boolean isPrivate) { this.isPrivate = isPrivate; }
    
    // Ghost Mode
    private boolean isGhostMode;
    @PropertyName("isGhostMode")
    public boolean isGhostMode() { return isGhostMode; }
    @PropertyName("isGhostMode")
    public void setGhostMode(boolean isGhostMode) { this.isGhostMode = isGhostMode; }
    
    // Coordinates for Distance Calculation
    private double lat;
    private double lng;
    
    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }
    
    public double getLng() { return lng; }
    public void setLng(double lng) { this.lng = lng; }

    // Admin Fields
    private boolean banned;
    private long banExpiryDate;
    private boolean restricted;
    private long restrictionExpiryDate;
    private boolean warned;

    public boolean isBanned() { return banned; }
    public void setBanned(boolean banned) { this.banned = banned; }

    public long getBanExpiryDate() { return banExpiryDate; }
    public void setBanExpiryDate(long banExpiryDate) { this.banExpiryDate = banExpiryDate; }

    public boolean isRestricted() { return restricted; }
    public void setRestricted(boolean restricted) { this.restricted = restricted; }

    public long getRestrictionExpiryDate() { return restrictionExpiryDate; }
    public void setRestrictionExpiryDate(long restrictionExpiryDate) { this.restrictionExpiryDate = restrictionExpiryDate; }

    public boolean isWarned() { return warned; }
    public void setWarned(boolean warned) { this.warned = warned; }
}
