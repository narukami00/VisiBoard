package com.visiboard.app.data;

public class Message {
    private String id;
    private String fromUserId;
    private String fromUserName;
    private String fromUserProfilePic;
    private String toUserId;
    private String messageText;
    private long timestamp;
    private boolean anonymous;
    private boolean read;

    public Message() {}

    public Message(String id, String fromUserId, String fromUserName, String fromUserProfilePic,
                   String toUserId, String messageText, long timestamp, boolean anonymous, boolean read) {
        this.id = id;
        this.fromUserId = fromUserId;
        this.fromUserName = fromUserName;
        this.fromUserProfilePic = fromUserProfilePic;
        this.toUserId = toUserId;
        this.messageText = messageText;
        this.timestamp = timestamp;
        this.anonymous = anonymous;
        this.read = read;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFromUserId() { return fromUserId; }
    public void setFromUserId(String fromUserId) { this.fromUserId = fromUserId; }

    public String getFromUserName() { return fromUserName; }
    public void setFromUserName(String fromUserName) { this.fromUserName = fromUserName; }

    public String getFromUserProfilePic() { return fromUserProfilePic; }
    public void setFromUserProfilePic(String fromUserProfilePic) { this.fromUserProfilePic = fromUserProfilePic; }

    public String getToUserId() { return toUserId; }
    public void setToUserId(String toUserId) { this.toUserId = toUserId; }

    public String getMessageText() { return messageText; }
    public void setMessageText(String messageText) { this.messageText = messageText; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public boolean isAnonymous() { return anonymous; }
    public void setAnonymous(boolean anonymous) { this.anonymous = anonymous; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
}
