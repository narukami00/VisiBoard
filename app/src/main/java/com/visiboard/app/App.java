package com.visiboard.app;

import android.app.Application;
import android.util.Log;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
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
        // init logging, analytics (opt-in later), or singletons
    }
}
