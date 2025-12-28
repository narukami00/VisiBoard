package com.visiboard.app.utils;

import android.content.Context;
import android.content.res.Configuration;
import android.util.DisplayMetrics;
import android.view.WindowManager;

public class ScreenUtils {
    
    /**
     * Check if device is a tablet
     */
    public static boolean isTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK)
                >= Configuration.SCREENLAYOUT_SIZE_LARGE;
    }
    
    /**
     * Check if device is in landscape orientation
     */
    public static boolean isLandscape(Context context) {
        return context.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
    }
    
    /**
     * Get screen width in dp
     */
    public static int getScreenWidthDp(Context context) {
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        return (int) (displayMetrics.widthPixels / displayMetrics.density);
    }
    
    /**
     * Get screen height in dp
     */
    public static int getScreenHeightDp(Context context) {
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        return (int) (displayMetrics.heightPixels / displayMetrics.density);
    }
    
    /**
     * Convert dp to pixels
     */
    public static int dpToPx(Context context, float dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }
    
    /**
     * Convert pixels to dp
     */
    public static float pxToDp(Context context, int px) {
        return px / context.getResources().getDisplayMetrics().density;
    }
    
    /**
     * Check if screen width is at least the specified dp value
     */
    public static boolean isScreenWidthAtLeast(Context context, int widthDp) {
        return getScreenWidthDp(context) >= widthDp;
    }
    
    /**
     * Get appropriate dialog width based on screen size
     */
    public static int getDialogWidth(Context context) {
        int screenWidthDp = getScreenWidthDp(context);
        
        if (screenWidthDp >= 720) {
            // Large tablet: 70% of screen width, max 700dp
            return Math.min(dpToPx(context, 700), (int)(context.getResources().getDisplayMetrics().widthPixels * 0.7));
        } else if (screenWidthDp >= 600) {
            // Tablet: 75% of screen width, max 600dp
            return Math.min(dpToPx(context, 600), (int)(context.getResources().getDisplayMetrics().widthPixels * 0.75));
        } else {
            // Phone: 90% of screen width, max 400dp
            return Math.min(dpToPx(context, 400), (int)(context.getResources().getDisplayMetrics().widthPixels * 0.9));
        }
    }
    
    /**
     * Get optimal number of grid columns based on screen width
     */
    public static int getOptimalGridColumns(Context context, int itemWidthDp) {
        int screenWidthDp = getScreenWidthDp(context);
        int columns = screenWidthDp / itemWidthDp;
        return Math.max(2, Math.min(columns, 5)); // Min 2, max 5 columns
    }
}
