package com.visiboard.app.ui.profile;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.visiboard.app.R;

/**
 * Activity to host UserProfileFragment for navigation from Activities (e.g., LeaderboardActivity).
 */
public class UserProfileActivity extends AppCompatActivity {

    public static final String EXTRA_USER_ID = "userId";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_profile);

        String userId = getIntent().getStringExtra(EXTRA_USER_ID);

        if (savedInstanceState == null && userId != null) {
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, UserProfileFragment.newInstance(userId))
                .commit();
        }
    }
}
