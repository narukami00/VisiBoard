package com.visiboard.app.ui.admin;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.visiboard.app.R;
import com.visiboard.app.utils.ThemeManager;

public class AdminDashboardActivity extends AppCompatActivity {
    
    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply saved theme before super.onCreate
        ThemeManager.getInstance(this).applySavedTheme();
        
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        bottomNav = findViewById(R.id.adminBottomNav);
        
        // Default fragment
        loadFragment(new AdminAnalyticsFragment());

        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_analytics) {
                loadFragment(new AdminAnalyticsFragment());
                return true;
            } else if (itemId == R.id.nav_reports) {
                loadFragment(new AdminReportsFragment());
                return true;
            } else if (itemId == R.id.nav_users) {
                loadFragment(new AdminUserListFragment());
                return true;
            }
            return false;
        });
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.adminFragmentContainer, fragment)
                .commit();
    }
}
