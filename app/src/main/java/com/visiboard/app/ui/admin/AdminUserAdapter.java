package com.visiboard.app.ui.admin;

import android.graphics.Color;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.visiboard.app.R;
import com.visiboard.app.data.UserInfo;

public class AdminUserAdapter extends RecyclerView.Adapter<AdminUserAdapter.UserViewHolder> {

    private java.util.List<UserInfo> users = new java.util.ArrayList<>();
    private final OnUserClickListener listener;

    public interface OnUserClickListener {
        void onUserClick(UserInfo user);
    }

    public AdminUserAdapter(OnUserClickListener listener) {
        this.listener = listener;
    }

    public void setUsers(java.util.List<UserInfo> users) {
        this.users = users;
        notifyDataSetChanged();
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        UserInfo model = users.get(position);
        holder.userName.setText(model.getName());
        holder.userEmail.setText(model.getEmail());
        
        // Status Indicators
        android.content.Context ctx = holder.itemView.getContext();
        if (model.isBanned()) {
            holder.userStatus.setText("Banned");
            holder.userStatus.setTextColor(ctx.getResources().getColor(R.color.error, null));
        } else if (model.isRestricted()) {
            holder.userStatus.setText("Restricted");
            holder.userStatus.setTextColor(ctx.getResources().getColor(R.color.warning, null));
        } else if (model.isWarned()) {
            holder.userStatus.setText("Warned");
            holder.userStatus.setTextColor(ctx.getResources().getColor(R.color.like_color, null));
        } else {
            holder.userStatus.setText("Active");
            holder.userStatus.setTextColor(ctx.getResources().getColor(R.color.accent, null));
        }

        // Load avatar
        String profilePic = model.getProfilePic();
        if (profilePic != null && !profilePic.isEmpty()) {
            if (profilePic.startsWith("http")) {
                Glide.with(holder.itemView.getContext())
                    .load(profilePic)
                    .placeholder(R.drawable.ic_person)
                    .circleCrop()
                    .into(holder.userAvatar);
            } else {
                try {
                    String base64Image = profilePic;
                    if (base64Image.startsWith("data:image")) {
                        if (base64Image.contains(",")) {
                            base64Image = base64Image.split(",")[1];
                        }
                    }
                    byte[] decodedString = android.util.Base64.decode(base64Image, android.util.Base64.DEFAULT);
                    Glide.with(holder.itemView.getContext())
                            .asBitmap()
                            .load(decodedString)
                            .placeholder(R.drawable.ic_person)
                            .circleCrop()
                            .into(holder.userAvatar);
                } catch (Exception e) {
                    // Fallback: try loading as string (non-http URL or file path)
                    Glide.with(holder.itemView.getContext())
                            .load(profilePic)
                            .placeholder(R.drawable.ic_person)
                            .circleCrop()
                            .into(holder.userAvatar);
                }
            }
        } else {
            holder.userAvatar.setImageResource(R.drawable.ic_person);
        }

        holder.itemView.setOnClickListener(v -> listener.onUserClick(model));
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_user, parent, false);
        return new UserViewHolder(view);
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView userName, userEmail, userStatus;
        ImageView userAvatar;

        public UserViewHolder(@NonNull View itemView) {
            super(itemView);
            userName = itemView.findViewById(R.id.adminUserName);
            userEmail = itemView.findViewById(R.id.adminUserEmail);
            userStatus = itemView.findViewById(R.id.adminUserStatus);
            userAvatar = itemView.findViewById(R.id.adminUserAvatar);
        }
    }
}
