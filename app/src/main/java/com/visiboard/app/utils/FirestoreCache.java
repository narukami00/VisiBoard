package com.visiboard.app.utils;

import android.util.LruCache;

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enhanced Firestore cache with:
 * - LRU memory management (max 500 items)
 * - Configurable TTL per data type
 * - Memory-efficient design
 */
public class FirestoreCache {
    
    private static FirestoreCache instance;
    private final LruCache<String, CachedData> cache;
    
    // Different TTL for different data types
    private static final long DEFAULT_EXPIRY_MS = 5 * 60 * 1000; // 5 minutes
    private static final long USER_EXPIRY_MS = 15 * 60 * 1000; // 15 minutes (user data changes rarely)
    private static final long NOTE_EXPIRY_MS = 3 * 60 * 1000; // 3 minutes (notes update frequently)
    private static final long LIST_EXPIRY_MS = 2 * 60 * 1000; // 2 minutes (lists like followers/following)
    
    private static class CachedData {
        final Object data;
        final long timestamp;
        final long customTTL;
        
        CachedData(Object data, long ttl) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
            this.customTTL = ttl;
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > customTTL;
        }
    }
    
    private FirestoreCache() {
        // LRU cache with max 500 entries
        cache = new LruCache<String, CachedData>(500) {
            @Override
            protected int sizeOf(String key, CachedData value) {
                return 1; // Count entries
            }
        };
    }
    
    public static synchronized FirestoreCache getInstance() {
        if (instance == null) {
            instance = new FirestoreCache();
        }
        return instance;
    }
    
    public void put(String key, Object data) {
        put(key, data, DEFAULT_EXPIRY_MS);
    }
    
    public void put(String key, Object data, long ttl) {
        if (key != null && data != null) {
            cache.put(key, new CachedData(data, ttl));
        }
    }
    
    public Object get(String key) {
        if (key == null) return null;
        
        CachedData cached = cache.get(key);
        if (cached != null && !cached.isExpired()) {
            return cached.data;
        }
        
        if (cached != null) {
            cache.remove(key);
        }
        return null;
    }
    
    public void invalidate(String key) {
        cache.remove(key);
    }
    
    public void clear() {
        cache.evictAll();
    }
    
    public void cleanExpired() {
        Map<String, CachedData> snapshot = cache.snapshot();
        for (Map.Entry<String, CachedData> entry : snapshot.entrySet()) {
            if (entry.getValue().isExpired()) {
                cache.remove(entry.getKey());
            }
        }
    }
    
    public void trimMemory() {
        // Trim to 50% on memory pressure
        cache.trimToSize(cache.size() / 2);
    }
    
    public int size() {
        return cache.size();
    }
    
    // Convenience methods with appropriate TTLs
    public void putUser(String userId, Object userData) {
        put(userKey(userId), userData, USER_EXPIRY_MS);
    }
    
    public void putNote(String noteId, Object noteData) {
        put(noteKey(noteId), noteData, NOTE_EXPIRY_MS);
    }
    
    public void putList(String key, Object listData) {
        put(key, listData, LIST_EXPIRY_MS);
    }
    
    // Cache key generators
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
    
    public static String commentsKey(String noteId) {
        return "comments_" + noteId;
    }
    
    public static String likesKey(String noteId) {
        return "likes_" + noteId;
    }
    
    public static String notificationsKey(String userId) {
        return "notifications_" + userId;
    }
}
