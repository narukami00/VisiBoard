package com.visiboard.app.ui.feed;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.visiboard.app.R;
import com.visiboard.app.data.Notification;
import java.util.ArrayList;
import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder> {

    private List<Notification> notifications = new ArrayList<>();
    private OnNotificationClickListener listener;

    public interface OnNotificationClickListener {
        void onNotificationClick(Notification notification);
        void onReplyClick(Notification notification);
    }

    public NotificationAdapter(OnNotificationClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification, parent, false);
        return new NotificationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
        Notification notification = notifications.get(position);
        holder.bind(notification);
    }

    @Override
    public int getItemCount() {
        return notifications.size();
    }

    public void setNotifications(List<Notification> notifications) {
        this.notifications = notifications;
        notifyDataSetChanged();
    }
    
    public Notification getItem(int position) {
        return notifications.get(position);
    }

    public void removeItem(int position) {
        notifications.remove(position);
        notifyItemRemoved(position);
    }

    public void restoreItem(Notification item, int position) {
        notifications.add(position, item);
        notifyItemInserted(position);
    }

    class NotificationViewHolder extends RecyclerView.ViewHolder {
        TextView tvNotificationText, tvNotificationTime;
        ImageView ivUserAvatar, ivNotificationIcon;
        View viewUnreadIndicator;
        com.google.android.material.button.MaterialButton btnReply;

        NotificationViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNotificationText = itemView.findViewById(R.id.tv_notification_text);
            tvNotificationTime = itemView.findViewById(R.id.tv_notification_time);
            ivUserAvatar = itemView.findViewById(R.id.iv_user_avatar);
            ivNotificationIcon = itemView.findViewById(R.id.iv_notification_icon);
            viewUnreadIndicator = itemView.findViewById(R.id.view_unread_indicator);
            btnReply = itemView.findViewById(R.id.btn_reply);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onNotificationClick(notifications.get(position));
                }
            });
            
            if (btnReply != null) {
                btnReply.setOnClickListener(v -> {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION && listener != null) {
                        listener.onReplyClick(notifications.get(position));
                    }
                });
            }
        }

        void bind(Notification notification) {
            String text = "";
            int iconRes = R.drawable.ic_like;

            switch (notification.getType()) {
                case "like":
                    text = notification.getFromUserName() + " liked your note";
                    iconRes = R.drawable.ic_like;
                    break;
                case "comment":
                    text = notification.getFromUserName() + " commented on your note";
                    iconRes = R.drawable.ic_comment;
                    break;
                case "follow":
                    text = notification.getFromUserName() + " started following you";
                    iconRes = R.drawable.ic_profile;
                    break;
                case "message":
                    text = notification.getFromUserName() + " sent you a message";
                    iconRes = R.drawable.ic_send;
                    break;
            }

            tvNotificationText.setText(text);
            tvNotificationTime.setText(getTimeAgo(notification.getTimestamp()));
            ivNotificationIcon.setImageResource(iconRes);
            
            if (viewUnreadIndicator != null) {
                viewUnreadIndicator.setVisibility(notification.isRead() ? View.INVISIBLE : View.VISIBLE);
            }
            
            if (btnReply != null) {
                // Show reply button only for messages
                if ("message".equals(notification.getType())) {
                    btnReply.setVisibility(View.VISIBLE);
                } else {
                    btnReply.setVisibility(View.GONE);
                }
            }
            
            // Load profile picture asynchronously with caching
            com.visiboard.app.utils.ImageCache.getInstance()
                .loadBase64Image(notification.getFromUserProfilePic(), ivUserAvatar, R.drawable.ic_profile);
        }

        private String getTimeAgo(long timestamp) {
            long now = System.currentTimeMillis();
            long diff = now - timestamp;

            long seconds = diff / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;
            long days = hours / 24;

            if (days > 0) return days + "d ago";
            if (hours > 0) return hours + "h ago";
            if (minutes > 0) return minutes + "m ago";
            return "Just now";
        }
    }
}
