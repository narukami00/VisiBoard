package com.visiboard.app.ui.feed;

import android.Manifest;
import android.content.pm.PackageManager;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.Query;
import com.visiboard.app.R;
import com.visiboard.app.data.NearbyNote;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DiscoverTabFragment extends Fragment {

    private static final String TAG = "DiscoverTabFragment";
    private static final int FETCH_SIZE = 10; 
    private static final int PAGE_SIZE = 5;

    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView rvPinterestFeed;
    private ProgressBar pbLoading;
    private View shimmerContainer;
    
    private PinterestFeedAdapter pinterestFeedAdapter;
    
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private FusedLocationProviderClient fusedLocationClient;
    private Location currentLocation;
    
    private boolean isLoading = false;
    private volatile boolean isFragmentDestroyed = false;
    
    // Privacy Lists
    private List<String> followingIds = new ArrayList<>();
    private List<String> blockedUserIds = new ArrayList<>();
    private List<String> hiddenNoteOtherIds = new ArrayList<>();
    private boolean isPrivacyDataLoaded = false;
    
    private FeedViewModel feedViewModel;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    
    private ObjectAnimator pulseAnimator;
    private Thread imageProcessingThread;

    private NoteClickListener noteClickListener;
    
    public interface NoteClickListener {
        void onNoteClick(NearbyNote note);
        void onShareClick(NearbyNote note);
    }
    
    public void setNoteClickListener(NoteClickListener listener) {
        this.noteClickListener = listener;
    }
    
    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (getParentFragment() instanceof NoteClickListener) {
            noteClickListener = (NoteClickListener) getParentFragment();
        } else if (context instanceof NoteClickListener) {
            noteClickListener = (NoteClickListener) context;
        }
    }
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        isFragmentDestroyed = false;
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        // Scope ViewModel to Parent Fragment (FeedFragment) so it survives tab switches but dies when leaving Feed
        feedViewModel = new ViewModelProvider(requireParentFragment()).get(FeedViewModel.class);
        
        requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) loadUserLocation();
            else loadPinterestFeed(false);
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_discover_tab, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // auth = FirebaseAuth.getInstance(); // Moved to onCreate
        // db = FirebaseFirestore.getInstance(); // Moved to onCreate
        // fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity()); // Moved to onCreate
        
        swipeRefresh = view.findViewById(R.id.swipe_refresh_discover);
        rvPinterestFeed = view.findViewById(R.id.rv_pinterest_feed_tab);
        pbLoading = view.findViewById(R.id.pb_loading_discover);
        shimmerContainer = view.findViewById(R.id.shimmer_view_container);
        
        setupRecyclerView();
        setupSwipeRefresh();
        
        // Only load data if ViewModel doesn't have it yet
        if (!feedViewModel.isDataLoaded() || feedViewModel.getAllPinterestNotes().isEmpty()) {
            loadPrivacyData();
        } else {
            // Use cached data
            pinterestFeedAdapter.setNotes(feedViewModel.getAllPinterestNotes());
            pbLoading.setVisibility(View.GONE);
            shimmerContainer.setVisibility(View.GONE);
            if (pulseAnimator != null) pulseAnimator.cancel();
            
            // Silently fetch location for distance calculations
            if (currentLocation == null) {
                fetchLocationSilently();
            }
        }
    }
    
    private void fetchLocationSilently() {
         try {
             fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                currentLocation = location;
            });
         } catch (SecurityException e) {
         }
    }
    
    private void startShimmer() {
        if (shimmerContainer.getVisibility() != View.VISIBLE) {
             shimmerContainer.setVisibility(View.VISIBLE);
             shimmerContainer.setAlpha(1.0f);
             
             if (pulseAnimator == null) {
                 pulseAnimator = ObjectAnimator.ofFloat(shimmerContainer, "alpha", 0.5f, 1.0f);
                 pulseAnimator.setDuration(800);
                 pulseAnimator.setRepeatCount(ObjectAnimator.INFINITE);
                 pulseAnimator.setRepeatMode(ObjectAnimator.REVERSE);
             }
             pulseAnimator.start();
        }
        rvPinterestFeed.setVisibility(View.GONE);
    }
    
    private void stopShimmer() {
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
        }
        shimmerContainer.animate()
            .alpha(0.0f)
            .setDuration(300)
            .withEndAction(() -> {
                shimmerContainer.setVisibility(View.GONE);
                rvPinterestFeed.setAlpha(0.0f);
                rvPinterestFeed.setVisibility(View.VISIBLE);
                rvPinterestFeed.animate().alpha(1.0f).setDuration(300).start();
            }).start();
    }
    
    private void setupRecyclerView() {
        pinterestFeedAdapter = new PinterestFeedAdapter(new PinterestFeedAdapter.OnNoteClickListener() {
            @Override
            public void onNoteClick(NearbyNote note) {
                if (noteClickListener != null) noteClickListener.onNoteClick(note);
            }

            @Override
            public void onShareClick(NearbyNote note) {
                 if (noteClickListener != null) noteClickListener.onShareClick(note);
            }
        });
        
        // Responsive grid columns based on screen size
        int columnCount = getResources().getInteger(R.integer.feed_grid_columns);
        StaggeredGridLayoutManager layoutManager = new StaggeredGridLayoutManager(columnCount, StaggeredGridLayoutManager.VERTICAL);
        layoutManager.setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_NONE);
        rvPinterestFeed.setLayoutManager(layoutManager);
        rvPinterestFeed.setAdapter(pinterestFeedAdapter);
        rvPinterestFeed.setItemAnimator(null); // Prevent jumps
        
        rvPinterestFeed.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                
                int[] lastVisibleItemPositions = layoutManager.findLastVisibleItemPositions(null);
                int lastVisibleItem = getLastVisibleItem(lastVisibleItemPositions);
                int totalItemCount = layoutManager.getItemCount();
                
                if (!feedViewModel.isLoading() && !feedViewModel.isLastPage() && totalItemCount <= (lastVisibleItem + 6)) {
                    loadPinterestFeed(true);
                }
            }
        });
    }
    
    private int getLastVisibleItem(int[] lastVisibleItemPositions) {
        int maxSize = 0;
        for (int i = 0; i < lastVisibleItemPositions.length; i++) {
            if (i == 0) {
                maxSize = lastVisibleItemPositions[i];
            } else if (lastVisibleItemPositions[i] > maxSize) {
                maxSize = lastVisibleItemPositions[i];
            }
        }
        return maxSize;
    }
    
    private void setupSwipeRefresh() {
        swipeRefresh.setColorSchemeResources(R.color.primary, R.color.secondary, R.color.accent);
        swipeRefresh.setOnRefreshListener(this::loadPrivacyData);
    }

    private void loadPrivacyData() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        // Chain load: Blocked -> Hidden -> Following -> Location/Feed
        db.collection("users").document(uid).collection("blocked_users").get()
            .addOnSuccessListener(snap -> {
                if (isFragmentDestroyed) return;
                blockedUserIds.clear();
                for (DocumentSnapshot d : snap) blockedUserIds.add(d.getId());

                db.collection("users").document(uid).collection("hidden_notes_others").get()
                    .addOnSuccessListener(snap2 -> {
                        if (isFragmentDestroyed) return;
                        hiddenNoteOtherIds.clear();
                        for (DocumentSnapshot d : snap2) hiddenNoteOtherIds.add(d.getId());

                        db.collection("users").document(uid).collection("following").get()
                            .addOnSuccessListener(snap3 -> {
                                if (isFragmentDestroyed) return;
                                followingIds.clear();
                                for (DocumentSnapshot d : snap3) followingIds.add(d.getId());
                                
                                isPrivacyDataLoaded = true;
                                
                                // Now proceed to location check
                                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                    loadUserLocation();
                                } else {
                                    requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
                                }
                            })
                            .addOnFailureListener(e -> proceedWithDefaults());
                    })
                    .addOnFailureListener(e -> proceedWithDefaults());
            })
            .addOnFailureListener(e -> proceedWithDefaults());
    }

    private void proceedWithDefaults() {
        // If privacy load fails, we default to strict safe mode (lists empty) or retry?
        // Empty lists mean we might SHOW blocked content if we aren't careful, 
        // BUT map logic implies 'blockedUserIds.contains' -> if empty, doesn't filter.
        // This is fail-open. Ideal is fail-closed, but for now let's just proceed to load what we can.
        // Better to try loading feed than stuck forever.
        if (isFragmentDestroyed) return;
         if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            loadUserLocation();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }
    
    private void loadUserLocation() {
        if (getActivity() == null) return;
        
        try {
             fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                if (isFragmentDestroyed || !isAdded()) return;
                currentLocation = location;
                loadPinterestFeed(false);
            }).addOnFailureListener(e -> {
                if (isFragmentDestroyed || !isAdded()) return;
                Log.e(TAG, "Error getting location", e);
                loadPinterestFeed(false);
            });
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception getting location", e);
            if (!isFragmentDestroyed && isAdded()) loadPinterestFeed(false);
        }
    }

    private void loadPinterestFeed(boolean isNextPage) {
        if (!isAdded() || getContext() == null) return; // Immediate safety check
        if (auth.getCurrentUser() == null) return;
        if (feedViewModel == null) return;
        if (feedViewModel.isLoading()) return;
        
        feedViewModel.setLoading(true);
        
        if (!isNextPage) {
            if (swipeRefresh != null) swipeRefresh.setRefreshing(true);
            feedViewModel.setLastPage(false);
            feedViewModel.setLastVisible(null);
            
            // Start Shimmer for initial load
            startShimmer();
        } else {
             // Use Adapter Footer for pagination, NOT the center spinner
             pinterestFeedAdapter.setLoading(true);
             // pbLoading.setVisibility(View.VISIBLE); // REMOVED
        }
        
        // Load more items to compensate for client-side privacy filtering
        Query query = db.collection("notes")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .orderBy("__name__", Query.Direction.DESCENDING) // Keep stability
            .limit(50); // Increased from 10 to 50
            
        if (isNextPage && feedViewModel.getLastVisible() != null) {
            query = query.startAfter(feedViewModel.getLastVisible());
        }
            
        query.get().addOnSuccessListener(queryDocumentSnapshots -> {
            // Critical safety check: Don't process if fragment is destroyed or detached
            if (isFragmentDestroyed || !isAdded() || getContext() == null) {
                Log.d(TAG, "Fragment detached, aborting data processing");
                return;
            }
            
            boolean isEmpty = queryDocumentSnapshots.isEmpty();
            
            if (isEmpty) {
                feedViewModel.setLastPage(true);
                feedViewModel.setLoading(false);
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false); 
                if (pbLoading != null) pbLoading.setVisibility(View.GONE);
                if (pinterestFeedAdapter != null) pinterestFeedAdapter.setShowEndMessage(true);
                if (!isNextPage) stopShimmer(); 
                return;
            }
            

            
            // Safety Check: If Fragment is destroyed/detached, don't start background processing
            if (isFragmentDestroyed || !isAdded() || getContext() == null) {
                Log.w(TAG, "Fragment destroyed/detached before processing. Aborting.");
                feedViewModel.setLoading(false);
                return;
            }

            final java.io.File cacheDir = getContext().getCacheDir();
            
            // Single Background Thread: Parse Metadata AND Save Images
            imageProcessingThread = new Thread(() -> {
                List<NearbyNote> fetchedNotes = new ArrayList<>();
                try {
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                         // Check for interruption or fragment destroyed
                         if (Thread.currentThread().isInterrupted() || isFragmentDestroyed) {
                             Log.d(TAG, "Thread stopped - Fragment destroyed or interrupted");
                             return;
                         }

                         // --- PRIVACY FILTERS ---
                         String noteId = doc.getId();
                         String userId = doc.getString("userId");
                         if (userId == null) continue; // Invalid note

                         // 1. Blocked User
                         if (blockedUserIds.contains(userId)) continue;

                         // 2. Hidden Note (Global or User-specific)
                         boolean isHidden = doc.getBoolean("isHidden") != null && doc.getBoolean("isHidden");
                         if (isHidden) continue;
                         if (hiddenNoteOtherIds.contains(noteId)) continue;
                         
                         // 3. User Visibility Settings
                         String visibility = doc.getString("visibility");
                         if (visibility == null) visibility = "public"; // Default to public
                         boolean isOwnerPrivate = doc.getBoolean("isOwnerPrivate") != null && doc.getBoolean("isOwnerPrivate");
                         
                         String currentUid = auth.getCurrentUser().getUid();
                         
                         // Skip my own hidden notes (if Hide My Notes is on - handled in Map, here we assume show mine unless I hid it specifically?)
                         // MapFragment logic: if (userId.equals(auth.getCurrentUser().getUid())) { if (isHideMyNotesEnabled) continue; }
                         // Feed doesn't seem to have "Hide My Notes" toggle state passed to it. 
                         // For now, allow my own notes.
                         
                         if (!userId.equals(currentUid)) {
                             // Strict Private
                             if ("private".equals(visibility)) continue;

                             // Followers Only Logic (Owner Private OR Note Followers-Only)
                             boolean isRestricted = isOwnerPrivate || "followers".equals(visibility);
                             if (isRestricted && !followingIds.contains(userId)) {
                                 continue;
                             }
                         }

                         // --- END PRIVACY FILTERS ---

                         // 1. Parse Metadata
                         final NearbyNote note = new NearbyNote();
                         note.setId(doc.getId());
                         
                         String text = doc.getString("text");
                         if (text == null) text = doc.getString("note");
                         note.setText(text);
                         note.setSummary(doc.getString("summary"));
                         note.setUserId(doc.getString("userId"));
                         note.setUserName(doc.getString("userName"));
                         note.setUserProfilePic(doc.getString("userProfilePic"));
                         
                         GeoPoint location = doc.getGeoPoint("location");
                         note.setLat(location != null ? location.getLatitude() : 0);
                         note.setLng(location != null ? location.getLongitude() : 0);
                         note.setTimestamp(doc.getLong("timestamp") != null ? doc.getLong("timestamp") : 0);
                         
                         // Distance
                         double distance = 0;
                         if (location != null && currentLocation != null) {
                             distance = calculateDistance(
                                 currentLocation.getLatitude(), 
                                 currentLocation.getLongitude(),
                                 location.getLatitude(), 
                                 location.getLongitude()
                             );
                         }
                         note.setDistance(distance);
                         
                         // Like Count
                         int likes = 0;
                         Object likesObj = doc.get("likesCount");
                         if (likesObj == null) likesObj = doc.get("likeCount");
                         if (likesObj instanceof Number) likes = ((Number) likesObj).intValue();
                         else if (likesObj instanceof String) try { likes = Integer.parseInt((String) likesObj); } catch(Exception e){}
                         note.setLikesCount(likes);
                         
                         // Dimensions
                         int imgWidth = 0;
                         int imgHeight = 0;
                         Object wObj = doc.get("imageWidth");
                         Object hObj = doc.get("imageHeight");
                         if (wObj instanceof Number) imgWidth = ((Number) wObj).intValue();
                         else if (wObj instanceof String) try { imgWidth = Integer.parseInt((String) wObj); } catch(Exception e){}
                         if (hObj instanceof Number) imgHeight = ((Number) hObj).intValue();
                         else if (hObj instanceof String) try { imgHeight = Integer.parseInt((String) hObj); } catch(Exception e){}
                         
                         note.setImageWidth(imgWidth);
                         note.setImageHeight(imgHeight);
                         
                         // 2. Process Image (Disk Cache Optimization)
                         String b64 = doc.getString("imageBase64");
                         if (b64 != null && !b64.isEmpty()) {
                             try {
                                 // Check interruption again before heavy IO
                                 if (Thread.currentThread().isInterrupted()) return;

                                 java.io.File file = new java.io.File(cacheDir, "note_" + doc.getId() + ".jpg");
                                 
                                 // Check if exists, if not save it
                                 if (!file.exists()) {
                                     byte[] decodedString = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
                                     
                                     // Decode with sampling to reduce memory (Optimization from user)
                                     android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
                                     options.inJustDecodeBounds = true;
                                     android.graphics.BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length, options);
                                     
                                     // Calculate sample size (max 1200px)
                                     int maxDim = Math.max(options.outWidth, options.outHeight);
                                     options.inSampleSize = maxDim > 1200 ? maxDim / 1200 : 1;
                                     options.inJustDecodeBounds = false;
                                     options.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565;
                                     
                                     // Check interruption before decode
                                     if (Thread.currentThread().isInterrupted()) return;
                                     
                                     android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(
                                         decodedString, 0, decodedString.length, options);
                                     
                                     if (bitmap != null) {
                                         // Save compressed version
                                         java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
                                         bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, fos);
                                         fos.flush();
                                         fos.close();
                                         bitmap.recycle(); // Free memory immediately
                                     }
                                 }
                                 note.setLocalImagePath(file.getAbsolutePath());
                                 note.setImageBase64(null); // Release memory
                             } catch (Exception e) {
                                 Log.e(TAG, "Error processing image for " + doc.getId(), e);
                                 // Fallback
                                 note.setImageBase64(b64);
                             } catch (OutOfMemoryError oom) {
                                 Log.e(TAG, "OOM processing image for " + doc.getId(), oom);
                                 System.gc();
                             }
                         } else {
                             note.setImageBase64(null);
                         }
                         
                         fetchedNotes.add(note);
                    }
                    
                    // 3. Shuffle & Optimize
                    Collections.shuffle(fetchedNotes);
                    
                    String[] wideTypes = {"switch", "gravity"};
                    if (fetchedNotes.size() > 5) {
                         int pos1 = 2 + (int)(Math.random() * 5);
                         if (pos1 < fetchedNotes.size()) {
                             NearbyNote f = new NearbyNote();
                             f.setId("fidget_" + wideTypes[(int)(Math.random() * wideTypes.length)] + "_" + System.nanoTime());
                             fetchedNotes.add(pos1, f);
                         }
                    }
                    
                    optimizeItemOrder(fetchedNotes);
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error in feed processing", e);
                }
                
                // Check if interrupted before UI update
                if (Thread.currentThread().isInterrupted()) return;

                // 4. Update UI
                if (!isFragmentDestroyed && getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                         // Safety Check: If fragment is detached or destroyed, do not touch UI
                         if (isFragmentDestroyed || !isAdded() || getContext() == null) return;

                         if (!isNextPage) {
                            feedViewModel.clear();
                        }
                        
                        // Update Pagination State (Must be done AFTER clear())
                        feedViewModel.setLastVisible(queryDocumentSnapshots.getDocuments().get(queryDocumentSnapshots.size() - 1));
                        
                        if (queryDocumentSnapshots.size() < FETCH_SIZE) {
                            feedViewModel.setLastPage(true);
                            pinterestFeedAdapter.setShowEndMessage(true);
                        }

                        List<NearbyNote> uniqueNotes = new ArrayList<>();
                        for (NearbyNote n : fetchedNotes) {
                            if (n.getId().startsWith("fidget") || !feedViewModel.getLoadedNoteIds().contains(n.getId())) {
                                if (!n.getId().startsWith("fidget")) {
                                    feedViewModel.getLoadedNoteIds().add(n.getId());
                                }
                                uniqueNotes.add(n);
                            }
                        }
                        
                        feedViewModel.getAllPinterestNotes().addAll(uniqueNotes);
                        feedViewModel.setDataLoaded(true);
                        
                        if (!isNextPage) {
                            pinterestFeedAdapter.setNotes(feedViewModel.getAllPinterestNotes());
                            // Stop Shimmer ONLY after everything is loaded
                            stopShimmer();
                        } else {
                            pinterestFeedAdapter.addNotes(uniqueNotes);
                            
                            // CRITICAL FIX: If we fetched notes but they were all duplicates (uniqueNotes is empty),
                            // the user sees nothing and cannot scroll to trigger the next load. We must auto-load the next page.
                            if (uniqueNotes.isEmpty() && !feedViewModel.isLastPage()) {
                                Log.d(TAG, "All fetched notes were duplicates. Auto-loading next page...");
                                loadPinterestFeed(true);
                            }
                        }
                        
                        feedViewModel.setLoading(false);
                        pinterestFeedAdapter.setLoading(false);
                        if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                        if (pbLoading != null) pbLoading.setVisibility(View.GONE); 
                    });
                }
            });
            imageProcessingThread.start();
        })
        .addOnFailureListener(e -> {
            // Safety check: Don't touch UI if fragment is destroyed or detached
            if (isFragmentDestroyed || !isAdded()) return;
            
            Log.e(TAG, "Error loading feed", e);
            feedViewModel.setLoading(false);
            if (pinterestFeedAdapter != null) pinterestFeedAdapter.setLoading(false);
            if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
            if (pbLoading != null) pbLoading.setVisibility(View.GONE); 
            if (!isNextPage) stopShimmer();
        });
    }
    
    private void optimizeItemOrder(List<NearbyNote> notes) {
        // "Smart Tetris" Packing with Fidget Injection
        // Instead of swapping, we now insert new Fidget gadgets to fill gaps!
        
        List<NearbyNote> optimized = new ArrayList<>();
        double col1Height = 0;
        double col2Height = 0;
        
        // Fidget Types config (Strings replaces Knob)
        String[] tallFidgets = {"lava"}; // 3:4
        String[] squareFidgets = {"trace", "bubble"}; // 1:1
        String[] wideFidgets = {"switch", "gravity"}; // Full Span
        String[] smallFidgets = {"spinner"}; // Small/Squat
        
        // Track inserted fidget count to avoid flooding
        int fidgetsInserted = 0;
        int maxFidgets = 6; // Reduced max per 50 items (User req: "frequency is too much")
        
        // Spacing Tracker: Size of 'optimized' list when last fidget was added
        int lastFidgetInsertionSize = -20; // Allow early one (-20 to start safely)
        
        // We iterate through original notes and treat them as a "Queue"
        int index = 0;
        
        while (index < notes.size()) {
            NearbyNote item = notes.get(index);
            
            // Check for gaps before placing item
            double diff = Math.abs(col1Height - col2Height);
            boolean isGap = diff > 0.6; 
            
            // LOGIC FIX 1: Spacing Check (At least 5 items apart)
            boolean isSpaced = (optimized.size() - lastFidgetInsertionSize) > 5;
            
            // LOGIC FIX 2: End of List Protection (Don't fill gaps in last 5 items)
            boolean isNearEnd = (index >= notes.size() - 5);
            
            // LOGIC FIX 3: Force Wide Widget Injection (Every ~12 items)
            if (index > 0 && index % 12 == 0 && Math.random() > 0.3) {
                 NearbyNote wideFidget = new NearbyNote();
                 String wType = wideFidgets[(int)(Math.random() * wideFidgets.length)];
                 wideFidget.setId("fidget_" + wType + "_" + System.nanoTime());
                 
                 // Reset heights to visually "break" the flow
                 double maxHeight = Math.max(col1Height, col2Height);
                 col1Height = maxHeight + 0.6;
                 col2Height = maxHeight + 0.6;
                 
                 optimized.add(wideFidget);
                 lastFidgetInsertionSize = optimized.size(); // Count as insertion
            }
            
            if (isGap && fidgetsInserted < maxFidgets && isSpaced && !isNearEnd) {
                // Gap detected & Conditions met! Fill it.
                NearbyNote filler = new NearbyNote();
                String type;
                double fillerHeight;
                
                // Determine gap type
                if (diff > 1.2) { // Tall gap
                     type = tallFidgets[(int)(Math.random() * tallFidgets.length)];
                     fillerHeight = 1.33; 
                } else if (diff > 0.8) { // Square-ish gap
                     type = squareFidgets[(int)(Math.random() * squareFidgets.length)];
                     fillerHeight = 1.0;
                } else if (diff > 0.3) { // Small gap (Lowered threshold from pure 'else' logic to be explicit)
                     type = smallFidgets[(int)(Math.random() * smallFidgets.length)];
                     fillerHeight = 0.5;
                } else {
                     // Very small gap, try spinner anyway as it can float?
                     type = smallFidgets[0];
                     fillerHeight = 0.5;
                }
                
                filler.setId("fidget_" + type + "_" + System.nanoTime()); // Unique ID
                
                // Add to the shorter column
                if (col1Height <= col2Height) {
                    col1Height += fillerHeight;
                } else {
                    col2Height += fillerHeight;
                }
                
                optimized.add(filler);
                fidgetsInserted++;
                lastFidgetInsertionSize = optimized.size(); // Update marker
                
                // Loop continues to check if gap is filled or place next item
                continue; 
            }
            
            // Place original item
            boolean isFullSpan = isFullSpan(item);
            double itemHeight = getItemHeight(item);
            
            // If it's a full span item (like Switch/Gravity or wide image)
            // We should ensure columns are roughly even before placing it
            if (isFullSpan) {
                // Force sync columns if possible using small fillers? 
                // For now, just place it.
                double maxHeight = Math.max(col1Height, col2Height);
                col1Height = maxHeight + itemHeight;
                col2Height = maxHeight + itemHeight;
            } else {
                if (col1Height <= col2Height) {
                    col1Height += itemHeight;
                } else {
                    col2Height += itemHeight;
                }
            }
            
            optimized.add(item);
            index++;
            
            // Random chance to scatter wide widgets (Switch/Gravity) if we have long run of notes?
            // (Handled by the inputs having them, or we could inject them sparsely too)
        }
        
        // Inject random wide fidget occasionally if we didn't use many gap fillers?
        // Or just trust the input list has them? 
        // Let's rely on gap filling primarily.
        
        // Move content back to original list
        notes.clear();
        notes.addAll(optimized);
    }
    
    private double getItemHeight(NearbyNote item) {
        if (item.getId() != null && item.getId().startsWith("fidget")) {
             String id = item.getId();
             if (id.contains("lava")) return 1.33;
             if (id.contains("trace") || id.contains("strings") || id.contains("bubble")) return 1.0;
             if (id.contains("switch") || id.contains("gravity")) return 0.6; // Full span but short
             if (id.contains("spinner")) return 0.5;
             return 1.0;
        }
        if (item.getImageWidth() > 0 && item.getImageHeight() > 0) {
             return (double) item.getImageHeight() / item.getImageWidth();
        }
        return 0.3; // Text note default
    }
    
    private boolean isFullSpan(NearbyNote note) {
        if (note.getId() != null) {
            String id = note.getId();
            if (id.startsWith("fidget_switch") || id.startsWith("fidget_gravity")) {
                return true;
            }
            if (id.startsWith("fidget")) return false; 
        }
        // Wide image check (must match Adapter logic)
        return note.getImageWidth() > 0 && note.getImageHeight() > 0 && 
               note.getImageWidth() > (note.getImageHeight() * 1.2f);
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371;
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        
        // Mark fragment as destroyed immediately
        isFragmentDestroyed = true;
        
        // Clean up to prevent memory leaks
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
            pulseAnimator = null;
        }
        if (rvPinterestFeed != null) {
            rvPinterestFeed.setAdapter(null);
        }
        
        // Trim image cache when leaving discover feed
        com.visiboard.app.utils.ImageCache.getInstance().trimMemory();
        
        // Kill zombie thread immediately
        if (imageProcessingThread != null && imageProcessingThread.isAlive()) {
            imageProcessingThread.interrupt();
            imageProcessingThread = null;
        }

        pinterestFeedAdapter = null;
        swipeRefresh = null;
    }
    
    @Override
    public void onPause() {
        super.onPause();
        if (pulseAnimator != null) {
            pulseAnimator.pause();
        }
        // Trim cache when user navigates away
        com.visiboard.app.utils.ImageCache.getInstance().trimMemory();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        if (pulseAnimator != null && pulseAnimator.isPaused()) {
            pulseAnimator.resume();
        }
    }
}
