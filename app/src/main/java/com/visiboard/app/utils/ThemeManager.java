package com.visiboard.app.utils;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;

public class ThemeManager {
    private static final String PREFS_NAME = "theme_prefs";
    private static final String KEY_THEME = "app_theme";
    private static final String THEME_LIGHT = "light";
    private static final String THEME_DARK = "dark";
    
    private static ThemeManager instance;
    private SharedPreferences prefs;
    
    private ThemeManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    public static ThemeManager getInstance(Context context) {
        if (instance == null) {
            instance = new ThemeManager(context.getApplicationContext());
        }
        return instance;
    }
    
    public boolean isDarkMode() {
        return THEME_DARK.equals(prefs.getString(KEY_THEME, THEME_LIGHT));
    }
    
    public void setDarkMode(boolean darkMode) {
        saveThemePreference(darkMode);
        applyTheme(darkMode);
    }

    public void saveThemePreference(boolean darkMode) {
        prefs.edit().putString(KEY_THEME, darkMode ? THEME_DARK : THEME_LIGHT).apply();
    }
    
    public void toggleTheme() {
        setDarkMode(!isDarkMode());
    }
    
    public void applyTheme(boolean darkMode) {
        AppCompatDelegate.setDefaultNightMode(
            darkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );
    }
    
    public void applySavedTheme() {
        applyTheme(isDarkMode());
    }
}
