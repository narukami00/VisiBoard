package com.visiboard.app.ui.map;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.visiboard.app.R;
import com.visiboard.app.data.UserInfo;
import de.hdodenhof.circleimageview.CircleImageView;
import java.util.ArrayList;
import java.util.List;

public class LegendAdapter extends RecyclerView.Adapter<LegendAdapter.LegendViewHolder> {

    private List<UserInfo> users = new ArrayList<>();
    private final OnUserClickListener listener;

    public interface OnUserClickListener {
        void onUserClick(UserInfo user);
    }

    public LegendAdapter(OnUserClickListener listener) {
        this.listener = listener;
    }

    public void setUsers(List<UserInfo> users) {
        this.users = users != null ? users : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void clearUsers() {
        this.users.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public LegendViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_legend_user, parent, false);
        return new LegendViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LegendViewHolder holder, int position) {
        holder.bind(users.get(position), position + 1);
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    class LegendViewHolder extends RecyclerView.ViewHolder {
        TextView tvRank, tvName, tvLocation, tvLikes, tvTier;
        CircleImageView ivAvatar;

        LegendViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRank = itemView.findViewById(R.id.tv_rank);
            tvName = itemView.findViewById(R.id.tv_name);
            tvLocation = itemView.findViewById(R.id.tv_location);
            tvLikes = itemView.findViewById(R.id.tv_likes);
            tvTier = itemView.findViewById(R.id.tv_tier);
            ivAvatar = itemView.findViewById(R.id.iv_avatar);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onUserClick(users.get(position));
                }
            });
        }

        void bind(UserInfo user, int rank) {
            tvRank.setText(String.valueOf(rank));
            tvName.setText(user.getName());
            
            // Gold/Silver/Bronze styling for top 3
            if (rank == 1) {
                tvRank.setTextColor(Color.parseColor("#FFD700")); // Gold
                ivAvatar.setBorderColor(Color.parseColor("#FFD700"));
                ivAvatar.setBorderWidth(4);
            } else if (rank == 2) {
                tvRank.setTextColor(Color.parseColor("#C0C0C0")); // Silver
                ivAvatar.setBorderColor(Color.parseColor("#C0C0C0"));
                ivAvatar.setBorderWidth(4);
            } else if (rank == 3) {
                tvRank.setTextColor(Color.parseColor("#CD7F32")); // Bronze
                ivAvatar.setBorderColor(Color.parseColor("#CD7F32"));
                ivAvatar.setBorderWidth(4);
            } else {
                tvRank.setTextColor(itemView.getContext().getResources().getColor(R.color.text_primary));
                ivAvatar.setBorderWidth(0);
            }

            if (user.getLastKnownLocation() != null) {
                tvLocation.setText(user.getLastKnownLocation());
                tvLocation.setVisibility(View.VISIBLE);
            } else {
                tvLocation.setVisibility(View.GONE);
            }

            tvLikes.setText(user.getTotalLikes() + " Likes");
            
            // Bind Tier
            String tier = user.getCurrentTier();
            if (tier != null && !tier.isEmpty() && !tier.equalsIgnoreCase("None")) {
                tvTier.setText(tier);
                tvTier.setVisibility(View.VISIBLE);
                
                int color = Color.GRAY;
                if (tier.equalsIgnoreCase("Bronze")) color = Color.parseColor("#CD7F32");
                else if (tier.equalsIgnoreCase("Silver")) color = Color.parseColor("#C0C0C0");
                else if (tier.equalsIgnoreCase("Gold")) color = Color.parseColor("#FFD700");
                else if (tier.equalsIgnoreCase("Diamond")) color = Color.parseColor("#B9F2FF");
                else if (tier.equalsIgnoreCase("Platinum")) color = Color.parseColor("#E5E4E2");
                
                tvTier.setTextColor(color);
            } else {
                tvTier.setText("No Rank");
                tvTier.setVisibility(View.VISIBLE);
                tvTier.setTextColor(Color.parseColor("#9E9E9E")); // Grey
                tvTier.setBackgroundResource(0); // Remove background capsule if desired, or keep light grey
                // Let's keep a subtle background or remove it. "Subtle No Rank text" implies just text.
                // But for consistency with layout, maybe keep it clean.
                // Actually, Step 595 added bg_capsule_light.
                // I'll set background to null for text-only look, or a grey capsule.
                // Let's go with text only for "subtle".
                tvTier.setPadding(0, 0, 0, 0);
            }

            com.visiboard.app.utils.ImageCache.getInstance()
                .loadBase64Image(user.getProfilePic(), ivAvatar, R.drawable.ic_profile);
        }
    }
}
