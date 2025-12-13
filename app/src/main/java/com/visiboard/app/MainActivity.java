package com.visiboard.app;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.visiboard.app.ui.auth.LoginActivity;
import com.visiboard.app.utils.ThemeManager;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import android.util.Log;

public class MainActivity extends AppCompatActivity {

    private boolean doubleBackToExitPressedOnce = false;
    private com.visiboard.app.utils.NetworkMonitor networkMonitor;
    private com.google.android.material.snackbar.Snackbar noInternetSnackbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Apply saved theme
        ThemeManager.getInstance(this).applySavedTheme();

        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        // Check initial network connectivity
        if (!com.visiboard.app.utils.NetworkMonitor.isConnected(this)) {
            showNoInternetDialog();
            return;
        }

        setContentView(R.layout.activity_main);
        
        // Setup network monitoring
        setupNetworkMonitoring();

        NavHostFragment navHostFragment =
                (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        NavController navController = navHostFragment.getNavController();
        
        // Setup navigation - supports both bottom nav and navigation rail for tablets
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        com.google.android.material.navigationrail.NavigationRailView navRail = findViewById(R.id.nav_rail);
        
        if (bottomNav != null && bottomNav.getVisibility() == android.view.View.VISIBLE) {
            NavigationUI.setupWithNavController(bottomNav, navController);
        } else if (navRail != null) {
            NavigationUI.setupWithNavController(navRail, navController);
        }

        // Custom Back Press Logic
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                int currentId = 0;
                if (navController.getCurrentDestination() != null) {
                    currentId = navController.getCurrentDestination().getId();
                }

                if (currentId == R.id.mapFragment || 
                    currentId == R.id.captureFragment || 
                    currentId == R.id.feedFragment || 
                    currentId == R.id.profileFragment) {
                    
                    if (doubleBackToExitPressedOnce) {
                        showExitConfirmationDialog();
                        return;
                    }

                    doubleBackToExitPressedOnce = true;
                    android.widget.Toast.makeText(MainActivity.this, "Press back again to exit", android.widget.Toast.LENGTH_SHORT).show();

                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> doubleBackToExitPressedOnce = false, 2000);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });

        // Asynchronously recalculate total likes to ensure consistency
        recalculateTotalLikes();
    }

    private void recalculateTotalLikes() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) return;

        String userId = auth.getCurrentUser().getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Query all notes by this user
        db.collection("notes")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener(querySnapshot -> {
                long totalLikes = 0;
                for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                    Long likes = doc.getLong("likeCount");
                    if (likes != null) {
                        totalLikes += likes;
                    }
                }

                // Update the user's profile with the correct total
                final long finalTotal = totalLikes;
                db.collection("users").document(userId)
                    .update("totalLikes", finalTotal)
                    .addOnSuccessListener(aVoid -> Log.d("MainActivity", "Total likes recalculated and updated: " + finalTotal))
                    .addOnFailureListener(e -> Log.e("MainActivity", "Error updating total likes", e));
            })
            .addOnFailureListener(e -> Log.e("MainActivity", "Error calculating total likes", e));
    }



    private void showExitConfirmationDialog() {
        android.view.View dialogView = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_confirmation, null);
        android.widget.TextView title = dialogView.findViewById(R.id.dialog_title);
        android.widget.TextView message = dialogView.findViewById(R.id.dialog_message);
        android.widget.Button btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        android.widget.Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        
        title.setText("Exit App");
        message.setText("Do you really want to exit the app?");
        btnConfirm.setText("Yes");
        btnCancel.setText("No");

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            finishAffinity(); // completely exit the app
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
    }
    
    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        
        // Handle orientation or screen size changes
        // Trim image cache to free memory for layout changes
        com.visiboard.app.utils.ImageCache.getInstance().trimMemory();
    }
    
    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode) {
        super.onMultiWindowModeChanged(isInMultiWindowMode);
        
        // Reduce memory usage in multi-window mode
        if (isInMultiWindowMode) {
            com.visiboard.app.utils.ImageCache.getInstance().trimMemory();
        }
    }
    
    private void setupNetworkMonitoring() {
        networkMonitor = new com.visiboard.app.utils.NetworkMonitor(this);
        networkMonitor.observe(this, isConnected -> {
            if (isConnected != null) {
                if (isConnected) {
                    hideNoInternetSnackbar();
                } else {
                    showNoInternetSnackbar();
                }
            }
        });
    }
    
    private void showNoInternetDialog() {
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_no_internet, null);
        
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create();
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        
        dialogView.findViewById(R.id.btn_retry).setOnClickListener(v -> {
            if (com.visiboard.app.utils.NetworkMonitor.isConnected(this)) {
                dialog.dismiss();
                recreate(); // Restart activity
            } else {
                android.widget.Toast.makeText(this, "Still no internet connection", android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        
        dialogView.findViewById(R.id.btn_exit).setOnClickListener(v -> {
            finishAffinity();
        });
        
        dialog.show();
    }
    
    private void showNoInternetSnackbar() {
        if (noInternetSnackbar != null && noInternetSnackbar.isShown()) {
            return;
        }
        
        android.view.View rootView = findViewById(android.R.id.content);
        if (rootView != null) {
            noInternetSnackbar = com.google.android.material.snackbar.Snackbar
                .make(rootView, "No internet connection", com.google.android.material.snackbar.Snackbar.LENGTH_INDEFINITE)
                .setAction("Retry", v -> {
                    if (com.visiboard.app.utils.NetworkMonitor.isConnected(this)) {
                        hideNoInternetSnackbar();
                    }
                })
                .setActionTextColor(getResources().getColor(R.color.accent));
            
            noInternetSnackbar.show();
        }
    }
    
    private void hideNoInternetSnackbar() {
        if (noInternetSnackbar != null && noInternetSnackbar.isShown()) {
            noInternetSnackbar.dismiss();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        hideNoInternetSnackbar();
    }

}
