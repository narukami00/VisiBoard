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
    private static final int FETCH_SIZE = 50; 
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
    
    private FeedViewModel feedViewModel;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    
    private ObjectAnimator pulseAnimator;

    private NoteClickListener noteClickListener;
    
    public interface NoteClickListener {
        void onNoteClick(NearbyNote note);
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
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        feedViewModel = new ViewModelProvider(requireActivity()).get(FeedViewModel.class);
        
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
        
        // if (feedViewModel != null && feedViewModel.isDataLoaded() && !feedViewModel.getAllPinterestNotes().isEmpty()) { // Original logic
        //     pinterestFeedAdapter.setNotes(feedViewModel.getAllPinterestNotes());
        //     if (currentLocation == null) {
        //          fetchLocationSilently();
        //     }
        // } else {
        //     loadUserLocation();
        // }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            loadUserLocation();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        
       // Check if data already exists in ViewModel
       if (feedViewModel.isDataLoaded() && !feedViewModel.getAllPinterestNotes().isEmpty()) {
           pinterestFeedAdapter.setNotes(feedViewModel.getAllPinterestNotes());
           pbLoading.setVisibility(View.GONE);
           shimmerContainer.setVisibility(View.GONE);
           if (pulseAnimator != null) pulseAnimator.cancel();
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
        pinterestFeedAdapter = new PinterestFeedAdapter(note -> {
             if (noteClickListener != null) noteClickListener.onNoteClick(note);
        });
        
        StaggeredGridLayoutManager layoutManager = new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
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
                
                if (!feedViewModel.isLoading() && !feedViewModel.isLastPage() && totalItemCount <= (lastVisibleItem + 2)) {
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
        swipeRefresh.setOnRefreshListener(this::loadUserLocation);
    }
    
    private void loadUserLocation() {
        if (getActivity() == null) return;
        
        try {
             fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                currentLocation = location;
                loadPinterestFeed(false);
            }).addOnFailureListener(e -> {
                Log.e(TAG, "Error getting location", e);
                loadPinterestFeed(false);
            });
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception getting location", e);
            loadPinterestFeed(false);
        }
    }

    private void loadPinterestFeed(boolean isNextPage) {
        if (auth.getCurrentUser() == null) return;
        if (feedViewModel == null) return;
        if (feedViewModel.isLoading()) return;
        
        feedViewModel.setLoading(true);
        
        if (!isNextPage) {
            swipeRefresh.setRefreshing(true);
            feedViewModel.setLastPage(false);
            feedViewModel.setLastVisible(null);
            
            // Start Shimmer for initial load
            startShimmer();
        } else {
             // Use Adapter Footer for pagination, NOT the center spinner
             pinterestFeedAdapter.setLoading(true);
             // pbLoading.setVisibility(View.VISIBLE); // REMOVED
        }
        
        // Original "Load Everything" Logic (Fetch 50)
        Query query = db.collection("notes")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .orderBy("__name__", Query.Direction.DESCENDING) // Keep stability
            .limit(50);
            
        if (isNextPage && feedViewModel.getLastVisible() != null) {
            query = query.startAfter(feedViewModel.getLastVisible());
        }
            
        query.get().addOnSuccessListener(queryDocumentSnapshots -> {
            boolean isEmpty = queryDocumentSnapshots.isEmpty();
            
            if (isEmpty) {
                feedViewModel.setLastPage(true);
                feedViewModel.setLoading(false);
                swipeRefresh.setRefreshing(false); 
                pbLoading.setVisibility(View.GONE);
                if (!isNextPage) stopShimmer(); // Stop even if empty
                return;
            }
            
            feedViewModel.setLastVisible(queryDocumentSnapshots.getDocuments().get(queryDocumentSnapshots.size() - 1));
            // Ensure we strictly check fetched size
            if (queryDocumentSnapshots.size() < 50) feedViewModel.setLastPage(true);
            
            // Offload parsing to background thread to prevent UI freeze
            new Thread(() -> {
                List<NearbyNote> fetchedNotes = new ArrayList<>();
                try {
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                         String b64 = doc.getString("imageBase64");
                         GeoPoint location = doc.getGeoPoint("location");
                         // Check for Number vs String issues
                         int imgWidth = 0;
                         int imgHeight = 0;
                         Object wObj = doc.get("imageWidth");
                         Object hObj = doc.get("imageHeight");
                         if (wObj instanceof Number) imgWidth = ((Number) wObj).intValue();
                         else if (wObj instanceof String) try { imgWidth = Integer.parseInt((String) wObj); } catch(Exception e){}
                         
                         if (hObj instanceof Number) imgHeight = ((Number) hObj).intValue();
                         else if (hObj instanceof String) try { imgHeight = Integer.parseInt((String) hObj); } catch(Exception e){}
                         
                         double distance = 0;
                         if (location != null && currentLocation != null) {
                             distance = calculateDistance(
                                 currentLocation.getLatitude(), 
                                 currentLocation.getLongitude(),
                                 location.getLatitude(), 
                                 location.getLongitude()
                             );
                         }
                         
                         final NearbyNote note = new NearbyNote();
                         note.setId(doc.getId());
                         String text = doc.getString("text");
                         if (text == null) text = doc.getString("note");
                         note.setText(text);
                         note.setSummary(doc.getString("summary"));
                         note.setUserId(doc.getString("userId"));
                         note.setUserName(doc.getString("userName"));
                         note.setUserProfilePic(doc.getString("userProfilePic"));
                         note.setLat(location != null ? location.getLatitude() : 0);
                         note.setLng(location != null ? location.getLongitude() : 0);
                         note.setTimestamp(doc.getLong("timestamp") != null ? doc.getLong("timestamp") : 0);
                         note.setImageBase64(b64);
                         note.setImageWidth(imgWidth);
                         note.setImageHeight(imgHeight);
                         
                         // Robust Like Count Parsing
                         int likes = 0;
                         Object likesObj = doc.get("likesCount");
                         if (likesObj == null) likesObj = doc.get("likeCount");
                         
                         if (likesObj instanceof Number) likes = ((Number) likesObj).intValue();
                         else if (likesObj instanceof String) try { likes = Integer.parseInt((String) likesObj); } catch(Exception e){}
                         
                         note.setLikesCount(likes);
                         note.setDistance(distance);
                         
                         fetchedNotes.add(note);
                    }
                    
                    // 1. SHUFFLE EVERYTHING -> True Randomness
                    Collections.shuffle(fetchedNotes);
                    
                    // 2. Insert Fidget Boxes at fixed intervals - RANDOM TYPES
                    String[] types = {"bubble", "spinner", "switch", "gravity"};
                    
                    if (fetchedNotes.size() > 12) {
                        NearbyNote f1 = new NearbyNote(); 
                        String type = types[(int)(Math.random() * types.length)];
                        f1.setId("fidget_" + type + "_" + System.currentTimeMillis() + "_1"); 
                        fetchedNotes.add(12, f1);
                    }
                    if (fetchedNotes.size() > 26) {
                        NearbyNote f2 = new NearbyNote(); 
                        String type = types[(int)(Math.random() * types.length)];
                        f2.setId("fidget_" + type + "_" + System.currentTimeMillis() + "_2");
                        fetchedNotes.add(26, f2);
                    }
                     if (fetchedNotes.size() > 40) {
                        NearbyNote f3 = new NearbyNote(); 
                        String type = types[(int)(Math.random() * types.length)];
                        f3.setId("fidget_" + type + "_" + System.currentTimeMillis() + "_3");
                        fetchedNotes.add(40, f3);
                    }
                    
                    // 3. OPTIMIZE LAYOUT TO REDUCE GAPS (Tetris)
                    optimizeItemOrder(fetchedNotes);

                } catch (Exception e) {
                    Log.e(TAG, "Error parsing notes in bg", e);
                }
                
                // Back to Main Thread
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        // 3. Update ViewModel & Adapter
                         if (!isNextPage) {
                            feedViewModel.clear();
                        }
                        
                        // Deduplicate
                        List<NearbyNote> uniqueNotes = new ArrayList<>();
                        for (NearbyNote n : fetchedNotes) {
                            if (n.getId() == null) continue; 
                            
                            // Allow fidgets
                            if (n.getId().startsWith("fidget")) {
                                uniqueNotes.add(n);
                            } else if (!feedViewModel.getLoadedNoteIds().contains(n.getId())) {
                                feedViewModel.getLoadedNoteIds().add(n.getId());
                                uniqueNotes.add(n);
                            }
                        }
                        
                        feedViewModel.getAllPinterestNotes().addAll(uniqueNotes);
                        feedViewModel.setDataLoaded(true);
                        
                        // STOP SHIMMER HERE
                        if (!isNextPage) {
                            pinterestFeedAdapter.setNotes(feedViewModel.getAllPinterestNotes());
                            stopShimmer();
                        } else {
                            pinterestFeedAdapter.addNotes(uniqueNotes);
                        }
                        
                        feedViewModel.setLoading(false);
                        pinterestFeedAdapter.setLoading(false); // Hide footer
                        swipeRefresh.setRefreshing(false);
                        pbLoading.setVisibility(View.GONE); 
                    });
                }
            }).start();
        })
        .addOnFailureListener(e -> {
            Log.e(TAG, "Error loading feed", e);
            feedViewModel.setLoading(false);
            pinterestFeedAdapter.setLoading(false); // Hide footer
            swipeRefresh.setRefreshing(false);
            pbLoading.setVisibility(View.GONE); 
            if (!isNextPage) stopShimmer(); // Ensure we don't get stuck
        });
    }
    
    private void optimizeItemOrder(List<NearbyNote> notes) {
        // Simulate StaggeredGridLayoutManager (Gap Strategy NONE)
        double col1Height = 0;
        double col2Height = 0;
        
        for (int i = 0; i < notes.size(); i++) {
            NearbyNote item = notes.get(i);
            boolean isFullSpan = isFullSpan(item);
            
            // Aspect Ratio (approximate height logic)
            double itemHeight = 1.0; 
            if (item.getImageWidth() > 0 && item.getImageHeight() > 0) {
                 itemHeight = (double) item.getImageHeight() / item.getImageWidth();
            } else if (item.getId() != null && item.getId().startsWith("fidget")) {
                itemHeight = 0.8; // Fidgets usually squat
            } else {
                itemHeight = 0.3; // Text notes usually short
            }
            
            // If full span, check for gap
            if (isFullSpan) {
                double diff = Math.abs(col1Height - col2Height);
                // If significant gap (> 0.4 aspect ratio, roughly half an image)
                if (diff > 0.4) {
                    // Find a filler (non-full-span) from upcoming items
                    int fillerIndex = -1;
                    for (int j = i + 1; j < Math.min(notes.size(), i + 10); j++) { // Look ahead 10
                         if (!isFullSpan(notes.get(j))) {
                             fillerIndex = j;
                             break;
                         }
                    }
                    
                    if (fillerIndex != -1) {
                         // Swap
                         NearbyNote filler = notes.get(fillerIndex);
                         notes.set(fillerIndex, item);
                         notes.set(i, filler);
                         
                         // Recalculate component for newly placed FIRST item (the filler)
                         item = filler;
                         isFullSpan = false;
                         if (item.getImageWidth() > 0 && item.getImageHeight() > 0) {
                             itemHeight = (double) item.getImageHeight() / item.getImageWidth();
                         } else {
                             itemHeight = 0.3; 
                         }
                         // Fall through to place the filler normally
                    }
                }
            }
            
            // Place Item
            if (isFullSpan) {
                // Takes max height of both, adds its height
                double maxHeight = Math.max(col1Height, col2Height);
                col1Height = maxHeight + itemHeight;
                col2Height = maxHeight + itemHeight;
            } else {
                // Adds to shortest column
                if (col1Height <= col2Height) {
                    col1Height += itemHeight;
                } else {
                    col2Height += itemHeight;
                }
            }
        }
    }
    
    private boolean isFullSpan(NearbyNote note) {
        if (note.getId() != null) {
            if (note.getId().startsWith("fidget_switch") || note.getId().startsWith("fidget_gravity")) {
                return true;
            }
            if (note.getId().startsWith("fidget")) return false; // Other fidgets are half
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
}
