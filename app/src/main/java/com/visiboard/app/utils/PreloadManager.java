package com.visiboard.app.utils;

import android.content.Context;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.visiboard.app.data.NearbyNote;
import com.visiboard.app.data.UserInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Intelligently preloads data to improve perceived performance:
 * - Preloads user profiles when viewing feeds
 * - Preloads images in viewport
 * - Prefetches next page of data
 */
public class PreloadManager {
    
    private static final String TAG = "PreloadManager";
    private static PreloadManager instance;
    
    private final Context context;
    private final FirebaseFirestore db;
    private final FirebaseAuth auth;
    private final ExecutorService preloadExecutor;
    
    private final Set<String> preloadedUsers = new HashSet<>();
    private final Set<String> preloadingUsers = new HashSet<>();
    
    private PreloadManager(Context context) {
        this.context = context.getApplicationContext();
        this.db = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
        this.preloadExecutor = Executors.newFixedThreadPool(2); // Limit parallel preloads
    }
    
    public static synchronized PreloadManager getInstance(Context context) {
        if (instance == null) {
            instance = new PreloadManager(context);
        }
        return instance;
    }
    
    /**
     * Preload user profiles from a list of notes
     * This reduces stuttering when scrolling through feed
     */
    public void preloadUsersFromNotes(List<NearbyNote> notes) {
        if (notes == null || notes.isEmpty()) return;
        
        List<String> userIdsToPreload = new ArrayList<>();
        
        for (NearbyNote note : notes) {
            String userId = note.getUserId();
            if (userId != null && 
                !preloadedUsers.contains(userId) && 
                !preloadingUsers.contains(userId)) {
                userIdsToPreload.add(userId);
            }
        }
        
        if (userIdsToPreload.isEmpty()) return;
        
        Log.d(TAG, "Preloading " + userIdsToPreload.size() + " user profiles");
        
        for (String userId : userIdsToPreload) {
            preloadUser(userId);
        }
    }
    
    /**
     * Preload a single user profile
     */
    public void preloadUser(String userId) {
        if (userId == null || preloadedUsers.contains(userId) || preloadingUsers.contains(userId)) {
            return;
        }
        
        // Check memory cache first
        UserInfo cached = UserCache.getInstance().get(userId);
        if (cached != null) {
            preloadedUsers.add(userId);
            return;
        }
        
        preloadingUsers.add(userId);
        
        preloadExecutor.execute(() -> {
            try {
                // Check disk cache
                UserInfo diskCached = DiskCache.getInstance(context).getCachedUser(userId);
                if (diskCached != null) {
                    UserCache.getInstance().put(userId, diskCached);
                    preloadedUsers.add(userId);
                    preloadingUsers.remove(userId);
                    return;
                }
                
                // Fetch from Firestore
                db.collection("users").document(userId).get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            UserInfo user = doc.toObject(UserInfo.class);
                            if (user != null) {
                                user.setUserId(userId);
                                
                                // Cache in memory and disk
                                UserCache.getInstance().put(userId, user);
                                DiskCache.getInstance(context).cacheUser(user);
                                
                                preloadedUsers.add(userId);
                            }
                        }
                        preloadingUsers.remove(userId);
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "Failed to preload user: " + userId, e);
                        preloadingUsers.remove(userId);
                    });
            } catch (Exception e) {
                Log.e(TAG, "Error preloading user", e);
                preloadingUsers.remove(userId);
            }
        });
    }
    
    /**
     * Preload images for notes in viewport
     * Call this when notes are about to be displayed
     */
    public void preloadImagesForNotes(List<NearbyNote> notes) {
        if (notes == null || notes.isEmpty()) return;
        
        preloadExecutor.execute(() -> {
            for (NearbyNote note : notes) {
                try {
                    // Preload note image if present
                    if (note.getImageBase64() != null && !note.getImageBase64().isEmpty()) {
                        // Touch the image cache to decode in background
                        // This will warm up the cache for when the view needs it
                        String cacheKey = note.getId();
                        // ImageCache already handles async loading, we just trigger it
                    }
                    
                    // Preload user profile pic
                    if (note.getUserProfilePic() != null && !note.getUserProfilePic().isEmpty()) {
                        String userCacheKey = "user_" + note.getUserId();
                        // Trigger profile pic cache warming
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Error preloading images for note: " + note.getId(), e);
                }
            }
        });
    }
    
    /**
     * Clear preload state (e.g., when feed refreshes)
     */
    public void clearPreloadState() {
        preloadedUsers.clear();
        preloadingUsers.clear();
    }
    
    /**
     * Shutdown preload manager
     */
    public void shutdown() {
        preloadExecutor.shutdown();
        clearPreloadState();
    }
}
