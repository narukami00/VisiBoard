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
    private TextView tvNoData;
    private View btnBack;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private RequestsAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_follow_requests);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        rvRequests = findViewById(R.id.rv_requests);
        pbLoading = findViewById(R.id.pb_loading);
        tvNoData = findViewById(R.id.tv_no_data);
        btnBack = findViewById(R.id.btn_back);

        btnBack.setOnClickListener(v -> finish());

        rvRequests.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RequestsAdapter();
        rvRequests.setAdapter(adapter);

        loadRequests();
    }

    private void loadRequests() {
        String userId = auth.getCurrentUser().getUid();
        pbLoading.setVisibility(View.VISIBLE);
        tvNoData.setVisibility(View.GONE);

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
                            String name = doc.getString("requesterName");
                            String pic = doc.getString("requesterProfilePic");
                            long timestamp = doc.getLong("timestamp") != null ? doc.getLong("timestamp") : 0L;
                            items.add(new RequestItem(requesterId, name, pic, timestamp));
                        }
                        adapter.setItems(items);
                    }
                })
                .addOnFailureListener(e -> {
                    pbLoading.setVisibility(View.GONE);
                    Toast.makeText(this, "Error loading requests", Toast.LENGTH_SHORT).show();
                });
    }

    private void acceptRequest(RequestItem item) {
        String currentUserId = auth.getCurrentUser().getUid();
        String requesterId = item.id;

        // 1. Add to my followers
        // 2. Add to their following
        // 3. Remove from requests
        // 4. Notify them

        // We can do this atomically or sequentially. 
        // Logic similar to MapFragment.followUser but reversed perspective.

        // Get my info for the "following" entry
        db.collection("users").document(currentUserId).get().addOnSuccessListener(myDoc -> {
             String myName = myDoc.getString("name");
             String myPic = myDoc.getString("profilePic");
             
             // Setup Batch
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
             followingData.put("followedName", myName);
             followingData.put("followedProfilePic", myPic);
             batch.set(db.collection("users").document(requesterId).collection("following").document(currentUserId), followingData);
             batch.update(db.collection("users").document(requesterId), "followingCount", com.google.firebase.firestore.FieldValue.increment(1));

             // Remove Request
             batch.delete(db.collection("users").document(currentUserId).collection("follow_requests").document(requesterId));

             batch.commit().addOnSuccessListener(aVoid -> {
                 Toast.makeText(this, "Accepted", Toast.LENGTH_SHORT).show();
                 adapter.removeItem(item);
                 
                 // Notify
                 createNotification(requesterId, currentUserId, "follow_accepted");
             }).addOnFailureListener(e -> Toast.makeText(this, "Failed", Toast.LENGTH_SHORT).show());
        });
    }

    private void deleteRequest(RequestItem item) {
        String currentUserId = auth.getCurrentUser().getUid();
        db.collection("users").document(currentUserId).collection("follow_requests").document(item.id)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
                    adapter.removeItem(item);
                    
                    // Record Rejection (5-Strike Rule)
                    recordRejection(currentUserId, item.id);
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
        // ... simplified reuse or copy logic
         db.collection("users").document(fromUserId).get().addOnSuccessListener(doc -> {
             String name = doc.getString("name");
             String pic = doc.getString("profilePic");
             
             java.util.Map<String, Object> notif = new java.util.HashMap<>();
             notif.put("type", type);
             notif.put("fromUserId", fromUserId);
             notif.put("fromUserName", name);
             notif.put("fromUserProfilePic", pic);
             notif.put("toUserId", toUserId);
             notif.put("timestamp", System.currentTimeMillis());
             notif.put("read", false);
             
             db.collection("notifications").add(notif);
         });
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
