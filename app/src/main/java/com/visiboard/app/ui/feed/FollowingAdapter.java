package com.visiboard.app.ui.feed;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.visiboard.app.R;
import com.visiboard.app.data.UserInfo;
import de.hdodenhof.circleimageview.CircleImageView;
import java.util.ArrayList;
import java.util.List;

public class FollowingAdapter extends RecyclerView.Adapter<FollowingAdapter.FollowingViewHolder> {

    private static final String TAG = "FollowingAdapter";
    private List<UserInfo> users = new ArrayList<>();
    private OnUserClickListener userClickListener;
    private OnSendMessageListener sendMessageListener;

    public interface OnUserClickListener {
        void onUserClick(UserInfo user);
    }

    public interface OnSendMessageListener {
        void onSendMessage(UserInfo user);
    }

    public FollowingAdapter(OnUserClickListener userClickListener, OnSendMessageListener sendMessageListener) {
        this.userClickListener = userClickListener;
        this.sendMessageListener = sendMessageListener;
    }

    @NonNull
    @Override
    public FollowingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_following_user, parent, false);
        return new FollowingViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FollowingViewHolder holder, int position) {
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

    class FollowingViewHolder extends RecyclerView.ViewHolder {
        TextView tvUserName, tvUserLocation;
        CircleImageView ivUserAvatar;
        ImageView btnSendMessage;

        FollowingViewHolder(@NonNull View itemView) {
            super(itemView);
            tvUserName = itemView.findViewById(R.id.tv_user_name);
            tvUserLocation = itemView.findViewById(R.id.tv_user_location);
            ivUserAvatar = itemView.findViewById(R.id.iv_user_avatar);
            btnSendMessage = itemView.findViewById(R.id.btn_send_message);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && userClickListener != null) {
                    userClickListener.onUserClick(users.get(position));
                }
            });

            btnSendMessage.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && sendMessageListener != null) {
                    sendMessageListener.onSendMessage(users.get(position));
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
            
            // Load profile picture
            String profilePic = user.getProfilePic();
            if (profilePic != null && !profilePic.isEmpty()) {
                try {
                    byte[] bytes = Base64.decode(profilePic, Base64.DEFAULT);
                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    ivUserAvatar.setImageBitmap(bitmap);
                } catch (Exception e) {
                    ivUserAvatar.setImageResource(R.drawable.ic_profile);
                    Log.e(TAG, "Error loading profile pic", e);
                }
            } else {
                ivUserAvatar.setImageResource(R.drawable.ic_profile);
            }
        }
    }
}
