package com.visiboard.app.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

/**
 * Cached feed note for offline access and quick loading
 */
@Entity(tableName = "cached_feed_notes")
public class CachedFeedNote {
    @PrimaryKey
    @NonNull
    public String noteId;
    
    public String text;
    public String summary;
    public String userId;
    public String userName;
    public String userProfilePic;
    public double lat;
    public double lng;
    public long timestamp;
    public int likesCount;
    public int commentsCount;
    public double distance;
    public String imageBase64;
    public int imageWidth;
    public int imageHeight;
    public String localImagePath;
    
    // Cache metadata
    public long cachedTimestamp;
    public int displayOrder; // For maintaining feed order
    
    public CachedFeedNote() {
        this.noteId = "";
    }
}
