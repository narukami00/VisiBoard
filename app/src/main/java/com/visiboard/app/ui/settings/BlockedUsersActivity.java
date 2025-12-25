package com.visiboard.app.ui.settings;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.visiboard.app.R;

import java.util.ArrayList;
import java.util.List;

public class BlockedUsersActivity extends AppCompatActivity {

    private RecyclerView rvBlockedUsers;
    private LinearLayout layoutEmptyState;
    private ProgressBar pbLoading;
    private androidx.appcompat.widget.Toolbar toolbar;

    private BlockedUsersAdapter adapter;
    private FirebaseFirestore db;
    private String currentUserId;
    private List<BlockedUserData> blockedUsers = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blocked_users);

        db = FirebaseFirestore.getInstance();
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        rvBlockedUsers = findViewById(R.id.rv_blocked_users);
        layoutEmptyState = findViewById(R.id.layout_empty_state);
        pbLoading = findViewById(R.id.progress_loading);
        toolbar = findViewById(R.id.toolbar);

        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        rvBlockedUsers.setLayoutManager(new LinearLayoutManager(this));
        adapter = new BlockedUsersAdapter();
        rvBlockedUsers.setAdapter(adapter);

        loadBlockedUsers();
    }

    private void loadBlockedUsers() {
        pbLoading.setVisibility(View.VISIBLE);
        layoutEmptyState.setVisibility(View.GONE);
        rvBlockedUsers.setVisibility(View.GONE);

        db.collection("users").document(currentUserId).collection("blocked_users")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    pbLoading.setVisibility(View.GONE);
                    blockedUsers.clear();
                    
                    if (queryDocumentSnapshots.isEmpty()) {
                        layoutEmptyState.setVisibility(View.VISIBLE);
                        rvBlockedUsers.setVisibility(View.GONE);
                        return;
                    }

                    rvBlockedUsers.setVisibility(View.VISIBLE);
                    
                    // Fetch user info for each blocked user
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        String blockedUserId = doc.getId();
                        Long blockedAt = doc.getLong("blockedAt");
                        
                        db.collection("users").document(blockedUserId).get()
                                .addOnSuccessListener(userDoc -> {
                                    BlockedUserData data = new BlockedUserData();
                                    data.userId = blockedUserId;
                                    data.blockedAt = blockedAt != null ? blockedAt : 0;
                                    if (userDoc.exists()) {
                                        data.name = userDoc.getString("name");
                                        data.profilePic = userDoc.getString("profilePic");
                                    }
                                    blockedUsers.add(data);
                                    adapter.notifyDataSetChanged();
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    pbLoading.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to load blocked users", Toast.LENGTH_SHORT).show();
                });
    }

    private void unblockUser(int position) {
        BlockedUserData user = blockedUsers.get(position);
        
        db.collection("users").document(currentUserId).collection("blocked_users")
                .document(user.userId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    blockedUsers.remove(position);
                    adapter.notifyItemRemoved(position);
                    if (blockedUsers.isEmpty()) {
                        layoutEmptyState.setVisibility(View.VISIBLE);
                    }
                    Toast.makeText(this, "Unblocked " + (user.name != null ? user.name : "user"), Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to unblock", Toast.LENGTH_SHORT).show();
                });
    }

    // Data class
    static class BlockedUserData {
        String userId;
        String name;
        String profilePic;
        long blockedAt;
    }

    // Adapter
    private class BlockedUsersAdapter extends RecyclerView.Adapter<BlockedUsersAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_blocked_user, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            BlockedUserData user = blockedUsers.get(position);
            
            holder.tvName.setText(user.name != null ? user.name : "Unknown User");
            
            if (user.profilePic != null && !user.profilePic.isEmpty()) {
                try {
                    byte[] bytes = Base64.decode(user.profilePic, Base64.DEFAULT);
                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    holder.ivAvatar.setImageBitmap(bitmap);
                } catch (Exception e) {
                    holder.ivAvatar.setImageResource(R.drawable.ic_person);
                }
            } else {
                holder.ivAvatar.setImageResource(R.drawable.ic_person);
            }
            
            holder.btnUnblock.setOnClickListener(v -> unblockUser(position));
        }

        @Override
        public int getItemCount() {
            return blockedUsers.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivAvatar;
            TextView tvName;
            MaterialButton btnUnblock;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                ivAvatar = itemView.findViewById(R.id.iv_avatar);
                tvName = itemView.findViewById(R.id.tv_name);
                btnUnblock = itemView.findViewById(R.id.btn_unblock);
            }
        }
    }
}
