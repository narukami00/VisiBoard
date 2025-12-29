package com.visiboard.app.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

/**
 * Cached user profile for offline access
 */
@Entity(tableName = "cached_users")
public class CachedUser {
    @PrimaryKey
    @NonNull
    public String userId;
    
    public String name;
    public String email;
    public String profilePic;
    public String currentTier;
    public String lastKnownLocation;
    public int followersCount;
    public int followingCount;
    public int totalLikes;
    public boolean isPrivate;
    
    // Cache metadata
    public long cachedTimestamp;
    public long lastAccessTimestamp;
    
    public CachedUser() {
        this.userId = "";
    }
}
