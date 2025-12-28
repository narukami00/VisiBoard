package com.visiboard.app.data;

/**
 * Represents a chat conversation for display in the chat list.
 * Contains metadata about the chat and the other participant.
 */
public class Chat {
    private String chatId;
    private String recipientId;
    private String recipientName;
    private String recipientProfilePic;
    private String lastMessage;
    private long lastMessageTime;
    private String lastMessageType;
    private int unreadCount;

    // Required empty constructor for Firebase
    public Chat() {}

    public Chat(String chatId, String recipientId, String recipientName, String recipientProfilePic) {
        this.chatId = chatId;
        this.recipientId = recipientId;
        this.recipientName = recipientName;
        this.recipientProfilePic = recipientProfilePic;
        this.unreadCount = 0;
    }

    // Getters and Setters
    public String getChatId() { return chatId; }
    public void setChatId(String chatId) { this.chatId = chatId; }

    public String getRecipientId() { return recipientId; }
    public void setRecipientId(String recipientId) { this.recipientId = recipientId; }

    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }

    public String getRecipientProfilePic() { return recipientProfilePic; }
    public void setRecipientProfilePic(String recipientProfilePic) { this.recipientProfilePic = recipientProfilePic; }

    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }

    public long getLastMessageTime() { return lastMessageTime; }
    public void setLastMessageTime(long lastMessageTime) { this.lastMessageTime = lastMessageTime; }

    public String getLastMessageType() { return lastMessageType; }
    public void setLastMessageType(String lastMessageType) { this.lastMessageType = lastMessageType; }

    public int getUnreadCount() { return unreadCount; }
    public void setUnreadCount(int unreadCount) { this.unreadCount = unreadCount; }

    /**
     * Returns display text for last message (handles voice messages)
     */
    public String getLastMessageDisplay() {
        if ("voice".equals(lastMessageType)) {
            return "🎤 Voice message";
        }
        return lastMessage;
    }
}
