package com.visiboard.app.ui.profile;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import com.visiboard.app.R;
import com.visiboard.app.data.NearbyNote;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;

public class UserProfileFragment extends Fragment {

    private static final String TAG = "UserProfileFragment";
    private static final String ARG_USER_ID = "userId";

    private String targetUserId;
    private String currentUserId;
    private boolean isFollowing = false;
    private boolean isRequested = false;
    private boolean isPrivateAccount = false;
    private boolean hasAccessToPrivate = false;

    // Views
    private View loadingOverlay;
    private FloatingPhysicsLayout physicsHeader;
    private CircleImageView ivProfilePic;
    private TextView tvUserName, tvAccountType, tvLocation, tvDistance, tvBio;
    private TextView tvTotalNotes, tvTotalLikes, tvFollowersCount, tvFollowingCount;
    private TextView tvRank, tvFavsHeader;
    private ImageView ivRankIcon, btnBack, btnOptionsMenu;
    private LinearLayout locationContainer, llDetailsContainer, llPrivateMessage, rankContainer;
    private LinearLayout rowWork, rowEducation, rowRelationship, rowHometown, rowBirthday;
    private TextView tvWork, tvEducation, tvRelationship, tvHometown, tvBirthday, tvJoinedDate;
    private LinearLayout llLinksContainer;
    private View svLinks;
    private Button btnFollow;
    private RecyclerView rvFavouriteNotes;
    private RecyclerView rvAllNotes;
    private TextView tvAllNotesHeader, tvNoNotes;
    private android.widget.ProgressBar pbNotesLoading;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefreshLayout;
    private RecentNotesAdapter allNotesAdapter;
    private List<com.google.firebase.firestore.DocumentSnapshot> allNotesList = new ArrayList<>();
    private com.google.firebase.firestore.DocumentSnapshot lastVisibleNote;
    private boolean isNotesLoading = false;
    private boolean isLastNoteReached = false;
    private static final int NOTES_PAGE_SIZE = 10;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private FusedLocationProviderClient fusedLocationClient;
    private Location currentUserLocation;

    public static UserProfileFragment newInstance(String userId) {
        UserProfileFragment fragment = new UserProfileFragment();
        Bundle args = new Bundle();
        args.putString(ARG_USER_ID, userId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        
        if (getArguments() != null) {
            targetUserId = getArguments().getString(ARG_USER_ID);
        }
        
        if (auth.getCurrentUser() != null) {
            currentUserId = auth.getCurrentUser().getUid();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_user_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Close any open note windows when this fragment is shown
        // Use post to ensure it runs after fragment is fully initialized and MapFragment is ready
        view.post(() -> {
            Bundle result = new Bundle();
            result.putBoolean("close_note_window", true);
            getParentFragmentManager().setFragmentResult("close_note_window", result);
        });
        
        bindViews(view);
        setupClickListeners();
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext());
        getCurrentLocation();
        
        if (targetUserId != null) {
            loadUserData();
        } else {
            Toast.makeText(requireContext(), "User not found", Toast.LENGTH_SHORT).show();
            navigateBack();
        }
    }

    private void bindViews(View view) {
        loadingOverlay = view.findViewById(R.id.loading_overlay);
        physicsHeader = view.findViewById(R.id.physics_header_container);
        ivProfilePic = view.findViewById(R.id.iv_profile_pic);
        tvUserName = view.findViewById(R.id.tv_user_name);
        tvAccountType = view.findViewById(R.id.tv_account_type);
        locationContainer = view.findViewById(R.id.location_container);
        tvLocation = view.findViewById(R.id.tv_location);
        tvDistance = view.findViewById(R.id.tv_distance);
        tvBio = view.findViewById(R.id.tv_bio);
        btnFollow = view.findViewById(R.id.btn_follow);
        llDetailsContainer = view.findViewById(R.id.ll_details_container);
        rowWork = view.findViewById(R.id.row_work);
        rowEducation = view.findViewById(R.id.row_education);
        rowRelationship = view.findViewById(R.id.row_relationship);
        rowHometown = view.findViewById(R.id.row_hometown);
        tvWork = view.findViewById(R.id.tv_work);
        tvEducation = view.findViewById(R.id.tv_education);
        tvRelationship = view.findViewById(R.id.tv_relationship);
        tvHometown = view.findViewById(R.id.tv_hometown);
        rowBirthday = view.findViewById(R.id.row_birthday);
        tvBirthday = view.findViewById(R.id.tv_birthday);
        tvJoinedDate = view.findViewById(R.id.tv_joined_date);
        svLinks = view.findViewById(R.id.sv_links);
        llLinksContainer = view.findViewById(R.id.ll_links_container);
        tvTotalNotes = view.findViewById(R.id.tv_total_notes);
        tvTotalLikes = view.findViewById(R.id.tv_total_likes);
        tvFollowersCount = view.findViewById(R.id.tv_followers_count);
        tvFollowingCount = view.findViewById(R.id.tv_following_count);
        rankContainer = view.findViewById(R.id.rank_container);
        ivRankIcon = view.findViewById(R.id.iv_rank_icon);
        tvRank = view.findViewById(R.id.tv_rank);
        tvFavsHeader = view.findViewById(R.id.tv_favs_header);
        rvFavouriteNotes = view.findViewById(R.id.rv_favourite_notes);
        rvAllNotes = view.findViewById(R.id.rv_all_notes);
        tvAllNotesHeader = view.findViewById(R.id.tv_all_notes_header);
        tvNoNotes = view.findViewById(R.id.tv_no_notes);
        pbNotesLoading = view.findViewById(R.id.pb_notes_loading);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout);
        llPrivateMessage = view.findViewById(R.id.ll_private_message);
        btnBack = view.findViewById(R.id.btn_back);
        btnOptionsMenu = view.findViewById(R.id.btn_options_menu);

        setupAllNotesRecyclerView();
        setupSwipeRefresh();
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> navigateBack());
        
