package com.visiboard.app.ui.map;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.firestore.FirebaseFirestore;
import com.visiboard.app.R;
import com.visiboard.app.data.UserInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class LeaderboardActivity extends AppCompatActivity implements LeaderboardFragment.UserClickListener {

    private static final String TAG = "LeaderboardActivity";
    
    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private MaterialToolbar toolbar;
    private FirebaseFirestore db;
    private String currentLocality = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        com.visiboard.app.utils.ThemeManager.getInstance(this).applySavedTheme();
        setContentView(R.layout.activity_leaderboard);
        
        db = FirebaseFirestore.getInstance();
        
        initViews();
        setupToolbar();
        fetchLocationAndSetupTabs();
    }
    
    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tabLayout = findViewById(R.id.tab_layout);
        viewPager = findViewById(R.id.viewPager);
    }
    
    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void fetchLocationAndSetupTabs() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                if (location != null) {
                    try {
                        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                        List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                        if (addresses != null && !addresses.isEmpty()) {
                            currentLocality = addresses.get(0).getLocality();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error fetching location name: " + e.getMessage());
                    }
                }
                setupViewPager();
            });
        } else {
            setupViewPager();
        }
    }
    
    private void setupViewPager() {
        LeaderboardPagerAdapter adapter = new LeaderboardPagerAdapter(this);
        viewPager.setAdapter(adapter);
        
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            if (position == 0) {
                tab.setText(currentLocality.isEmpty() ? "Local" : "Local (" + currentLocality + ")");
            } else {
                tab.setText("Global");
            }
        }).attach();
    }

    @Override
    public void onUserClick(UserInfo user) {
        showUserInfo(user.getUserId());
    }

    private void showUserInfo(String userId) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_user_info, null);
        
        de.hdodenhof.circleimageview.CircleImageView profilePic = dialogView.findViewById(R.id.dialog_user_profile_pic);
        TextView userName = dialogView.findViewById(R.id.dialog_user_name);
        TextView userLocation = dialogView.findViewById(R.id.dialog_user_location);
        android.widget.LinearLayout locationContainer = dialogView.findViewById(R.id.dialog_location_container);
        TextView userRank = dialogView.findViewById(R.id.dialog_user_rank);
        TextView followersCount = dialogView.findViewById(R.id.dialog_followers_count);
        TextView followingCount = dialogView.findViewById(R.id.dialog_following_count);
        android.widget.Button followBtn = dialogView.findViewById(R.id.dialog_follow_btn);
        
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .create();
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        
        db.collection("users").document(userId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String name = doc.getString("name");
                        userName.setText(name != null ? name : "Anonymous");
                        
                        String location = doc.getString("lastKnownLocation");
                        if (location != null && !location.isEmpty()) {
                            userLocation.setText(location);
                            locationContainer.setVisibility(View.VISIBLE);
                        }
                        
                        String tier = doc.getString("currentTier");
                        userRank.setText(tier != null ? tier : "None");
                        
                        String pic = doc.getString("profilePic");
                        if (pic != null && !pic.isEmpty()) {
                            com.visiboard.app.utils.ImageCache.getInstance()
                                .loadBase64Image("user_" + userId, pic, profilePic, com.visiboard.app.R.drawable.ic_profile);
                        }
                        
                        Long followers = doc.getLong("followersCount");
                        Long following = doc.getLong("followingCount");
                        followersCount.setText(String.valueOf(followers != null ? followers : 0));
                        followingCount.setText(String.valueOf(following != null ? following : 0));
                        
                        com.google.firebase.auth.FirebaseUser currentUser = 
                            com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
                        if (currentUser != null && !userId.equals(currentUser.getUid())) {
                            followBtn.setVisibility(View.VISIBLE);
                            
                            db.collection("users").document(currentUser.getUid())
                                    .collection("following").document(userId)
                                    .get()
                                    .addOnSuccessListener(followDoc -> {
                                        if (followDoc.exists()) {
                                            followBtn.setText("Following");
                                            followBtn.setEnabled(false);
                                        }
                                    });
                            
                            followBtn.setOnClickListener(v -> {
                                if (followBtn.getText().equals("Follow")) {
                                    followUser(userId, followBtn, followersCount);
                                } else {
                                    unfollowUser(userId, followBtn, followersCount);
                                }
                            });
                        } else {
                            followBtn.setVisibility(View.GONE);
                        }
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load info", Toast.LENGTH_SHORT).show());
        
        dialog.show();
    }
    
    private void followUser(String userId, android.widget.Button followBtn, TextView followersCount) {
        com.google.firebase.auth.FirebaseUser currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;
        String currentUserId = currentUser.getUid();
        
        db.collection("users").document(currentUserId).collection("following").document(userId).set(new HashMap<>());
        db.collection("users").document(currentUserId).update("followingCount", com.google.firebase.firestore.FieldValue.increment(1));
        
        db.collection("users").document(userId).collection("followers").document(currentUserId).set(new HashMap<>());
        db.collection("users").document(userId).update("followersCount", com.google.firebase.firestore.FieldValue.increment(1));
        
        followBtn.setText("Following");
        followBtn.setEnabled(false);
        followersCount.setText(String.valueOf(Integer.parseInt(followersCount.getText().toString()) + 1));
        Toast.makeText(this, "Following user", Toast.LENGTH_SHORT).show();
    }

    private void unfollowUser(String userId, android.widget.Button followBtn, TextView followersCount) {
         com.google.firebase.auth.FirebaseUser currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;
        String currentUserId = currentUser.getUid();
        
        db.collection("users").document(currentUserId).collection("following").document(userId).delete();
        db.collection("users").document(currentUserId).update("followingCount", com.google.firebase.firestore.FieldValue.increment(-1));
        
        db.collection("users").document(userId).collection("followers").document(currentUserId).delete();
        db.collection("users").document(userId).update("followersCount", com.google.firebase.firestore.FieldValue.increment(-1));
        
        followBtn.setText("Follow");
        followBtn.setEnabled(true);
        followersCount.setText(String.valueOf(Math.max(0, Integer.parseInt(followersCount.getText().toString()) - 1)));
        Toast.makeText(this, "Unfollowed user", Toast.LENGTH_SHORT).show();
    }

    private class LeaderboardPagerAdapter extends FragmentStateAdapter {
        public LeaderboardPagerAdapter(FragmentActivity fa) {
            super(fa);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return LeaderboardFragment.newInstance(position == 0);
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }
}
