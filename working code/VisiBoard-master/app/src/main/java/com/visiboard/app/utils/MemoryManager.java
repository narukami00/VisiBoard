package com.visiboard.app.utils;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.ref.WeakReference;

public class MemoryManager {
    
    private static final String TAG = "MemoryManager";
    private static MemoryManager instance;
    private WeakReference<Context> contextRef;
    private Handler memoryCheckHandler;
    private static final long MEMORY_CHECK_INTERVAL = 30000; // 30 seconds
    
    private MemoryManager(Context context) {
        this.contextRef = new WeakReference<>(context.getApplicationContext());
        this.memoryCheckHandler = new Handler(Looper.getMainLooper());
    }
    
    public static synchronized MemoryManager getInstance(Context context) {
        if (instance == null) {
            instance = new MemoryManager(context);
        }
        return instance;
    }
    
    /**
     * Start periodic memory monitoring
     */
    public void startMemoryMonitoring() {
        memoryCheckHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                checkMemoryAndClean();
                memoryCheckHandler.postDelayed(this, MEMORY_CHECK_INTERVAL);
            }
        }, MEMORY_CHECK_INTERVAL);
    }
    
    /**
     * Stop memory monitoring
     */
    public void stopMemoryMonitoring() {
        memoryCheckHandler.removeCallbacksAndMessages(null);
    }
    
    /**
     * Check memory usage and trigger cleanup if needed
     */
    public void checkMemoryAndClean() {
        Context context = contextRef.get();
        if (context == null) return;
        
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) return;
        
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        
        long availableMemory = memoryInfo.availMem;
        long totalMemory = memoryInfo.totalMem;
        long usedMemory = totalMemory - availableMemory;
        float usagePercent = (float) usedMemory / totalMemory * 100;
        
        Log.d(TAG, String.format("Memory Usage: %.1f%% (%d MB / %d MB)", 
            usagePercent, 
            usedMemory / (1024 * 1024),
            totalMemory / (1024 * 1024)));
        
        // If memory usage is high (>80%), trigger cleanup
        if (usagePercent > 80 || memoryInfo.lowMemory) {
            Log.w(TAG, "High memory usage detected, triggering cleanup");
            performMemoryCleanup();
        }
    }
    
    /**
     * Perform aggressive memory cleanup
     */
    public void performMemoryCleanup() {
        Log.d(TAG, "Performing memory cleanup");
        
        // Clear image cache
        ImageCache imageCache = ImageCache.getInstance();
        imageCache.trimMemory();
        
        // Clear Firestore cache
        FirestoreCache firestoreCache = FirestoreCache.getInstance();
        firestoreCache.cleanExpired();
        
        // Request garbage collection (hint only)
        System.gc();
        
        Log.d(TAG, "Memory cleanup completed");
    }
    
    /**
     * Get current memory usage percentage
     */
    public float getMemoryUsagePercent() {
        Context context = contextRef.get();
        if (context == null) return 0;
        
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) return 0;
        
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        
        long availableMemory = memoryInfo.availMem;
        long totalMemory = memoryInfo.totalMem;
        long usedMemory = totalMemory - availableMemory;
        
        return (float) usedMemory / totalMemory * 100;
    }
    
    /**
     * Check if app is in low memory state
     */
    public boolean isLowMemory() {
        Context context = contextRef.get();
        if (context == null) return false;
        
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) return false;
        
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        
        return memoryInfo.lowMemory;
    }
}
