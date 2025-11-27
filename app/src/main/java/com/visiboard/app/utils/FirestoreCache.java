package com.visiboard.app.utils;

import android.util.LruCache;

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FirestoreCache {
    
    private static FirestoreCache instance;
    private final Map<String, CachedData> cache;
    private static final long CACHE_EXPIRY_MS = 5 * 60 * 1000; // 5 minutes
    
    private static class CachedData {
        final Object data;
        final long timestamp;
        
        CachedData(Object data) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_EXPIRY_MS;
        }
    }
    
    private FirestoreCache() {
        cache = new ConcurrentHashMap<>();
    }
    
    public static synchronized FirestoreCache getInstance() {
        if (instance == null) {
            instance = new FirestoreCache();
        }
        return instance;
    }
    
    public void put(String key, Object data) {
        cache.put(key, new CachedData(data));
    }
    
    public Object get(String key) {
        CachedData cached = cache.get(key);
        if (cached != null && !cached.isExpired()) {
            return cached.data;
        }
        cache.remove(key);
        return null;
    }
    
    public void invalidate(String key) {
        cache.remove(key);
    }
    
    public void clear() {
        cache.clear();
    }
    
    public void cleanExpired() {
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
    
    // User-specific cache keys
    public static String userKey(String userId) {
        return "user_" + userId;
    }
    
    public static String noteKey(String noteId) {
        return "note_" + noteId;
    }
    
    public static String followersKey(String userId) {
        return "followers_" + userId;
    }
    
    public static String followingKey(String userId) {
        return "following_" + userId;
    }
}
