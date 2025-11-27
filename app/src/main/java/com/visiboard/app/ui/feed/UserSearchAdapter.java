package com.visiboard.app.ui.feed;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.visiboard.app.R;
import com.visiboard.app.data.UserInfo;
import de.hdodenhof.circleimageview.CircleImageView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserSearchAdapter extends RecyclerView.Adapter<UserSearchAdapter.UserViewHolder> {

    private static final String TAG = "UserSearchAdapter";
    private List<UserInfo> users = new ArrayList<>();
    private OnUserClickListener listener;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    public interface OnUserClickListener {
        void onUserClick(UserInfo user);
    }
    
    public interface OnMessageClickListener {
        void onMessageClick(UserInfo user);
    }

    private OnMessageClickListener messageListener;

    public UserSearchAdapter(OnUserClickListener listener) {
        this.listener = listener;
        this.auth = FirebaseAuth.getInstance();
        this.db = FirebaseFirestore.getInstance();
    }
    
    public void setOnMessageClickListener(OnMessageClickListener messageListener) {
        this.messageListener = messageListener;
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_user_follow, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        UserInfo user = users.get(position);
        holder.bind(user);
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    public void setUsers(List<UserInfo> users) {
        this.users = users;
        notifyDataSetChanged();
    }

    class UserViewHolder extends RecyclerView.ViewHolder {
        TextView tvUserName, tvUserLocation;
        CircleImageView ivProfilePic;
        Button btnFollow;
        android.widget.ImageView ivMessage;
        String currentUserId;

        UserViewHolder(@NonNull View itemView) {
            super(itemView);
            tvUserName = itemView.findViewById(R.id.item_user_name);
            tvUserLocation = itemView.findViewById(R.id.item_user_location);
            ivProfilePic = itemView.findViewById(R.id.item_user_profile_pic);
            btnFollow = itemView.findViewById(R.id.item_follow_btn);
            ivMessage = itemView.findViewById(R.id.item_message_icon);
            currentUserId = auth.getCurrentUser().getUid();

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onUserClick(users.get(position));
                }
            });
            
            ivMessage.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && messageListener != null) {
                    messageListener.onMessageClick(users.get(position));
                }
            });
        }

        void bind(UserInfo user) {
            tvUserName.setText(user.getName() != null ? user.getName() : "Anonymous");
            
            if (user.getLastKnownLocation() != null && !user.getLastKnownLocation().isEmpty()) {
                tvUserLocation.setVisibility(View.VISIBLE);
                tvUserLocation.setText(user.getLastKnownLocation());
            } else {
                tvUserLocation.setVisibility(View.GONE);
            }
            
            // Load profile picture asynchronously with caching
            com.visiboard.app.utils.ImageCache.getInstance()
                .loadBase64Image(user.getProfilePic(), ivProfilePic, R.drawable.ic_profile);
            
            // Check follow status and update button
            db.collection("users").document(currentUserId)
                .collection("following").document(user.getUserId())
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        btnFollow.setText("Following");
                        btnFollow.setBackgroundResource(R.drawable.btn_following_selector);
                        btnFollow.setTextColor(itemView.getContext().getResources().getColor(R.color.button_text_following, null));
                    } else {
                        btnFollow.setText("Follow");
                        btnFollow.setBackgroundResource(R.drawable.btn_primary_selector);
                        btnFollow.setTextColor(itemView.getContext().getResources().getColor(R.color.button_text_primary, null));
                    }
                });
            
            // Handle follow button click
            btnFollow.setOnClickListener(v -> {
                if (btnFollow.getText().equals("Follow")) {
                    followUser(user, btnFollow);
                } else {
                    unfollowUser(user, btnFollow);
                }
            });
        }
        
        private void followUser(UserInfo targetUser, Button btn) {
            db.collection("users").document(currentUserId).get()
                .addOnSuccessListener(currentUserDoc -> {
                    String myName = currentUserDoc.getString("name");
                    String myProfilePic = currentUserDoc.getString("profilePic");
                    
                    Map<String, Object> followerData = new HashMap<>();
                    followerData.put("timestamp", System.currentTimeMillis());
                    followerData.put("followerName", myName);
                    followerData.put("followerProfilePic", myProfilePic);
                    
                    db.collection("users").document(targetUser.getUserId())
                        .collection("followers").document(currentUserId)
                        .set(followerData);
                    
                    db.collection("users").document(targetUser.getUserId())
                        .update("followersCount", FieldValue.increment(1));
                    
                    Map<String, Object> followingData = new HashMap<>();
                    followingData.put("timestamp", System.currentTimeMillis());
                    followingData.put("followedName", targetUser.getName());
                    followingData.put("followedProfilePic", targetUser.getProfilePic());
                    
                    db.collection("users").document(currentUserId)
                        .collection("following").document(targetUser.getUserId())
                        .set(followingData);
                    
                    db.collection("users").document(currentUserId)
                        .update("followingCount", FieldValue.increment(1));
                    
                    btn.setText("Following");
                    btn.setBackgroundResource(R.drawable.btn_following_selector);
                    btn.setTextColor(itemView.getContext().getResources().getColor(R.color.button_text_following, null));
                    
                    // Create notification
                    createFollowNotification(targetUser.getUserId(), currentUserId, myName, myProfilePic);
                });
        }
        
        private void unfollowUser(UserInfo targetUser, Button btn) {
            db.collection("users").document(targetUser.getUserId())
                .collection("followers").document(currentUserId)
                .delete();
            
            db.collection("users").document(targetUser.getUserId())
                .update("followersCount", FieldValue.increment(-1));
            
            db.collection("users").document(currentUserId)
                .collection("following").document(targetUser.getUserId())
                .delete();
            
            db.collection("users").document(currentUserId)
                .update("followingCount", FieldValue.increment(-1));
            
            btn.setText("Follow");
            btn.setBackgroundResource(R.drawable.btn_primary_selector);
            btn.setTextColor(itemView.getContext().getResources().getColor(R.color.button_text_primary, null));
        }
        
        private void createFollowNotification(String toUserId, String fromUserId, String fromUserName, String fromUserProfilePic) {
            Map<String, Object> notification = new HashMap<>();
            notification.put("toUserId", toUserId);
            notification.put("fromUserId", fromUserId);
            notification.put("fromUserName", fromUserName);
            notification.put("fromUserProfilePic", fromUserProfilePic);
            notification.put("type", "follow");
            notification.put("timestamp", System.currentTimeMillis());
            notification.put("read", false);
            
            db.collection("notifications").add(notification);
        }
    }
}
