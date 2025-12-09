package com.visiboard.app.ui.feed;

import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
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
    private static final int PAGE_SIZE = 10;

    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView rvPinterestFeed;
    private ProgressBar pbLoading;
    
    private PinterestFeedAdapter pinterestFeedAdapter;
    private List<NearbyNote> allPinterestNotes = new ArrayList<>();
    private java.util.Set<String> loadedNoteIds = new java.util.HashSet<>();
    
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private FusedLocationProviderClient fusedLocationClient;
    private Location currentLocation;
    
    private NoteClickListener noteClickListener;
    
    // Pagination
    private DocumentSnapshot lastVisible;
    private boolean isLoading = false;
    private boolean isLastPage = false;

    public interface NoteClickListener {
        void onNoteClick(NearbyNote note);
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
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_discover_tab, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        
        swipeRefresh = view.findViewById(R.id.swipe_refresh_discover);
        rvPinterestFeed = view.findViewById(R.id.rv_pinterest_feed_tab);
        pbLoading = view.findViewById(R.id.pb_loading_discover);
        
        setupRecyclerView();
        setupSwipeRefresh();
        loadUserLocation();
    }
    
    private void setupRecyclerView() {
        StaggeredGridLayoutManager layoutManager = new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        rvPinterestFeed.setLayoutManager(layoutManager);
        pinterestFeedAdapter = new PinterestFeedAdapter(note -> {
            if (noteClickListener != null) {
                noteClickListener.onNoteClick(note);
            }
        });
        rvPinterestFeed.setAdapter(pinterestFeedAdapter);
        
        rvPinterestFeed.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                
                int[] lastVisibleItemPositions = new int[2];
                layoutManager.findLastVisibleItemPositions(lastVisibleItemPositions);
                int lastVisibleItem = Math.max(lastVisibleItemPositions[0], lastVisibleItemPositions[1]);
                int totalItemCount = layoutManager.getItemCount();
                
                if (!isLoading && !isLastPage && totalItemCount <= (lastVisibleItem + 4)) {
                    loadPinterestFeed(true);
                }
            }
        });
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
                // Load feed anyway, distance will be 0
                loadPinterestFeed(false);
            });
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception getting location", e);
            loadPinterestFeed(false);
        }
    }
    
    private void loadPinterestFeed(boolean isNextPage) {
        if (auth.getCurrentUser() == null) return;
        if (isLoading) return;
        
        isLoading = true;
        if (!isNextPage) {
            swipeRefresh.setRefreshing(true);
            isLastPage = false;
            lastVisible = null;
        } else {
            pinterestFeedAdapter.setLoading(true);
        }
        
        Query query = db.collection("notes")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50);
            
        if (isNextPage && lastVisible != null) {
            query = query.startAfter(lastVisible);
        }
            
        query.get().addOnSuccessListener(queryDocumentSnapshots -> {
            // Retrieve data
            boolean isEmpty = queryDocumentSnapshots.isEmpty();
            
            if (isNextPage) {
                pinterestFeedAdapter.setLoading(false);
            }
            swipeRefresh.setRefreshing(false);
            pbLoading.setVisibility(View.GONE); 
            
            if (isEmpty) {
                isLastPage = true;
                isLoading = false;
                if (!isNextPage) {
                     allPinterestNotes.clear();
                     loadedNoteIds.clear();
                     pinterestFeedAdapter.setNotes(allPinterestNotes);
                }
                return;
            }
            
            lastVisible = queryDocumentSnapshots.getDocuments().get(queryDocumentSnapshots.size() - 1);
            if (queryDocumentSnapshots.size() < 50) isLastPage = true;
            
            List<NearbyNote> imageNotes = new ArrayList<>();
            List<NearbyNote> textNotes = new ArrayList<>();
            
            for (DocumentSnapshot doc : queryDocumentSnapshots) {
                try {
                    String b64 = doc.getString("imageBase64");
                    GeoPoint location = doc.getGeoPoint("location");
                    Long imgWidth = doc.getLong("imageWidth");
                    Long imgHeight = doc.getLong("imageHeight");
                    
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
                    note.setImageWidth(imgWidth != null ? imgWidth.intValue() : 0);
                    note.setImageHeight(imgHeight != null ? imgHeight.intValue() : 0);
                    Long likesCount = doc.getLong("likesCount");
                    if (likesCount == null) likesCount = doc.getLong("likeCount");
                    note.setLikesCount(likesCount != null ? likesCount.intValue() : 0);
                    note.setDistance(distance);
                    
                    if (b64 != null && !b64.isEmpty()) {
                        imageNotes.add(note);
                    } else {
                        if (text != null && !text.isEmpty()) {
                            textNotes.add(note);
                        }
                    }

                } catch (Exception e) {
                    Log.e(TAG, "Error parsing note: " + e.getMessage());
                }
            }
            
            // Logic: Include all images, and a random subset of text notes (approx 1 text per 2 images)
            List<NearbyNote> pageFeed = new ArrayList<>(imageNotes);
            Collections.shuffle(textNotes);
            
            int maxTextNotes = Math.max(2, imageNotes.size() / 2);
            if (textNotes.size() > maxTextNotes) {
                pageFeed.addAll(textNotes.subList(0, maxTextNotes));
            } else {
                pageFeed.addAll(textNotes);
            }
            
            Collections.shuffle(pageFeed); // Shuffle everything for that Pinterest look
            
            // Deduplicate
            if (!isNextPage) {
                allPinterestNotes.clear();
                loadedNoteIds.clear();
            }
            
            List<NearbyNote> uniqueNotes = new ArrayList<>();
            for (NearbyNote n : pageFeed) {
                if (!loadedNoteIds.contains(n.getId())) {
                    loadedNoteIds.add(n.getId());
                    uniqueNotes.add(n);
                }
            }
            
            allPinterestNotes.addAll(uniqueNotes);
            
            if (!isNextPage) {
                pinterestFeedAdapter.setNotes(allPinterestNotes);
            } else {
                pinterestFeedAdapter.addNotes(uniqueNotes);
            }
            
            isLoading = false;
        })
        .addOnFailureListener(e -> {
            Log.e(TAG, "Error loading feed", e);
            isLoading = false;
            
            if (isNextPage) {
                pinterestFeedAdapter.setLoading(false);
            }
            swipeRefresh.setRefreshing(false);
            pbLoading.setVisibility(View.GONE);
            if (getContext() != null)
                Toast.makeText(getContext(), "Failed to load feed", Toast.LENGTH_SHORT).show();
        });
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
