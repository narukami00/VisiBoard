package com.visiboard.app.chat;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.visiboard.app.data.Chat;
import com.visiboard.app.data.ChatMessage;

import java.util.HashMap;
import java.util.Map;

/**
 * Singleton manager for all chat-related Firebase Realtime Database operations.
 */
public class ChatManager {
    private static final String TAG = "ChatManager";
    
    private static ChatManager instance;
    private final DatabaseReference database;
    private final FirebaseAuth auth;

    private ChatManager() {
        database = FirebaseDatabase.getInstance().getReference();
        auth = FirebaseAuth.getInstance();
    }

    public static synchronized ChatManager getInstance() {
        if (instance == null) {
            instance = new ChatManager();
        }
        return instance;
    }

    /**
     * Generates a consistent chat ID for two users.
     * Always sorts user IDs to ensure the same chat ID regardless of who initiates.
     */
    public static String generateChatId(String uid1, String uid2) {
        return uid1.compareTo(uid2) < 0 
            ? uid1 + "_" + uid2 
            : uid2 + "_" + uid1;
    }

    /**
     * Gets the current user's ID.
     */
    public String getCurrentUserId() {
        return auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
    }

    /**
     * Sends a text message to a chat.
     */
    public void sendMessage(String chatId, String recipientId, String recipientName, 
                           String recipientProfilePic, String text, SendMessageCallback callback) {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            callback.onError("Not logged in");
            return;
        }

        long timestamp = System.currentTimeMillis();
        
        // Create message
        ChatMessage message = new ChatMessage(currentUserId, text, timestamp);
        
        // Generate message ID
        String messageId = database.child("chats").child(chatId).child("messages").push().getKey();
        if (messageId == null) {
            callback.onError("Failed to generate message ID");
            return;
        }
        message.setId(messageId);

        // Build updates map for atomic write
        Map<String, Object> updates = new HashMap<>();
        
        // Add message to chat
        updates.put("chats/" + chatId + "/messages/" + messageId, message);
        
        // Update last message in chat
        Map<String, Object> lastMessage = new HashMap<>();
        lastMessage.put("text", text);
        lastMessage.put("senderId", currentUserId);
        lastMessage.put("timestamp", timestamp);
        lastMessage.put("type", "text");
        updates.put("chats/" + chatId + "/lastMessage", lastMessage);
        
        // Ensure participants are set
        updates.put("chats/" + chatId + "/participants/" + currentUserId, true);
        updates.put("chats/" + chatId + "/participants/" + recipientId, true);
        
        // Update user_chats for current user
        updates.put("user_chats/" + currentUserId + "/" + chatId + "/recipientId", recipientId);
        updates.put("user_chats/" + currentUserId + "/" + chatId + "/recipientName", recipientName);
        updates.put("user_chats/" + currentUserId + "/" + chatId + "/recipientProfilePic", recipientProfilePic);
        updates.put("user_chats/" + currentUserId + "/" + chatId + "/lastMessage", text);
        updates.put("user_chats/" + currentUserId + "/" + chatId + "/lastMessageTime", timestamp);
        updates.put("user_chats/" + currentUserId + "/" + chatId + "/lastMessageType", "text");
        
        // Update user_chats for recipient
        // We need to get current user's info - for now, we'll set basic info
        // This will be enhanced later with proper user data
        updates.put("user_chats/" + recipientId + "/" + chatId + "/recipientId", currentUserId);
        updates.put("user_chats/" + recipientId + "/" + chatId + "/lastMessage", text);
        updates.put("user_chats/" + recipientId + "/" + chatId + "/lastMessageTime", timestamp);
        updates.put("user_chats/" + recipientId + "/" + chatId + "/lastMessageType", "text");
        
        // Increment unread count for recipient
        database.child("user_chats").child(recipientId).child(chatId).child("unreadCount")
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    int currentCount = 0;
                    if (snapshot.exists()) {
                        Integer val = snapshot.getValue(Integer.class);
                        if (val != null) currentCount = val;
                    }
                    updates.put("user_chats/" + recipientId + "/" + chatId + "/unreadCount", currentCount + 1);
                    
