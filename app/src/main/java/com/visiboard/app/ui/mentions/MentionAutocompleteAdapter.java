package com.visiboard.app.ui.mentions;

import android.content.Context;
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
import com.visiboard.app.data.FollowerSuggestion;

import java.util.ArrayList;
import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * RecyclerView adapter for the mention autocomplete popup.
 * Displays a list of followers that match the current query.
 */
public class MentionAutocompleteAdapter extends RecyclerView.Adapter<MentionAutocompleteAdapter.SuggestionViewHolder> {

    private List<FollowerSuggestion> suggestions = new ArrayList<>();
    private final OnSuggestionClickListener listener;
    private final Context context;

    /**
     * Callback for when a suggestion is selected
     */
    public interface OnSuggestionClickListener {
        void onSuggestionClick(FollowerSuggestion suggestion);
    }

    public MentionAutocompleteAdapter(Context context, OnSuggestionClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    /**
     * Update the suggestions list with filtered results
     */
    public void setSuggestions(List<FollowerSuggestion> suggestions) {
        this.suggestions = suggestions != null ? suggestions : new ArrayList<>();
        notifyDataSetChanged();
    }

    /**
     * Clear all suggestions
     */
    public void clear() {
        this.suggestions.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SuggestionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_mention_suggestion, parent, false);
        return new SuggestionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SuggestionViewHolder holder, int position) {
        FollowerSuggestion suggestion = suggestions.get(position);
        holder.bind(suggestion);
    }

    @Override
    public int getItemCount() {
        return suggestions.size();
    }

    class SuggestionViewHolder extends RecyclerView.ViewHolder {
        private final CircleImageView ivProfile;
        private final TextView tvName;
        private final ImageView ivFollowingIcon;

        SuggestionViewHolder(@NonNull View itemView) {
            super(itemView);
            ivProfile = itemView.findViewById(R.id.iv_profile);
            tvName = itemView.findViewById(R.id.tv_name);
            ivFollowingIcon = itemView.findViewById(R.id.iv_following_icon);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onSuggestionClick(suggestions.get(position));
                }
            });
        }

        void bind(FollowerSuggestion suggestion) {
            tvName.setText(suggestion.getName());

            if (suggestion.isFollowingAll()) {
                // Special styling for @following
                ivProfile.setImageResource(R.drawable.ic_users);
                ivFollowingIcon.setVisibility(View.VISIBLE);
                tvName.setTextColor(context.getResources().getColor(R.color.primary, null));
            } else {
                ivFollowingIcon.setVisibility(View.GONE);
                tvName.setTextColor(context.getResources().getColor(R.color.text_primary, null));

                // Load profile picture
                String profilePic = suggestion.getProfilePic();
                if (profilePic != null && !profilePic.isEmpty()) {
                    try {
                        byte[] bytes = Base64.decode(profilePic, Base64.DEFAULT);
                        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        ivProfile.setImageBitmap(bitmap);
                    } catch (Exception e) {
                        ivProfile.setImageResource(R.drawable.ic_profile_placeholder);
                    }
                } else {
                    ivProfile.setImageResource(R.drawable.ic_profile_placeholder);
                }
            }
        }
    }
}
