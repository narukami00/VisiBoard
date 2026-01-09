package com.visiboard.app.utils;

import android.util.Log;

import androidx.annotation.Nullable;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.visiboard.app.data.Mention;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles sending notifications when users are mentioned in notes or comments.
 */
public class MentionNotificationHelper {

    private static final String TAG = "MentionNotification";

    /**
     * Send mention notifications to all mentioned users.
     * Handles both individual mentions and @following (broadcasts to all followers).
     *
     * @param fromUserId      The user who created the mention
     * @param fromUserName    Display name of the mentioning user
     * @param fromUserPic     Profile pic (base64) of the mentioning user
     * @param noteId          The note ID where mention occurred
     * @param noteText        Preview of the note text
     * @param lat             Latitude of the note
     * @param lng             Longitude of the note
     * @param commentId       Comment ID if mention is in a comment (null for note mentions)
     * @param mentions        List of mentions to process
     */
    public static void sendMentionNotifications(
            String fromUserId,
            String fromUserName,
            String fromUserPic,
            String noteId,
            String noteText,
            double lat,
            double lng,
            @Nullable String commentId,
            List<Mention> mentions) {

        if (mentions == null || mentions.isEmpty()) {
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        for (Mention mention : mentions) {
            if (mention.isFollowingMention()) {
                // Send to all followers
                sendToAllFollowers(db, fromUserId, fromUserName, fromUserPic,
                        noteId, noteText, lat, lng, commentId);
            } else if (mention.getUserId() != null) {
                // Don't notify yourself
                if (!mention.getUserId().equals(fromUserId)) {
                    sendSingleMentionNotification(db, mention.getUserId(),
                            fromUserId, fromUserName, fromUserPic,
                            noteId, noteText, lat, lng, commentId);
                }
            }
        }
    }

    /**
     * Send notification to all followers of the mentioning user.
     */
    private static void sendToAllFollowers(
            FirebaseFirestore db,
            String fromUserId,
            String fromUserName,
            String fromUserPic,
            String noteId,
            String noteText,
            double lat,
            double lng,
            @Nullable String commentId) {

        Log.d(TAG, "Sending @following notification to all followers of: " + fromUserId);

        db.collection("users").document(fromUserId)
                .collection("followers").get()
                .addOnSuccessListener(followers -> {
                    Log.d(TAG, "Found " + followers.size() + " followers to notify");
                    
                    for (DocumentSnapshot follower : followers) {
                        String followerId = follower.getId();
                        // Don't notify yourself
                        if (!followerId.equals(fromUserId)) {
                            sendSingleMentionNotification(db, followerId,
                                    fromUserId, fromUserName, fromUserPic,
                                    noteId, noteText, lat, lng, commentId);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to fetch followers for @following", e);
                });
    }

    /**
     * Send a single mention notification to one user.
     */
    private static void sendSingleMentionNotification(
            FirebaseFirestore db,
            String toUserId,
            String fromUserId,
            String fromUserName,
            String fromUserPic,
            String noteId,
            String noteText,
            double lat,
            double lng,
            @Nullable String commentId) {

        Log.d(TAG, "Sending mention notification to: " + toUserId);

        Map<String, Object> notification = new HashMap<>();
        notification.put("toUserId", toUserId);
        notification.put("fromUserId", fromUserId);
        notification.put("fromUserName", fromUserName);
        notification.put("fromUserProfilePic", fromUserPic);
        notification.put("type", "mention");
        notification.put("noteId", noteId);
        notification.put("noteText", noteText);
        notification.put("noteLat", lat);
        notification.put("noteLng", lng);
        notification.put("timestamp", System.currentTimeMillis());
        notification.put("read", false);

        if (commentId != null) {
            notification.put("commentId", commentId);
        }

        db.collection("notifications").add(notification)
                .addOnSuccessListener(docRef -> {
                    Log.d(TAG, "Mention notification sent: " + docRef.getId());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to send mention notification to " + toUserId, e);
                });
    }
}
