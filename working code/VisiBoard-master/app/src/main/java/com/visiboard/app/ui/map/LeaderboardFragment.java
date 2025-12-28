package com.visiboard.app.ui.map;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.visiboard.app.R;
import com.visiboard.app.data.UserInfo;

import java.util.ArrayList;
import java.util.List;

public class LeaderboardFragment extends Fragment {

    private static final String ARG_IS_LOCAL = "is_local";
    private static final String TAG = "LeaderboardFragment";

    private boolean isLocal;
    private RecyclerView rvLegends;
    private ProgressBar pbLoading;
    private View emptyState;
    private LegendAdapter adapter;
    private FirebaseFirestore db;
    private UserClickListener callback;

    public interface UserClickListener {
        void onUserClick(UserInfo user);
    }

    public static LeaderboardFragment newInstance(boolean isLocal) {
        LeaderboardFragment fragment = new LeaderboardFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_IS_LOCAL, isLocal);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof UserClickListener) {
            callback = (UserClickListener) context;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            isLocal = getArguments().getBoolean(ARG_IS_LOCAL);
        }
        db = FirebaseFirestore.getInstance();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_leaderboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        rvLegends = view.findViewById(R.id.rv_legends);
        pbLoading = view.findViewById(R.id.pb_loading);
        emptyState = view.findViewById(R.id.empty_state);

        setupRecyclerView();
        loadData();
    }

    private void setupRecyclerView() {
        rvLegends.setLayoutManager(new LinearLayoutManager(getContext()));
        rvLegends.setHasFixedSize(true);
        
        adapter = new LegendAdapter(user -> {
            if (callback != null) callback.onUserClick(user);
        });
        rvLegends.setAdapter(adapter);
    }

    private void loadData() {
        pbLoading.setVisibility(View.VISIBLE);
        rvLegends.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);

        db.collection("users")
            .orderBy("totalLikes", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(100) // Get more users to filter from
            .get()
            .addOnSuccessListener(querySnapshot -> {
                if (!isAdded()) return;
                pbLoading.setVisibility(View.GONE);
                
                List<UserInfo> users = new ArrayList<>();
                for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                    UserInfo user = doc.toObject(UserInfo.class);
                    if (user != null) {
                        user.setUserId(doc.getId());
                        Long totalLikes = doc.getLong("totalLikes");
                        user.setTotalLikes(totalLikes != null ? totalLikes.intValue() : 0);
                        users.add(user);
                    }
                }

                if (isLocal) {
                     filterNearbyUsers(users);
                } else {
                     adapter.setCurrentLocation(null);
                     showUsers(users);
                }
            })
            .addOnFailureListener(e -> {
                if (!isAdded()) return;
                pbLoading.setVisibility(View.GONE);
                emptyState.setVisibility(View.VISIBLE);
                Log.e(TAG, "Error loading leaderboard", e);
            });
    }

    private void filterNearbyUsers(List<UserInfo> allUsers) {
        if (androidx.core.app.ActivityCompat.checkSelfPermission(requireContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            com.google.android.gms.location.FusedLocationProviderClient fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(requireActivity());
            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    adapter.setCurrentLocation(location);
                    List<UserInfo> nearbyUsers = new ArrayList<>();
                    float[] results = new float[1];
                    
                    for (UserInfo u : allUsers) {
                        if (u.getLat() != 0 && u.getLng() != 0) {
                            android.location.Location.distanceBetween(location.getLatitude(), location.getLongitude(), 
                                   u.getLat(), u.getLng(), results);
                            if (results[0] / 1000f <= 50) { // 50km
                                nearbyUsers.add(u);
                            }
                        }
                    }
                    if (nearbyUsers.isEmpty()) {
                        Toast.makeText(getContext(), "No legends around you (50km)", Toast.LENGTH_SHORT).show();
                    }
                    showUsers(nearbyUsers);
                } else {
                    Toast.makeText(getContext(), "Location unavailable for Nearby", Toast.LENGTH_SHORT).show();
                    showUsers(new ArrayList<>());
                }
            });
        } else {
            Toast.makeText(getContext(), "Location permission needed for Nearby", Toast.LENGTH_SHORT).show();
            showUsers(new ArrayList<>());
        }
    }

    private void showUsers(List<UserInfo> users) {
        if (users.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            rvLegends.setVisibility(View.GONE);
        } else {
            rvLegends.setVisibility(View.VISIBLE);
            adapter.setUsers(users);
            runLayoutAnimation();
        }
    }

    private void runLayoutAnimation() {
        final Context context = rvLegends.getContext();
        final LayoutAnimationController controller =
                AnimationUtils.loadLayoutAnimation(context, R.anim.layout_animation_fall_down);
        rvLegends.setLayoutAnimation(controller);
        rvLegends.getAdapter().notifyDataSetChanged();
        rvLegends.scheduleLayoutAnimation();
    }
}
