package com.visiboard.app.ui.profile;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import com.visiboard.app.R;
import com.visiboard.app.data.NearbyNote;
import com.visiboard.app.utils.ImageCache;
import java.util.ArrayList;
import java.util.List;

public class RecentNotesAdapter extends RecyclerView.Adapter<RecentNotesAdapter.NoteViewHolder> {

    private List<NearbyNote> notes = new ArrayList<>();
    private OnNoteClickListener listener;
    private int lastPosition = -1;

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
                .inflate(R.layout.item_profile_note, parent, false);
        return new NoteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
        NearbyNote note = notes.get(position);
        holder.bind(note);
        setAnimation(holder.itemView, position);
    }

    @Override
    public int getItemCount() {
        return notes.size();
    }

    public void setNotes(List<NearbyNote> newNotes) {
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new NoteDiffCallback(this.notes, newNotes));
        this.notes.clear();
        this.notes.addAll(newNotes);
        diffResult.dispatchUpdatesTo(this);
    }
    
    private void setAnimation(View viewToAnimate, int position) {
        if (position > lastPosition) {
            Animation animation = AnimationUtils.loadAnimation(viewToAnimate.getContext(), android.R.anim.fade_in);
            viewToAnimate.startAnimation(animation);
            lastPosition = position;
        }
    }

    static class NoteDiffCallback extends DiffUtil.Callback {
        private final List<NearbyNote> oldList;
        private final List<NearbyNote> newList;

        public NoteDiffCallback(List<NearbyNote> oldList, List<NearbyNote> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() { return oldList.size(); }

        @Override
        public int getNewListSize() { return newList.size(); }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return oldList.get(oldItemPosition).getId().equals(newList.get(newItemPosition).getId());
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            NearbyNote oldNote = oldList.get(oldItemPosition);
            NearbyNote newNote = newList.get(newItemPosition);
            return oldNote.getLikesCount() == newNote.getLikesCount() &&
                   oldNote.getCommentsCount() == newNote.getCommentsCount() &&
                   oldNote.getText().equals(newNote.getText());
        }
    }

    class NoteViewHolder extends RecyclerView.ViewHolder {
        TextView tvTime, tvNoteText, tvLikesCount, tvCommentsCount;
        ImageView ivNoteImage;

        NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTime = itemView.findViewById(R.id.tv_time);
            tvNoteText = itemView.findViewById(R.id.tv_note_text);
            tvLikesCount = itemView.findViewById(R.id.tv_likes_count);
            tvCommentsCount = itemView.findViewById(R.id.tv_comments_count);
            ivNoteImage = itemView.findViewById(R.id.iv_note_image);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onNoteClick(notes.get(position));
                }
            });
        }

        void bind(NearbyNote note) {
            tvTime.setText(getTimeAgo(note.getTimestamp()));
            
            String displayText = note.getSummary() != null && !note.getSummary().isEmpty() 
                ? note.getSummary() : note.getText();
            tvNoteText.setText(displayText != null ? displayText : "");
            
            tvLikesCount.setText(String.valueOf(note.getLikesCount()));
            tvCommentsCount.setText(String.valueOf(note.getCommentsCount()));
            
            // Handle Image using ImageCache for background loading
            if (note.getImageBase64() != null && !note.getImageBase64().isEmpty()) {
                ivNoteImage.setVisibility(View.VISIBLE);
                // Use note ID as cache key for uniqueness
                ImageCache.getInstance().loadBase64Image(note.getId(), note.getImageBase64(), ivNoteImage, R.drawable.ic_image);
            } else {
                ivNoteImage.setVisibility(View.GONE);
            }
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
