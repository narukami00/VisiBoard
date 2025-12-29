package com.visiboard.app.utils;

import android.content.Context;
import android.util.Log;

import com.visiboard.app.data.AppDatabase;
import com.visiboard.app.data.CachedFeedNote;
import com.visiboard.app.data.CachedUser;
import com.visiboard.app.data.NearbyNote;
import com.visiboard.app.data.UserInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages disk-based caching using Room database
 * Provides persistence layer for offline access
 */
public class DiskCache {
    
    private static final String TAG = "DiskCache";
    private static DiskCache instance;
    
    private final Context context;
    private final AppDatabase database;
    private final ExecutorService executor;
    
    // Cache expiry times
    private static final long USER_CACHE_EXPIRY_MS = 24 * 60 * 60 * 1000; // 24 hours
    private static final long FEED_CACHE_EXPIRY_MS = 6 * 60 * 60 * 1000; // 6 hours
    
    private DiskCache(Context context) {
        this.context = context.getApplicationContext();
        this.database = AppDatabase.getInstance(this.context);
        this.executor = Executors.newSingleThreadExecutor();
    }
    
    public static synchronized DiskCache getInstance(Context context) {
        if (instance == null) {
            instance = new DiskCache(context);
        }
        return instance;
    }
    
    // ==================== USER CACHING ====================
    
    /**
     * Cache user to disk asynchronously
     */
    public void cacheUser(UserInfo user) {
        if (user == null || user.getUserId() == null) return;
        
        executor.execute(() -> {
            try {
                CachedUser cached = new CachedUser();
                cached.userId = user.getUserId();
                cached.name = user.getName();
                cached.email = user.getEmail();
                cached.profilePic = user.getProfilePic();
                cached.currentTier = user.getCurrentTier();
                cached.lastKnownLocation = user.getLastKnownLocation();
                cached.followersCount = user.getFollowersCount();
                cached.followingCount = user.getFollowingCount();
                cached.totalLikes = user.getTotalLikes();
                cached.isPrivate = user.isPrivate();
                cached.cachedTimestamp = System.currentTimeMillis();
                cached.lastAccessTimestamp = System.currentTimeMillis();
                
                database.cachedUserDao().insert(cached);
            } catch (Exception e) {
                Log.e(TAG, "Error caching user", e);
            }
        });
    }
    
    /**
     * Get cached user synchronously (call from background thread)
     */
    public UserInfo getCachedUser(String userId) {
        if (userId == null) return null;
        
        try {
            CachedUser cached = database.cachedUserDao().getUserById(userId);
            if (cached == null) return null;
            
            // Check if expired
            if (System.currentTimeMillis() - cached.cachedTimestamp > USER_CACHE_EXPIRY_MS) {
                deleteCachedUser(userId);
                return null;
            }
            
            // Update access time
            database.cachedUserDao().updateAccessTime(userId, System.currentTimeMillis());
            
            // Convert to UserInfo
            UserInfo user = new UserInfo();
            user.setUserId(cached.userId);
            user.setName(cached.name);
            user.setEmail(cached.email);
            user.setProfilePic(cached.profilePic);
            user.setCurrentTier(cached.currentTier);
            user.setLastKnownLocation(cached.lastKnownLocation);
            user.setFollowersCount(cached.followersCount);
            user.setFollowingCount(cached.followingCount);
            user.setTotalLikes(cached.totalLikes);
            user.setPrivate(cached.isPrivate);
            
            return user;
        } catch (Exception e) {
            Log.e(TAG, "Error getting cached user", e);
            return null;
        }
    }
    
