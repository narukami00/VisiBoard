package com.visiboard.app;

import android.app.Application;
import android.util.Log;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.visiboard.app.utils.CacheManager;
import com.visiboard.app.utils.MemoryManager;
import org.maplibre.android.MapLibre;

public class App extends Application {
    private static final String TAG = "VisiBoard";
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Initialize Firebase
        FirebaseApp firebaseApp = FirebaseApp.initializeApp(this);
        if (firebaseApp != null) {
            FirebaseOptions options = firebaseApp.getOptions();
            Log.d(TAG, "Firebase initialized successfully");
            Log.d(TAG, "Project ID: " + options.getProjectId());
            Log.d(TAG, "Application ID: " + options.getApplicationId());
            Log.d(TAG, "API Key: " + (options.getApiKey() != null ? "Present" : "Missing"));
        } else {
            Log.e(TAG, "Firebase initialization FAILED!");
        }
        
        MapLibre.getInstance(this);
        
        // Initialize cache manager with periodic cleanup
        CacheManager.getInstance().initialize(this);
        
        // Initialize and start memory monitoring
        MemoryManager memoryManager = MemoryManager.getInstance(this);
        memoryManager.startMemoryMonitoring();
        
        Log.d(TAG, "VisiBoard app initialized with caching and memory monitoring");
    }
    
    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Log.w(TAG, "Low memory warning received");
        
        // Coordinate memory cleanup
        CacheManager.getInstance().onTrimMemory(TRIM_MEMORY_COMPLETE);
        MemoryManager.getInstance(this).performMemoryCleanup();
    }
    
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        Log.d(TAG, "onTrimMemory called with level: " + level);
        
        // Let cache manager handle memory pressure
        CacheManager.getInstance().onTrimMemory(level);
        
        // Perform cleanup based on memory pressure level
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            MemoryManager.getInstance(this).performMemoryCleanup();
        }
    }
    
    @Override
    public void onTerminate() {
        super.onTerminate();
        CacheManager.getInstance().shutdown();
    }
}
