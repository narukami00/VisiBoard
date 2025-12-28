package com.visiboard.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferencesHelper {
    
    private static final String PREF_NAME = "VisiBoard_Prefs";
    private static final String KEY_FIRST_LAUNCH = "first_launch";
    private static final String KEY_NOTIFICATIONS_ENABLED = "notifications_enabled";
    private static final String KEY_HAPTIC_FEEDBACK = "haptic_feedback";
    private static final String KEY_DATA_SAVER = "data_saver";
    private static final String KEY_AUTO_PLAY_VIDEOS = "auto_play_videos";
    private static final String KEY_LAST_LOCATION_LAT = "last_location_lat";
    private static final String KEY_LAST_LOCATION_LNG = "last_location_lng";
    
    private SharedPreferences prefs;
    private static PreferencesHelper instance;
    
    private PreferencesHelper(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
    
    public static synchronized PreferencesHelper getInstance(Context context) {
        if (instance == null) {
            instance = new PreferencesHelper(context);
        }
        return instance;
    }
    
    // First launch
    public boolean isFirstLaunch() {
        return prefs.getBoolean(KEY_FIRST_LAUNCH, true);
    }
    
    public void setFirstLaunchComplete() {
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply();
    }
    
    // Notifications
    public boolean isNotificationsEnabled() {
        return prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true);
    }
    
    public void setNotificationsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply();
    }
    
    // Haptic feedback
    public boolean isHapticFeedbackEnabled() {
        return prefs.getBoolean(KEY_HAPTIC_FEEDBACK, true);
    }
    
    public void setHapticFeedbackEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_HAPTIC_FEEDBACK, enabled).apply();
    }
    
    // Data saver
    public boolean isDataSaverEnabled() {
        return prefs.getBoolean(KEY_DATA_SAVER, false);
    }
    
    public void setDataSaverEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_DATA_SAVER, enabled).apply();
    }
    
    // Auto-play videos
    public boolean isAutoPlayVideosEnabled() {
        return prefs.getBoolean(KEY_AUTO_PLAY_VIDEOS, true);
    }
    
    public void setAutoPlayVideosEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_PLAY_VIDEOS, enabled).apply();
    }
    
    // Last known location
    public void saveLastLocation(double lat, double lng) {
        prefs.edit()
            .putLong(KEY_LAST_LOCATION_LAT, Double.doubleToRawLongBits(lat))
            .putLong(KEY_LAST_LOCATION_LNG, Double.doubleToRawLongBits(lng))
            .apply();
    }
    
    public double getLastLocationLat() {
        return Double.longBitsToDouble(prefs.getLong(KEY_LAST_LOCATION_LAT, 0));
    }
    
    public double getLastLocationLng() {
        return Double.longBitsToDouble(prefs.getLong(KEY_LAST_LOCATION_LNG, 0));
    }
    
    public boolean hasLastLocation() {
        return prefs.contains(KEY_LAST_LOCATION_LAT) && prefs.contains(KEY_LAST_LOCATION_LNG);
    }
    
    // Generic getters/setters
    public void putString(String key, String value) {
        prefs.edit().putString(key, value).apply();
    }
    
    public String getString(String key, String defaultValue) {
        return prefs.getString(key, defaultValue);
    }
    
    public void putInt(String key, int value) {
        prefs.edit().putInt(key, value).apply();
    }
    
    public int getInt(String key, int defaultValue) {
        return prefs.getInt(key, defaultValue);
    }
    
    public void putBoolean(String key, boolean value) {
        prefs.edit().putBoolean(key, value).apply();
    }
    
    public boolean getBoolean(String key, boolean defaultValue) {
        return prefs.getBoolean(key, defaultValue);
    }
    
    public void clear() {
        prefs.edit().clear().apply();
    }
}
