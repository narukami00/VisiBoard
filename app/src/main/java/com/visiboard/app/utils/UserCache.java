package com.visiboard.app.utils;

import android.util.Log;
import android.util.LruCache;

import com.visiboard.app.data.UserInfo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Two-tier cache for user profiles:
 * - Memory cache (LRU) for hot data
 * - Time-based expiry (15 minutes for user profiles)
 */
public class UserCache {
    
    private static final String TAG = "UserCache";
    private static UserCache instance;
    
    // User profiles change rarely - 15 minute cache
    private static final long USER_CACHE_EXPIRY_MS = 15 * 60 * 1000;
    
    // LRU cache for most accessed users (max 100 users in memory)
    private final LruCache<String, CachedUser> memoryCache;
    
    // Timestamp tracking for all users
    private final Map<String, Long> accessTimestamps;
    
    private static class CachedUser {
        final UserInfo user;
        final long timestamp;
        
        CachedUser(UserInfo user) {
            this.user = user;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > USER_CACHE_EXPIRY_MS;
        }
    }
    
    private UserCache() {
        // Cache up to 100 users in memory
        memoryCache = new LruCache<String, CachedUser>(100) {
            @Override
            protected int sizeOf(String key, CachedUser value) {
                return 1; // Count items
            }
        };
        accessTimestamps = new ConcurrentHashMap<>();
    }
    
    public static synchronized UserCache getInstance() {
        if (instance == null) {
            instance = new UserCache();
        }
        return instance;
    }
    
    public void put(String userId, UserInfo user) {
        if (userId == null || user == null) return;
        
        memoryCache.put(userId, new CachedUser(user));
        accessTimestamps.put(userId, System.currentTimeMillis());
    }
    
    public UserInfo get(String userId) {
        if (userId == null) return null;
        
        CachedUser cached = memoryCache.get(userId);
        if (cached != null) {
            if (!cached.isExpired()) {
                accessTimestamps.put(userId, System.currentTimeMillis());
                return cached.user;
            } else {
                // Expired, remove
                memoryCache.remove(userId);
                accessTimestamps.remove(userId);
            }
        }
        return null;
    }
    
    public void invalidate(String userId) {
        memoryCache.remove(userId);
        accessTimestamps.remove(userId);
    }
    
    public void clear() {
        memoryCache.evictAll();
        accessTimestamps.clear();
    }
    
    public void cleanExpired() {
        // Remove expired entries
        Map<String, CachedUser> snapshot = memoryCache.snapshot();
        for (Map.Entry<String, CachedUser> entry : snapshot.entrySet()) {
            if (entry.getValue().isExpired()) {
                memoryCache.remove(entry.getKey());
                accessTimestamps.remove(entry.getKey());
            }
        }
    }
    
    public int size() {
        return memoryCache.size();
    }
    
    public void trimMemory() {
        // Trim to 50% capacity on memory pressure
        memoryCache.trimToSize(memoryCache.size() / 2);
    }
}