                    // Perform atomic update
                    database.updateChildren(updates)
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "Message sent successfully");
                            callback.onSuccess(message);
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to send message", e);
                            callback.onError(e.getMessage());
                        });
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    callback.onError(error.getMessage());
                }
            });
    }

    /**
     * Sends a reply to a specific message.
     */
    public void replyToMessage(String chatId, String recipientId, String recipientName, 
                              String recipientProfilePic, String text, ChatMessage replyTo, 
                              SendMessageCallback callback) {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            callback.onError("Not logged in");
            return;
        }

        long timestamp = System.currentTimeMillis();
        
        // Create message
        ChatMessage message = new ChatMessage(currentUserId, text, timestamp);
        // Set reply fields
        message.setReplyToId(replyTo.getId());
        message.setReplyToName(replyTo.getSenderId().equals(currentUserId) ? "You" : recipientName); // Simple logic (or pass sender name)
        message.setReplyToText(replyTo.isVoice() ? "🎤 Voice message" : replyTo.getText());
        message.setReplyToType(replyTo.getType());
        
        // Generate message ID
        String messageId = database.child("chats").child(chatId).child("messages").push().getKey();
        if (messageId == null) {
            callback.onError("Failed to generate message ID");
            return;
        }
        message.setId(messageId);

        // Build updates map for atomic write
        Map<String, Object> updates = new HashMap<>();
        
        // Add message to chat
        updates.put("chats/" + chatId + "/messages/" + messageId, message);
        
        // Update last message in chat
        Map<String, Object> lastMessage = new HashMap<>();
        lastMessage.put("text", text);
        lastMessage.put("senderId", currentUserId);
        lastMessage.put("timestamp", timestamp);
        lastMessage.put("type", "text");
        updates.put("chats/" + chatId + "/lastMessage", lastMessage);
        
        // Ensure participants are set
        updates.put("chats/" + chatId + "/participants/" + currentUserId, true);
        updates.put("chats/" + chatId + "/participants/" + recipientId, true);
        
        // Update user_chats for current user
        updates.put("user_chats/" + currentUserId + "/" + chatId + "/recipientId", recipientId);
        updates.put("user_chats/" + currentUserId + "/" + chatId + "/recipientName", recipientName);
        updates.put("user_chats/" + currentUserId + "/" + chatId + "/recipientProfilePic", recipientProfilePic);
        updates.put("user_chats/" + currentUserId + "/" + chatId + "/lastMessage", text);
        updates.put("user_chats/" + currentUserId + "/" + chatId + "/lastMessageTime", timestamp);
        updates.put("user_chats/" + currentUserId + "/" + chatId + "/lastMessageType", "text");
        
        // Update user_chats for recipient
        updates.put("user_chats/" + recipientId + "/" + chatId + "/recipientId", currentUserId);
        updates.put("user_chats/" + recipientId + "/" + chatId + "/lastMessage", text);
        updates.put("user_chats/" + recipientId + "/" + chatId + "/lastMessageTime", timestamp);
        updates.put("user_chats/" + recipientId + "/" + chatId + "/lastMessageType", "text");
        
        // Increment unread count for recipient
        database.child("user_chats").child(recipientId).child(chatId).child("unreadCount")
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    int currentCount = 0;
                    if (snapshot.exists()) {
                        Integer val = snapshot.getValue(Integer.class);
                        if (val != null) currentCount = val;
                    }
                    updates.put("user_chats/" + recipientId + "/" + chatId + "/unreadCount", currentCount + 1);
                    
                    database.updateChildren(updates)
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "Reply sent successfully");
                            callback.onSuccess(message);
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to send reply", e);
                            callback.onError(e.getMessage());
                        });
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    callback.onError(error.getMessage());
                }
            });
    }

    /**
     * Sends a voice message to a chat.
     */
    public void sendVoiceMessage(String chatId, String recipientId, String recipientName,
                                 String recipientProfilePic, String voiceBase64, int duration,
                                 SendMessageCallback callback) {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            callback.onError("Not logged in");
            return;
        }

        long timestamp = System.currentTimeMillis();
        
        // Create voice message
        ChatMessage message = new ChatMessage(currentUserId, voiceBase64, duration, timestamp);
        
        // Generate message ID
        String messageId = database.child("chats").child(chatId).child("messages").push().getKey();
        if (messageId == null) {
            callback.onError("Failed to generate message ID");
            return;
        }
        message.setId(messageId);

        String displayText = "🎤 Voice message";

        // Build updates map
        Map<String, Object> updates = new HashMap<>();
        
        updates.put("chats/" + chatId + "/messages/" + messageId, message);
        
        Map<String, Object> lastMessage = new HashMap<>();
        lastMessage.put("text", displayText);
        lastMessage.put("senderId", currentUserId);
        lastMessage.put("timestamp", timestamp);
        lastMessage.put("type", "voice");
        updates.put("chats/" + chatId + "/lastMessage", lastMessage);
        
        updates.put("chats/" + chatId + "/participants/" + currentUserId, true);
        updates.put("chats/" + chatId + "/participants/" + recipientId, true);
        
        updates.put("user_chats/" + currentUserId + "/" + chatId + "/recipientId", recipientId);
        updates.put("user_chats/" + currentUserId + "/" + chatId + "/recipientName", recipientName);
        updates.put("user_chats/" + currentUserId + "/" + chatId + "/recipientProfilePic", recipientProfilePic);
        updates.put("user_chats/" + currentUserId + "/" + chatId + "/lastMessage", displayText);
        updates.put("user_chats/" + currentUserId + "/" + chatId + "/lastMessageTime", timestamp);
        updates.put("user_chats/" + currentUserId + "/" + chatId + "/lastMessageType", "voice");
        
        updates.put("user_chats/" + recipientId + "/" + chatId + "/recipientId", currentUserId);
        updates.put("user_chats/" + recipientId + "/" + chatId + "/lastMessage", displayText);
        updates.put("user_chats/" + recipientId + "/" + chatId + "/lastMessageTime", timestamp);
        updates.put("user_chats/" + recipientId + "/" + chatId + "/lastMessageType", "voice");
        
        database.updateChildren(updates)
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "Voice message sent successfully");
                callback.onSuccess(message);
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Failed to send voice message", e);
                callback.onError(e.getMessage());
            });
    }

    /**
     * Sends an image message to a chat.
     */
    public void sendImageMessage(String chatId, String recipientId, String recipientName,
                                 String recipientProfilePic, String imageUrl,
                                 SendMessageCallback callback) {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            callback.onError("Not logged in");
            return;
        }

        long timestamp = System.currentTimeMillis();
        
        // Create image message
        ChatMessage message = new ChatMessage(currentUserId, imageUrl, timestamp, true);
        
        // Generate message ID
        String messageId = database.child("chats").child(chatId).child("messages").push().getKey();
        if (messageId == null) {
            callback.onError("Failed to generate message ID");
            return;
        }
        message.setId(messageId);

        String displayText = "📷 Image";

        // Build updates map
        Map<String, Object> updates = new HashMap<>();
        
        updates.put("chats/" + chatId + "/messages/" + messageId, message);
        
        Map<String, Object> lastMessage = new HashMap<>();
        lastMessage.put("text", displayText);
        lastMessage.put("senderId", currentUserId);
        lastMessage.put("timestamp", timestamp);
        lastMessage.put("type", "image");
        updates.put("chats/" + chatId + "/lastMessage", lastMessage);
        
        updates.put("chats/" + chatId + "/participants/" + currentUserId, true);
        updates.put("chats/" + chatId + "/participants/" + recipientId, true);
        
        updates.put("user_chats/" + currentUserId + "/" + chatId + "/recipientId", recipientId);
        updates.put("user_chats/" + currentUserId + "/" + chatId + "/recipientName", recipientName);
        updates.put("user_chats/" + currentUserId + "/" + chatId + "/recipientProfilePic", recipientProfilePic);
        updates.put("user_chats/" + currentUserId + "/" + chatId + "/lastMessage", displayText);
        updates.put("user_chats/" + currentUserId + "/" + chatId + "/lastMessageTime", timestamp);
        updates.put("user_chats/" + currentUserId + "/" + chatId + "/lastMessageType", "image");
        
        updates.put("user_chats/" + recipientId + "/" + chatId + "/recipientId", currentUserId);
        updates.put("user_chats/" + recipientId + "/" + chatId + "/lastMessage", displayText);
        updates.put("user_chats/" + recipientId + "/" + chatId + "/lastMessageTime", timestamp);
        updates.put("user_chats/" + recipientId + "/" + chatId + "/lastMessageType", "image");
        
        database.updateChildren(updates)
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "Image message sent successfully");
                callback.onSuccess(message);
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Failed to send image message", e);
                callback.onError(e.getMessage());
            });
    }

    /**
     * Listens for new messages in a chat.
     */
    public ChildEventListener listenForMessages(String chatId, MessageListener listener) {
        Query messagesRef = database.child("chats").child(chatId).child("messages")
            .orderByChild("timestamp");
        
        ChildEventListener childListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                ChatMessage message = snapshot.getValue(ChatMessage.class);
                if (message != null) {
                    message.setId(snapshot.getKey());
                    listener.onMessageReceived(message);
                }
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                ChatMessage message = snapshot.getValue(ChatMessage.class);
                if (message != null) {
                    message.setId(snapshot.getKey());
                    listener.onMessageUpdated(message);
                }
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                // Messages are not deleted in this implementation
            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                // Not used
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Messages listener cancelled", error.toException());
            }
        };
        
        messagesRef.addChildEventListener(childListener);
        return childListener;
    }

    /**
     * Removes message listener.
     */
    public void removeMessagesListener(String chatId, ChildEventListener listener) {
        if (listener != null) {
            database.child("chats").child(chatId).child("messages").removeEventListener(listener);
        }
    }

    /**
     * Listens for user's active chats.
     */
    public ValueEventListener listenForUserChats(String userId, ChatsListener listener) {
        ValueEventListener valueListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                listener.onChatsLoaded(snapshot);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "User chats listener cancelled", error.toException());
            }
        };
        
        database.child("user_chats").child(userId)
            .orderByChild("lastMessageTime")
            .addValueEventListener(valueListener);
        
        return valueListener;
    }

    /**
     * Removes user chats listener.
     */
    public void removeUserChatsListener(String userId, ValueEventListener listener) {
        if (listener != null) {
            database.child("user_chats").child(userId).removeEventListener(listener);
        }
    }

    /**
     * Marks all messages in a chat as read for the current user.
     */
    public void markChatAsRead(String chatId) {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null) return;
        
        // Reset unread count
        database.child("user_chats").child(currentUserId).child(chatId).child("unreadCount").setValue(0);
    }

    /**
     * Checks if a chat exists between two users.
     */
    public void checkChatExists(String chatId, ChatExistsCallback callback) {
        database.child("chats").child(chatId).child("messages")
            .limitToFirst(1)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    callback.onResult(snapshot.exists() && snapshot.getChildrenCount() > 0);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    callback.onResult(false);
                }
            });
    }

    /**
     * Updates user info for a chat (used when we have current user's info).
     */
    public void updateChatUserInfo(String chatId, String recipientId, 
                                   String currentUserName, String currentUserProfilePic) {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null) return;
        
        Map<String, Object> updates = new HashMap<>();
        updates.put("user_chats/" + recipientId + "/" + chatId + "/recipientName", currentUserName);
        updates.put("user_chats/" + recipientId + "/" + chatId + "/recipientProfilePic", currentUserProfilePic);
        
        database.updateChildren(updates);
    }

    // Callbacks
    public interface SendMessageCallback {
        void onSuccess(ChatMessage message);
        void onError(String error);
    }

    public interface MessageListener {
        void onMessageReceived(ChatMessage message);
        void onMessageUpdated(ChatMessage message);
    }

    public interface ChatsListener {
        void onChatsLoaded(DataSnapshot snapshot);
    }

    public interface ChatExistsCallback {
        void onResult(boolean exists);
    }
    /**
     * Sets the typing status for the current user in a chat.
     */
    public void setTypingStatus(String chatId, String userId, boolean isTyping) {
        database.child("chats").child(chatId).child("typing").child(userId).setValue(isTyping);
    }

    /**
     * Listens for the other user's typing status in a chat.
     */
    public void listenForTyping(String chatId, String otherUserId, TypingListener listener) {
        database.child("chats").child(chatId).child("typing").child(otherUserId)
            .addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    Boolean isTyping = snapshot.getValue(Boolean.class);
                    listener.onTypingChanged(isTyping != null && isTyping);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    // Ignore
                }
            });
    }

    /**
     * Sets the user's presence status (Online/Offline) and Last Seen.
     * Should be called when the app starts or user logs in.
     */
    public void setupPresenceSystem(String userId) {
        DatabaseReference userStatusRef = database.child("users").child(userId).child("status");
        DatabaseReference lastSeenRef = database.child("users").child(userId).child("lastSeen");
        DatabaseReference connectedRef = database.child(".info/connected");

        connectedRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean connected = Boolean.TRUE.equals(snapshot.getValue(Boolean.class));
                if (connected) {
                    // When connected, set status to online
                    userStatusRef.setValue("online");
                    
                    // When disconnected, set status to offline and update lastSeen
                    userStatusRef.onDisconnect().setValue("offline");
                    lastSeenRef.onDisconnect().setValue(com.google.firebase.database.ServerValue.TIMESTAMP);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    /**
     * Listens for another user's presence status.
     */
    public void listenForUserPresence(String userId, PresenceListener listener) {
        database.child("users").child(userId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String status = snapshot.child("status").getValue(String.class);
                Long lastSeen = snapshot.child("lastSeen").getValue(Long.class);
                
                boolean isOnline = "online".equals(status);
                listener.onPresenceChanged(isOnline, lastSeen != null ? lastSeen : 0);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    // Additional Callbacks
    public interface TypingListener {
        void onTypingChanged(boolean isTyping);
    }

    public interface PresenceListener {
        void onPresenceChanged(boolean isOnline, long lastSeenTimestamp);
    }
}
