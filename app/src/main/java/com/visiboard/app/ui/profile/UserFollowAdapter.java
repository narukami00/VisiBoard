package com.visiboard.app.ui.profile;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.visiboard.app.R;
import com.visiboard.app.data.UserInfo;

import java.util.ArrayList;
import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class UserFollowAdapter extends RecyclerView.Adapter<UserFollowAdapter.UserViewHolder> {

    private List<UserInfo> users = new ArrayList<>();
    private OnUserClickListener listener;
    private boolean isFollowingList;

    public interface OnUserClickListener {
        void onUserClick(UserInfo user);
        void onFollowClick(UserInfo user, int position);
    }

    public UserFollowAdapter(OnUserClickListener listener, boolean isFollowingList) {
        this.listener = listener;
        this.isFollowingList = isFollowingList;
    }

    public void setUsers(List<UserInfo> users) {
        this.users = users;
        notifyDataSetChanged();
    }

    public void removeUser(int position) {
        if (position >= 0 && position < users.size()) {
            users.remove(position);
            notifyItemRemoved(position);
        }
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

    class UserViewHolder extends RecyclerView.ViewHolder {
        CircleImageView profilePic;
        TextView userName, userLocation;
        Button followBtn;

        UserViewHolder(@NonNull View itemView) {
            super(itemView);
            profilePic = itemView.findViewById(R.id.item_user_profile_pic);
            userName = itemView.findViewById(R.id.item_user_name);
            userLocation = itemView.findViewById(R.id.item_user_location);
            followBtn = itemView.findViewById(R.id.item_follow_btn);
        }

        void bind(UserInfo user) {
            userName.setText(user.getName() != null ? user.getName() : "Anonymous");

            if (user.getLastKnownLocation() != null && !user.getLastKnownLocation().isEmpty()) {
                userLocation.setText(user.getLastKnownLocation());
                userLocation.setVisibility(View.VISIBLE);
            } else {
                userLocation.setVisibility(View.GONE);
            }

            // Load profile picture
            if (user.getProfilePic() != null && !user.getProfilePic().isEmpty()) {
                try {
                    byte[] bytes = Base64.decode(user.getProfilePic(), Base64.DEFAULT);
                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    profilePic.setImageBitmap(bitmap);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            // Set button text based on list type
            if (isFollowingList) {
                followBtn.setText("Unfollow");
                followBtn.setBackgroundResource(R.drawable.bg_button_secondary);
                followBtn.setTextColor(itemView.getContext().getColor(R.color.button_text_secondary));
            } else {
                followBtn.setText("Remove");
                followBtn.setBackgroundResource(R.drawable.bg_button_secondary);
                followBtn.setTextColor(itemView.getContext().getColor(R.color.button_text_secondary));
            }

            // Click listeners
            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onUserClick(user);
            });

            followBtn.setOnClickListener(v -> {
                if (listener != null) listener.onFollowClick(user, getAdapterPosition());
            });
        }
    }
}
