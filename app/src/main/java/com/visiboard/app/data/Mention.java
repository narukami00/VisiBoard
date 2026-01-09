package com.visiboard.app.data;

/**
 * Represents a single mention in a note or comment.
 * Used to store and parse @mentions for styling and notifications.
 */
public class Mention {
    private String userId;      // null for @following
    private String userName;    // Display name (e.g., "John Doe" or "@following")
    private int startIndex;     // Position in text where mention starts
    private int endIndex;       // Position in text where mention ends
    private boolean isFollowingMention;  // true for @following special mention

    // Required empty constructor for Firestore
    public Mention() {
    }

    public Mention(String userId, String userName, int startIndex, int endIndex, boolean isFollowingMention) {
        this.userId = userId;
        this.userName = userName;
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.isFollowingMention = isFollowingMention;
    }

    /**
     * Factory method for creating a regular user mention
     */
    public static Mention forUser(String userId, String userName, int startIndex, int endIndex) {
        return new Mention(userId, userName, startIndex, endIndex, false);
    }

    /**
     * Factory method for creating an @following mention
     */
    public static Mention forFollowing(int startIndex, int endIndex) {
        return new Mention(null, "@following", startIndex, endIndex, true);
    }

    // Getters and Setters
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public int getStartIndex() {
        return startIndex;
    }

    public void setStartIndex(int startIndex) {
        this.startIndex = startIndex;
    }

    public int getEndIndex() {
        return endIndex;
    }

    public void setEndIndex(int endIndex) {
        this.endIndex = endIndex;
    }

    public boolean isFollowingMention() {
        return isFollowingMention;
    }

    public void setFollowingMention(boolean followingMention) {
        isFollowingMention = followingMention;
    }

    /**
     * Returns the display text for this mention (e.g., "@John Doe")
     */
    public String getDisplayText() {
        return "@" + userName;
    }

    @Override
    public String toString() {
        return "Mention{" +
                "userId='" + userId + '\'' +
                ", userName='" + userName + '\'' +
                ", startIndex=" + startIndex +
                ", endIndex=" + endIndex +
                ", isFollowingMention=" + isFollowingMention +
                '}';
    }
}
