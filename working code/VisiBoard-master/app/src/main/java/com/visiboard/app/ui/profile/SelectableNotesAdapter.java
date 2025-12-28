package com.visiboard.app.ui.profile;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.DocumentSnapshot;
import com.visiboard.app.R;
import com.visiboard.app.utils.ImageCache;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SelectableNotesAdapter extends RecyclerView.Adapter<SelectableNotesAdapter.ViewHolder> {

    private final List<DocumentSnapshot> notes = new ArrayList<>();
    private final Set<String> selectedIds = new HashSet<>();
    private static final int MAX_SELECTION = 3;

    public void setNotes(List<DocumentSnapshot> newNotes) {
        notes.clear();
        notes.addAll(newNotes);
        notifyDataSetChanged();
    }

    public void setSelectedIds(List<String> ids) {
        selectedIds.clear();
        if (ids != null) {
            selectedIds.addAll(ids);
        }
        notifyDataSetChanged();
    }
    
    public List<String> getSelectedIds() {
        return new ArrayList<>(selectedIds);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_note_selectable, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DocumentSnapshot doc = notes.get(position);
        String id = doc.getId();
        
        // --- Content Logic (Reused from RecentNotesAdapter logic) ---
        String text = doc.getString("text");
        if (text == null) text = doc.getString("note"); // Fallback
        String imageBase64 = doc.getString("imageBase64"); // Correct field name!
        Long timestamp = doc.getLong("timestamp");

        // Set Text
        holder.tvText.setText(text != null && !text.isEmpty() ? text : "Image Note");
        
        // Set Time
        if (timestamp != null) {
            holder.tvTime.setText(getTimeAgo(timestamp));
        } else {
            holder.tvTime.setText("");
        }

        // Set Stats
        Long likes = doc.getLong("likesCount");
        holder.tvLikes.setText(likes != null ? String.valueOf(likes) : "0");

        // Set Image (Using ImageCache)
        if (imageBase64 != null && !imageBase64.isEmpty()) {
            holder.ivImage.setVisibility(View.VISIBLE);
            try {
                ImageCache.getInstance().loadBase64Image("sel_" + id, imageBase64, holder.ivImage, R.drawable.ic_image_placeholder);
            } catch (Exception e) {
                holder.ivImage.setImageResource(R.drawable.ic_image_placeholder);
            }
        } else {
            holder.ivImage.setVisibility(View.GONE);
        }

        // --- Selection Logic ---
        holder.checkBox.setChecked(selectedIds.contains(id));
        
        // Make entire item clickable
        holder.itemView.setOnClickListener(v -> {
            toggleSelection(id);
        });
        
        // Make checkbox consistent with item click
        holder.checkBox.setOnClickListener(v -> {
            toggleSelection(id);
        });
    }
    
    private void toggleSelection(String id) {
        if (selectedIds.contains(id)) {
            selectedIds.remove(id);
        } else {
            if (selectedIds.size() >= MAX_SELECTION) {
                // Determine context via itemView usually, but here we can't easily toast without View reference in a clean way
                // Just prevent selection returns
                return;
            }
            selectedIds.add(id);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return notes.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CheckBox checkBox;
        ImageView ivImage;
        TextView tvText, tvTime, tvLikes;

        ViewHolder(View v) {
            super(v);
            checkBox = v.findViewById(R.id.cb_select);
            ivImage = v.findViewById(R.id.iv_note_image);
            tvText = v.findViewById(R.id.tv_note_text);
            tvTime = v.findViewById(R.id.tv_time);
            tvLikes = v.findViewById(R.id.tv_likes_count);
        }
    }
    
    private String getTimeAgo(long timestamp) {
        // Simple time ago logic
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
