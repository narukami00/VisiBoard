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
                            
                            // 1. Initial Loading State
                            followBtn.setText("...");
                            followBtn.setEnabled(false);

                            // 2. Check Following Status
                            db.collection("users").document(currentUser.getUid())
                                    .collection("following").document(userId)
                                    .get()
                                    .addOnSuccessListener(followDoc -> {
                                        if (followDoc.exists()) {
                                            // Already Following
                                            followBtn.setText("Following");
                                            followBtn.setBackgroundResource(R.drawable.btn_following_selector);
                                            followBtn.setTextColor(getResources().getColor(R.color.button_text_following, null));
                                            followBtn.setEnabled(true);
                                        } else {
                                            // 3. Not Following -> Check Pending Request
                                            db.collection("users").document(userId)
                                                    .collection("follow_requests").document(currentUser.getUid())
                                                    .get()
                                                    .addOnSuccessListener(requestDoc -> {
                                                        if (requestDoc.exists()) {
                                                            // Request Pending
                                                            followBtn.setText("Requested");
                                                            followBtn.setBackgroundResource(R.drawable.btn_following_selector);
                                                            followBtn.setTextColor(getResources().getColor(R.color.button_text_following, null));
                                                        } else {
                                                            // No Relation
                                                            followBtn.setText("Follow");
                                                            followBtn.setBackgroundResource(R.drawable.btn_primary_selector);
                                                            followBtn.setTextColor(getResources().getColor(R.color.button_text_primary, null));
                                                        }
                                                        followBtn.setEnabled(true);
                                                    })
                                                    .addOnFailureListener(e -> {
                                                        followBtn.setText("Follow");
                                                        followBtn.setEnabled(true);
                                                    });
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                         followBtn.setText("Follow");
                                         followBtn.setEnabled(true);
                                    });
                            
                            followBtn.setOnClickListener(v -> {
                                String text = followBtn.getText().toString();
                                if (text.equals("Follow")) {
                                    followUser(userId, followBtn, followersCount);
                                } else if (text.equals("Requested")) {
                                    cancelFollowRequest(userId, followBtn);
                                } else if (text.equals("Following")) {
                                    unfollowUser(userId, followBtn, followersCount);
                                }
                            });
                        } else {
                            followBtn.setVisibility(View.GONE);
                        }
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load info", Toast.LENGTH_SHORT).show());
        
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(dialogView);
                
        // Add Report Button
        com.google.firebase.auth.FirebaseUser currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null && !userId.equals(currentUser.getUid())) {
             builder.setNeutralButton("Report User", (d, w) -> {
                 com.visiboard.app.ui.report.ReportBottomSheetFragment reportSheet =
                         com.visiboard.app.ui.report.ReportBottomSheetFragment.newInstance(
                                 userId,
                                 userName.getText().toString(),
                                 "USER",
                                 0, 0
                         );
                 reportSheet.show(getSupportFragmentManager(), "ReportBottomSheet");
             });
        }
        
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        dialog.show();
    }
    
    // Updated Helper Methods
    
    private void followUser(String targetUserId, android.widget.Button btn, TextView followersCount) {
        com.google.firebase.auth.FirebaseUser currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;
        String currentUserId = currentUser.getUid();
        
        String originalText = btn.getText().toString();
        btn.setText("...");
        btn.setEnabled(false);

        db.collection("users").document(targetUserId).get()
            .addOnSuccessListener(targetUserDoc -> {
                boolean isPrivate = targetUserDoc.getBoolean("isPrivate") != null && targetUserDoc.getBoolean("isPrivate");
                
                if (isPrivate) {
                     // Check Rejection Status First (5-Strike Rule)
                    db.collection("users").document(targetUserId).collection("rejections").document(currentUserId)
                        .get()
                        .addOnSuccessListener(rejectionDoc -> {
                            boolean blocked = false;
                            if (rejectionDoc.exists()) {
                                Long count = rejectionDoc.getLong("count");
                                Long time = rejectionDoc.getLong("lastRejectionTime");
                                if (count != null && count >= 5 && time != null) {
                                     if (System.currentTimeMillis() - time < 24 * 60 * 60 * 1000) {
                                         blocked = true;
                                     }
                                }
                            }
                            
                            if (blocked) {
                                Toast.makeText(this, "Too many follow requests. Try again later.", Toast.LENGTH_LONG).show();
                                btn.setText(originalText);
                                btn.setEnabled(true);
                            } else {
                                sendFollowRequest(targetUserId, btn, currentUserId);
                            }
                        })
                        .addOnFailureListener(e -> {
                            btn.setText(originalText);
                            btn.setEnabled(true);
                        });
                } else {
                    performDirectFollow(targetUserId, btn, currentUserId, followersCount);
                }
            })
            .addOnFailureListener(e -> {
                 btn.setText(originalText);
                 btn.setEnabled(true);
            });
    }

    private void sendFollowRequest(String targetUserId, android.widget.Button btn, String currentUserId) {
        db.collection("users").document(currentUserId).get().addOnSuccessListener(currentUserDoc -> {
             String myName = currentUserDoc.getString("name");
             String myProfilePic = currentUserDoc.getString("profilePic");
             java.util.Map<String, Object> requestData = new HashMap<>();
             requestData.put("timestamp", System.currentTimeMillis());
             requestData.put("requesterName", myName);
             requestData.put("requesterProfilePic", myProfilePic);
             
             db.collection("users").document(targetUserId).collection("follow_requests").document(currentUserId)
                 .set(requestData)
                 .addOnSuccessListener(aVoid -> {
                     btn.setText("Requested");
                     btn.setBackgroundResource(R.drawable.btn_following_selector); 
                     btn.setTextColor(getResources().getColor(R.color.button_text_following, null));
                     
                     createNotification(targetUserId, currentUserId, "follow_request");
                     Toast.makeText(this, "Request sent", Toast.LENGTH_SHORT).show();
                     btn.setEnabled(true);
                 })
                 .addOnFailureListener(e -> btn.setEnabled(true));
        });
    }

    private void performDirectFollow(String targetUserId, android.widget.Button btn, String currentUserId, TextView followersCount) {
        db.collection("users").document(currentUserId).get().addOnSuccessListener(currentUserDoc -> {
             String myName = currentUserDoc.getString("name");
             String myProfilePic = currentUserDoc.getString("profilePic");
             
             java.util.Map<String, Object> followerData = new HashMap<>();
             followerData.put("timestamp", System.currentTimeMillis());
             followerData.put("followerName", myName);
             followerData.put("followerProfilePic", myProfilePic);
             db.collection("users").document(targetUserId).collection("followers").document(currentUserId).set(followerData);
             db.collection("users").document(targetUserId).update("followersCount", com.google.firebase.firestore.FieldValue.increment(1));
             
             db.collection("users").document(targetUserId).get().addOnSuccessListener(targetUserDoc -> {
                 String targetName = targetUserDoc.getString("name");
                 String targetProfilePic = targetUserDoc.getString("profilePic");
                 
                 java.util.Map<String, Object> followingData = new HashMap<>();
                 followingData.put("timestamp", System.currentTimeMillis());
                 followingData.put("followedName", targetName);
                 followingData.put("followedProfilePic", targetProfilePic);
                 
                 db.collection("users").document(currentUserId).collection("following").document(targetUserId).set(followingData);
                 db.collection("users").document(currentUserId).update("followingCount", com.google.firebase.firestore.FieldValue.increment(1));
                 
                 btn.setText("Following");
                 btn.setBackgroundResource(R.drawable.btn_following_selector);
                 btn.setTextColor(getResources().getColor(R.color.button_text_following, null));
                 btn.setEnabled(true);
                 
                 int count = Integer.parseInt(followersCount.getText().toString());
                 followersCount.setText(String.valueOf(count + 1));
                 
                 createNotification(targetUserId, currentUserId, "follow");
                 Toast.makeText(this, "Following " + targetName, Toast.LENGTH_SHORT).show();
             });
        });
    }

    private void cancelFollowRequest(String targetUserId, android.widget.Button btn) {
        com.google.firebase.auth.FirebaseUser currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;
        
        db.collection("users").document(targetUserId).collection("follow_requests").document(currentUser.getUid()).delete()
            .addOnSuccessListener(aVoid -> {
                btn.setText("Follow");
                btn.setBackgroundResource(R.drawable.btn_primary_selector);
                btn.setTextColor(getResources().getColor(R.color.button_text_primary, null));
                Toast.makeText(this, "Request canceled", Toast.LENGTH_SHORT).show();
            });
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
        followBtn.setBackgroundResource(R.drawable.btn_primary_selector);
        followBtn.setTextColor(getResources().getColor(R.color.button_text_primary, null));
        
        int count = Integer.parseInt(followersCount.getText().toString());
        followersCount.setText(String.valueOf(Math.max(0, count - 1)));
        
        Toast.makeText(this, "Unfollowed user", Toast.LENGTH_SHORT).show();
    }

    private void createNotification(String toUserId, String fromUserId, String type) {
        db.collection("users").document(fromUserId).get()
            .addOnSuccessListener(doc -> {
                String name = doc.getString("name");
                String pic = doc.getString("profilePic");
                
                java.util.Map<String, Object> notif = new HashMap<>();
                notif.put("type", type);
                notif.put("fromUserId", fromUserId);
                notif.put("fromUserName", name);
                notif.put("fromUserProfilePic", pic);
                notif.put("toUserId", toUserId);
                notif.put("timestamp", System.currentTimeMillis());
                notif.put("read", false);
                
                db.collection("notifications").add(notif);
            });
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
