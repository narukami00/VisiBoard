package com.visiboard.app.data;

public class Notification {
    private String id;
    private String toUserId;
    private String type; // "like", "comment", "follow", "message", "admin", "mention"
    private String fromUserId;
    private String fromUserName;
    private String fromUserProfilePic;
    private String noteId;
    private String noteText;
    private double noteLat;
    private double noteLng;
    private String messageId;
    private String messageText;
    private String commentId;  // For mention-in-comment navigation
    private long timestamp;
    private boolean read;

    public Notification() {
    }

    public Notification(String id, String type, String fromUserId, String fromUserName, 
                       String fromUserProfilePic, String noteId, String noteText,
                       double noteLat, double noteLng, long timestamp, boolean read) {
        this.id = id;
        this.type = type;
        this.fromUserId = fromUserId;
        this.fromUserName = fromUserName;
        this.fromUserProfilePic = fromUserProfilePic;
        this.noteId = noteId;
        this.noteText = noteText;
        this.noteLat = noteLat;
        this.noteLng = noteLng;
        this.timestamp = timestamp;
        this.read = read;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getToUserId() { return toUserId; }
    public void setToUserId(String toUserId) { this.toUserId = toUserId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getFromUserId() { return fromUserId; }
    public void setFromUserId(String fromUserId) { this.fromUserId = fromUserId; }

    public String getFromUserName() { return fromUserName; }
    public void setFromUserName(String fromUserName) { this.fromUserName = fromUserName; }

    public String getFromUserProfilePic() { return fromUserProfilePic; }
    public void setFromUserProfilePic(String fromUserProfilePic) { this.fromUserProfilePic = fromUserProfilePic; }

    public String getNoteId() { return noteId; }
    public void setNoteId(String noteId) { this.noteId = noteId; }

    public String getNoteText() { return noteText; }
    public void setNoteText(String noteText) { this.noteText = noteText; }

    public double getNoteLat() { return noteLat; }
    public void setNoteLat(double noteLat) { this.noteLat = noteLat; }

    public double getNoteLng() { return noteLng; }
    public void setNoteLng(double noteLng) { this.noteLng = noteLng; }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public String getMessageText() { return messageText; }
    public void setMessageText(String messageText) { this.messageText = messageText; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }

    public String getCommentId() { return commentId; }
    public void setCommentId(String commentId) { this.commentId = commentId; }
}
