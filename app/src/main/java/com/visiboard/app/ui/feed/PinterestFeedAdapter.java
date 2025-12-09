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
import com.visiboard.app.data.NearbyNote;
import java.util.ArrayList;
import java.util.List;

public class PinterestFeedAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_IMAGE = 0;
    private static final int VIEW_TYPE_TEXT = 1;
    private static final int VIEW_TYPE_LOADING = 2;

    private List<NearbyNote> notes = new ArrayList<>();
    private OnNoteClickListener listener;
    private boolean isLoading = false;

    public interface OnNoteClickListener {
        void onNoteClick(NearbyNote note);
    }

    public PinterestFeedAdapter(OnNoteClickListener listener) {
        this.listener = listener;
    }
    
    public void setLoading(boolean loading) {
        if (this.isLoading != loading) {
            this.isLoading = loading;
            if (loading) {
                notifyItemInserted(notes.size());
            } else {
                notifyItemRemoved(notes.size());
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (isLoading && position == notes.size()) {
            return VIEW_TYPE_LOADING;
        }
        NearbyNote note = notes.get(position);
        return (note.getImageBase64() != null && !note.getImageBase64().isEmpty()) ? VIEW_TYPE_IMAGE : VIEW_TYPE_TEXT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_LOADING) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_loading_footer, parent, false);
            return new LoadingViewHolder(view);
        } else if (viewType == VIEW_TYPE_IMAGE) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_pinterest_note, parent, false);
            return new ImageNoteViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_pinterest_text, parent, false);
            return new TextNoteViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof LoadingViewHolder) {
            ViewGroup.LayoutParams layoutParams = holder.itemView.getLayoutParams();
            if (layoutParams instanceof androidx.recyclerview.widget.StaggeredGridLayoutManager.LayoutParams) {
                ((androidx.recyclerview.widget.StaggeredGridLayoutManager.LayoutParams) layoutParams).setFullSpan(true);
            }
            return;
        }

        NearbyNote note = notes.get(position);
        
        boolean isHorizontal = note.getImageWidth() > note.getImageHeight() && note.getImageHeight() > 0;
        
        ViewGroup.LayoutParams layoutParams = holder.itemView.getLayoutParams();
        if (layoutParams instanceof androidx.recyclerview.widget.StaggeredGridLayoutManager.LayoutParams) {
            ((androidx.recyclerview.widget.StaggeredGridLayoutManager.LayoutParams) layoutParams).setFullSpan(isHorizontal);
        }

        if (holder instanceof ImageNoteViewHolder) {
            ((ImageNoteViewHolder) holder).bind(note);
        } else if (holder instanceof TextNoteViewHolder) {
            ((TextNoteViewHolder) holder).bind(note);
        }
    }

    @Override
    public int getItemCount() {
        return notes.size() + (isLoading ? 1 : 0);
    }

    public void setNotes(List<NearbyNote> notes) {
        this.notes = notes;
        notifyDataSetChanged();
    }
    
    public void addNotes(List<NearbyNote> newNotes) {
        int startPos = this.notes.size();
        this.notes.addAll(newNotes);
        notifyItemRangeInserted(startPos, newNotes.size());
    }
    
    public void clear() {
        this.notes.clear();
        notifyDataSetChanged();
    }

    class LoadingViewHolder extends RecyclerView.ViewHolder {
        LoadingViewHolder(View itemView) {
            super(itemView);
        }
    }

    class ImageNoteViewHolder extends RecyclerView.ViewHolder {
        ImageView ivNoteImage;
        TextView tvLikeCount;
        TextView tvDistance;

        ImageNoteViewHolder(@NonNull View itemView) {
            super(itemView);
            ivNoteImage = itemView.findViewById(R.id.iv_note_image);
            tvLikeCount = itemView.findViewById(R.id.tv_like_count);
            tvDistance = itemView.findViewById(R.id.tv_distance);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onNoteClick(notes.get(position));
                }
            });
        }

        void bind(NearbyNote note) {
            tvLikeCount.setText(String.valueOf(note.getLikesCount()));
            
            if (note.getDistance() > 0) {
                tvDistance.setVisibility(View.VISIBLE);
                if (note.getDistance() < 1.0) {
                    tvDistance.setText(String.format("%.0fm", note.getDistance() * 1000));
                } else {
                    tvDistance.setText(String.format("%.1fkm", note.getDistance()));
                }
            } else {
                tvDistance.setVisibility(View.GONE);
            }

            String imageBase64 = note.getImageBase64(); 
            if (imageBase64 != null && !imageBase64.isEmpty()) {
                com.visiboard.app.utils.ImageCache.getInstance()
                    .loadBase64Image(imageBase64, ivNoteImage, R.drawable.placeholder_image);
            } else {
                ivNoteImage.setImageResource(R.drawable.placeholder_image);
            }
        }
    }

    class TextNoteViewHolder extends RecyclerView.ViewHolder {
        de.hdodenhof.circleimageview.CircleImageView ivAvatar;
        TextView tvName, tvText, tvLikeCount, tvDistance;

        TextNoteViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_user_avatar);
            tvName = itemView.findViewById(R.id.tv_user_name);
            tvText = itemView.findViewById(R.id.tv_note_text);
            tvLikeCount = itemView.findViewById(R.id.tv_like_count);
            tvDistance = itemView.findViewById(R.id.tv_distance);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onNoteClick(notes.get(position));
                }
            });
        }

        void bind(NearbyNote note) {
            tvText.setText(note.getText() != null ? note.getText() : "");
            tvLikeCount.setText(String.valueOf(note.getLikesCount()));
            
            String userName = note.getUserName();
            tvName.setText(userName != null && !userName.isEmpty() ? userName : "User");

            String userProfilePic = note.getUserProfilePic();
            if (userProfilePic != null && !userProfilePic.isEmpty()) {
                com.visiboard.app.utils.ImageCache.getInstance()
                    .loadBase64Image(userProfilePic, ivAvatar, R.drawable.ic_profile_placeholder);
            } else {
                ivAvatar.setImageResource(R.drawable.ic_profile_placeholder);
            }

            if (note.getDistance() > 0) {
                tvDistance.setVisibility(View.VISIBLE);
                 if (note.getDistance() < 1.0) {
                    tvDistance.setText(String.format("%.0fm", note.getDistance() * 1000));
                } else {
                    tvDistance.setText(String.format("%.1fkm", note.getDistance()));
                }
            } else {
                tvDistance.setVisibility(View.GONE);
            }
        }
    }
}
