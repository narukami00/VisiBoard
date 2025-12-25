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
                tab.setText("Nearby");
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
        android.content.Intent intent = new android.content.Intent(this, com.visiboard.app.ui.profile.UserProfileActivity.class);
        intent.putExtra(com.visiboard.app.ui.profile.UserProfileActivity.EXTRA_USER_ID, userId);
        startActivity(intent);
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
