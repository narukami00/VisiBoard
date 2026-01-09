package com.visiboard.app.data;

/**
 * Represents a user suggestion for the mention autocomplete popup.
 * Used to display followers when user types @.
 */
public class FollowerSuggestion {
    private String userId;
    private String name;
    private String profilePic;  // Base64 encoded
    private boolean isFollowingAll;  // true for the special @following suggestion

    /**
     * Special constant for the @following option that mentions all followers
     */
    public static final FollowerSuggestion FOLLOWING_ALL = 
        new FollowerSuggestion(null, "@following", null, true);

    // Required empty constructor
    public FollowerSuggestion() {
    }

    public FollowerSuggestion(String userId, String name, String profilePic) {
        this.userId = userId;
        this.name = name;
        this.profilePic = profilePic;
        this.isFollowingAll = false;
    }

    private FollowerSuggestion(String userId, String name, String profilePic, boolean isFollowingAll) {
        this.userId = userId;
        this.name = name;
        this.profilePic = profilePic;
        this.isFollowingAll = isFollowingAll;
    }

    /**
     * Creates a new @following suggestion instance
     * (Use this instead of modifying the static constant)
     */
    public static FollowerSuggestion createFollowingAll() {
        return new FollowerSuggestion(null, "@following", null, true);
    }

    // Getters and Setters
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProfilePic() {
        return profilePic;
    }

    public void setProfilePic(String profilePic) {
        this.profilePic = profilePic;
    }

    public boolean isFollowingAll() {
        return isFollowingAll;
    }

    public void setFollowingAll(boolean followingAll) {
        isFollowingAll = followingAll;
    }

    /**
     * Check if this suggestion matches the given query (case-insensitive)
     */
    public boolean matchesQuery(String query) {
        if (query == null || query.isEmpty()) {
            return true;
        }
        String lowerQuery = query.toLowerCase();
        
        // Special handling for @following
        if (isFollowingAll) {
            return "following".startsWith(lowerQuery) || "@following".contains(lowerQuery);
        }
        
        return name != null && name.toLowerCase().contains(lowerQuery);
    }

    @Override
    public String toString() {
        return "FollowerSuggestion{" +
                "userId='" + userId + '\'' +
                ", name='" + name + '\'' +
                ", isFollowingAll=" + isFollowingAll +
                '}';
    }
}