    /**
     * Delete cached user
     */
    public void deleteCachedUser(String userId) {
        executor.execute(() -> {
            try {
                CachedUser user = database.cachedUserDao().getUserById(userId);
                if (user != null) {
                    database.cachedUserDao().delete(user);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error deleting cached user", e);
            }
        });
    }
    
    // ==================== FEED CACHING ====================
    
    /**
     * Cache feed notes to disk asynchronously
     */
    public void cacheFeedNotes(List<NearbyNote> notes) {
        if (notes == null || notes.isEmpty()) return;
        
        executor.execute(() -> {
            try {
                List<CachedFeedNote> cachedNotes = new ArrayList<>();
                long timestamp = System.currentTimeMillis();
                
                for (int i = 0; i < notes.size(); i++) {
                    NearbyNote note = notes.get(i);
                    if (note.getId() == null) continue;
                    
                    CachedFeedNote cached = new CachedFeedNote();
                    cached.noteId = note.getId();
                    cached.text = note.getText();
                    cached.summary = note.getSummary();
                    cached.userId = note.getUserId();
                    cached.userName = note.getUserName();
                    cached.userProfilePic = note.getUserProfilePic();
                    cached.lat = note.getLat();
                    cached.lng = note.getLng();
                    cached.timestamp = note.getTimestamp();
                    cached.likesCount = note.getLikesCount();
                    cached.commentsCount = note.getCommentsCount();
                    cached.distance = note.getDistance();
                    cached.imageBase64 = note.getImageBase64();
                    cached.imageWidth = note.getImageWidth();
                    cached.imageHeight = note.getImageHeight();
                    cached.localImagePath = note.getLocalImagePath();
                    cached.cachedTimestamp = timestamp;
                    cached.displayOrder = i;
                    
                    cachedNotes.add(cached);
                }
                
                database.cachedFeedNoteDao().insertAll(cachedNotes);
                Log.d(TAG, "Cached " + cachedNotes.size() + " feed notes to disk");
            } catch (Exception e) {
                Log.e(TAG, "Error caching feed notes", e);
            }
        });
    }
    
    /**
     * Get cached feed notes synchronously (call from background thread)
     */
    public List<NearbyNote> getCachedFeedNotes(int limit) {
        try {
            List<CachedFeedNote> cachedNotes = database.cachedFeedNoteDao().getFeedNotes(limit);
            if (cachedNotes == null || cachedNotes.isEmpty()) return null;
            
            // Check if expired
            long oldestTimestamp = cachedNotes.get(0).cachedTimestamp;
            if (System.currentTimeMillis() - oldestTimestamp > FEED_CACHE_EXPIRY_MS) {
                clearFeedCache();
                return null;
            }
            
            // Convert to NearbyNote
            List<NearbyNote> notes = new ArrayList<>();
            for (CachedFeedNote cached : cachedNotes) {
                NearbyNote note = new NearbyNote();
                note.setId(cached.noteId);
                note.setText(cached.text);
                note.setSummary(cached.summary);
                note.setUserId(cached.userId);
                note.setUserName(cached.userName);
                note.setUserProfilePic(cached.userProfilePic);
                note.setLat(cached.lat);
                note.setLng(cached.lng);
                note.setTimestamp(cached.timestamp);
                note.setLikesCount(cached.likesCount);
                note.setCommentsCount(cached.commentsCount);
                note.setDistance(cached.distance);
                note.setImageBase64(cached.imageBase64);
                note.setImageWidth(cached.imageWidth);
                note.setImageHeight(cached.imageHeight);
                note.setLocalImagePath(cached.localImagePath);
                
                notes.add(note);
            }
            
            return notes;
        } catch (Exception e) {
            Log.e(TAG, "Error getting cached feed notes", e);
            return null;
        }
    }
    
    /**
     * Clear feed cache
     */
    public void clearFeedCache() {
        executor.execute(() -> {
            try {
                database.cachedFeedNoteDao().deleteAll();
                Log.d(TAG, "Feed cache cleared");
            } catch (Exception e) {
                Log.e(TAG, "Error clearing feed cache", e);
            }
        });
    }
    
    /**
     * Delete specific cached note
     */
    public void deleteCachedNote(String noteId) {
        executor.execute(() -> {
            try {
                database.cachedFeedNoteDao().deleteById(noteId);
            } catch (Exception e) {
                Log.e(TAG, "Error deleting cached note", e);
            }
        });
    }
    
    // ==================== CLEANUP ====================
    
    /**
     * Clean expired entries from disk cache
     */
    public void cleanExpired() {
        executor.execute(() -> {
            try {
                long userExpiry = System.currentTimeMillis() - USER_CACHE_EXPIRY_MS;
                long feedExpiry = System.currentTimeMillis() - FEED_CACHE_EXPIRY_MS;
                
                database.cachedUserDao().deleteExpired(userExpiry);
                database.cachedFeedNoteDao().deleteExpired(feedExpiry);
                
                Log.d(TAG, "Expired disk cache entries cleaned");
            } catch (Exception e) {
                Log.e(TAG, "Error cleaning expired cache", e);
            }
        });
    }
    
    /**
     * Clear all disk cache
     */
    public void clearAll() {
        executor.execute(() -> {
            try {
                database.cachedUserDao().deleteAll();
                database.cachedFeedNoteDao().deleteAll();
                Log.d(TAG, "All disk cache cleared");
            } catch (Exception e) {
                Log.e(TAG, "Error clearing all cache", e);
            }
        });
    }
}
