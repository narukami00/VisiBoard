package com.visiboard.app.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.visiboard.app.R;
import com.visiboard.app.data.UserInfo;
import com.visiboard.app.utils.ImageCache;

import java.util.ArrayList;
import java.util.List;

public class FollowRequestsActivity extends AppCompatActivity {

    private RecyclerView rvRequests;
    private View pbLoading;
    private View tvNoData;
    private androidx.appcompat.widget.Toolbar toolbar;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private RequestsAdapter adapter;

    private UserInfo currentUserInfo;
    private List<String> blockedUserIds = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_follow_requests);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        rvRequests = findViewById(R.id.rv_requests);
        pbLoading = findViewById(R.id.pb_loading);
        tvNoData = findViewById(R.id.tv_no_data);
        toolbar = findViewById(R.id.toolbar);

        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        rvRequests.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RequestsAdapter();
        rvRequests.setAdapter(adapter);

        loadCurrentUser();
        loadRequests();
    }

    private void loadCurrentUser() {
        if (auth.getCurrentUser() != null) {
            // Check cache first
            UserInfo cached = com.visiboard.app.utils.UserCache.getInstance().get(auth.getCurrentUser().getUid());
            if (cached != null) {
                currentUserInfo = cached;
            } else {
                db.collection("users").document(auth.getCurrentUser().getUid()).get()
                    .addOnSuccessListener(doc -> currentUserInfo = doc.toObject(UserInfo.class));
            }
        }
    }

    private void loadRequests() {
        String userId = auth.getCurrentUser().getUid();
        pbLoading.setVisibility(View.VISIBLE);
        tvNoData.setVisibility(View.GONE);

        // Load blocked users first
        db.collection("users").document(userId).collection("blocked_users").get()
            .addOnSuccessListener(blockedSnap -> {
                blockedUserIds.clear();
                for (DocumentSnapshot d : blockedSnap) blockedUserIds.add(d.getId());
                
                // Then load requests
                fetchFollowRequests(userId);
            })
            .addOnFailureListener(e -> {
                // Determine if we should fail open or closed. Let's fail open (load requests)
                fetchFollowRequests(userId);
            });
    }

    private void fetchFollowRequests(String userId) {
        db.collection("users").document(userId).collection("follow_requests")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    pbLoading.setVisibility(View.GONE);
                    List<RequestItem> items = new ArrayList<>();
                    if (queryDocumentSnapshots.isEmpty()) {
                        tvNoData.setVisibility(View.VISIBLE);
                    } else {
                        for (DocumentSnapshot doc : queryDocumentSnapshots) {
                            String requesterId = doc.getId();
                            
                            // Filter blocked users
                            if (blockedUserIds.contains(requesterId)) continue;
                            
                            String name = doc.getString("requesterName");
                            String pic = doc.getString("requesterProfilePic");
                            long timestamp = doc.getLong("timestamp") != null ? doc.getLong("timestamp") : 0L;
                            items.add(new RequestItem(requesterId, name, pic, timestamp));
                        }
                        
                        if (items.isEmpty()) {
                            tvNoData.setVisibility(View.VISIBLE);
                        } else {
                            adapter.setItems(items);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    pbLoading.setVisibility(View.GONE);
                    com.visiboard.app.utils.UiHelper.showError(findViewById(android.R.id.content), "Error loading requests");
                });
    }

    private void acceptRequest(RequestItem item) {
        if (currentUserInfo == null) {
            com.visiboard.app.utils.UiHelper.showInfo(findViewById(android.R.id.content), "Please wait, loading user info...");
            loadCurrentUser();
            return;
        }

        // Optimistic UI Update
        adapter.removeItem(item);

        String currentUserId = auth.getCurrentUser().getUid();
        String requesterId = item.id;

        com.google.firebase.firestore.WriteBatch batch = db.batch();
        
        // Add to my followers
        java.util.Map<String, Object> followerData = new java.util.HashMap<>();
        followerData.put("timestamp", System.currentTimeMillis());
        followerData.put("followerName", item.name);
        followerData.put("followerProfilePic", item.pic);
        batch.set(db.collection("users").document(currentUserId).collection("followers").document(requesterId), followerData);
        batch.update(db.collection("users").document(currentUserId), "followersCount", com.google.firebase.firestore.FieldValue.increment(1));

        // Add to their following
        java.util.Map<String, Object> followingData = new java.util.HashMap<>();
        followingData.put("timestamp", System.currentTimeMillis());
        followingData.put("followedName", currentUserInfo.getName());
        followingData.put("followedProfilePic", currentUserInfo.getProfilePic());
        batch.set(db.collection("users").document(requesterId).collection("following").document(currentUserId), followingData);
        batch.update(db.collection("users").document(requesterId), "followingCount", com.google.firebase.firestore.FieldValue.increment(1));

        // Remove Request
        batch.delete(db.collection("users").document(currentUserId).collection("follow_requests").document(requesterId));

        batch.commit().addOnSuccessListener(aVoid -> {
            com.visiboard.app.utils.UiHelper.showSuccess(findViewById(android.R.id.content), "Accepted");
            // Notify
            createNotification(requesterId, currentUserId, "follow_accepted");
        }).addOnFailureListener(e -> {
            com.visiboard.app.utils.UiHelper.showError(findViewById(android.R.id.content), "Failed to accept, undoing...");
            // Re-add item (simplified: just reload)
            loadRequests();
        });
    }

    private void deleteRequest(RequestItem item) {
        // Optimistic UI
        adapter.removeItem(item);
        
        String currentUserId = auth.getCurrentUser().getUid();
        db.collection("users").document(currentUserId).collection("follow_requests").document(item.id)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    com.visiboard.app.utils.UiHelper.showSuccess(findViewById(android.R.id.content), "Deleted");
                    // Record Rejection (5-Strike Rule)
                    recordRejection(currentUserId, item.id);
                })
                .addOnFailureListener(e -> {
                     com.visiboard.app.utils.UiHelper.showError(findViewById(android.R.id.content), "Failed to delete");
                     loadRequests();
                });
    }

    private void recordRejection(String targetId, String requesterId) {
        db.collection("users").document(targetId).collection("rejections").document(requesterId)
            .get()
            .addOnSuccessListener(doc -> {
                long count = 0;
                if (doc.exists()) {
                    Long c = doc.getLong("count");
                    if (c != null) count = c;
                }
                
                java.util.Map<String, Object> data = new java.util.HashMap<>();
                data.put("count", count + 1);
                data.put("lastRejectionTime", System.currentTimeMillis());
                
                db.collection("users").document(targetId).collection("rejections").document(requesterId)
                    .set(data, com.google.firebase.firestore.SetOptions.merge());
            });
    }

    private void createNotification(String toUserId, String fromUserId, String type) {
         if (currentUserInfo == null) return;
         
         java.util.Map<String, Object> notif = new java.util.HashMap<>();
         notif.put("type", type);
         notif.put("fromUserId", fromUserId);
         notif.put("fromUserName", currentUserInfo.getName());
         notif.put("fromUserProfilePic", currentUserInfo.getProfilePic());
         notif.put("toUserId", toUserId);
         notif.put("timestamp", System.currentTimeMillis());
         notif.put("read", false);
         
         db.collection("notifications").add(notif);
    }

    // --- Inner Classes ---

    private static class RequestItem {
        String id; String name; String pic; long timestamp;
        RequestItem(String id, String name, String pic, long timestamp) {
            this.id = id; this.name = name; this.pic = pic; this.timestamp = timestamp;
        }
    }

    private class RequestsAdapter extends RecyclerView.Adapter<RequestsAdapter.ViewHolder> {
        private List<RequestItem> items = new ArrayList<>();

        void setItems(List<RequestItem> newItems) {
            items = newItems;
            notifyDataSetChanged();
        }
        
        void removeItem(RequestItem item) {
            int pos = items.indexOf(item);
            if (pos != -1) {
                items.remove(pos);
                notifyItemRemoved(pos);
                if (items.isEmpty()) tvNoData.setVisibility(View.VISIBLE);
            }
        }

        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_follow_request, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            RequestItem item = items.get(position);
            holder.tvName.setText(item.name != null ? item.name : "Unknown");
            
            if (item.pic != null) {
                try {
                    ImageCache.getInstance().loadBase64Image(item.pic, holder.ivProfile, R.drawable.ic_profile);
                } catch (Exception e) {}
            } else {
                holder.ivProfile.setImageResource(R.drawable.ic_profile);
            }

            holder.btnConfirm.setOnClickListener(v -> acceptRequest(item));
            holder.btnDelete.setOnClickListener(v -> deleteRequest(item));
        }

        @Override public int getItemCount() { return items.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            de.hdodenhof.circleimageview.CircleImageView ivProfile;
            TextView tvName;
            Button btnConfirm, btnDelete;
            ViewHolder(View v) {
                super(v);
                ivProfile = v.findViewById(R.id.iv_profile);
                tvName = v.findViewById(R.id.tv_name);
                btnConfirm = v.findViewById(R.id.btn_confirm);
                btnDelete = v.findViewById(R.id.btn_delete);
            }
        }
    }
}
