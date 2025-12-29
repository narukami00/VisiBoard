package com.visiboard.app.utils;

import android.util.LruCache;

import com.visiboard.app.data.NearbyNote;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache for feed notes with intelligent memory management
 * - Short TTL (3 minutes) since feed content updates frequently
 * - LRU eviction for memory efficiency
 */
public class FeedCache {
    
    private static FeedCache instance;
    private static final long FEED_CACHE_EXPIRY_MS = 3 * 60 * 1000; // 3 minutes
    
    private final LruCache<String, CachedNote> noteCache;
    private final Map<String, Long> noteTimestamps;
    
    // Cache feed list separately with pagination cursor
    private List<NearbyNote> cachedFeedList;
    private long feedListTimestamp;
    private Object lastCursor;
    
    private static class CachedNote {
        final NearbyNote note;
        final long timestamp;
        
        CachedNote(NearbyNote note) {
            this.note = note;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > FEED_CACHE_EXPIRY_MS;
        }
    }
    
    private FeedCache() {
        // Cache up to 200 notes in memory (reasonable for feed scrolling)
        noteCache = new LruCache<String, CachedNote>(200) {
            @Override
            protected int sizeOf(String key, CachedNote value) {
                return 1;
            }
        };
        noteTimestamps = new ConcurrentHashMap<>();
        cachedFeedList = new ArrayList<>();
    }
    
    public static synchronized FeedCache getInstance() {
        if (instance == null) {
            instance = new FeedCache();
        }
        return instance;
    }
    
    public void putNote(String noteId, NearbyNote note) {
        if (noteId == null || note == null) return;
        noteCache.put(noteId, new CachedNote(note));
        noteTimestamps.put(noteId, System.currentTimeMillis());
    }
    
    public NearbyNote getNote(String noteId) {
        if (noteId == null) return null;
        
        CachedNote cached = noteCache.get(noteId);
        if (cached != null && !cached.isExpired()) {
            return cached.note;
        }
        
        if (cached != null && cached.isExpired()) {
            noteCache.remove(noteId);
            noteTimestamps.remove(noteId);
        }
        return null;
    }
    
    public void putFeedList(List<NearbyNote> notes, Object cursor) {
        cachedFeedList = new ArrayList<>(notes);
        feedListTimestamp = System.currentTimeMillis();
        lastCursor = cursor;
        
        // Also cache individual notes
        for (NearbyNote note : notes) {
            putNote(note.getId(), note);
        }
    }
    
    public List<NearbyNote> getCachedFeedList() {
        if (System.currentTimeMillis() - feedListTimestamp < FEED_CACHE_EXPIRY_MS) {
            return new ArrayList<>(cachedFeedList);
        }
        return null;
    }
    
    public Object getLastCursor() {
        return lastCursor;
    }
    
    public void invalidateNote(String noteId) {
        noteCache.remove(noteId);
        noteTimestamps.remove(noteId);
        
        // Also remove from feed list if present
        cachedFeedList.removeIf(note -> noteId.equals(note.getId()));
    }
    
    public void clear() {
        noteCache.evictAll();
        noteTimestamps.clear();
        cachedFeedList.clear();
        lastCursor = null;
    }
    
    public void cleanExpired() {
        Map<String, CachedNote> snapshot = noteCache.snapshot();
        for (Map.Entry<String, CachedNote> entry : snapshot.entrySet()) {
            if (entry.getValue().isExpired()) {
                noteCache.remove(entry.getKey());
                noteTimestamps.remove(entry.getKey());
            }
        }
        
        // Clear feed list if expired
        if (System.currentTimeMillis() - feedListTimestamp > FEED_CACHE_EXPIRY_MS) {
            cachedFeedList.clear();
            lastCursor = null;
        }
    }
    
    public void trimMemory() {
        noteCache.trimToSize(noteCache.size() / 2);
        if (cachedFeedList.size() > 100) {
            cachedFeedList = new ArrayList<>(cachedFeedList.subList(0, 100));
        }
    }
}
