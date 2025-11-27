package com.visiboard.app.ui.profile;

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
import com.visiboard.app.data.NearbyNote;
import de.hdodenhof.circleimageview.CircleImageView;
import java.util.ArrayList;
import java.util.List;

public class RecentNotesAdapter extends RecyclerView.Adapter<RecentNotesAdapter.NoteViewHolder> {

    private List<NearbyNote> notes = new ArrayList<>();
    private OnNoteClickListener listener;

    public interface OnNoteClickListener {
        void onNoteClick(NearbyNote note);
    }

    public RecentNotesAdapter(OnNoteClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_nearby_note, parent, false);
        return new NoteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
        NearbyNote note = notes.get(position);
        holder.bind(note);
    }

    @Override
    public int getItemCount() {
        return notes.size();
    }

    public void setNotes(List<NearbyNote> notes) {
        this.notes = notes;
        notifyDataSetChanged();
    }

    class NoteViewHolder extends RecyclerView.ViewHolder {
        TextView tvUserName, tvDistance, tvTime, tvNoteText, tvLikesCount, tvCommentsCount;
        CircleImageView ivUserAvatar;

        NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            tvUserName = itemView.findViewById(R.id.tv_user_name);
            tvDistance = itemView.findViewById(R.id.tv_distance);
            tvTime = itemView.findViewById(R.id.tv_time);
            tvNoteText = itemView.findViewById(R.id.tv_note_text);
            tvLikesCount = itemView.findViewById(R.id.tv_likes_count);
            tvCommentsCount = itemView.findViewById(R.id.tv_comments_count);
            ivUserAvatar = itemView.findViewById(R.id.iv_user_avatar);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onNoteClick(notes.get(position));
                }
            });
        }

        void bind(NearbyNote note) {
            tvUserName.setText(note.getUserName() != null ? note.getUserName() : "Anonymous");
            
            // Hide distance for profile notes
            tvDistance.setVisibility(View.GONE);
            
            tvTime.setText(getTimeAgo(note.getTimestamp()));
            
            String displayText = note.getSummary() != null && !note.getSummary().isEmpty() 
                ? note.getSummary() : note.getText();
            tvNoteText.setText(displayText != null ? displayText : "");
            
            tvLikesCount.setText(String.valueOf(note.getLikesCount()));
            tvCommentsCount.setText(String.valueOf(note.getCommentsCount()));
            
            // Load profile picture asynchronously with caching
            com.visiboard.app.utils.ImageCache.getInstance()
                .loadBase64Image(note.getUserProfilePic(), ivUserAvatar, R.drawable.ic_profile);
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