        btnOptionsMenu.setOnClickListener(v -> showOptionsBottomSheet());
        
        btnFollow.setOnClickListener(v -> handleFollowClick());
    }

    private void navigateBack() {
        if (getActivity() != null) {
            getActivity().onBackPressed();
        }
    }

    private void getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                currentUserLocation = location;
            }
        });
    }

    private void loadUserData() {
        db.collection("users").document(targetUserId).get()
            .addOnSuccessListener(doc -> {
                if (!isAdded() || !doc.exists()) {
                    hideLoading();
                    return;
                }
                
                // Basic Info
                String name = doc.getString("name");
                tvUserName.setText(name != null ? name : "User");
                
                // Profile Pic
                String pic = doc.getString("profilePic");
                if (pic != null && !pic.isEmpty()) {
                    try {
                        byte[] bytes = Base64.decode(pic, Base64.DEFAULT);
                        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        ivProfilePic.setImageBitmap(bitmap);
                    } catch (Exception e) {
                        Log.e(TAG, "Error decoding profile pic", e);
                    }
                }
                
                // Account Type
                Boolean isPrivate = doc.getBoolean("isPrivate");
                isPrivateAccount = isPrivate != null && isPrivate;
                tvAccountType.setText(isPrivateAccount ? "Private Account" : "Public Account");
                
        // Check if current user has access (is following), or if viewing own profile
        checkFollowStatus(() -> {
            // If viewing own profile, always have access
            if (targetUserId.equals(currentUserId)) {
                hasAccessToPrivate = true;
            } else {
                hasAccessToPrivate = !isPrivateAccount || isFollowing;
            }
            updateUIForPrivacy(doc);
            // Load note stats after we know access status
            loadNoteStats();
            
            // Load all notes if allowed
            if (targetUserId.equals(currentUserId) || hasAccessToPrivate) {
                // Determine visibility filter based on relationship
                List<String> allowedVisibilities = new ArrayList<>();
                allowedVisibilities.add("public");
                if (targetUserId.equals(currentUserId) || isFollowing) {
                    allowedVisibilities.add("followers");
                }
                // Determine if we show private notes (only for self)
                boolean showPrivate = targetUserId.equals(currentUserId);
                
                loadUserNotes(allowedVisibilities, showPrivate, true);
            } else {
                 // Public user, non-follower -> show only public notes
                 if (!isPrivateAccount) {
                     List<String> publicOnly = new ArrayList<>();
                     publicOnly.add("public");
                     loadUserNotes(publicOnly, false, true);
                 } else {
                     // Private user, non-follower -> UI handled by updateUIForPrivacy (hides sections)
                 }
            }

            hideLoading();
        });
                
                // Stats (always visible)
                Long followers = doc.getLong("followersCount");
                Long following = doc.getLong("followingCount");
                tvFollowersCount.setText(String.valueOf(followers != null ? followers : 0));
                tvFollowingCount.setText(String.valueOf(following != null ? following : 0));
                
                // Rank
                String tier = doc.getString("currentTier");
                if (tier != null && !tier.isEmpty()) {
                    tvRank.setText(tier);
                    ivRankIcon.setImageResource(getTierIcon(tier));
                }
                
                // Load notes count and likes (will be filtered based on access)
                // Note: loadNoteStats needs hasAccessToPrivate, so we'll call it after checkFollowStatus
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error loading user", e);
                Toast.makeText(requireContext(), "Failed to load profile", Toast.LENGTH_SHORT).show();
                hideLoading();
            });
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener(() -> {
            loadUserData();
            // Also refresh location if needed
            getCurrentLocation();
        });
        
        // Configure colors
        swipeRefreshLayout.setColorSchemeResources(R.color.primary, R.color.accent);
    }

    private void updateUIForPrivacy(com.google.firebase.firestore.DocumentSnapshot doc) {
        if (!isAdded()) return;
        
        // If viewing own profile, show everything including private info, hide follow button
        if (targetUserId.equals(currentUserId)) {
            btnFollow.setVisibility(View.GONE);
            btnOptionsMenu.setVisibility(View.GONE); // Hide options menu (block/report not needed for own profile)
            hasAccessToPrivate = true; // Always have access to own profile
            showPublicDetails(doc);
            // Show all notes regardless of visibility for own profile
        } else {
            btnOptionsMenu.setVisibility(View.VISIBLE); // Show options menu for other users
            // Show follow button if not self
            btnFollow.setVisibility(View.VISIBLE);
            updateFollowButton();
            
            if (hasAccessToPrivate) {
                // Show all details
                showPublicDetails(doc);
            } else {
                // Hide sensitive sections, show private message
                llDetailsContainer.setVisibility(View.GONE);
                svLinks.setVisibility(View.GONE);
                tvFavsHeader.setVisibility(View.GONE);
                rvFavouriteNotes.setVisibility(View.GONE);
                locationContainer.setVisibility(View.GONE);
                llPrivateMessage.setVisibility(View.VISIBLE);
                
                // Hide All Notes Section for Private + Non-Follower
                tvAllNotesHeader.setVisibility(View.GONE);
                rvAllNotes.setVisibility(View.GONE);
                tvNoNotes.setVisibility(View.GONE);
                pbNotesLoading.setVisibility(View.GONE);
            }
        }
        
        // Load favourite notes for cover after access is determined
        loadFavouriteNotesForCoverFromDoc(doc);
    }
    
    private void loadFavouriteNotesForCoverFromDoc(com.google.firebase.firestore.DocumentSnapshot doc) {
        if (physicsHeader == null || !isAdded()) return;
        
        List<String> favs = (List<String>) doc.get("favouriteNotes");
        
        // If viewing own profile or has access, load favourite notes
        boolean canViewNotes = targetUserId.equals(currentUserId) || hasAccessToPrivate;
        
        if (canViewNotes && favs != null && !favs.isEmpty()) {
            loadFavouriteNotesForCover(favs);
        } else {
            // No access, no favs, or empty favs - show decorative icons
            physicsHeader.removeAllViews();
            addDecorativeSocialIcons();
            physicsHeader.postDelayed(() -> {
                if (isAdded() && physicsHeader != null) {
                    physicsHeader.initializeEntities();
                }
            }, 100);
        }
    }

    private void showPublicDetails(com.google.firebase.firestore.DocumentSnapshot doc) {
        llPrivateMessage.setVisibility(View.GONE);
        
        // Location
        String location = doc.getString("lastKnownLocation");
        if (location != null && !location.isEmpty()) {
            tvLocation.setText(location);
            locationContainer.setVisibility(View.VISIBLE);
            
            // Calculate distance if we have user's location
            Double lat = doc.getDouble("lat");
            Double lng = doc.getDouble("lng");
            if (lat != null && lng != null && currentUserLocation != null) {
                float[] results = new float[1];
                Location.distanceBetween(
                    currentUserLocation.getLatitude(), currentUserLocation.getLongitude(),
                    lat, lng, results
                );
                float distanceKm = results[0] / 1000;
                if (distanceKm < 1) {
                    tvDistance.setText(String.format(" • %.0f m away", results[0]));
                } else {
                    tvDistance.setText(String.format(" • %.1f km away", distanceKm));
                }
                tvDistance.setVisibility(View.VISIBLE);
            }
        }
        
        // Bio
        String bio = doc.getString("bio");
        if (bio != null && !bio.isEmpty()) {
            tvBio.setText(bio);
            tvBio.setVisibility(View.VISIBLE);
        }
        
        // Details
        String work = doc.getString("work");
        if (work != null && !work.isEmpty()) {
            tvWork.setText("Works at " + work);
            rowWork.setVisibility(View.VISIBLE);
            llDetailsContainer.setVisibility(View.VISIBLE);
        }
        
        String education = doc.getString("education");
        if (education != null && !education.isEmpty()) {
            tvEducation.setText("Studied at " + education);
            rowEducation.setVisibility(View.VISIBLE);
            llDetailsContainer.setVisibility(View.VISIBLE);
        }
        
        String relationship = doc.getString("relationship");
        if (relationship != null && !relationship.isEmpty()) {
            tvRelationship.setText(relationship);
            rowRelationship.setVisibility(View.VISIBLE);
            llDetailsContainer.setVisibility(View.VISIBLE);
        }
        
        String hometown = doc.getString("hometown");
        if (hometown != null && !hometown.isEmpty()) {
            tvHometown.setText("From " + hometown);
            rowHometown.setVisibility(View.VISIBLE);
            llDetailsContainer.setVisibility(View.VISIBLE);
        }
        
        String birthday = doc.getString("birthday");
        if (birthday != null && !birthday.isEmpty()) {
            tvBirthday.setText("Born on " + birthday);
            rowBirthday.setVisibility(View.VISIBLE);
            llDetailsContainer.setVisibility(View.VISIBLE);
        }
        
        // Joined Date
        Long createdAt = doc.getLong("createdAt");
        if (createdAt != null && tvJoinedDate != null) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault());
            String joined = "Joined in " + sdf.format(new java.util.Date(createdAt));
            tvJoinedDate.setText(joined);
            tvJoinedDate.setVisibility(View.VISIBLE);
        }
        
        // Links
        List<Map<String, String>> links = (List<Map<String, String>>) doc.get("socialLinks");
        if (links != null && !links.isEmpty()) {
            renderLinks(links);
            svLinks.setVisibility(View.VISIBLE);
        }
        
        // Favourite Notes section (only show if has access or viewing own profile)
        List<String> favs = (List<String>) doc.get("favouriteNotes");
        if ((targetUserId.equals(currentUserId) || hasAccessToPrivate) && favs != null && !favs.isEmpty()) {
            tvFavsHeader.setVisibility(View.VISIBLE);
            loadFavouriteNotesList(favs);
        } else {
            tvFavsHeader.setVisibility(View.GONE);
            rvFavouriteNotes.setVisibility(View.GONE);
        }
    }

    private void loadNoteStats() {
        db.collection("notes")
            .whereEqualTo("userId", targetUserId)
            .get()
            .addOnSuccessListener(querySnapshot -> {
                if (!isAdded()) return;
                
                int totalNotes = 0;
                int totalLikes = 0;
                
                // Filter notes based on visibility and access
                for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot) {
                    String visibility = doc.getString("visibility");
                    if (visibility == null) visibility = "public";
                    
                    boolean isHidden = doc.getBoolean("isHidden") != null && doc.getBoolean("isHidden");
                    if (isHidden) continue;
                    
                    // If viewing own profile, count all notes
                    // Otherwise, only count visible notes
                    boolean canSee = targetUserId.equals(currentUserId) || 
                                   "public".equals(visibility) || 
                                   (hasAccessToPrivate && !"private".equals(visibility));
                    
                    if (canSee) {
                        totalNotes++;
                        Long likes = doc.getLong("likeCount");
                        if (likes == null) likes = doc.getLong("likesCount");
                        if (likes != null) totalLikes += likes.intValue();
                    }
                }
                
                tvTotalNotes.setText(String.valueOf(totalNotes));
                tvTotalLikes.setText(String.valueOf(totalLikes));
            });
    }

    private void checkFollowStatus(Runnable callback) {
        if (currentUserId == null || targetUserId.equals(currentUserId)) {
            callback.run();
            return;
        }
        
        // Check if following
        db.collection("users").document(currentUserId)
            .collection("following").document(targetUserId)
            .get()
            .addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    isFollowing = true;
                    isRequested = false;
                    callback.run();
                } else {
                    // Check if requested
                    db.collection("users").document(targetUserId)
                        .collection("follow_requests").document(currentUserId)
                        .get()
                        .addOnSuccessListener(reqDoc -> {
                            isRequested = reqDoc.exists();
                            callback.run();
                        })
                        .addOnFailureListener(e -> callback.run());
                }
            })
            .addOnFailureListener(e -> callback.run());
    }

    private void updateFollowButton() {
        if (isFollowing) {
            btnFollow.setText("Following");
            btnFollow.setBackgroundResource(R.drawable.btn_following_selector);
            btnFollow.setTextColor(getResources().getColor(R.color.button_text_following, null));
        } else if (isRequested) {
            btnFollow.setText("Requested");
            btnFollow.setBackgroundResource(R.drawable.btn_following_selector);
            btnFollow.setTextColor(getResources().getColor(R.color.button_text_following, null));
        } else {
            btnFollow.setText("Follow");
            btnFollow.setBackgroundResource(R.drawable.btn_primary_selector);
            btnFollow.setTextColor(getResources().getColor(R.color.button_text_primary, null));
        }
        btnFollow.setEnabled(true);
    }

    private void handleFollowClick() {
        if (isFollowing) {
            showUnfollowConfirmation();
        } else if (isRequested) {
            cancelFollowRequest();
        } else {
            followUser();
        }
    }

    private void followUser() {
        btnFollow.setEnabled(false);
        btnFollow.setText("...");
        
        if (isPrivateAccount) {
            // Send follow request
            db.collection("users").document(currentUserId).get()
                .addOnSuccessListener(currentUserDoc -> {
                    String myName = currentUserDoc.getString("name");
                    String myProfilePic = currentUserDoc.getString("profilePic");
                    
                    Map<String, Object> requestData = new HashMap<>();
                    requestData.put("timestamp", System.currentTimeMillis());
                    requestData.put("requesterName", myName);
                    requestData.put("requesterProfilePic", myProfilePic);
                    
                    db.collection("users").document(targetUserId)
                        .collection("follow_requests").document(currentUserId)
                        .set(requestData)
                        .addOnSuccessListener(aVoid -> {
                            isRequested = true;
                            updateFollowButton();
                            createNotification("follow_request");
                            Toast.makeText(requireContext(), "Request sent", Toast.LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(e -> {
                            btnFollow.setEnabled(true);
                            Toast.makeText(requireContext(), "Failed to send request", Toast.LENGTH_SHORT).show();
                        });
                });
        } else {
            // Direct follow
            performDirectFollow();
        }
    }

    private void performDirectFollow() {
        db.collection("users").document(currentUserId).get()
            .addOnSuccessListener(currentUserDoc -> {
                String myName = currentUserDoc.getString("name");
                String myProfilePic = currentUserDoc.getString("profilePic");
                
                // Add to target's followers
                Map<String, Object> followerData = new HashMap<>();
                followerData.put("timestamp", System.currentTimeMillis());
                followerData.put("followerName", myName);
                followerData.put("followerProfilePic", myProfilePic);
                
                db.collection("users").document(targetUserId)
                    .collection("followers").document(currentUserId)
                    .set(followerData);
                
                db.collection("users").document(targetUserId)
                    .update("followersCount", FieldValue.increment(1));
                
                // Get target user info and add to my following
                db.collection("users").document(targetUserId).get()
                    .addOnSuccessListener(targetUserDoc -> {
                        String targetName = targetUserDoc.getString("name");
                        String targetProfilePic = targetUserDoc.getString("profilePic");
                        
                        Map<String, Object> followingData = new HashMap<>();
                        followingData.put("timestamp", System.currentTimeMillis());
                        followingData.put("followedName", targetName);
                        followingData.put("followedProfilePic", targetProfilePic);
                        
                        db.collection("users").document(currentUserId)
                            .collection("following").document(targetUserId)
                            .set(followingData);
                        
                        db.collection("users").document(currentUserId)
                            .update("followingCount", FieldValue.increment(1));
                        
                        isFollowing = true;
                        hasAccessToPrivate = true;
                        updateFollowButton();
                        
                        // Update followers count in UI
                        try {
                            int count = Integer.parseInt(tvFollowersCount.getText().toString());
                            tvFollowersCount.setText(String.valueOf(count + 1));
                        } catch (Exception e) {}
                        
                        createNotification("follow");
                        Toast.makeText(requireContext(), "Following " + targetName, Toast.LENGTH_SHORT).show();
                    });
            });
    }

    private void showUnfollowConfirmation() {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Unfollow")
            .setMessage("Are you sure you want to unfollow this user?")
            .setPositiveButton("Unfollow", (d, w) -> unfollowUser())
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void unfollowUser() {
        btnFollow.setEnabled(false);
        
        // Remove from target's followers
        db.collection("users").document(targetUserId)
            .collection("followers").document(currentUserId).delete();
        
        db.collection("users").document(targetUserId)
            .update("followersCount", FieldValue.increment(-1));
        
        // Remove from my following
        db.collection("users").document(currentUserId)
            .collection("following").document(targetUserId).delete();
        
        db.collection("users").document(currentUserId)
            .update("followingCount", FieldValue.increment(-1));
        
        isFollowing = false;
        hasAccessToPrivate = !isPrivateAccount;
        updateFollowButton();
        
        // Update followers count in UI
        try {
            int count = Integer.parseInt(tvFollowersCount.getText().toString());
            tvFollowersCount.setText(String.valueOf(Math.max(0, count - 1)));
        } catch (Exception e) {}
        
        Toast.makeText(requireContext(), "Unfollowed", Toast.LENGTH_SHORT).show();
        
        // Reload to update privacy-based visibility
        if (isPrivateAccount) {
            loadUserData();
        }
    }

    private void cancelFollowRequest() {
        btnFollow.setEnabled(false);
        
        db.collection("users").document(targetUserId)
            .collection("follow_requests").document(currentUserId)
            .delete()
            .addOnSuccessListener(aVoid -> {
                isRequested = false;
                updateFollowButton();
                Toast.makeText(requireContext(), "Request cancelled", Toast.LENGTH_SHORT).show();
            })
            .addOnFailureListener(e -> {
                btnFollow.setEnabled(true);
                Toast.makeText(requireContext(), "Failed to cancel", Toast.LENGTH_SHORT).show();
            });
    }

    private void createNotification(String type) {
        db.collection("users").document(currentUserId).get()
            .addOnSuccessListener(doc -> {
                String name = doc.getString("name");
                String pic = doc.getString("profilePic");
                
                Map<String, Object> notif = new HashMap<>();
                notif.put("type", type);
                notif.put("fromUserId", currentUserId);
                notif.put("fromUserName", name);
                notif.put("fromUserProfilePic", pic);
                notif.put("toUserId", targetUserId);
                notif.put("timestamp", System.currentTimeMillis());
                notif.put("read", false);
                
                db.collection("notifications").add(notif);
            });
    }

    private void showOptionsBottomSheet() {
        UserProfileOptionsBottomSheet bottomSheet = UserProfileOptionsBottomSheet.newInstance(
            targetUserId, 
            tvUserName.getText().toString()
        );
        bottomSheet.show(getParentFragmentManager(), "UserProfileOptionsBottomSheet");
    }

    private final java.util.concurrent.atomic.AtomicInteger noteFetchGeneration = new java.util.concurrent.atomic.AtomicInteger(0);

    private void loadFavouriteNotesForCover(List<String> noteIds) {
        if (physicsHeader == null || !isAdded()) return;
        
        final int currentGen = noteFetchGeneration.incrementAndGet();
        physicsHeader.removeAllViews();
        
        // Add decorative social icons in the background
        addDecorativeSocialIcons();
        
        // Remove duplicates if any
        List<String> uniqueIds = new ArrayList<>(new java.util.HashSet<>(noteIds));
        final int total = uniqueIds.size();
        final java.util.concurrent.atomic.AtomicInteger loadedCount = new java.util.concurrent.atomic.AtomicInteger(0);
        
        if (total == 0) {
            physicsHeader.initializeEntities();
            return;
        }
        
        for (String noteId : uniqueIds) {
            db.collection("notes").document(noteId).get()
                .addOnSuccessListener(doc -> {
                    // Check if this result belongs to the latest request
                    if (currentGen != noteFetchGeneration.get() || !isAdded() || !doc.exists()) {
                        if (loadedCount.incrementAndGet() == total) {
                            physicsHeader.initializeEntities();
                        }
                        return;
                    }
                    
                    View v = createFloatingNoteView(doc);
                    if (v != null) {
                        physicsHeader.addFloatingView(v);
                    }
                    
                    if (loadedCount.incrementAndGet() == total) {
                        physicsHeader.initializeEntities();
                    }
                })
                .addOnFailureListener(e -> {
                    if (currentGen != noteFetchGeneration.get()) return;
                    if (loadedCount.incrementAndGet() == total) {
                        physicsHeader.initializeEntities();
                    }
                });
        }
    }
    
    private View createFloatingNoteView(com.google.firebase.firestore.DocumentSnapshot doc) {
        String content = doc.getString("text");
        if (content == null) content = doc.getString("note"); // Fallback
        
        String imageBase64 = doc.getString("imageBase64");
        
        // Check visibility permissions
        String visibility = doc.getString("visibility");
        if (visibility == null) visibility = "public";
        
        // If viewing own profile, show all notes regardless of visibility
        boolean canView = targetUserId.equals(currentUserId) || 
                         "public".equals(visibility) || 
                         (hasAccessToPrivate && !"private".equals(visibility));
        
        if (!canView) return null;
        
        View v;
        if (imageBase64 != null && !imageBase64.isEmpty()) {
            v = LayoutInflater.from(getContext()).inflate(R.layout.item_floating_note_image, physicsHeader, false);
            ImageView iv = v.findViewById(R.id.iv_note_image);
            com.visiboard.app.utils.ImageCache.getInstance().loadBase64Image("note_" + doc.getId(), imageBase64, iv, R.drawable.ic_image_placeholder);
        } else {
            v = LayoutInflater.from(getContext()).inflate(R.layout.item_floating_note_text, physicsHeader, false);
            TextView tv = v.findViewById(R.id.tv_note_text);
            tv.setText(content != null ? content : "...");
            tv.setTextColor(0xFF000000); // Force Black Text
            
            // Random Pastel Colors
            int[] pastelColors = {
                0xFFFFF0F0, // Light Red
                0xFFF0F4FF, // Light Blue
                0xFFF0FFF4, // Light Green
                0xFFFFFDF0, // Light Yellow
                0xFFE6E6FA, // Lavender
                0xFFE0F7FA, // Cyan
                0xFFF8F8FF  // Ghost White
            };
            int randomColor = pastelColors[Math.abs(doc.getId().hashCode()) % pastelColors.length];
            
            if (v instanceof androidx.cardview.widget.CardView) {
                ((androidx.cardview.widget.CardView) v).setCardBackgroundColor(randomColor);
            }
        }
        
        // Random Rotation (-15 to 15 degrees)
        float randomRotation = (new java.util.Random().nextFloat() * 30) - 15;
        v.setRotation(randomRotation);
        
        // No click listener - cover notes are visual only in user profile
    
        return v;
    }
    
    private void setupAllNotesRecyclerView() {
        allNotesAdapter = new RecentNotesAdapter(note -> {
            // Handle note click - open details
            navigateToNote(note);
        });
        
        rvAllNotes.setLayoutManager(new LinearLayoutManager(getContext()));
        rvAllNotes.setAdapter(allNotesAdapter);
        
        // Add scroll listener for pagination
        rvAllNotes.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager != null && !isNotesLoading && !isLastNoteReached) {
                    int visibleItemCount = layoutManager.getChildCount();
                    int totalItemCount = layoutManager.getItemCount();
                    int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();
                    
                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                            && firstVisibleItemPosition >= 0
                            && totalItemCount >= NOTES_PAGE_SIZE) {
                        
                        // Load next page
                        // Re-determine constraints same as initial load
                         boolean showPrivate = targetUserId.equals(currentUserId);
                         List<String> allowedVisibilities = new ArrayList<>();
                         allowedVisibilities.add("public");
                         if (targetUserId.equals(currentUserId) || isFollowing) {
                             allowedVisibilities.add("followers");
                         }
                         
                        loadUserNotes(allowedVisibilities, showPrivate, false);
                    }
                }
            }
        });
    }

    private void loadUserNotes(List<String> allowedVisibilities, boolean showPrivate, boolean isFirstLoad) {
        if (isFirstLoad) {
            allNotesList.clear();
            accumulatedNotes.clear();
            allNotesAdapter.setNotes(new ArrayList<>()); // Clear adapter
            lastVisibleNote = null;
            isLastNoteReached = false;
            
            tvAllNotesHeader.setVisibility(View.VISIBLE);
            rvAllNotes.setVisibility(View.VISIBLE);
            tvNoNotes.setVisibility(View.GONE);
        }
        
        isNotesLoading = true;
        pbNotesLoading.setVisibility(View.VISIBLE);
        
        // Query all notes by user, filter visibility client-side
        // This handles legacy notes without visibility field (default to "public")
        com.google.firebase.firestore.Query query = db.collection("notes")
                .whereEqualTo("userId", targetUserId)
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING);
        
        if (lastVisibleNote != null) {
            query = query.startAfter(lastVisibleNote);
        }
        
        // Fetch more than page size to account for client-side filtering
        query.limit(NOTES_PAGE_SIZE * 2)
             .get()
             .addOnSuccessListener(querySnapshot -> {
                 if (!isAdded()) return;
                 isNotesLoading = false;
                 pbNotesLoading.setVisibility(View.GONE);
                 
                 if (querySnapshot.isEmpty()) {
                     isLastNoteReached = true;
                     if (isFirstLoad) {
                         tvNoNotes.setVisibility(View.VISIBLE);
                         rvAllNotes.setVisibility(View.GONE);
                     }
                     return;
                 }
                 
                 lastVisibleNote = querySnapshot.getDocuments().get(querySnapshot.size() - 1);
                 
                 List<com.visiboard.app.data.NearbyNote> newNotes = new ArrayList<>();
                 int includedCount = 0;
                 
                 for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot) {
                     // Check hidden status
                     boolean isHidden = doc.getBoolean("isHidden") != null && doc.getBoolean("isHidden");
                     if (isHidden && !targetUserId.equals(currentUserId)) continue;
                     
                     // Get visibility, defaulting to "public" for legacy notes without the field
                     String visibility = doc.getString("visibility");
                     if (visibility == null) visibility = "public";
                     
                     // Client-side visibility filtering
                     boolean canView;
                     if (targetUserId.equals(currentUserId)) {
                         // Viewing own profile - show all notes
                         canView = true;
                     } else if (showPrivate) {
                         // Has full access (following a private account)
                         canView = !visibility.equals("private");
                     } else {
                         // Check against allowed visibilities
                         canView = allowedVisibilities.contains(visibility);
                     }
                     
                     if (!canView) continue;
                     
                     // Limit to page size
                     if (includedCount >= NOTES_PAGE_SIZE) break;
                     includedCount++;
                     
                     // Create NearbyNote object (reusing existing data class for adapter)
                     com.visiboard.app.data.NearbyNote note = new com.visiboard.app.data.NearbyNote();
                     note.setId(doc.getId());
                     note.setText(doc.getString("text") != null ? doc.getString("text") : doc.getString("note"));
                     note.setImageBase64(doc.getString("imageBase64"));
                     note.setTimestamp(doc.getLong("timestamp") != null ? doc.getLong("timestamp") : 0);
                     note.setUserId(doc.getString("userId"));
                     note.setUserName(doc.getString("userName"));
                     note.setUserProfilePic(doc.getString("userProfilePic"));
                     note.setLikesCount(doc.getLong("likeCount") != null ? doc.getLong("likeCount").intValue() : 0);
                     note.setCommentsCount(doc.getLong("commentCount") != null ? doc.getLong("commentCount").intValue() : 0);
                     note.setDistance(-1); // Not needed for profile
                     
                     newNotes.add(note);
                     allNotesList.add(doc);
                 }
                 
                 if (querySnapshot.size() < NOTES_PAGE_SIZE * 2) {
                     isLastNoteReached = true;
                 }
                 
                 updateAdapterInternal(newNotes);
                 
                 // Recursive Load Logic:
                 // If we fetched data BUT client-side filtering removed everything,
                 // AND we haven't reached the end, trigger the next load automatically.
                 if (newNotes.isEmpty() && !querySnapshot.isEmpty() && !isLastNoteReached) {
                     loadUserNotes(allowedVisibilities, showPrivate, false);
                 }
                 
                 if (allNotesList.isEmpty() && isFirstLoad) {
                      tvNoNotes.setVisibility(View.VISIBLE);
                      rvAllNotes.setVisibility(View.GONE);
                 }
             })
             .addOnFailureListener(e -> {
                 if (!isAdded()) return;
                 isNotesLoading = false;
                 pbNotesLoading.setVisibility(View.GONE);
                 Log.e(TAG, "Error loading notes", e);
                 if (isFirstLoad) {
                      tvNoNotes.setText("Error loading notes");
                      tvNoNotes.setVisibility(View.VISIBLE);
                 }
             });
    }

    private List<com.visiboard.app.data.NearbyNote> accumulatedNotes = new ArrayList<>();

    private void updateAdapterInternal(List<com.visiboard.app.data.NearbyNote> newNotes) {
        if (accumulatedNotes == null) accumulatedNotes = new ArrayList<>();
        int startPos = accumulatedNotes.size();
        accumulatedNotes.addAll(newNotes);
        
        // If adapter supports adding, great. If not, full refresh.
        // Assuming full refresh support for now or I'll check adapter.
        if (allNotesAdapter != null) {
            allNotesAdapter.setNotes(accumulatedNotes); 
            // Optimally: allNotesAdapter.notifyItemRangeInserted(startPos, newNotes.size());
            // But setNotes likely calls notifyDataSetChanged.
        }
    }

    private void addDecorativeSocialIcons() {
        if (physicsHeader == null || getContext() == null) return;
        
        // Social icons to display (heart, like, comment, share, bookmark, send)
        int[] iconResIds = {
            R.drawable.ic_heart,
            R.drawable.ic_heart_outline,
            R.drawable.ic_like,
            R.drawable.ic_comment,
            R.drawable.ic_share,
            R.drawable.ic_bookmark,
            R.drawable.ic_send
        };
        
        // Add 8-12 decorative icons randomly distributed
        java.util.Random random = new java.util.Random();
        int iconCount = 8 + random.nextInt(5); // 8-12 icons
        
        for (int i = 0; i < iconCount; i++) {
            ImageView iconView = (ImageView) LayoutInflater.from(getContext())
                .inflate(R.layout.item_floating_social_icon, physicsHeader, false);
            
            // Make icons non-clickable so they don't interfere with note clicks
            iconView.setClickable(false);
            iconView.setFocusable(false);
            
            // Random icon from the list
            int iconRes = iconResIds[random.nextInt(iconResIds.length)];
            iconView.setImageResource(iconRes);
            
            // Random size variation (24-40dp)
            int size = 24 + random.nextInt(17); // 24-40dp
            ViewGroup.LayoutParams params = iconView.getLayoutParams();
            params.width = (int) (size * getResources().getDisplayMetrics().density);
            params.height = (int) (size * getResources().getDisplayMetrics().density);
            iconView.setLayoutParams(params);
            
            // Random alpha (0.25-0.5 for subtle effect)
            float alpha = 0.25f + random.nextFloat() * 0.25f;
            iconView.setAlpha(alpha);
            
            // Random initial rotation
            iconView.setRotation(random.nextFloat() * 360);
            
            // Mark as decorative and add to physics header
            physicsHeader.addFloatingView(iconView, true);
        }
    }

    private void loadFavouriteNotesList(List<String> noteIds) {
        rvFavouriteNotes.setLayoutManager(new LinearLayoutManager(getContext()));
        List<NearbyNote> notes = new ArrayList<>();
        RecentNotesAdapter adapter = new RecentNotesAdapter(note -> navigateToNote(note));
        rvFavouriteNotes.setAdapter(adapter);
        rvFavouriteNotes.setVisibility(View.VISIBLE);
        
        for (String noteId : noteIds) {
            db.collection("notes").document(noteId).get()
                .addOnSuccessListener(doc -> {
                    if (!isAdded() || !doc.exists()) return;
                    
                    // Check visibility permissions (unless viewing own profile)
                    if (!targetUserId.equals(currentUserId)) {
                        String visibility = doc.getString("visibility");
                        if (visibility == null) visibility = "public";
                        
                        boolean isHidden = doc.getBoolean("isHidden") != null && doc.getBoolean("isHidden");
                        if (isHidden) return;
                        
                        if ("private".equals(visibility)) return;
                        if ("followers".equals(visibility) && !hasAccessToPrivate) return;
                    }
                    
                    NearbyNote note = new NearbyNote();
                    note.setId(doc.getId());
                    String noteText = doc.getString("text");
                    if (noteText == null) noteText = doc.getString("note");
                    note.setText(noteText);
                    note.setImageBase64(doc.getString("imageBase64"));
                    
                    String summary = doc.getString("summary");
                    if (summary == null && noteText != null && noteText.length() > 100) {
                        summary = noteText.substring(0, 100) + "...";
                    }
                    note.setSummary(summary);
                    
                    Double lat = doc.getDouble("lat");
                    Double lng = doc.getDouble("lon");
                    if (lat != null && lng != null) {
                        note.setLat(lat);
                        note.setLng(lng);
                    }
                    
                    Long timestamp = doc.getLong("timestamp");
                    note.setTimestamp(timestamp != null ? timestamp : 0);
                    
                    Long likes = doc.getLong("likeCount");
                    if (likes == null) likes = doc.getLong("likesCount");
                    note.setLikesCount(likes != null ? likes.intValue() : 0);
                    
                    notes.add(note);
                    adapter.setNotes(notes);
                });
        }
    }

    private void navigateToNote(NearbyNote note) {
        if (note.getLat() == 0 && note.getLng() == 0) {
            // Fetch location first
            db.collection("notes").document(note.getId()).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Double lat = doc.getDouble("lat");
                        Double lng = doc.getDouble("lon");
                        if (lat != null && lng != null) {
                            navigateToMap(lat, lng, note.getId());
                        }
                    }
                });
        } else {
            navigateToMap(note.getLat(), note.getLng(), note.getId());
        }
    }

    private void navigateToMap(double lat, double lng, String noteId) {
        // Close any open note windows when navigating away
        Bundle result = new Bundle();
        result.putBoolean("close_note_window", true);
        getParentFragmentManager().setFragmentResult("close_note_window", result);
        
        Bundle args = new Bundle();
        args.putDouble("target_lat", lat);
        args.putDouble("target_lng", lng);
        args.putString("target_note_id", noteId);
        args.putBoolean("open_note_window", true);
        Navigation.findNavController(requireView()).navigate(R.id.mapFragment, args);
    }

    private void renderLinks(List<Map<String, String>> links) {
        if (llLinksContainer == null) return;
        
        llLinksContainer.removeAllViews();
        
        if (links == null || links.isEmpty()) {
            svLinks.setVisibility(View.GONE);
            return;
        }
        
        for (Map<String, String> link : links) {
            // Support both old format (platform/url) and new format (name/url)
            String name = link.get("name");
            if (name == null) name = link.get("platform");
            String url = link.get("url");
            
            if (name == null || url == null || url.isEmpty()) continue;
            
            // Create link button similar to ProfileFragment
            LinearLayout linkBtn = new LinearLayout(requireContext());
            linkBtn.setOrientation(LinearLayout.HORIZONTAL);
            linkBtn.setBackgroundResource(R.drawable.bg_capsule_light);
            linkBtn.setPadding((int)(32 * getResources().getDisplayMetrics().density), 
                              (int)(16 * getResources().getDisplayMetrics().density), 
                              (int)(32 * getResources().getDisplayMetrics().density), 
                              (int)(16 * getResources().getDisplayMetrics().density));
            linkBtn.setGravity(android.view.Gravity.CENTER);
            
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd((int)(16 * getResources().getDisplayMetrics().density));
            linkBtn.setLayoutParams(params);

            TextView tv = new TextView(requireContext());
            tv.setText(name);
            tv.setTextColor(getResources().getColor(R.color.text_primary, null));
            tv.setTextSize(12);
            tv.setTypeface(null, android.graphics.Typeface.BOLD);
            
            linkBtn.addView(tv);
            
            linkBtn.setOnClickListener(v -> {
                try {
                    String finalUrl = url;
                    if (!finalUrl.startsWith("http://") && !finalUrl.startsWith("https://")) {
                        finalUrl = "https://" + finalUrl;
                    }
                    android.content.Intent browserIntent = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(finalUrl));
                    startActivity(browserIntent);
                } catch (Exception e) {
                    Toast.makeText(requireContext(), "Invalid URL", Toast.LENGTH_SHORT).show();
                }
            });
            
            llLinksContainer.addView(linkBtn);
        }
        
        // Make sure links container is visible
        if (llLinksContainer.getChildCount() > 0) {
            svLinks.setVisibility(View.VISIBLE);
        }
    }

    private int getTierIcon(String tier) {
        switch (tier.toLowerCase()) {
            case "bronze": return R.drawable.ic_bronze;
            case "silver": return R.drawable.ic_silver;
            case "gold": return R.drawable.ic_gold;
            case "platinum": return R.drawable.ic_platinum;
            case "diamond": return R.drawable.ic_diamond;
            default: return R.drawable.ic_default_tier;
        }
    }

    private void hideLoading() {
        if (loadingOverlay != null) {
            loadingOverlay.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction(() -> loadingOverlay.setVisibility(View.GONE))
                .start();
        }
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(false);
        }
    }
}
