package com.visiboard.app.utils;

import android.content.Context;
import android.widget.Toast;
import com.google.android.material.snackbar.Snackbar;
import android.view.View;

public class ErrorHandler {
    
    /**
     * Show a generic error message
     */
    public static void showError(Context context, String message) {
        if (context != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Show error with Snackbar (with retry action)
     */
    public static void showErrorWithRetry(View view, String message, Runnable retryAction) {
        if (view == null) return;
        
        Snackbar.make(view, message, Snackbar.LENGTH_LONG)
            .setAction("Retry", v -> {
                if (retryAction != null) {
                    retryAction.run();
                }
            })
            .show();
    }
    
    /**
     * Handle common Firebase errors
     */
    public static String getFirebaseErrorMessage(Exception e) {
        if (e == null) return "An unknown error occurred";
        
        String message = e.getMessage();
        if (message == null) return "An unknown error occurred";
        
        // Common Firebase errors
        if (message.contains("network")) {
            return "Network error. Please check your connection.";
        } else if (message.contains("permission")) {
            return "Permission denied. Please check your access rights.";
        } else if (message.contains("not-found")) {
            return "Data not found.";
        } else if (message.contains("already-exists")) {
            return "This item already exists.";
        } else if (message.contains("timeout")) {
            return "Request timed out. Please try again.";
        } else if (message.contains("quota")) {
            return "Service quota exceeded. Please try again later.";
        }
        
        return "Error: " + message;
    }
    
    /**
     * Check if error is network related
     */
    public static boolean isNetworkError(Exception e) {
        if (e == null) return false;
        String message = e.getMessage();
        return message != null && (message.contains("network") || 
                                   message.contains("connection") ||
                                   message.contains("timeout"));
    }
    
    /**
     * Show appropriate error based on exception type
     */
    public static void handleException(Context context, Exception e) {
        if (isNetworkError(e)) {
            showError(context, "Network error. Please check your connection.");
        } else {
            showError(context, getFirebaseErrorMessage(e));
        }
    }
}
