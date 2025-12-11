package com.visiboard.app.data;

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
}
