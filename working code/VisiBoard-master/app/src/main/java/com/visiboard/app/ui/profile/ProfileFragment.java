package com.visiboard.app.ui.profile;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import android.widget.LinearLayout;
import com.visiboard.app.R;
import com.visiboard.app.data.NearbyNote;
import com.visiboard.app.ui.auth.LoginActivity;
import com.visiboard.app.utils.ThemeManager;
import com.visiboard.app.utils.ImageCache;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class ProfileFragment extends Fragment {

    private CircleImageView profileImage;
    private TextView nameText, emailText, tvTotalNotes, tvTotalLikes, tvNoRecentNotes, tvMilestone, tvMilestoneProgress, tvLocation;
    private TextView tvFollowersCount, tvFollowingCount;
    private TextView tvBio, tvWork, tvEducation, tvRelationship, tvHometown, tvBirthday, tvJoinedDate; // Public Details
    private ImageView ivTierIcon, shineView;
    private android.view.View rowWork, rowEducation, rowRelationship, rowHometown, rowBirthday;
    private android.widget.LinearLayout llLinksContainer;
    private ProgressBar progressMilestone;
    private android.view.View loadingOverlay, skeletonView;
    private RecyclerView rvRecentNotes;
    private RecentNotesAdapter recentNotesAdapter;
    private SwipeRefreshLayout swipeRefreshLayout;
    private LinearLayout locationContainer;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private ProfileViewModel viewModel;
    
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
    
    private long lastThemeToggleTime = 0;
    private static final long THEME_TOGGLE_COOLDOWN = 2000;

    private com.visiboard.app.ui.profile.FloatingPhysicsLayout physicsHeader;
    
    @Nullable
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getParentFragmentManager() != null) {
            getParentFragmentManager().setFragmentResultListener("details_updated", this, (requestKey, result) -> {
                refreshData();
                safeToast("Profile Updated");
            });
            
            getParentFragmentManager().setFragmentResultListener("fav_notes_updated", this, (requestKey, result) -> {
                refreshData(); 
            });
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        profileImage = view.findViewById(R.id.profileImage);
        nameText = view.findViewById(R.id.profileName);
        emailText = view.findViewById(R.id.profileEmail);
        tvTotalNotes = view.findViewById(R.id.tv_total_notes);
        tvTotalLikes = view.findViewById(R.id.tv_total_likes);
        tvNoRecentNotes = view.findViewById(R.id.tv_no_recent_notes);
        rvRecentNotes = view.findViewById(R.id.rv_recent_notes);
        tvMilestone = view.findViewById(R.id.tv_milestone);
        tvMilestoneProgress = view.findViewById(R.id.tv_milestone_progress);
        tvLocation = view.findViewById(R.id.tv_location);
        tvFollowersCount = view.findViewById(R.id.tv_followers_count);
        tvFollowingCount = view.findViewById(R.id.tv_following_count);
        ivTierIcon = view.findViewById(R.id.iv_tier_icon);
        progressMilestone = view.findViewById(R.id.progress_milestone);
        // logoutIcon removed
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        locationContainer = view.findViewById(R.id.location_container);
        loadingOverlay = view.findViewById(R.id.loading_overlay);
        skeletonView = view.findViewById(R.id.skeleton_view);
        shineView = view.findViewById(R.id.shine_view);
        
        // Bind Physics Header
        physicsHeader = view.findViewById(R.id.physics_header_container);
        
        // New Detail Views
        tvBio = view.findViewById(R.id.tv_bio);
        tvWork = view.findViewById(R.id.tv_work);
        tvEducation = view.findViewById(R.id.tv_education);
        tvRelationship = view.findViewById(R.id.tv_relationship);
        tvHometown = view.findViewById(R.id.tv_hometown);
        tvBirthday = view.findViewById(R.id.tv_birthday);
        tvJoinedDate = view.findViewById(R.id.tv_joined_date);
        
        rowWork = view.findViewById(R.id.row_work);
        rowEducation = view.findViewById(R.id.row_education);
        rowRelationship = view.findViewById(R.id.row_relationship);
        rowHometown = view.findViewById(R.id.row_hometown);
        rowBirthday = view.findViewById(R.id.row_birthday);
        
        llLinksContainer = view.findViewById(R.id.ll_links_container);
        
        // Listen for favorite selection updates
        getParentFragmentManager().setFragmentResultListener("fav_notes_updated", getViewLifecycleOwner(), (key, bundle) -> {
            if (bundle.getBoolean("refresh_favs")) {
                loadUserData(); // Reloads user data which fetches favourites
            }
        });

        swipeRefreshLayout.setOnRefreshListener(this::refreshData);
        swipeRefreshLayout.setColorSchemeResources(R.color.primary);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);
        
        // Setup RecyclerView
        rvRecentNotes.setLayoutManager(new LinearLayoutManager(getContext()));
        // rvRecentNotes.setHasFixedSize(true); // Removed to allow dynamic height inside NestedScrollView
        recentNotesAdapter = new RecentNotesAdapter(note -> {
            navigateToNoteOnMap(note.getLat(), note.getLng(), note.getId());
        });
        rvRecentNotes.setAdapter(recentNotesAdapter);
        
        startShineAnimation();

        setupViewModelObservers();
        
        // Only load data if needed
        if (viewModel.shouldRefreshData()) {
            loadUserData();
            loadUserStats();
            updateUserLocation();
        } else {
            // If data is already there, hide skeleton immediately
            hideSkeleton();
        }

        profileImage.setOnClickListener(v -> pickImage());
        profileImage.setOnClickListener(v -> pickImage());
        // logoutIcon listener removed
        view.findViewById(R.id.settings_icon).setOnClickListener(v -> {
            startActivity(new Intent(getActivity(), com.visiboard.app.ui.settings.SettingsActivity.class));
        });
        view.findViewById(R.id.btn_view_all_notes).setOnClickListener(v -> showAllNotesDialog());
        nameText.setOnClickListener(v -> showEditNameDialog());
        
        // Create Note Button
        Button btnCreateNote = view.findViewById(R.id.btn_create_first_note);
        if (btnCreateNote != null) {
            btnCreateNote.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
                Navigation.findNavController(v).navigate(R.id.mapFragment);
            });
        }
        
        // Theme toggle
        ImageView themeToggle = view.findViewById(R.id.theme_toggle_icon);
        ThemeManager themeManager = ThemeManager.getInstance(requireContext());
        updateThemeIcon(themeToggle, themeManager.isDarkMode());
        
        themeToggle.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastThemeToggleTime < THEME_TOGGLE_COOLDOWN) {
                Toast.makeText(getContext(), "Please wait...", Toast.LENGTH_SHORT).show();
                return;
            }
            
            lastThemeToggleTime = currentTime;
            themeToggle.setEnabled(false);
            
            boolean newMode = !themeManager.isDarkMode();
            themeManager.saveThemePreference(newMode);
            updateThemeIcon(themeToggle, newMode);
            
            v.postDelayed(() -> {
                if (getActivity() != null) {
                    Log.d("ProfileFragment", "Starting theme transition sequence");
                    
                    showThemeTransitionDialog(newMode);
                    
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (getActivity() == null) return;
                        
                        Log.d("ProfileFragment", "Navigating to MapFragment");
                        try {
                            Navigation.findNavController(requireView()).navigate(R.id.mapFragment);
                        } catch (Exception e) {
                            Log.e("ProfileFragment", "Navigation failed", e);
                        }
                        
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            if (getActivity() != null) {
                                Log.d("ProfileFragment", "Restarting app to apply theme");
                                Intent intent = new Intent(getActivity(), com.visiboard.app.MainActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                getActivity().finish();
                                getActivity().overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                            }
                        }, 500);
                        
                    }, 1000);
                }
            }, 100);
        });
        
        // Followers/Following click listeners
        view.findViewById(R.id.followers_section).setOnClickListener(v -> showFollowersDialog(false));
        view.findViewById(R.id.following_section).setOnClickListener(v -> showFollowersDialog(true));
        
        // Make milestone card clickable
        view.findViewById(R.id.milestone_card).setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
            showRankRoadmapDialog();
            showRankRoadmapDialog();
        });
        
        // Detail Edit Listeners
        tvBio.setOnClickListener(v -> showEditBioDialog());
        view.findViewById(R.id.btn_edit_details).setOnClickListener(v -> showEditDetailsDialog());
        view.findViewById(R.id.btn_add_link).setOnClickListener(v -> showAddLinkDialog());

        return view;
    }
    
    private void startShineAnimation() {
        if (shineView == null) return;
        
        // Wait for layout to get width if needed, or just animate translate
        shineView.post(() -> {
            ValueAnimator animator = ValueAnimator.ofFloat(-200f, 1000f);
            animator.setDuration(3000);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setRepeatMode(ValueAnimator.RESTART);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(animation -> {
                if (shineView != null) {
                    shineView.setTranslationX((float) animation.getAnimatedValue());
                }
            });
            animator.start();
        });
    }

    private void hideSkeleton() {
        if (skeletonView != null && skeletonView.getVisibility() == View.VISIBLE) {
            skeletonView.animate()
                .alpha(0f)
                .setDuration(400)
                .withEndAction(() -> skeletonView.setVisibility(View.GONE))
                .start();
        }
    }
    
    private void setupViewModelObservers() {
        viewModel.getUserName().observe(getViewLifecycleOwner(), name -> {
            if (name != null) nameText.setText(name);
        });
        
        viewModel.getUserEmail().observe(getViewLifecycleOwner(), email -> {
            if (email != null) emailText.setText(email);
        });
        
        viewModel.getProfilePicBase64().observe(getViewLifecycleOwner(), base64 -> {
            if (base64 != null && !base64.isEmpty()) {
                ImageCache.getInstance().loadBase64Image("profile_pic", base64, profileImage, R.drawable.ic_profile);
            }
        });
        
        viewModel.getLocation().observe(getViewLifecycleOwner(), location -> {
            if (location != null && !location.isEmpty()) {
                tvLocation.setText(location);
                locationContainer.setVisibility(View.VISIBLE);
            }
        });
        
        viewModel.getTotalNotes().observe(getViewLifecycleOwner(), total -> {
            if (total != null) animateTextViewCount(tvTotalNotes, total);
        });
        
        viewModel.getTotalLikes().observe(getViewLifecycleOwner(), total -> {
            Log.d("ProfileFragment", "Observer: Total Likes updated to: " + total);
            if (total != null) animateTextViewCount(tvTotalLikes, total);
        });
        
        viewModel.getFollowersCount().observe(getViewLifecycleOwner(), count -> {
            if (count != null) animateTextViewCount(tvFollowersCount, count);
        });
        
        viewModel.getFollowingCount().observe(getViewLifecycleOwner(), count -> {
            if (count != null) animateTextViewCount(tvFollowingCount, count);
        });
        
        viewModel.getCurrentTier().observe(getViewLifecycleOwner(), tier -> {
            if (tier != null) tvMilestone.setText(tier);
        });
        
        viewModel.getTierProgress().observe(getViewLifecycleOwner(), progress -> {
            if (progress != null) {
                // Determine if we need to animate (e.g. initial load or change)
                if (progressMilestone.getProgress() != progress) {
                    ObjectAnimator animation = ObjectAnimator.ofInt(progressMilestone, "progress", progress);
                    animation.setDuration(1200); // 1.2 second smooth fill
                    animation.setInterpolator(new AccelerateDecelerateInterpolator());
                    animation.start();
                } else {
                    progressMilestone.setProgress(progress);
                }
            }
        });
        
        viewModel.getTierMax().observe(getViewLifecycleOwner(), max -> {
            if (max != null) progressMilestone.setMax(max);
        });
        
        viewModel.getTierProgressText().observe(getViewLifecycleOwner(), text -> {
            if (text != null) tvMilestoneProgress.setText(text);
        });
        
        viewModel.getTierIconRes().observe(getViewLifecycleOwner(), res -> {
            if (res != null) ivTierIcon.setImageResource(res);
        });
        
        viewModel.getRecentNotes().observe(getViewLifecycleOwner(), notes -> {
            if (notes != null && !notes.isEmpty()) {
                rvRecentNotes.setVisibility(View.VISIBLE);
                tvNoRecentNotes.setVisibility(View.GONE);
                recentNotesAdapter.setNotes(notes);
            } else {
                rvRecentNotes.setVisibility(View.GONE);
                tvNoRecentNotes.setVisibility(View.VISIBLE);
                Button btnCreateNote = getView().findViewById(R.id.btn_create_first_note);
                if (btnCreateNote != null) btnCreateNote.setVisibility(View.VISIBLE);
            }
            // Once we have notes (or empty state), we assume main processing is done
             // In refresh, we might want to hide skeleton earlier if user data loads fast
             hideSkeleton();
        });
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
                            locationContainer.setVisibility(View.VISIBLE);
                            
                            // Save to Firestore
                            String uid = auth.getCurrentUser().getUid();
                            java.util.Map<String, Object> updates = new java.util.HashMap<>();
                            updates.put("lastKnownLocation", locationText);
                            updates.put("lat", location.getLatitude());
                            updates.put("lng", location.getLongitude());

                            db.collection("users").document(uid)
                                    .update(updates)
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

        viewModel.setUserEmail(user.getEmail());

        String uid = user.getUid();
        
        // Try cache first for instant display
        db.collection("users").document(uid)
                .get(com.google.firebase.firestore.Source.CACHE)
                .addOnSuccessListener(cachedDoc -> {
                    if (cachedDoc.exists() && isAdded()) {
                        updateUserDataFromDoc(cachedDoc);
                    }
                    // Then get fresh data in background
                    db.collection("users").document(uid).get()
                            .addOnSuccessListener(doc -> {
                                if (isAdded()) updateUserDataFromDoc(doc);
                            })
                            .addOnFailureListener(e -> 
                                    Log.e("ProfileFragment", "Error loading user data: " + e.getMessage()));
                })
                .addOnFailureListener(e -> {
                    // Cache miss, load from server
                    db.collection("users").document(uid).get()
                            .addOnSuccessListener(doc -> {
                                if (isAdded()) updateUserDataFromDoc(doc);
                            })
                            .addOnFailureListener(err -> {
                                    Log.e("ProfileFragment", "Error loading user data: " + err.getMessage());
                                    loadingOverlay.setVisibility(View.GONE);
                            });
                });
    }

    private void refreshData() {
        viewModel.invalidateCache();
        loadUserData();
        loadUserStats();
    }
    
    private void safeToast(String message) {
        if (getContext() != null && isAdded()) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }
    
    private void updateUserDataFromDoc(com.google.firebase.firestore.DocumentSnapshot doc) {
        if (!isAdded() || !doc.exists()) return;
        
        String name = doc.getString("name");
        if (name != null) viewModel.setUserName(name);

        String location = doc.getString("lastKnownLocation");
        if (location != null) viewModel.setLocation(location);

        Long followersCount = doc.getLong("followersCount");
        if (followersCount != null) viewModel.setFollowersCount(followersCount);
        
        Long followingCount = doc.getLong("followingCount");
        if (followingCount != null) viewModel.setFollowingCount(followingCount);

        if (followingCount != null) viewModel.setFollowingCount(followingCount);

        String pic = doc.getString("profilePic");
        if (pic != null && !pic.isEmpty()) viewModel.setProfilePicBase64(pic);
        
        // --- Load New Details ---
        String bio = doc.getString("bio");
        updateBio(bio);
        
        String work = doc.getString("work");
        updateDetailRow(rowWork, tvWork, work, "Works at ");
        
        String education = doc.getString("education");
        updateDetailRow(rowEducation, tvEducation, education, "Studied at ");
        
        String relationship = doc.getString("relationship");
        updateDetailRow(rowRelationship, tvRelationship, relationship, "");
        
        String hometown = doc.getString("hometown");
        updateDetailRow(rowHometown, tvHometown, hometown, "From ");
        
        String birthday = doc.getString("birthday");
        updateDetailRow(rowBirthday, tvBirthday, birthday, "Born on ");
        
        // Joined Date
        Long createdAt = doc.getLong("createdAt");
        if (createdAt != null && tvJoinedDate != null) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault());
            String joined = "Joined in " + sdf.format(new java.util.Date(createdAt));
            tvJoinedDate.setText(joined);
            tvJoinedDate.setVisibility(View.VISIBLE);
        }
        
        // Load Links
        List<java.util.Map<String, String>> links = (List<java.util.Map<String, String>>) doc.get("socialLinks");
        renderLinks(links);
        
        // Load Floating Notes (Favorites)
        List<String> favs = (List<String>) doc.get("favouriteNotes");
        if (favs == null || favs.isEmpty()) {
            if (physicsHeader != null) showEmptyFloatingState();
        } else {
            if (physicsHeader != null) fetchNotesAndRender(favs);
        }
    }



    private void loadUserStats() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            swipeRefreshLayout.setRefreshing(false);
            return;
        }

        String uid = user.getUid();
        
        // First load current user's info for the notes
        db.collection("users").document(uid).get()
            .addOnSuccessListener(currentUserDoc -> {
                if (!isAdded()) return;
                String currentUserName = currentUserDoc.getString("name");
                String currentUserProfilePic = currentUserDoc.getString("profilePic");
                
                // Query global notes collection for current user's notes
                db.collection("notes")
                    .whereEqualTo("userId", uid)
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        if (!isAdded()) {
                            swipeRefreshLayout.setRefreshing(false);
                            return;
                        }
                        
                        int totalNotes = querySnapshot.size();
                        viewModel.setTotalNotes(totalNotes);

                        // Calculate total likes across all notes
                        int totalLikes = 0;
                        List<NearbyNote> recentNotes = new ArrayList<>();
                        
                        if (!querySnapshot.isEmpty()) {
                            List<com.google.firebase.firestore.DocumentSnapshot> docs = new ArrayList<>(querySnapshot.getDocuments());
                            
                            // Sort by timestamp descending
                            docs.sort((d1, d2) -> {
                                Long t1 = d1.getLong("timestamp");
                                Long t2 = d2.getLong("timestamp");
                                if (t1 == null) t1 = 0L;
                                if (t2 == null) t2 = 0L;
                                return t2.compareTo(t1);
                            });
                            
                            // Get up to 3 most recent notes
                            int limit = Math.min(3, docs.size());
                            for (int i = 0; i < limit; i++) {
                                com.google.firebase.firestore.DocumentSnapshot doc = docs.get(i);
                                
                                NearbyNote note = new NearbyNote();
                                note.setId(doc.getId());
                                
                                String noteText = doc.getString("text");
                                if (noteText == null) noteText = doc.getString("note");
                                note.setText(noteText);
                                
                                String summary = doc.getString("summary");
                                if (summary == null && noteText != null && noteText.length() > 100) {
                                    summary = noteText.substring(0, 100) + "...";
                                }
                                note.setSummary(summary);
                                
                                // Use current user's info
                                note.setUserName(currentUserName != null ? currentUserName : "You");
                                note.setUserProfilePic(currentUserProfilePic);
                                note.setUserId(uid);
                                
                                Double lat = doc.getDouble("lat");
                                Double lon = doc.getDouble("lon");
                                if (lat != null && lon != null) {
                                    note.setLat(lat);
                                    note.setLng(lon);
                                }
                                
                                Long timestamp = doc.getLong("timestamp");
                                note.setTimestamp(timestamp != null ? timestamp : 0);
                                
                                // Get likes count - check both field names
                                Long likesCount = doc.getLong("likeCount");
                                if (likesCount == null) likesCount = doc.getLong("likesCount");
                                note.setLikesCount(likesCount != null ? likesCount.intValue() : 0);
                                
                                // Get Image
                                String imageBase64 = doc.getString("imageBase64");
                                note.setImageBase64(imageBase64);
                                
                                recentNotes.add(note);
                            }
                        }
                            
                        // Calculate total likes from notes (Source of Truth)
                        if (!querySnapshot.isEmpty()) {
                            for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot.getDocuments()) {
                                Long likeCount = doc.getLong("likeCount");
                                if (likeCount == null) likeCount = doc.getLong("likesCount");
                                if (likeCount != null) {
                                    totalLikes += likeCount.intValue();
                                }
                            }
                        }
                        
                        // Self-healing: Update the user's totalLikes to match the actual calculated value
                        // This fixes the drift between the cached value and actual sum
                        if (currentUserDoc.getLong("totalLikes") == null || currentUserDoc.getLong("totalLikes") != totalLikes) {
                            Log.d("ProfileFragment", "Self-healing totalLikes. Calculated: " + totalLikes + ", Stored: " + currentUserDoc.getLong("totalLikes"));
                            db.collection("users").document(uid)
                                .update("totalLikes", totalLikes)
                                .addOnFailureListener(e -> Log.e("ProfileFragment", "Failed to sync totalLikes: " + e.getMessage()));
                        }

                        Log.d("ProfileFragment", "Setting VM Total Likes to: " + totalLikes);
                        viewModel.setTotalLikes(totalLikes);
                        viewModel.setRecentNotes(recentNotes);

                            // Determine tier and progress
                         int tierIndex = -1;
                         for (int i = 0; i < milestones.length; i++) {
                             if (totalLikes >= milestones[i]) tierIndex = i;
                         }

                         String currentTier;
                         String tierText;
                         int iconRes;
                         int maxProgress;
                         int currentProgress;
                         String progressText;

                         if (tierIndex == -1) {
                             currentTier = "None";
                             tierText = "No Tier Yet";
                             iconRes = R.drawable.ic_default_tier;
                             maxProgress = milestones[0];
                             currentProgress = totalLikes;
                             progressText = totalLikes + " / " + milestones[0] + " likes to Bronze";
                         } else if (tierIndex < milestones.length - 1) {
                             currentTier = milestoneTiers[tierIndex];
                             tierText = "Current Tier: " + currentTier;
                             iconRes = milestoneIcons[tierIndex];
                             int nextGoal = milestones[tierIndex + 1];
                             maxProgress = nextGoal;
                             currentProgress = totalLikes;
                             progressText = totalLikes + " / " + nextGoal + " likes to " + milestoneTiers[tierIndex + 1];
                         } else {
                             currentTier = "Platinum";
                             tierText = "Max Tier: Platinum";
                             iconRes = milestoneIcons[milestoneIcons.length - 1];
                             maxProgress = milestones[milestones.length - 1];
                             currentProgress = milestones[milestones.length - 1];
                             progressText = "Maxed Out";
                         }
                         
                         viewModel.setCurrentTier(tierText);
                         viewModel.setTierIconRes(iconRes);
                         viewModel.setTierMax(maxProgress);
                         viewModel.setTierProgress(currentProgress);
                         viewModel.setTierProgressText(progressText);
                         
                         viewModel.setDataLoaded(true);
                         viewModel.setDataLoaded(true);
                         swipeRefreshLayout.setRefreshing(false);
                         loadingOverlay.setVisibility(View.GONE);
                         
                         // Haptic feedback on refresh complete
                         if (swipeRefreshLayout.isRefreshing()) {
                            swipeRefreshLayout.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
                         }

                         // Update tier in database in background
                         db.collection("users").document(uid)
                                 .update("currentTier", currentTier)
                                 .addOnFailureListener(e ->
                                         Log.e("ProfileFragment", "Tier update failed: " + e.getMessage())
                                 );
                     })
                     .addOnFailureListener(e -> {
                         Log.e("ProfileFragment", "Error loading stats: " + e.getMessage());
                         safeToast("Failed to load statistics");
                         swipeRefreshLayout.setRefreshing(false);
                         loadingOverlay.setVisibility(View.GONE);
                     });
             })
             .addOnFailureListener(e -> {
                 Log.e("ProfileFragment", "Error loading user info: " + e.getMessage());
                 swipeRefreshLayout.setRefreshing(false);
                 loadingOverlay.setVisibility(View.GONE);
             });
     }
     
     private void animateTextViewCount(TextView textView, long endValue) {
        ValueAnimator animator = ValueAnimator.ofInt(0, (int) endValue);
        animator.setDuration(1200);
        animator.setInterpolator(new android.view.animation.DecelerateInterpolator());
        animator.addUpdateListener(animation -> textView.setText(String.valueOf(animation.getAnimatedValue())));
        animator.start();
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
                safeToast("Failed to load image");
            }
        }
    }

    private void updateProfilePic(String base64Image) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        String uid = user.getUid();
        db.collection("users").document(uid).update("profilePic", base64Image)
                .addOnSuccessListener(unused -> {
                    viewModel.setProfilePicBase64(base64Image);
                    safeToast("Profile picture updated");
                })
                .addOnFailureListener(e -> safeToast("Failed to update: " + e.getMessage()));
    }

    private void showEditNameDialog() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_edit_username, null);
        com.google.android.material.textfield.TextInputEditText input = dialogView.findViewById(R.id.et_username_input);
        Button btnSave = dialogView.findViewById(R.id.btn_save);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        
        String currentName = nameText.getText().toString();
        if (!currentName.isEmpty() && !currentName.equals("User Name")) {
            input.setText(currentName);
        }

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(getContext())
                .setView(dialogView)
                .create();
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        
        btnSave.setOnClickListener(v -> {
            String newName = input.getText().toString().trim();
            if (newName.isEmpty()) {
                safeToast("Name cannot be empty");
                return;
            }
            
            if (newName.length() > 30) {
                safeToast("Name too long (max 30 characters)");
                return;
            }
            
            updateUserName(newName);
            dialog.dismiss();
        });
        
        dialog.show();
    }
    
    private void showAboutDialog() {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_about, null);
        TextView tvThankYou = dialogView.findViewById(R.id.tv_thank_you);
        com.google.android.material.button.MaterialButton btnGithub = dialogView.findViewById(R.id.btn_github);
        
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(getContext())
            .setView(dialogView)
            .create();
            
        btnGithub.setOnClickListener(v -> {
            try {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/narukami00/VisiBoard"));
                startActivity(browserIntent);
            } catch (Exception e) {
                safeToast("Could not open link");
            }
        });
        
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            
        dialog.show();
        
        // Thank you animation
        tvThankYou.animate()
            .alpha(1f)
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(1000)
            .withEndAction(() -> 
                tvThankYou.animate().scaleX(1f).scaleY(1f).setDuration(500).start()
            )
            .start();
    }


    private void updateUserName(String newName) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        String uid = user.getUid();
        db.collection("users").document(uid).update("name", newName)
                .addOnSuccessListener(unused -> {
                    viewModel.setUserName(newName);
                    safeToast("Name updated successfully");
                })
                .addOnFailureListener(e -> safeToast("Failed to update name: " + e.getMessage()));
    }

    // --- Bio, Details, Links Logic ---

    private void updateBio(String bio) {
        if (bio != null && !bio.isEmpty()) {
            tvBio.setText(bio);
            tvBio.setTextColor(getResources().getColor(R.color.text_primary));
        } else {
            tvBio.setText("Tap to add bio...");
            tvBio.setTextColor(getResources().getColor(R.color.text_secondary));
        }
    }

    private void updateDetailRow(View row, TextView tv, String value, String prefix) {
        if (value != null && !value.isEmpty()) {
            row.setVisibility(View.VISIBLE);
            tv.setText(prefix + value);
        } else {
            row.setVisibility(View.GONE);
        }
    }

    private void renderLinks(List<java.util.Map<String, String>> links) {
        // Clear existing links (keep only the "Add Link" button, index 0)
        int childCount = llLinksContainer.getChildCount();
        if (childCount > 1) {
            llLinksContainer.removeViews(1, childCount - 1);
        }

        if (links != null) {
            for (java.util.Map<String, String> link : links) {
                String name = link.get("name");
                String url = link.get("url");
                addLinkView(name, url);
            }
        }
    }

    private void addLinkView(String name, String url) {
        LinearLayout linkBtn = new LinearLayout(getContext());
        linkBtn.setOrientation(LinearLayout.HORIZONTAL);
        linkBtn.setBackgroundResource(R.drawable.bg_capsule_light);
        linkBtn.setPadding(32, 16, 32, 16); // px padding (approx 12dp, 6dp)
        linkBtn.setGravity(android.view.Gravity.CENTER);
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd(16);
        linkBtn.setLayoutParams(params);

        TextView tv = new TextView(getContext());
        tv.setText(name);
        tv.setTextColor(getResources().getColor(R.color.text_primary));
        tv.setTextSize(12);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        
        linkBtn.addView(tv);
        
        linkBtn.setOnClickListener(v -> {
            try {
                 Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                 startActivity(browserIntent);
            } catch (Exception e) {
                safeToast("Invalid URL");
            }
        });
        
        // Long press to delete? For now simple implementation.
        
        llLinksContainer.addView(linkBtn);
    }

    private void showEditBioDialog() {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_edit_bio, null);
        
        com.google.android.material.textfield.TextInputEditText etBio = view.findViewById(R.id.et_bio_input);
        Button btnSave = view.findViewById(R.id.btn_save);
        Button btnCancel = view.findViewById(R.id.btn_cancel);
        
        String currentBio = tvBio.getText().toString();
        if (!currentBio.equals("Tap to add bio...")) {
            etBio.setText(currentBio);
        }
        
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(getContext())
            .setView(view)
            .create();
            
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        
        btnSave.setOnClickListener(v -> {
            String newBio = etBio.getText().toString().trim();
            if (newBio.length() > 150) {
                safeToast("Bio too long");
                return;
            }
            
             db.collection("users").document(auth.getCurrentUser().getUid()).update("bio", newBio)
                 .addOnSuccessListener(a -> {
                     updateBio(newBio);
                     dialog.dismiss();
                 })
                 .addOnFailureListener(e -> safeToast("Failed to save"));
        });
        
        dialog.show();
    }
    
    // Generic Dialog Helper
    private void showTextInputDialog(String title, String hint, String currentVal,  com.google.android.gms.tasks.OnSuccessListener<String> onSuccess) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_edit_username, null); // Reuse layout
        com.google.android.material.textfield.TextInputEditText input = view.findViewById(R.id.et_username_input);
        input.setHint(hint);
        input.setText(currentVal);
        
        new androidx.appcompat.app.AlertDialog.Builder(getContext())
            .setTitle(title)
            .setView(view)
            .setPositiveButton("Save", (d, w) -> {
                String val = input.getText().toString().trim();
                onSuccess.onSuccess(val);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showEditDetailsDialog() {
        openDetailsEditor(); 
    }
    
    private void openDetailsEditor() {
         com.visiboard.app.ui.profile.EditDetailsBottomSheet sheet = new com.visiboard.app.ui.profile.EditDetailsBottomSheet();
         sheet.show(getParentFragmentManager(), "EditDetails");
    }

    private void showAddLinkDialog() {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_link, null);
        
        com.google.android.material.textfield.TextInputEditText etName = view.findViewById(R.id.et_link_name);
        com.google.android.material.textfield.TextInputEditText etUrl = view.findViewById(R.id.et_link_url);
        Button btnAdd = view.findViewById(R.id.btn_add);
        Button btnCancel = view.findViewById(R.id.btn_cancel);
        
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(getContext())
            .setView(view)
            .create();
            
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        
        btnAdd.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String url = etUrl.getText().toString().trim();
            
            if (name.isEmpty() || url.isEmpty()) {
                safeToast("Please fill all fields");
                return;
            }
            
            if (!url.startsWith("http")) {
                url = "https://" + url;
            }
            
            java.util.Map<String, Object> link = new java.util.HashMap<>();
            link.put("name", name);
            link.put("url", url);
            
            btnAdd.setText("Adding...");
            btnAdd.setEnabled(false);
            
            db.collection("users").document(auth.getCurrentUser().getUid())
                .update("socialLinks", com.google.firebase.firestore.FieldValue.arrayUnion(link))
                .addOnSuccessListener(aVoid -> {
                    safeToast("Link added");
                    dialog.dismiss();
                    refreshData();
                })
                .addOnFailureListener(e -> {
                    safeToast("Failed: " + e.getMessage());
                    btnAdd.setText("Add Link");
                    btnAdd.setEnabled(true);
                });
        });
            
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
        
        // Get current stats from ViewModel (Source of Truth)
        Integer cachedLikes = viewModel.getTotalLikes().getValue();
        int totalLikes = cachedLikes != null ? cachedLikes : 0;
        
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
        
        // Setup rank items synchronously
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
        
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(getContext())
                .setView(dialogView)
                .create();
        
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        
        // Close button click listener
        ImageButton btnCloseRanking = dialogView.findViewById(R.id.btn_close_ranking);
        if (btnCloseRanking != null) {
            btnCloseRanking.setOnClickListener(v -> dialog.dismiss());
        }
        
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
        ImageButton btnCloseDialog = dialogView.findViewById(R.id.btn_close_dialog);
        
        recyclerView.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(getContext()));
        
        // Create dialog first so we can reference it in callbacks
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(getContext())
                .setView(dialogView)
                .create();
        
        // Close button click listener
        if (btnCloseDialog != null) {
            btnCloseDialog.setOnClickListener(v -> dialog.dismiss());
        }
        
        final boolean[] isFollowingList = {showFollowing};
        final UserFollowAdapter[] adapterHolder = new UserFollowAdapter[1];
        
        UserFollowAdapter adapter = new UserFollowAdapter(new UserFollowAdapter.OnUserClickListener() {
            @Override
            public void onUserClick(com.visiboard.app.data.UserInfo user) {
                dialog.dismiss(); // Auto-dismiss dialog before navigating
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
            followingTab.setBackgroundResource(R.drawable.bg_button_gradient);
            followingTab.setTextColor(getResources().getColor(R.color.white, null));
            followersTab.setBackgroundResource(0);
            followersTab.setTextColor(getResources().getColor(R.color.text_secondary, null));
        } else {
             followersTab.setBackgroundResource(R.drawable.bg_button_gradient);
             followersTab.setTextColor(getResources().getColor(R.color.white, null));
             followingTab.setBackgroundResource(0);
             followingTab.setTextColor(getResources().getColor(R.color.text_secondary, null));
        }
        
        // Load initial list
        loadUsersList(showFollowing, adapter, emptyState);
        
        // Tab switching
        followersTab.setOnClickListener(v -> {
            followersTab.setBackgroundResource(R.drawable.bg_button_gradient);
            followersTab.setTextColor(getResources().getColor(R.color.white, null));
            followingTab.setBackgroundResource(0);
            followingTab.setTextColor(getResources().getColor(R.color.text_secondary, null));
            isFollowingList[0] = false;
            loadUsersList(false, adapter, emptyState);
        });
        
        followingTab.setOnClickListener(v -> {
            followingTab.setBackgroundResource(R.drawable.bg_button_gradient);
            followingTab.setTextColor(getResources().getColor(R.color.white, null));
            followersTab.setBackgroundResource(0);
            followersTab.setTextColor(getResources().getColor(R.color.text_secondary, null));
            isFollowingList[0] = true;
            loadUsersList(true, adapter, emptyState);
        });
        
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

    // Show user info dialog - now navigates to full page
    private void showUserInfoDialog(String userId) {
        Bundle args = new Bundle();
        args.putString("userId", userId);
        androidx.navigation.Navigation.findNavController(requireView()).navigate(R.id.userProfileFragment, args);
    }
    
    // Show all notes dialog
    private void showAllNotesDialog() {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_all_notes, null);
        
        androidx.recyclerview.widget.RecyclerView recyclerView = dialogView.findViewById(R.id.rv_all_notes);
        TextView emptyState = dialogView.findViewById(R.id.tv_no_notes);
        TextView notesCount = dialogView.findViewById(R.id.tv_notes_count);
        ImageView btnClose = dialogView.findViewById(R.id.btn_close);
        
        recyclerView.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(getContext()));
        
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(getContext())
                .setView(dialogView)
                .create();
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        
        btnClose.setOnClickListener(v -> dialog.dismiss());
        
        // Load all notes
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            String uid = user.getUid();
            
            db.collection("users").document(uid).get()
                .addOnSuccessListener(currentUserDoc -> {
                    String currentUserName = currentUserDoc.getString("name");
                    String currentUserProfilePic = currentUserDoc.getString("profilePic");
                    
                    db.collection("notes")
                        .whereEqualTo("userId", uid)
                        .get()
                        .addOnSuccessListener(querySnapshot -> {
                            List<NearbyNote> allNotes = new ArrayList<>();
                            
                            for (var doc : querySnapshot.getDocuments()) {
                                NearbyNote note = new NearbyNote();
                                note.setId(doc.getId());
                                
                                String noteText = doc.getString("text");
                                if (noteText == null) noteText = doc.getString("note");
                                note.setText(noteText);
                                
                                String summary = doc.getString("summary");
                                if (summary == null && noteText != null && noteText.length() > 100) {
                                    summary = noteText.substring(0, 100) + "...";
                                }
                                note.setSummary(summary);
                                
                                note.setUserName(currentUserName != null ? currentUserName : "You");
                                note.setUserProfilePic(currentUserProfilePic);
                                note.setUserId(uid);
                                
                                Double lat = doc.getDouble("lat");
                                Double lon = doc.getDouble("lon");
                                if (lat != null && lon != null) {
                                    note.setLat(lat);
                                    note.setLng(lon);
                                }
                                
                                Long timestamp = doc.getLong("timestamp");
                                note.setTimestamp(timestamp != null ? timestamp : 0);
                                
                                Long likeCount = doc.getLong("likeCount");
                                if (likeCount == null) likeCount = doc.getLong("likesCount");
                                note.setLikesCount(likeCount != null ? likeCount.intValue() : 0);
                                
                                Long commentsCount = doc.getLong("commentsCount");
                                note.setCommentsCount(commentsCount != null ? commentsCount.intValue() : 0);
                                
                                allNotes.add(note);
                            }
                            
                            // Sort by timestamp descending (newest first)
                            allNotes.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
                            
                            if (allNotes.isEmpty()) {
                                emptyState.setVisibility(View.VISIBLE);
                                recyclerView.setVisibility(View.GONE);
                                notesCount.setVisibility(View.GONE);
                            } else {
                                emptyState.setVisibility(View.GONE);
                                recyclerView.setVisibility(View.VISIBLE);
                                notesCount.setVisibility(View.VISIBLE);
                                notesCount.setText(allNotes.size() + (allNotes.size() == 1 ? " note" : " notes"));
                                
                                RecentNotesAdapter adapter = new RecentNotesAdapter(note -> {
                                    dialog.dismiss();
                                    navigateToNoteOnMap(note.getLat(), note.getLng(), note.getId());
                                });
                                adapter.setNotes(allNotes);
                                recyclerView.setAdapter(adapter);
                            }
                        })
                        .addOnFailureListener(e -> {
                            emptyState.setVisibility(View.VISIBLE);
                            recyclerView.setVisibility(View.GONE);
                            notesCount.setVisibility(View.GONE);
                            Log.e("ProfileFragment", "Error loading all notes: " + e.getMessage());
                        });
                });
        }
        
        dialog.show();
    }
    
    private void navigateToNoteOnMap(double lat, double lng, String noteId) {
        Bundle args = new Bundle();
        args.putDouble("target_lat", lat);
        args.putDouble("target_lng", lng);
        args.putString("target_note_id", noteId);
        args.putBoolean("open_note_window", true);

        Navigation.findNavController(requireView())
            .navigate(R.id.mapFragment, args);
    }
    
    private void showThemeTransitionDialog(boolean isDarkMode) {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(requireContext());
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_theme_transition, null);
        builder.setView(view);
        
        ImageView icon = view.findViewById(R.id.theme_icon);
        TextView text = view.findViewById(R.id.theme_text);
        
        if (isDarkMode) {
            icon.setImageResource(R.drawable.ic_moon);
            text.setText("Dark Mode");
        } else {
            icon.setImageResource(R.drawable.ic_sun);
            text.setText("Light Mode");
        }
        
        icon.animate().rotation(360).setDuration(1000).start();
        
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        dialog.show();
        
        new Handler(Looper.getMainLooper()).postDelayed(dialog::dismiss, 2000);
    }

    private void updateThemeIcon(ImageView themeToggle, boolean isDarkMode) {
        themeToggle.setImageResource(isDarkMode ? R.drawable.ic_sun : R.drawable.ic_moon);
    }

    // --- Floating Physics Notes Logic ---

    private void loadFloatingNotes() {
        if (auth.getCurrentUser() == null || physicsHeader == null) return;
        String uid = auth.getCurrentUser().getUid();
        
        db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
            if (!doc.exists()) return;
            List<String> favs = (List<String>) doc.get("favouriteNotes");
            
            if (favs == null || favs.isEmpty()) {
                showEmptyFloatingState();
            } else {
                fetchNotesAndRender(favs);
            }
        });
    }

    private void showEmptyFloatingState() {
        physicsHeader.removeAllViews();
        addDecorativeSocialIcons(); // Add background decorative icons
        View v = LayoutInflater.from(getContext()).inflate(R.layout.item_floating_empty, physicsHeader, false);
        physicsHeader.addFloatingView(v); 
        physicsHeader.initializeEntities();
        
        v.setOnClickListener(view -> openSelectFavourites());
        physicsHeader.setOnClickListener(view -> openSelectFavourites()); // Also background click
    }
    
    private void openSelectFavourites() {
        if (getView() != null) {
            androidx.navigation.Navigation.findNavController(getView())
                .navigate(R.id.action_profile_to_favouriteSelection);
        }
    }
    
    private final java.util.concurrent.atomic.AtomicInteger noteFetchGeneration = new java.util.concurrent.atomic.AtomicInteger(0);

    private void fetchNotesAndRender(List<String> ids) {
        final int currentGen = noteFetchGeneration.incrementAndGet();
        physicsHeader.removeAllViews();
        
        // Add decorative social icons in the background
        addDecorativeSocialIcons();
        
        // Ensure background clicks open selection even when notes exist
        physicsHeader.setOnClickListener(view -> openSelectFavourites());
        
        // Remove duplicates if any
        List<String> uniqueIds = new ArrayList<>(new java.util.HashSet<>(ids));
        final int total = uniqueIds.size();
        final java.util.concurrent.atomic.AtomicInteger loadedCount = new java.util.concurrent.atomic.AtomicInteger(0);
        
        for (String id : uniqueIds) {
            db.collection("notes").document(id).get().addOnSuccessListener(noteDoc -> {
                // Check if this result belongs to the latest request
                if (currentGen != noteFetchGeneration.get()) return;
                
                if (noteDoc.exists()) {
                    View v = createFloatingNoteView(noteDoc);
                    if (v != null) physicsHeader.addFloatingView(v);
                }
                if (loadedCount.incrementAndGet() == total) {
                     physicsHeader.initializeEntities();
                }
            }).addOnFailureListener(e -> {
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
        
        String imageBase64 = doc.getString("imageBase64"); // Correct field name!
        
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
            
            // Random Pastel Helpers
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
        
        v.setOnClickListener(view -> {
             Double lat = doc.getDouble("lat");
             Double lng = doc.getDouble("lng");
             if (lng == null) lng = doc.getDouble("lon");
             
             if (lat != null && lng != null) {
                 navigateToNoteOnMap(lat, lng, doc.getId());
             }
        });
        
        // Ensure background clicks on physics header still work even with notes
        physicsHeader.setOnClickListener(view -> openSelectFavourites());
        
        return v;
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

    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Clear references to prevent memory leaks
        if (recentNotesAdapter != null) {
            recentNotesAdapter.setNotes(new ArrayList<>());
        }
        swipeRefreshLayout = null;
        recentNotesAdapter = null;
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // Always reload basic user data on resume to catch updates from sub-screens (like Favourite Selection)
        // This is safer than relying solely on FragmentResult for hardware back button cases.
        loadUserData(); 
        
        // Refresh other stats if stale
        if (viewModel.shouldRefreshData()) {
            loadUserStats();
        }
    }
}
