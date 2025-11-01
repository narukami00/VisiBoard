package com.visiboard.app.ui.profile;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import android.widget.LinearLayout;
import com.visiboard.app.R;
import com.visiboard.app.ui.auth.LoginActivity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class ProfileFragment extends Fragment {

    private CircleImageView profileImage;
    private TextView nameText, emailText, tvTotalNotes, tvTotalLikes, tvRecentNote, tvMilestone, tvMilestoneProgress, tvLocation;
    private TextView tvFollowersCount, tvFollowingCount;
    private ImageView ivTierIcon, logoutIcon;
    private ProgressBar progressMilestone;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private final int PICK_IMAGE = 101;
    private String base64Image = "";

    private final int[] milestones = {100, 500, 1000, 5000, 10000};
    private final String[] milestoneTiers = {"Bronze", "Silver", "Gold", "Diamond", "Platinum"};
    private final int[] milestoneIcons = {
            R.drawable.ic_bronze,
            R.drawable.ic_silver,
            R.drawable.ic_gold,
            R.drawable.ic_diamond,
            R.drawable.ic_platinum
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        profileImage = view.findViewById(R.id.profileImage);
        nameText = view.findViewById(R.id.profileName);
        emailText = view.findViewById(R.id.profileEmail);
        tvTotalNotes = view.findViewById(R.id.tv_total_notes);
        tvTotalLikes = view.findViewById(R.id.tv_total_likes);
        tvRecentNote = view.findViewById(R.id.tv_recent_note);
        tvMilestone = view.findViewById(R.id.tv_milestone);
        tvMilestoneProgress = view.findViewById(R.id.tv_milestone_progress);
        tvLocation = view.findViewById(R.id.tv_location);
        tvFollowersCount = view.findViewById(R.id.tv_followers_count);
        tvFollowingCount = view.findViewById(R.id.tv_following_count);
        ivTierIcon = view.findViewById(R.id.iv_tier_icon);
        progressMilestone = view.findViewById(R.id.progress_milestone);
        logoutIcon = view.findViewById(R.id.logout_icon);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        loadUserData();
        loadUserStats();
        updateUserLocation();

        profileImage.setOnClickListener(v -> pickImage());
        logoutIcon.setOnClickListener(v -> showLogoutConfirmation());
        
        // Followers/Following click listeners
        view.findViewById(R.id.followers_section).setOnClickListener(v -> showFollowersDialog(false));
        view.findViewById(R.id.following_section).setOnClickListener(v -> showFollowersDialog(true));
        
        // Make milestone card clickable
        view.findViewById(R.id.milestone_card).setOnClickListener(v -> showRankRoadmapDialog());

        return view;
    }

    private void updateUserLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(), 
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        com.google.android.gms.location.FusedLocationProviderClient fusedLocationClient = 
                com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(requireContext());
        
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null && auth.getCurrentUser() != null) {
                // Use Geocoder to get area name
                android.location.Geocoder geocoder = new android.location.Geocoder(requireContext(), java.util.Locale.getDefault());
                try {
                    java.util.List<android.location.Address> addresses = geocoder.getFromLocation(
                            location.getLatitude(), location.getLongitude(), 1);
                    
                    if (addresses != null && !addresses.isEmpty()) {
                        android.location.Address address = addresses.get(0);
                        String locality = address.getLocality(); // City
                        String country = address.getCountryName();
                        
                        String locationText = locality != null ? locality : "";
                        if (country != null) {
                            locationText += (locationText.isEmpty() ? "" : ", ") + country;
                        }
                        
                        if (!locationText.isEmpty()) {
                            tvLocation.setText(locationText);
                            tvLocation.setVisibility(View.VISIBLE);
                            
                            // Save to Firestore
                            String uid = auth.getCurrentUser().getUid();
                            db.collection("users").document(uid)
                                    .update("lastKnownLocation", locationText)
                                    .addOnFailureListener(e -> 
                                            Log.e("ProfileFragment", "Failed to update location: " + e.getMessage()));
                        }
                    }
                } catch (Exception e) {
                    Log.e("ProfileFragment", "Geocoder error: " + e.getMessage());
                }
            }
        });
    }

    private void loadUserData() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        emailText.setText(user.getEmail());

        String uid = user.getUid();
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String name = doc.getString("name");
                        nameText.setText(name != null ? name : "User");

                        String location = doc.getString("lastKnownLocation");
                        if (location != null && !location.isEmpty()) {
                            tvLocation.setText(location);
                            tvLocation.setVisibility(View.VISIBLE);
                        }

                        // Load followers and following counts
                        Long followersCount = doc.getLong("followersCount");
                        Long followingCount = doc.getLong("followingCount");
                        tvFollowersCount.setText(String.valueOf(followersCount != null ? followersCount : 0));
                        tvFollowingCount.setText(String.valueOf(followingCount != null ? followingCount : 0));

                        String pic = doc.getString("profilePic");
                        if (pic != null && !pic.isEmpty()) {
                            try {
                                byte[] bytes = Base64.decode(pic, Base64.DEFAULT);
                                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                                profileImage.setImageBitmap(bitmap);
                            } catch (IllegalArgumentException e) {
                                e.printStackTrace();
                                Toast.makeText(getContext(), "Error loading profile picture", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });
    }

    private void loadUserStats() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        String uid = user.getUid();
        
        // Query global notes collection for current user's notes
        db.collection("notes")
                .whereEqualTo("userId", uid)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    int totalNotes = querySnapshot.size();
                    tvTotalNotes.setText(String.valueOf(totalNotes));

                    // Calculate total likes across all notes
                    int totalLikes = 0;
                    if (!querySnapshot.isEmpty()) {
                        List<com.google.firebase.firestore.DocumentSnapshot> docs = querySnapshot.getDocuments();
                        String recent = docs.get(docs.size() - 1).getString("note");
                        tvRecentNote.setText("Recent Note: " + (recent != null && recent.length() > 30 ? recent.substring(0, 30) + "..." : recent));
                        
                        for (com.google.firebase.firestore.DocumentSnapshot doc : docs) {
                            Long likeCount = doc.getLong("likeCount");
                            if (likeCount != null) {
                                totalLikes += likeCount.intValue();
                            }
                        }
                    } else {
                        tvRecentNote.setText("No notes yet.");
                    }
                    
                    tvTotalLikes.setText(String.valueOf(totalLikes));

                    // Determine tier and progress (based on likes now for better gamification)
                    int tierIndex = -1;
                    for (int i = 0; i < milestones.length; i++) {
                        if (totalLikes >= milestones[i]) tierIndex = i;
                    }

                    String currentTier;

                    if (tierIndex == -1) {
                        currentTier = "None";
                        tvMilestone.setText("No Tier Yet");
                        ivTierIcon.setImageResource(R.drawable.ic_default_tier);
                        progressMilestone.setMax(milestones[0]);
                        progressMilestone.setProgress(totalLikes);
                        tvMilestoneProgress.setText(totalLikes + " / " + milestones[0] + " likes to Bronze");
                    } else if (tierIndex < milestones.length - 1) {
                        currentTier = milestoneTiers[tierIndex];
                        tvMilestone.setText("Current Tier: " + currentTier);
                        ivTierIcon.setImageResource(milestoneIcons[tierIndex]);
                        int nextGoal = milestones[tierIndex + 1];
                        progressMilestone.setMax(nextGoal);
                        progressMilestone.setProgress(totalLikes);
                        tvMilestoneProgress.setText(totalLikes + " / " + nextGoal + " likes to " + milestoneTiers[tierIndex + 1]);
                    } else {
                        currentTier = "Platinum";
                        tvMilestone.setText("Max Tier: Platinum");
                        ivTierIcon.setImageResource(milestoneIcons[milestoneIcons.length - 1]);
                        progressMilestone.setMax(milestones[milestones.length - 1]);
                        progressMilestone.setProgress(milestones[milestones.length - 1]);
                        tvMilestoneProgress.setText("Maxed Out");
                    }

                    // 🔹 UPDATE TIER IN DATABASE
                    db.collection("users").document(uid)
                            .update("currentTier", currentTier)
                            .addOnFailureListener(e ->
                                    Toast.makeText(getContext(), "Tier update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                            );
                })
                .addOnFailureListener(e -> {
                    Log.e("ProfileFragment", "Error loading stats: " + e.getMessage());
                    Toast.makeText(getContext(), "Failed to load statistics", Toast.LENGTH_SHORT).show();
                });
    }


    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_IMAGE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE && resultCode == getActivity().RESULT_OK && data != null) {
            Uri imgUri = data.getData();
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getActivity().getContentResolver(), imgUri);
                profileImage.setImageBitmap(bitmap);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos);
                base64Image = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);

                updateProfilePic(base64Image);
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(getContext(), "Failed to load image", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updateProfilePic(String base64Image) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        String uid = user.getUid();
        db.collection("users").document(uid).update("profilePic", base64Image)
                .addOnSuccessListener(unused -> Toast.makeText(getContext(), "Profile picture updated", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(getContext(), "Failed to update: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void showLogoutConfirmation() {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_confirmation, null);
        TextView title = dialogView.findViewById(R.id.dialog_title);
        TextView message = dialogView.findViewById(R.id.dialog_message);
        Button btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        
        title.setText("Logout");
        message.setText("Are you sure you want to logout?");
        btnConfirm.setText("Logout");

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(getContext())
                .setView(dialogView)
                .create();
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnConfirm.setOnClickListener(v -> {
            logoutUser();
            dialog.dismiss();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
    }

    private void logoutUser() {
        auth.signOut();
        startActivity(new Intent(getActivity(), LoginActivity.class));
        getActivity().finish();
    }

    private void showRankRoadmapDialog() {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_rank_roadmap, null);
        
        // Set current rank info
        ImageView currentTierIcon = dialogView.findViewById(R.id.dialog_current_tier_icon);
        TextView currentTierName = dialogView.findViewById(R.id.dialog_current_tier_name);
        TextView currentLikes = dialogView.findViewById(R.id.dialog_current_likes);
        
        // Get current stats
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            String uid = user.getUid();
            db.collection("notes")
                    .whereEqualTo("userId", uid)
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        int totalLikes = 0;
                        for (var doc : querySnapshot.getDocuments()) {
                            Long likeCount = doc.getLong("likeCount");
                            if (likeCount != null) totalLikes += likeCount.intValue();
                        }
                        
                        // Determine current tier
                        int tierIndex = -1;
                        for (int i = 0; i < milestones.length; i++) {
                            if (totalLikes >= milestones[i]) tierIndex = i;
                        }
                        
                        if (tierIndex >= 0) {
                            currentTierName.setText(milestoneTiers[tierIndex]);
                            currentTierIcon.setImageResource(milestoneIcons[tierIndex]);
                        } else {
                            currentTierName.setText("None");
                            currentTierIcon.setImageResource(R.drawable.ic_default_tier);
                        }
                        currentLikes.setText(totalLikes + " Likes");
                        
                        // Setup rank items
                        setupRankItem(dialogView, R.id.rank_bronze, "Bronze", milestones[0], 
                                R.drawable.ic_bronze, totalLikes >= milestones[0]);
                        setupRankItem(dialogView, R.id.rank_silver, "Silver", milestones[1],
                                R.drawable.ic_silver, totalLikes >= milestones[1]);
                        setupRankItem(dialogView, R.id.rank_gold, "Gold", milestones[2],
                                R.drawable.ic_gold, totalLikes >= milestones[2]);
                        setupRankItem(dialogView, R.id.rank_diamond, "Diamond", milestones[3],
                                R.drawable.ic_diamond, totalLikes >= milestones[3]);
                        setupRankItem(dialogView, R.id.rank_platinum, "Platinum", milestones[4],
                                R.drawable.ic_platinum, totalLikes >= milestones[4]);
                    });
        }
        
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(getContext())
                .setView(dialogView)
                .setPositiveButton("Close", null)
                .create();
        
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        
        dialog.show();
    }
    
    private void setupRankItem(View dialogView, int rankViewId, String rankName, int requirement,
                                int iconRes, boolean achieved) {
        View rankView = dialogView.findViewById(rankViewId);
        ImageView rankIcon = rankView.findViewById(R.id.rank_icon);
        TextView rankNameView = rankView.findViewById(R.id.rank_name);
        TextView rankRequirement = rankView.findViewById(R.id.rank_requirement);
        ImageView rankStatus = rankView.findViewById(R.id.rank_status);
        
        rankIcon.setImageResource(iconRes);
        rankNameView.setText(rankName);
        rankRequirement.setText(requirement + " Likes Required");
        
        if (achieved) {
            rankStatus.setImageResource(R.drawable.ic_check);
            rankStatus.setColorFilter(getResources().getColor(R.color.accent, null));
            rankView.setBackgroundColor(getResources().getColor(R.color.primary, null));
            rankView.setAlpha(0.2f);
        } else {
            rankStatus.setImageResource(R.drawable.ic_lock);
            rankStatus.setColorFilter(getResources().getColor(R.color.secondary, null));
        }
    }

    // Show followers/following dialog
    private void showFollowersDialog(boolean showFollowing) {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_followers_list, null);
        
        android.widget.Button followersTab = dialogView.findViewById(R.id.btn_followers_tab);
        android.widget.Button followingTab = dialogView.findViewById(R.id.btn_following_tab);
        androidx.recyclerview.widget.RecyclerView recyclerView = dialogView.findViewById(R.id.users_recycler);
        TextView emptyState = dialogView.findViewById(R.id.empty_state);
        
        recyclerView.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(getContext()));
        
        final boolean[] isFollowingList = {showFollowing};
        final UserFollowAdapter[] adapterHolder = new UserFollowAdapter[1];
        
        UserFollowAdapter adapter = new UserFollowAdapter(new UserFollowAdapter.OnUserClickListener() {
            @Override
            public void onUserClick(com.visiboard.app.data.UserInfo user) {
                showUserInfoDialog(user.getUserId());
            }
            
            @Override
            public void onFollowClick(com.visiboard.app.data.UserInfo user, int position) {
                if (isFollowingList[0]) {
                    // Unfollow
                    unfollowUserFromList(user.getUserId(), adapterHolder[0], position);
                } else {
                    // Remove follower
                    removeFollower(user.getUserId(), adapterHolder[0], position);
                }
            }
        }, showFollowing);
        
        adapterHolder[0] = adapter;
        recyclerView.setAdapter(adapter);
        
        // Set initial tab state
        if (showFollowing) {
            followingTab.setBackgroundResource(R.drawable.btn_primary_selector);
            followingTab.setTextColor(getResources().getColor(R.color.button_text_primary, null));
            followersTab.setBackgroundResource(R.drawable.btn_secondary_selector);
            followersTab.setTextColor(getResources().getColor(R.color.button_text_secondary, null));
        }
        
        // Load initial list
        loadUsersList(showFollowing, adapter, emptyState);
        
        // Tab switching
        followersTab.setOnClickListener(v -> {
            followersTab.setBackgroundResource(R.drawable.btn_primary_selector);
            followersTab.setTextColor(getResources().getColor(R.color.button_text_primary, null));
            followingTab.setBackgroundResource(R.drawable.btn_secondary_selector);
            followingTab.setTextColor(getResources().getColor(R.color.button_text_secondary, null));
            isFollowingList[0] = false;
            loadUsersList(false, adapter, emptyState);
        });
        
        followingTab.setOnClickListener(v -> {
            followingTab.setBackgroundResource(R.drawable.btn_primary_selector);
            followingTab.setTextColor(getResources().getColor(R.color.button_text_primary, null));
            followersTab.setBackgroundResource(R.drawable.btn_secondary_selector);
            followersTab.setTextColor(getResources().getColor(R.color.button_text_secondary, null));
            isFollowingList[0] = true;
            loadUsersList(true, adapter, emptyState);
        });
        
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(getContext())
                .setView(dialogView)
                .create();
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        
        dialog.show();
    }

    // Load users list (followers or following)
    private void loadUsersList(boolean isFollowing, UserFollowAdapter adapter, TextView emptyState) {
        String uid = auth.getCurrentUser().getUid();
        String collection = isFollowing ? "following" : "followers";
        
        db.collection("users").document(uid).collection(collection).get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        emptyState.setVisibility(View.VISIBLE);
                        adapter.setUsers(new java.util.ArrayList<>());
                    } else {
                        emptyState.setVisibility(View.GONE);
                        List<com.visiboard.app.data.UserInfo> users = new java.util.ArrayList<>();
                        
                        for (var doc : querySnapshot.getDocuments()) {
                            String userId = doc.getId();
                            
                            // Load full user info
                            db.collection("users").document(userId).get()
                                    .addOnSuccessListener(userDoc -> {
                                        if (userDoc.exists()) {
                                            com.visiboard.app.data.UserInfo userInfo = new com.visiboard.app.data.UserInfo();
                                            userInfo.setUserId(userId);
                                            userInfo.setName(userDoc.getString("name"));
                                            userInfo.setProfilePic(userDoc.getString("profilePic"));
                                            userInfo.setLastKnownLocation(userDoc.getString("lastKnownLocation"));
                                            userInfo.setCurrentTier(userDoc.getString("currentTier"));
                                            Long followers = userDoc.getLong("followersCount");
                                            Long following = userDoc.getLong("followingCount");
                                            userInfo.setFollowersCount(followers != null ? followers.intValue() : 0);
                                            userInfo.setFollowingCount(following != null ? following.intValue() : 0);
                                            
                                            users.add(userInfo);
                                            adapter.setUsers(users);
                                        }
                                    });
                        }
                    }
                });
    }

    // Unfollow user from list
    private void unfollowUserFromList(String targetUserId, UserFollowAdapter adapter, int position) {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_confirmation, null);
        TextView title = dialogView.findViewById(R.id.dialog_title);
        TextView message = dialogView.findViewById(R.id.dialog_message);
        Button btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        
        title.setText("Unfollow User");
        message.setText("Are you sure you want to unfollow this user?");
        btnConfirm.setText("Unfollow");

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(getContext())
                .setView(dialogView)
                .create();
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnConfirm.setOnClickListener(v -> {
            String currentUserId = auth.getCurrentUser().getUid();
            
            // Remove from target user's followers
            db.collection("users").document(targetUserId)
                    .collection("followers").document(currentUserId)
                    .delete();
            db.collection("users").document(targetUserId)
                    .update("followersCount", com.google.firebase.firestore.FieldValue.increment(-1));
            
            // Remove from current user's following
            db.collection("users").document(currentUserId)
                    .collection("following").document(targetUserId)
                    .delete();
            db.collection("users").document(currentUserId)
                    .update("followingCount", com.google.firebase.firestore.FieldValue.increment(-1));
            
            // Update UI
            adapter.removeUser(position);
            int count = Integer.parseInt(tvFollowingCount.getText().toString());
            tvFollowingCount.setText(String.valueOf(Math.max(0, count - 1)));
            
            Toast.makeText(getContext(), "Unfollowed", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
    }

    // Remove follower
    private void removeFollower(String followerId, UserFollowAdapter adapter, int position) {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_confirmation, null);
        TextView title = dialogView.findViewById(R.id.dialog_title);
        TextView message = dialogView.findViewById(R.id.dialog_message);
        Button btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        
        title.setText("Remove Follower");
        message.setText("Are you sure you want to remove this follower?");
        btnConfirm.setText("Remove");

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(getContext())
                .setView(dialogView)
                .create();
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnConfirm.setOnClickListener(v -> {
            String currentUserId = auth.getCurrentUser().getUid();
            
            // Remove from current user's followers
            db.collection("users").document(currentUserId)
                    .collection("followers").document(followerId)
                    .delete();
            db.collection("users").document(currentUserId)
                    .update("followersCount", com.google.firebase.firestore.FieldValue.increment(-1));
            
            // Remove from follower's following
            db.collection("users").document(followerId)
                    .collection("following").document(currentUserId)
                    .delete();
            db.collection("users").document(followerId)
                    .update("followingCount", com.google.firebase.firestore.FieldValue.increment(-1));
            
            // Update UI
            adapter.removeUser(position);
            int count = Integer.parseInt(tvFollowersCount.getText().toString());
            tvFollowersCount.setText(String.valueOf(Math.max(0, count - 1)));
            
            Toast.makeText(getContext(), "Follower removed", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
    }

    // Show user info dialog
    private void showUserInfoDialog(String userId) {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_user_info, null);
        
        de.hdodenhof.circleimageview.CircleImageView profilePic = dialogView.findViewById(R.id.dialog_user_profile_pic);
        TextView userName = dialogView.findViewById(R.id.dialog_user_name);
        TextView userLocation = dialogView.findViewById(R.id.dialog_user_location);
        LinearLayout locationContainer = dialogView.findViewById(R.id.dialog_location_container);
        TextView userRank = dialogView.findViewById(R.id.dialog_user_rank);
        ImageView rankIcon = dialogView.findViewById(R.id.dialog_user_rank_icon);
        TextView followersCount = dialogView.findViewById(R.id.dialog_followers_count);
        TextView followingCount = dialogView.findViewById(R.id.dialog_following_count);
        android.widget.Button followBtn = dialogView.findViewById(R.id.dialog_follow_btn);
        
        // Load user data (same as MapFragment showUserInfoDialog)
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
                            try {
                                byte[] bytes = Base64.decode(pic, Base64.DEFAULT);
                                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                                profilePic.setImageBitmap(bitmap);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        
                        Long followers = doc.getLong("followersCount");
                        Long following = doc.getLong("followingCount");
                        followersCount.setText(String.valueOf(followers != null ? followers : 0));
                        followingCount.setText(String.valueOf(following != null ? following : 0));
                        
                        String currentUserId = auth.getCurrentUser().getUid();
                        if (!userId.equals(currentUserId)) {
                            followBtn.setVisibility(View.VISIBLE);
                            
                            db.collection("users").document(currentUserId)
                                    .collection("following").document(userId)
                                    .get()
                                    .addOnSuccessListener(followDoc -> {
                                        if (followDoc.exists()) {
                                            followBtn.setText("Unfollow");
                                            followBtn.setBackgroundResource(R.drawable.bg_button_secondary);
                                            followBtn.setTextColor(getResources().getColor(R.color.black, null));
                                        }
                                    });
                            
                            followBtn.setOnClickListener(v -> {
                                // Implement follow/unfollow logic
                                Toast.makeText(getContext(), "Follow functionality", Toast.LENGTH_SHORT).show();
                            });
                        }
                    }
                });
        
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(getContext())
                .setView(dialogView)
                .create();
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        
        dialog.show();
    }
}
