package com.visiboard.app.utils;

import android.app.Application;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.util.Log;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Central cache coordinator that:
 * - Manages all app caches (memory, disk, image)
 * - Responds to memory pressure
 * - Periodically cleans expired entries
 * - Provides cache statistics
 */
public class CacheManager {
    
    private static final String TAG = "CacheManager";
    private static CacheManager instance;
    
    private final ScheduledExecutorService cleanupExecutor;
    private boolean isInitialized = false;
    private Context appContext;
    
    private CacheManager() {
        cleanupExecutor = Executors.newSingleThreadScheduledExecutor();
    }
    
    public static synchronized CacheManager getInstance() {
        if (instance == null) {
            instance = new CacheManager();
        }
        return instance;
    }
    
    /**
     * Initialize cache manager with cleanup scheduling
     */
    public void initialize(Context context) {
        if (isInitialized) return;
        
        this.appContext = context.getApplicationContext();
        
        // Schedule periodic cleanup every 5 minutes
        cleanupExecutor.scheduleWithFixedDelay(
            this::cleanupExpiredEntries,
            5, // Initial delay
            5, // Period
            TimeUnit.MINUTES
        );
        
        isInitialized = true;
        Log.d(TAG, "CacheManager initialized with periodic cleanup");
    }
    
    /**
     * Handle memory pressure from system
     */
    public void onTrimMemory(int level) {
        Log.d(TAG, "onTrimMemory: level=" + level);
        
        switch (level) {
            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL:
            case ComponentCallbacks2.TRIM_MEMORY_COMPLETE:
                // Critical memory pressure - clear most caches
                clearAllCaches();
                Log.w(TAG, "Critical memory - cleared all caches");
                break;
                
            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW:
            case ComponentCallbacks2.TRIM_MEMORY_MODERATE:
                // Moderate pressure - trim caches
                trimAllCaches();
                Log.i(TAG, "Moderate memory pressure - trimmed caches");
                break;
                
            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE:
            case ComponentCallbacks2.TRIM_MEMORY_BACKGROUND:
                // App in background - trim non-critical caches
                trimNonCriticalCaches();
                Log.i(TAG, "Background trim - cleared non-critical caches");
                break;
                
            case ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN:
                // UI hidden - good time to cleanup
                cleanupExpiredEntries();
                Log.i(TAG, "UI hidden - cleaned expired entries");
                break;
        }
    }
    
    /**
     * Clean expired entries from all caches
     */
    private void cleanupExpiredEntries() {
        try {
            FirestoreCache.getInstance().cleanExpired();
            UserCache.getInstance().cleanExpired();
            FeedCache.getInstance().cleanExpired();
            ImageCache.getInstance().trimMemory();
            
            if (appContext != null) {
                DiskCache.getInstance(appContext).cleanExpired();
            }
            
            logCacheStats();
        } catch (Exception e) {
            Log.e(TAG, "Error during cache cleanup", e);
        }
    }
    
    /**
     * Trim all caches to 50% capacity
     */
    private void trimAllCaches() {
        FirestoreCache.getInstance().trimMemory();
        UserCache.getInstance().trimMemory();
        FeedCache.getInstance().trimMemory();
        ImageCache.getInstance().trimMemory();
    }
    
    /**
     * Clear non-critical caches (keep user/auth data)
     */
    private void trimNonCriticalCaches() {
        FeedCache.getInstance().clear();
        ImageCache.getInstance().trimMemory();
    }
    
    /**
     * Clear all caches completely
     */
    public void clearAllCaches() {
        FirestoreCache.getInstance().clear();
        UserCache.getInstance().clear();
        FeedCache.getInstance().clear();
        ImageCache.getInstance().clearCache();
        
        if (appContext != null) {
            DiskCache.getInstance(appContext).clearAll();
        }
        
        Log.i(TAG, "All caches cleared");
    }
    
    /**
     * Invalidate specific user data across all caches
     */
    public void invalidateUser(String userId) {
        if (userId == null) return;
        
        FirestoreCache.getInstance().invalidate(FirestoreCache.userKey(userId));
        FirestoreCache.getInstance().invalidate(FirestoreCache.followersKey(userId));
        FirestoreCache.getInstance().invalidate(FirestoreCache.followingKey(userId));
        UserCache.getInstance().invalidate(userId);
        
        if (appContext != null) {
            DiskCache.getInstance(appContext).deleteCachedUser(userId);
        }
        
        Log.d(TAG, "Invalidated user cache: " + userId);
    }
    
    /**
     * Invalidate specific note across all caches
     */
    public void invalidateNote(String noteId) {
        if (noteId == null) return;
        
        FirestoreCache.getInstance().invalidate(FirestoreCache.noteKey(noteId));
        FirestoreCache.getInstance().invalidate(FirestoreCache.commentsKey(noteId));
        FirestoreCache.getInstance().invalidate(FirestoreCache.likesKey(noteId));
        FeedCache.getInstance().invalidateNote(noteId);
        
        if (appContext != null) {
            DiskCache.getInstance(appContext).deleteCachedNote(noteId);
        }
        
        Log.d(TAG, "Invalidated note cache: " + noteId);
    }
    
    /**
     * Get cache statistics for monitoring
     */
    public CacheStats getCacheStats() {
        return new CacheStats(
            FirestoreCache.getInstance().size(),
            UserCache.getInstance().size(),
            ImageCache.getInstance().getCacheSizeKB()
        );
    }
    
    /**
     * Log cache statistics
     */
    private void logCacheStats() {
        CacheStats stats = getCacheStats();
        Log.d(TAG, String.format("Cache Stats - Firestore: %d, Users: %d, Images: %d KB",
            stats.firestoreSize, stats.userSize, stats.imageSize));
    }
    
    /**
     * Shutdown cache manager
     */
    public void shutdown() {
        cleanupExecutor.shutdown();
        isInitialized = false;
    }
    
    /**
     * Cache statistics holder
     */
    public static class CacheStats {
        public final int firestoreSize;
        public final int userSize;
        public final int imageSize;
        
        public CacheStats(int firestoreSize, int userSize, int imageSize) {
            this.firestoreSize = firestoreSize;
            this.userSize = userSize;
            this.imageSize = imageSize;
        }
    }
}
