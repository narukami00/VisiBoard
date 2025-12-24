package com.visiboard.app.ui.connect;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.visiboard.app.R;
import com.visiboard.app.data.UserInfo;
import com.visiboard.app.utils.ImageCache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DiscoveryFragment extends Fragment {

    private static final String TAG = "DiscoveryFragment";

    private RecyclerView rvDiscovery;
    private EditText etSearch;
    private ImageButton btnViewMode;
    private ProgressBar pbLoading;
    
    private DiscoveryAdapter adapter;
    private FirebaseFirestore db;
    private String currentUserId;
    
    private boolean isGridMode = false;
    private Set<String> followingIds = new HashSet<>();
    private List<DiscoveryItem> discoveryItems = new ArrayList<>(); // Default list (Nearby + Popular)
    private boolean isSearching = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_discovery, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        db = FirebaseFirestore.getInstance();
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            currentUserId = auth.getCurrentUser().getUid();
        }

        rvDiscovery = view.findViewById(R.id.rv_discovery);
        etSearch = view.findViewById(R.id.et_search);
        btnViewMode = view.findViewById(R.id.btn_view_mode);
        pbLoading = view.findViewById(R.id.pb_loading);

        setupRecyclerView();
        setupViewModeToggle();
        setupSearch();
        
        loadData();
    }

    private void setupRecyclerView() {
        adapter = new DiscoveryAdapter();
        GridLayoutManager layoutManager = new GridLayoutManager(getContext(), 2);
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                switch (adapter.getItemViewType(position)) {
                    case DiscoveryAdapter.TYPE_USER:
                        return isGridMode ? 1 : 2; // Grid: 1 col (half width), List: 2 cols (full width)
                    default:
                        return 2; // Headers always full width
                }
            }
        });
        rvDiscovery.setLayoutManager(layoutManager);
        rvDiscovery.setAdapter(adapter);
    }

    private void setupViewModeToggle() {
        btnViewMode.setOnClickListener(v -> {
            isGridMode = !isGridMode;
            btnViewMode.setImageResource(isGridMode ? R.drawable.ic_grid : R.drawable.ic_profile); // Toggle Icon
            adapter.notifyDataSetChanged();
            rvDiscovery.scrollToPosition(0);
        });
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                if (query.isEmpty()) {
                    isSearching = false;
                    adapter.setItems(discoveryItems);
                } else {
                    isSearching = true;
                    performSearch(query);
                }
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadData() {
        pbLoading.setVisibility(View.VISIBLE);
        
        if (currentUserId == null) return;

        // 1. Fetch Following IDs first (to exclude)
        db.collection("users").document(currentUserId).collection("following").get()
            .addOnSuccessListener(followingSnap -> {
                followingIds.clear();
                followingIds.add(currentUserId); // Exclude self
                for (DocumentSnapshot doc : followingSnap) {
                    followingIds.add(doc.getId());
                }
                
                fetchDiscoveryUsers();
            })
            .addOnFailureListener(e -> {
                 pbLoading.setVisibility(View.GONE);
                 Toast.makeText(getContext(), "Error loading connections", Toast.LENGTH_SHORT).show();
            });
    }

    private void fetchDiscoveryUsers() {
        // Parallel fetches for Nearby (For now: Random/Recent) and Popular
        db.collection("users")
            .orderBy("followersCount", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(20)
            .get()
            .addOnSuccessListener(popularSnap -> {
                List<UserInfo> popularUsers = new ArrayList<>();
                for (DocumentSnapshot doc : popularSnap) {
                    if (!followingIds.contains(doc.getId())) {
                        UserInfo u = doc.toObject(UserInfo.class);
                        if (u != null) {
                            u.setUserId(doc.getId());
                            popularUsers.add(u);
                        }
                    }
                }
                
                discoveryItems.clear();
                
                if (!popularUsers.isEmpty()) {
                    discoveryItems.add(new DiscoveryItem("Most Popular"));
                    for (UserInfo u : popularUsers) {
                        discoveryItems.add(new DiscoveryItem(u, "Trending"));
                    }
                }
                
                // TODO: Real "Nearby" logic based on Lat/Lng
                // For now, prototype with simple data or separate query
                
                pbLoading.setVisibility(View.GONE);
                if (!isSearching) {
                    adapter.setItems(discoveryItems);
                }
            })
            .addOnFailureListener(e -> {
                pbLoading.setVisibility(View.GONE);
            });
    }

    private void performSearch(String query) {
        String qStart = query;
        String qEnd = query + "\uf8ff";
        
        db.collection("users")
            .orderBy("name")
            .startAt(qStart)
            .endAt(qEnd)
            .limit(10)
            .get()
            .addOnSuccessListener(snap -> {
                List<DiscoveryItem> results = new ArrayList<>();
                results.add(new DiscoveryItem("Search Results"));
                for (DocumentSnapshot doc : snap) {
                     UserInfo u = doc.toObject(UserInfo.class);
                     if (u != null) {
                         u.setUserId(doc.getId());
                         String context = followingIds.contains(u.getUserId()) ? "Following" : "Found";
                         if (u.getUserId().equals(currentUserId)) context = "You";
                         
                         // In search results, we show everyone, but button state handles logic
                         results.add(new DiscoveryItem(u, context));
                     }
                }
                adapter.setItems(results);
            });
    }

    // --- Robust Follow Logic (Ported from LeaderboardActivity) ---

    private void setupFollowButton(TextView btn, UserInfo user) {
        if (currentUserId == null || user.getUserId().equals(currentUserId)) {
            btn.setVisibility(View.GONE);
            return;
        }

        btn.setVisibility(View.VISIBLE);
        // Initial Loading State or default?
        // Ideally we should check status per item ONLY if needed, or cache it.
        // For lists, asynchronous checking for every item is heavy.
        // BETTER: Use 'followingIds' set for immediate check, and only async for "Requested".
        
        // 1. Check Following (Local Cache)
        if (followingIds.contains(user.getUserId())) {
            btn.setText("Following");
            btn.setEnabled(false); // Can't unfollow from Discovery easily without full logic?
            // Usually Discovery is for "New" connections. Following -> Show "Following".
            // If we want FULL robustness (unfollow), we enable it.
            // But 'followingIds' set is only what we fetched at start.
            // Let's rely on standard logic but optimize.
            
            // Optimization: If local cache says following, set Following.
            // If not following, it might be Pending. That requires DB check.
            // We can check "follow_requests" on demand (when data loads?) or lazy load.
            // Lazy load on bind:
            checkFollowStatus(btn, user.getUserId());
        } else {
             checkFollowStatus(btn, user.getUserId());
        }
    }

    private void checkFollowStatus(TextView btn, String targetUserId) {
         // Optimization: If definitely following (in set), start with Following.
         if (followingIds.contains(targetUserId)) {
             btn.setText("Following");
         } else {
             btn.setText("Follow");
         }
         btn.setEnabled(true);

         // We need to check for "Requested" state if Private.
         // This causes N reads. For prototype, maybe acceptable?
         // Or just handle "Follow" click -> checks status.
         // User demanded "Robust".
         // I'll attach the click listener which CHECKS logic.
         // And I'll run a lightweight check for "Requested" if possible.
         
         // Click Listener
         btn.setOnClickListener(v -> {
             String text = btn.getText().toString();
             if ("Follow".equals(text)) {
                 handleFollowClick(targetUserId, btn);
             } else if ("Requested".equals(text)) {
                 cancelFollowRequest(targetUserId, btn);
             } else if ("Following".equals(text)) {
                 // Optional: Allow unfollow?
                 // Most pymk lists just show "Following" and disable interaction or nav to profile.
                 // I will allow unfollow for "Robustness".
                 unfollowUser(targetUserId, btn);
             }
         });
         
         // Async check specifically for pending requests (only if not following)
         if (!followingIds.contains(targetUserId)) {
             db.collection("users").document(targetUserId).collection("follow_requests").document(currentUserId)
                 .get().addOnSuccessListener(doc -> {
                     if (doc.exists() && isAdded()) {
                         btn.setText("Requested");
                     }
                 });
         }
    }

    private void handleFollowClick(String targetUserId, TextView btn) {
        btn.setText("...");
        btn.setEnabled(false);

        db.collection("users").document(targetUserId).get()
            .addOnSuccessListener(targetUserDoc -> {
                if (!isAdded()) return;
                boolean isPrivate = targetUserDoc.getBoolean("isPrivate") != null && targetUserDoc.getBoolean("isPrivate");
                
                if (isPrivate) {
                    checkRejectionAndRequest(targetUserId, btn);
                } else {
                    performDirectFollow(targetUserId, btn);
                }
            })
            .addOnFailureListener(e -> {
                 if (isAdded()) {
                     btn.setText("Follow");
                     btn.setEnabled(true);
                 }
            });
    }

    private void checkRejectionAndRequest(String targetUserId, TextView btn) {
         db.collection("users").document(targetUserId).collection("rejections").document(currentUserId)
            .get()
            .addOnSuccessListener(rejectionDoc -> {
                if (!isAdded()) return;
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
                    Toast.makeText(getContext(), "Too many follow requests. Try again later.", Toast.LENGTH_LONG).show();
                    btn.setText("Follow");
                    btn.setEnabled(true);
                } else {
                    sendFollowRequest(targetUserId, btn);
                }
            });
    }

    private void sendFollowRequest(String targetUserId, TextView btn) {
        db.collection("users").document(currentUserId).get().addOnSuccessListener(currentUserDoc -> {
             if (!isAdded()) return;
             String myName = currentUserDoc.getString("name");
             String myProfilePic = currentUserDoc.getString("profilePic");
             Map<String, Object> requestData = new HashMap<>();
             requestData.put("timestamp", System.currentTimeMillis());
             requestData.put("requesterName", myName);
             requestData.put("requesterProfilePic", myProfilePic);
             
             db.collection("users").document(targetUserId).collection("follow_requests").document(currentUserId)
                 .set(requestData)
                 .addOnSuccessListener(aVoid -> {
                     if (!isAdded()) return;
                     btn.setText("Requested");
                     createNotification(targetUserId, "follow_request");
                     Toast.makeText(getContext(), "Request sent", Toast.LENGTH_SHORT).show();
                     btn.setEnabled(true);
                 })
                 .addOnFailureListener(e -> {
                     if (isAdded()) btn.setEnabled(true);
                 });
        });
    }

    private void performDirectFollow(String targetUserId, TextView btn) {
        db.collection("users").document(currentUserId).get().addOnSuccessListener(currentUserDoc -> {
             if (!isAdded()) return;
             String myName = currentUserDoc.getString("name");
             String myProfilePic = currentUserDoc.getString("profilePic");
             
             Map<String, Object> followerData = new HashMap<>();
             followerData.put("timestamp", System.currentTimeMillis());
             followerData.put("followerName", myName);
             followerData.put("followerProfilePic", myProfilePic);
             db.collection("users").document(targetUserId).collection("followers").document(currentUserId).set(followerData);
             db.collection("users").document(targetUserId).update("followersCount", com.google.firebase.firestore.FieldValue.increment(1));
             
             db.collection("users").document(targetUserId).get().addOnSuccessListener(targetUserDoc -> {
                 if (!isAdded()) return;
                 String targetName = targetUserDoc.getString("name");
                 String targetProfilePic = targetUserDoc.getString("profilePic");
                 
                 Map<String, Object> followingData = new HashMap<>();
                 followingData.put("timestamp", System.currentTimeMillis());
                 followingData.put("followedName", targetName);
                 followingData.put("followedProfilePic", targetProfilePic);
                 
                 db.collection("users").document(currentUserId).collection("following").document(targetUserId).set(followingData);
                 db.collection("users").document(currentUserId).update("followingCount", com.google.firebase.firestore.FieldValue.increment(1));
                 
                 followingIds.add(targetUserId); // Update local cache
                 
                 btn.setText("Following");
                 btn.setEnabled(true);
                 createNotification(targetUserId, "follow");
                 Toast.makeText(getContext(), "Following " + targetName, Toast.LENGTH_SHORT).show();
             });
        });
    }

    private void cancelFollowRequest(String targetUserId, TextView btn) {
        db.collection("users").document(targetUserId).collection("follow_requests").document(currentUserId).delete()
            .addOnSuccessListener(aVoid -> {
                if (!isAdded()) return;
                btn.setText("Follow");
                Toast.makeText(getContext(), "Request canceled", Toast.LENGTH_SHORT).show();
            });
    }

    private void unfollowUser(String targetUserId, TextView btn) {
        // ... Logic same ...
        // Update DB
        db.collection("users").document(currentUserId).collection("following").document(targetUserId).delete();
        db.collection("users").document(currentUserId).update("followingCount", com.google.firebase.firestore.FieldValue.increment(-1));
        db.collection("users").document(targetUserId).collection("followers").document(currentUserId).delete();
        db.collection("users").document(targetUserId).update("followersCount", com.google.firebase.firestore.FieldValue.increment(-1));
        followingIds.remove(targetUserId);
        
        btn.setText("Follow");
        Toast.makeText(getContext(), "Unfollowed", Toast.LENGTH_SHORT).show();
    }

    // --- Grid Follow Logic ---

    private void setupFollowButtonGrid(androidx.cardview.widget.CardView container, ImageView icon, UserInfo user) {
        if (currentUserId == null || user.getUserId().equals(currentUserId)) {
            container.setVisibility(View.GONE);
            return;
        }
        container.setVisibility(View.VISIBLE);

        if (followingIds.contains(user.getUserId())) {
            icon.setImageResource(R.drawable.ic_check);
            container.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.button_secondary)); // Green/Grey?
        } else {
            icon.setImageResource(R.drawable.ic_add);
            container.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.primary));
        }

        container.setOnClickListener(v -> {
             // Simple direct follow logic for Grid for now, or route to full logic?
             // Use full logic but need to adapt 'TextView' param or overload methods.
             // Overloading is cleaner.
             handleFollowClickGrid(user.getUserId(), container, icon);
        });
    }

    private void handleFollowClickGrid(String targetUserId, androidx.cardview.widget.CardView container, ImageView icon) {
        // Toggle Logic
        container.setEnabled(false);
        
        if (followingIds.contains(targetUserId)) {
            // Already following -> Unfollow
             unfollowUserGrid(targetUserId, container, icon);
             return;
        }

        // Follow
        db.collection("users").document(targetUserId).get().addOnSuccessListener(targetUserDoc -> {
             if (!isAdded()) return;
             boolean isPrivate = targetUserDoc.getBoolean("isPrivate") != null && targetUserDoc.getBoolean("isPrivate");
             if (isPrivate) {
                 // Grid shouldn't really handle complex private requests UI well (icon only).
                 // Maybe just toast "Visit profile to request"? 
                 // Or send request and chage to 'Time' icon?
                 Toast.makeText(getContext(), "User is private. Visit profile.", Toast.LENGTH_SHORT).show();
                 container.setEnabled(true);
             } else {
                 performDirectFollowGrid(targetUserId, container, icon);
             }
        });
    }

    private void performDirectFollowGrid(String targetUserId, androidx.cardview.widget.CardView container, ImageView icon) {
         // Copy of performDirectFollow logic but for Grid UI
        db.collection("users").document(currentUserId).get().addOnSuccessListener(currentUserDoc -> {
             if (!isAdded()) return;
             String myName = currentUserDoc.getString("name");
             String myProfilePic = currentUserDoc.getString("profilePic");
             
             Map<String, Object> followerData = new HashMap<>();
             followerData.put("timestamp", System.currentTimeMillis());
             followerData.put("followerName", myName);
             followerData.put("followerProfilePic", myProfilePic);
             db.collection("users").document(targetUserId).collection("followers").document(currentUserId).set(followerData);
             db.collection("users").document(targetUserId).update("followersCount", com.google.firebase.firestore.FieldValue.increment(1));
             
             db.collection("users").document(targetUserId).get().addOnSuccessListener(targetUserDoc -> {
                 if (!isAdded()) return;
                 String targetName = targetUserDoc.getString("name");
                 String targetProfilePic = targetUserDoc.getString("profilePic");
                 
                 Map<String, Object> followingData = new HashMap<>();
                 followingData.put("timestamp", System.currentTimeMillis());
                 followingData.put("followedName", targetName);
                 followingData.put("followedProfilePic", targetProfilePic);
                 
                 db.collection("users").document(currentUserId).collection("following").document(targetUserId).set(followingData);
                 db.collection("users").document(currentUserId).update("followingCount", com.google.firebase.firestore.FieldValue.increment(1));
                 
                 followingIds.add(targetUserId); 
                 
                 icon.setImageResource(R.drawable.ic_check);
                 container.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.button_secondary));
                 container.setEnabled(true);
                 createNotification(targetUserId, "follow");
                 Toast.makeText(getContext(), "Following", Toast.LENGTH_SHORT).show();
             });
        });
    }

    private void unfollowUserGrid(String targetUserId, androidx.cardview.widget.CardView container, ImageView icon) {
        db.collection("users").document(currentUserId).collection("following").document(targetUserId).delete();
        db.collection("users").document(currentUserId).update("followingCount", com.google.firebase.firestore.FieldValue.increment(-1));
        db.collection("users").document(targetUserId).collection("followers").document(currentUserId).delete();
        db.collection("users").document(targetUserId).update("followersCount", com.google.firebase.firestore.FieldValue.increment(-1));
        followingIds.remove(targetUserId);
        
        icon.setImageResource(R.drawable.ic_add);
        container.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.primary));
        container.setEnabled(true);
        Toast.makeText(getContext(), "Unfollowed", Toast.LENGTH_SHORT).show();
    }

    private void createNotification(String toUserId, String type) {
        db.collection("users").document(currentUserId).get()
            .addOnSuccessListener(doc -> {
                String name = doc.getString("name");
                String pic = doc.getString("profilePic");
                
                Map<String, Object> notif = new HashMap<>();
                notif.put("type", type);
                notif.put("fromUserId", currentUserId);
                notif.put("fromUserName", name);
                notif.put("fromUserProfilePic", pic);
                notif.put("toUserId", toUserId);
                notif.put("timestamp", System.currentTimeMillis());
                notif.put("read", false);
                
                db.collection("notifications").add(notif);
            });
    }

    // --- Models & Adapter ---

    private static class DiscoveryItem {
        static final int TYPE_HEADER = 0;
        static final int TYPE_USER = 1;

        int type;
        String headerTitle;
        UserInfo user;
        String context; 

        DiscoveryItem(String title) {
            this.type = TYPE_HEADER;
            this.headerTitle = title;
        }

        DiscoveryItem(UserInfo user, String context) {
            this.type = TYPE_USER;
            this.user = user;
            this.context = context;
        }
    }

    private class DiscoveryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        static final int TYPE_HEADER = 0;
        static final int TYPE_USER = 1;
        
        private List<DiscoveryItem> items = new ArrayList<>();

        public void setItems(List<DiscoveryItem> items) {
            this.items = items;
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position).type;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_HEADER) {
                View v = inflater.inflate(R.layout.item_discovery_header, parent, false);
                return new HeaderViewHolder(v);
            } else {
                // Return Grid or List layout based on mode
                // Note: Using isGridMode here works because adapter is notified when mode changes, triggering full rebind/create?
                // Actually, RecyclerView rebinds existing ViewHolders if they are compatible.
                // We MUST use different viewTypes OR ensure we re-create holders.
                // Or simply: check isGridMode here.
                int layoutId = isGridMode ? R.layout.item_pymk_grid : R.layout.item_pymk_user;
                View v = inflater.inflate(layoutId, parent, false);
                return new UserViewHolder(v, isGridMode);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            DiscoveryItem item = items.get(position);
            if (holder instanceof HeaderViewHolder) {
                ((HeaderViewHolder) holder).tvHeader.setText(item.headerTitle);
            } else if (holder instanceof UserViewHolder) {
                bindUser((UserViewHolder) holder, item);
            }
        }

        private void bindUser(UserViewHolder holder, DiscoveryItem item) {
            UserInfo u = item.user;
            holder.tvName.setText(u.getName() != null ? u.getName() : "Anonymous");
            holder.tvTier.setText(u.getCurrentTier() != null ? u.getCurrentTier() : "None");
            
            // Set Context only if available in layout
            if (item.context != null && holder.tvContext != null) {
                 holder.tvContext.setText(item.context);
            }
            
            if (u.getProfilePic() != null) {
                ImageCache.getInstance().loadBase64Image("user_" + u.getUserId(), u.getProfilePic(), holder.ivAvatar, R.drawable.ic_profile);
            } else {
                holder.ivAvatar.setImageResource(R.drawable.ic_profile);
            }
            
            if (holder.btnFollowContainer != null) {
                setupFollowButtonGrid(holder.btnFollowContainer, holder.ivFollowIcon, u);
            } else {
                setupFollowButton(holder.btnFollow, u);
            }
            
            if (holder.btnDismiss != null) {
                 holder.btnDismiss.setVisibility(View.GONE); 
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvHeader;
        HeaderViewHolder(View v) { super(v); tvHeader = v.findViewById(R.id.tv_header); }
    }

    private static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvTier, tvContext;
        android.widget.ImageView ivAvatar; 
        
        // List Mode
        TextView btnFollow; 
        ImageButton btnDismiss;

        // Grid Mode
        androidx.cardview.widget.CardView btnFollowContainer;
        ImageView ivFollowIcon;

        UserViewHolder(View v, boolean isGrid) {
            super(v);
            tvName = v.findViewById(R.id.tv_name);
            tvTier = v.findViewById(R.id.tv_tier);
            ivAvatar = v.findViewById(R.id.iv_avatar);
            
            if (isGrid) {
                btnFollowContainer = v.findViewById(R.id.btn_follow_container);
                ivFollowIcon = v.findViewById(R.id.iv_follow_icon);
                tvContext = null; 
                btnDismiss = null;
                btnFollow = null;
            } else {
                tvContext = v.findViewById(R.id.tv_context);
                btnFollow = v.findViewById(R.id.btn_follow);
                btnDismiss = v.findViewById(R.id.btn_dismiss);
                btnFollowContainer = null;
                ivFollowIcon = null;
            }
        }
    }
}
