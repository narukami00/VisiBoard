package com.visiboard.app.data;

public class NearbyNote {
    private String id;
    private String text;
    private String summary;
    private String userId;
    private String userName;
    private String userProfilePic;
    private double lat;
    private double lng;
    private long timestamp;
    private int likesCount;
    private int commentsCount;
    private double distance;

    public NearbyNote() {
    }

    public NearbyNote(String id, String text, String summary, String userId, String userName,
                     String userProfilePic, double lat, double lng, long timestamp,
                     int likesCount, int commentsCount, double distance) {
        this.id = id;
        this.text = text;
        this.summary = summary;
        this.userId = userId;
        this.userName = userName;
        this.userProfilePic = userProfilePic;
        this.lat = lat;
        this.lng = lng;
        this.timestamp = timestamp;
        this.likesCount = likesCount;
        this.commentsCount = commentsCount;
        this.distance = distance;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getUserProfilePic() { return userProfilePic; }
    public void setUserProfilePic(String userProfilePic) { this.userProfilePic = userProfilePic; }

    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }

    public double getLng() { return lng; }
    public void setLng(double lng) { this.lng = lng; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public int getLikesCount() { return likesCount; }
    public void setLikesCount(int likesCount) { this.likesCount = likesCount; }

    public int getCommentsCount() { return commentsCount; }
    public void setCommentsCount(int commentsCount) { this.commentsCount = commentsCount; }

    public double getDistance() { return distance; }
    public void setDistance(double distance) { this.distance = distance; }
}
