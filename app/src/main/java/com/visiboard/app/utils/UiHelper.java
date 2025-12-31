package com.visiboard.app.utils;

import android.view.View;
import com.google.android.material.snackbar.Snackbar;
import androidx.core.content.ContextCompat;
import com.visiboard.app.R;

public class UiHelper {

    public static void showSuccess(View view, String message) {
        if (view == null) return;
        Snackbar snackbar = Snackbar.make(view, message, Snackbar.LENGTH_SHORT);
        snackbar.getView().setBackgroundColor(ContextCompat.getColor(view.getContext(), R.color.success)); // Assuming a success color exists or use a green
        snackbar.show();
    }

    public static void showError(View view, String message) {
        if (view == null) return;
        Snackbar snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG);
        snackbar.getView().setBackgroundColor(ContextCompat.getColor(view.getContext(), R.color.error)); // Assuming error color exists
        snackbar.show();
    }

    public static void showWarning(View view, String message) {
        if (view == null) return;
        Snackbar snackbar = Snackbar.make(view, message, Snackbar.LENGTH_SHORT);
        // snackbar.getView().setBackgroundColor(ContextCompat.getColor(view.getContext(), R.color.warning)); 
        snackbar.show();
    }
    
    public static void showInfo(View view, String message) {
        if (view == null) return;
        Snackbar.make(view, message, Snackbar.LENGTH_SHORT).show();
    }
}
