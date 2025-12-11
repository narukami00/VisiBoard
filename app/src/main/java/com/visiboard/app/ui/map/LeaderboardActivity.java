package com.visiboard.app.ui.map;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.visiboard.app.R;
import com.visiboard.app.data.UserInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import androidx.core.app.ActivityCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

public class LeaderboardActivity extends AppCompatActivity {

    private static final String TAG = "LeaderboardActivity";
    
    private RecyclerView rvLegends;
    private ProgressBar pbLoading;
    private View emptyState;
    private TabLayout tabLayout;
    private MaterialToolbar toolbar;
    
    private LegendAdapter adapter;
    private FirebaseFirestore db;
    private boolean isLocal = true; // Start with Local tab

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Apply theme
        com.visiboard.app.utils.ThemeManager.getInstance(this).applySavedTheme();
        
        setContentView(R.layout.activity_leaderboard);
        
        db = FirebaseFirestore.getInstance();
        
        initViews();
        setupToolbar();
        setupRecyclerView();
        setupTabs();
        
        // Load initial data (Local)
        loadLeaderboardData(true);
        
        // Update Local tab with location name
        updateLocalTabTitle();
    }
    
    private void updateLocalTabTitle() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                try {
                    Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                    List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                    if (addresses != null && !addresses.isEmpty()) {
                        String locality = addresses.get(0).getLocality();
                        if (locality != null && !locality.isEmpty()) {
                            if (tabLayout.getTabCount() > 0) {
                                TabLayout.Tab tab = tabLayout.getTabAt(0);
                                if (tab != null) {
                                    tab.setText("Local (" + locality + ")");
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error fetching location name: " + e.getMessage());
                }
            }
        });
    }
    
    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tabLayout = findViewById(R.id.tab_layout);
        rvLegends = findViewById(R.id.rv_legends);
        pbLoading = findViewById(R.id.pb_loading);
        emptyState = findViewById(R.id.empty_state);
    }
    
    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }
    
    private void setupRecyclerView() {
        rvLegends.setLayoutManager(new LinearLayoutManager(this));
        rvLegends.setHasFixedSize(true);
        
        adapter = new LegendAdapter(user -> {
            // Open user info dialog or navigate to profile
            showUserInfo(user.getUserId());
        });
        
        rvLegends.setAdapter(adapter);
    }
    
    private void setupTabs() {
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                isLocal = tab.getPosition() == 0; // Position 0 = Local, 1 = Global
                loadLeaderboardData(isLocal);
            }
            
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}
            
            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }
    
    private void loadLeaderboardData(boolean isLocal) {
        // Clear existing data
        adapter.clearUsers();
        emptyState.setVisibility(View.GONE);
        
        pbLoading.setVisibility(View.VISIBLE);
        rvLegends.setVisibility(View.VISIBLE);
        
        // Fetch top users from Firestore
        db.collection("users")
            .limit(50)
            .get()
            .addOnSuccessListener(querySnapshot -> {
                pbLoading.setVisibility(View.GONE);
                
                List<UserInfo> users = new ArrayList<>();
                for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                    UserInfo user = doc.toObject(UserInfo.class);
                    if (user != null) {
                        user.setUserId(doc.getId());
                        
                        // Get totalLikes from document
                        Long totalLikes = doc.getLong("totalLikes");
                        if (totalLikes != null) {
                            user.setTotalLikes(totalLikes.intValue());
                        } else {
                            user.setTotalLikes(0);
                        }
                        
                        users.add(user);
                    }
                }
                
                // Sort by totalLikes descending
                users.sort((u1, u2) -> Integer.compare(u2.getTotalLikes(), u1.getTotalLikes()));
                
                // Limit based on Local vs Global
                int limit = isLocal ? 10 : 50; // Show more users in full page
                if (users.size() > limit) {
                    users = users.subList(0, limit);
                }
                
                if (users.isEmpty()) {
                    emptyState.setVisibility(View.VISIBLE);
                    rvLegends.setVisibility(View.GONE);
                } else {
                    adapter.setUsers(users);
                }
            })
            .addOnFailureListener(e -> {
                pbLoading.setVisibility(View.GONE);
                Toast.makeText(this, "Failed to load leaderboard: " + e.getMessage(), 
                    Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Leaderboard error: " + e.getMessage(), e);
                
                emptyState.setVisibility(View.VISIBLE);
                rvLegends.setVisibility(View.GONE);
            });
    }
    
    private void showUserInfo(String userId) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_user_info, null);
        
        de.hdodenhof.circleimageview.CircleImageView profilePic = dialogView.findViewById(R.id.dialog_user_profile_pic);
        TextView userName = dialogView.findViewById(R.id.dialog_user_name);
        TextView userLocation = dialogView.findViewById(R.id.dialog_user_location);
        android.widget.LinearLayout locationContainer = dialogView.findViewById(R.id.dialog_location_container);
        TextView userRank = dialogView.findViewById(R.id.dialog_user_rank);
        android.widget.ImageView rankIcon = dialogView.findViewById(R.id.dialog_user_rank_icon);
        TextView followersCount = dialogView.findViewById(R.id.dialog_followers_count);
        TextView followingCount = dialogView.findViewById(R.id.dialog_following_count);
        android.widget.Button followBtn = dialogView.findViewById(R.id.dialog_follow_btn);
        
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .create();
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        
        // Load user data
        db.collection("users").document(userId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        // Set name
                        String name = doc.getString("name");
                        userName.setText(name != null ? name : "Anonymous");
                        
                        // Set location
                        String location = doc.getString("lastKnownLocation");
                        if (location != null && !location.isEmpty()) {
                            userLocation.setText(location);
                            locationContainer.setVisibility(View.VISIBLE);
                        }
                        
                        // Set rank
                        String tier = doc.getString("currentTier");
                        userRank.setText(tier != null ? tier : "None");
                        
                        // Set profile pic
                        String pic = doc.getString("profilePic");
                        if (pic != null && !pic.isEmpty()) {
                            com.visiboard.app.utils.ImageCache.getInstance()
                                .loadBase64Image("user_" + userId, pic, profilePic, com.visiboard.app.R.drawable.ic_profile);
                        }
                        
                        // Set counts
                        Long followers = doc.getLong("followersCount");
                        Long following = doc.getLong("followingCount");
                        followersCount.setText(String.valueOf(followers != null ? followers : 0));
                        followingCount.setText(String.valueOf(following != null ? following : 0));
                        
                        // Show follow button if not viewing own profile
                        com.google.firebase.auth.FirebaseUser currentUser = 
                            com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
                        if (currentUser != null && !userId.equals(currentUser.getUid())) {
                            followBtn.setVisibility(View.VISIBLE);
                            
                            // Check if already following
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
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load user info", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error loading user info", e);
                });
        
        dialog.show();
    }
    
    private void followUser(String userId, android.widget.Button followBtn, TextView followersCount) {
        com.google.firebase.auth.FirebaseUser currentUser = 
            com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;
        
        String currentUserId = currentUser.getUid();
        
        // Add to current user's following
        db.collection("users").document(currentUserId)
                .collection("following").document(userId)
                .set(new java.util.HashMap<>());
        db.collection("users").document(currentUserId)
                .update("followingCount", com.google.firebase.firestore.FieldValue.increment(1));
        
        // Add to target user's followers
        db.collection("users").document(userId)
                .collection("followers").document(currentUserId)
                .set(new java.util.HashMap<>());
        db.collection("users").document(userId)
                .update("followersCount", com.google.firebase.firestore.FieldValue.increment(1));
        
        followBtn.setText("Following");
        followBtn.setEnabled(false);
        
        int count = Integer.parseInt(followersCount.getText().toString());
        followersCount.setText(String.valueOf(count + 1));
        
        Toast.makeText(this, "Following user", Toast.LENGTH_SHORT).show();
    }
    
    private void unfollowUser(String userId, android.widget.Button followBtn, TextView followersCount) {
        com.google.firebase.auth.FirebaseUser currentUser = 
            com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;
        
        String currentUserId = currentUser.getUid();
        
        // Remove from current user's following
        db.collection("users").document(currentUserId)
                .collection("following").document(userId)
                .delete();
        db.collection("users").document(currentUserId)
                .update("followingCount", com.google.firebase.firestore.FieldValue.increment(-1));
        
        // Remove from target user's followers
        db.collection("users").document(userId)
                .collection("followers").document(currentUserId)
                .delete();
        db.collection("users").document(userId)
                .update("followersCount", com.google.firebase.firestore.FieldValue.increment(-1));
        
        followBtn.setText("Follow");
        followBtn.setEnabled(true);
        
        int count = Integer.parseInt(followersCount.getText().toString());
        followersCount.setText(String.valueOf(Math.max(0, count - 1)));
        
        Toast.makeText(this, "Unfollowed user", Toast.LENGTH_SHORT).show();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (adapter != null) {
            adapter.clearUsers();
        }
    }
}
