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


        setContentView(R.layout.activity_main);

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        NavHostFragment navHostFragment =
                (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        NavController navController = navHostFragment.getNavController();
        NavigationUI.setupWithNavController(bottomNav, navController);

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

}
