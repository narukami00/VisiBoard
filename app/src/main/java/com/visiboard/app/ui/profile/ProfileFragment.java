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
    private ImageView ivTierIcon, logoutIcon, shineView;
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
        logoutIcon = view.findViewById(R.id.logout_icon);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        locationContainer = view.findViewById(R.id.location_container);
        loadingOverlay = view.findViewById(R.id.loading_overlay);
        skeletonView = view.findViewById(R.id.skeleton_view);
        shineView = view.findViewById(R.id.shine_view);

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
        logoutIcon.setOnClickListener(v -> showLogoutConfirmation());
        view.findViewById(R.id.settings_icon).setOnClickListener(v -> {
            startActivity(new Intent(getActivity(), com.visiboard.app.ui.settings.SettingsActivity.class));
        });
        view.findViewById(R.id.btn_view_all_notes).setOnClickListener(v -> showAllNotesDialog());
        nameText.setOnClickListener(v -> showEditNameDialog());
        view.findViewById(R.id.btn_about).setOnClickListener(v -> showAboutDialog());
        
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
        });

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

        String pic = doc.getString("profilePic");
        if (pic != null && !pic.isEmpty()) viewModel.setProfilePicBase64(pic);
        
        // Hide loading progress if basic user data is loaded, 
        // though stats might still be loading. Best to wait for stats?
        // Let's keep it visible until stats load too, or hide here if stats are independent.
        // The user wants "profile fragment is loading".
        // We'll trust the stats loader to hide it finally, or do it here if stats aren't called?
        // Actually loadUserStats is called right after loadUserData in refresh logic.
        // But in initial load, they are called sequentially.
        // Let's hide it in the final callback of loadUserStats.
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
        input.setText(nameText.getText().toString());

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(getContext())
                .setTitle("Edit Name")
                .setView(dialogView)
                .setPositiveButton("Save", (d, w) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        updateUserName(newName);
                    } else {
                        safeToast("Name cannot be empty");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
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
        // Refresh data if it's stale (older than cache duration)
        if (viewModel.shouldRefreshData()) {
            loadUserData();
            loadUserStats();
        }
    }
}
