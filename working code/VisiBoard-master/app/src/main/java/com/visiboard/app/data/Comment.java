package com.visiboard.app.data;

public class Comment {
    public String id;
    public String userId;
    public String userName;
    public String text;
    public long timestamp;

    public Comment() {
    }

    public Comment(String id, String userId, String userName, String text, long timestamp) {
        this.id = id;
        this.userId = userId;
        this.userName = userName;
        this.text = text;
        this.timestamp = timestamp;
    }
}
