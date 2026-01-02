package com.visiboard.app.data;

/**
 * Represents a single chat message in a conversation.
 * Used with Firebase Realtime Database.
 */
public class ChatMessage {
    private String id;
    private String senderId;
    private String text;
    private long timestamp;
    private String type; // "text" or "voice"
    private String voiceBase64; // Base64 encoded audio for voice messages
    private int voiceDuration; // Duration in seconds for voice messages
    private boolean read;
    private String imageUrl; // URL of the image from ImgBB

    // Required empty constructor for Firebase
    public ChatMessage() {}

    public ChatMessage(String senderId, String text, long timestamp) {
        this.senderId = senderId;
        this.text = text;
        this.timestamp = timestamp;
        this.type = "text";
        this.read = false;
    }

    // Voice message constructor
    public ChatMessage(String senderId, String voiceBase64, int voiceDuration, long timestamp) {
        this.senderId = senderId;
        this.voiceBase64 = voiceBase64;
        this.voiceDuration = voiceDuration;
        this.timestamp = timestamp;
        this.type = "voice";
        this.text = "🎤 Voice message";
        this.read = false;
    }

    // Image message constructor
    public ChatMessage(String senderId, String imageUrl, long timestamp, boolean isImage) {
        this.senderId = senderId;
        this.imageUrl = imageUrl;
        this.timestamp = timestamp;
        this.type = "image";
        this.text = "📷 Image";
        this.read = false;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getVoiceBase64() { return voiceBase64; }
    public void setVoiceBase64(String voiceBase64) { this.voiceBase64 = voiceBase64; }

    public int getVoiceDuration() { return voiceDuration; }
    public void setVoiceDuration(int voiceDuration) { this.voiceDuration = voiceDuration; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }

    public boolean isVoice() {
        return "voice".equals(type);
    }
    
    public boolean isImage() {
        return "image".equals(type);
    }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    // Reply fields
    private String replyToId;
    private String replyToName;
    private String replyToText;
    private String replyToType; // "text" or "voice"

    public String getReplyToId() { return replyToId; }
    public void setReplyToId(String replyToId) { this.replyToId = replyToId; }

    public String getReplyToName() { return replyToName; }
    public void setReplyToName(String replyToName) { this.replyToName = replyToName; }

    public String getReplyToText() { return replyToText; }
    public void setReplyToText(String replyToText) { this.replyToText = replyToText; }

    public String getReplyToType() { return replyToType; }
    public void setReplyToType(String replyToType) { this.replyToType = replyToType; }

    // Reactions
    // Map of userId -> reactionType (e.g., "haha", "love")
    private java.util.Map<String, String> reactions = new java.util.HashMap<>();

    public java.util.Map<String, String> getReactions() { return reactions; }
    public void setReactions(java.util.Map<String, String> reactions) { this.reactions = reactions; }
    
    public void addReaction(String userId, String reaction) {
        if (this.reactions == null) this.reactions = new java.util.HashMap<>();
        this.reactions.put(userId, reaction);
    }
    
    public void removeReaction(String userId) {
        if (this.reactions != null) {
            this.reactions.remove(userId);
        }
    }
}
