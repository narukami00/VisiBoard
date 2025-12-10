package com.visiboard.app.ui.feed;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.graphics.Color;
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
    private static final int VIEW_TYPE_FIDGET_BUBBLE = 10;
    private static final int VIEW_TYPE_FIDGET_SPINNER = 11;
    private static final int VIEW_TYPE_FIDGET_SWITCH = 12;
    private static final int VIEW_TYPE_FIDGET_GRAVITY = 13;

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
        if (note.getId() != null && note.getId().startsWith("fidget")) { 
             String id = note.getId();
             if (id.contains("spinner")) return VIEW_TYPE_FIDGET_SPINNER;
             if (id.contains("switch")) return VIEW_TYPE_FIDGET_SWITCH;
             if (id.contains("gravity")) return VIEW_TYPE_FIDGET_GRAVITY;
             return VIEW_TYPE_FIDGET_BUBBLE; // Default
        }
        return (note.getImageBase64() != null && !note.getImageBase64().isEmpty()) ? VIEW_TYPE_IMAGE : VIEW_TYPE_TEXT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_LOADING) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_loading_footer, parent, false);
            return new LoadingViewHolder(view);
        } else if (viewType == VIEW_TYPE_FIDGET_BUBBLE) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_fidget_box, parent, false);
            return new FidgetBubbleViewHolder(view);
        } else if (viewType == VIEW_TYPE_FIDGET_SPINNER) {
             View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_fidget_spinner, parent, false);
            return new FidgetSpinnerViewHolder(view);
        } else if (viewType == VIEW_TYPE_FIDGET_SWITCH) {
             View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_fidget_switch, parent, false);
            return new FidgetSwitchViewHolder(view);
        } else if (viewType == VIEW_TYPE_FIDGET_GRAVITY) {
             View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_fidget_gravity, parent, false);
            return new FidgetGravityViewHolder(view);
        } else if (viewType == VIEW_TYPE_TEXT) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pinterest_text, parent, false);
            return new TextNoteViewHolder(view);
        } else { // VIEW_TYPE_IMAGE
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pinterest_note, parent, false);
            return new ImageNoteViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (getItemViewType(position) == VIEW_TYPE_LOADING) {
            ViewGroup.LayoutParams layoutParams = holder.itemView.getLayoutParams();
            if (layoutParams instanceof androidx.recyclerview.widget.StaggeredGridLayoutManager.LayoutParams) {
                ((androidx.recyclerview.widget.StaggeredGridLayoutManager.LayoutParams) layoutParams).setFullSpan(true);
            }
            return;
        }
        
        // Fidgets don't need data binding usually, self contained logic in ViewHolder
        if (holder instanceof ImageNoteViewHolder) {
            ((ImageNoteViewHolder) holder).bind(notes.get(position));
        } else if (holder instanceof TextNoteViewHolder) {
            ((TextNoteViewHolder) holder).bind(notes.get(position));
        }
        
        // Staggered Layout Logic
        ViewGroup.LayoutParams layoutParams = holder.itemView.getLayoutParams();
        if (layoutParams instanceof androidx.recyclerview.widget.StaggeredGridLayoutManager.LayoutParams) {
            boolean isFullSpan = false;
            int type = getItemViewType(position);
            
            // Fidgets and Text Notes spanning logic
            if (type >= 10) { // All Fidgets
                isFullSpan = false; // Let them fit in columns mostly, except maybe gravity?
                if (type == VIEW_TYPE_FIDGET_GRAVITY || type == VIEW_TYPE_FIDGET_SWITCH) isFullSpan = true;
            } else if (type == VIEW_TYPE_IMAGE) {
                // Wide images
                NearbyNote n = notes.get(position);
                float ar = (float) n.getImageWidth() / (float) n.getImageHeight();
                if (ar > 1.2f) isFullSpan = true;
            }
            
            ((androidx.recyclerview.widget.StaggeredGridLayoutManager.LayoutParams) layoutParams).setFullSpan(isFullSpan);
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
    
    class FidgetBubbleViewHolder extends RecyclerView.ViewHolder {
        GridLayout gridBubbles;
        FidgetBubbleViewHolder(View itemView) {
            super(itemView);
            gridBubbles = itemView.findViewById(R.id.gl_bubbles);
            for (int i = 0; i < gridBubbles.getChildCount(); i++) {
                final View child = gridBubbles.getChildAt(i);
                if (child instanceof ImageButton) {
                    child.setOnClickListener(v -> {
                         ImageButton btn = (ImageButton) v;
                         btn.setImageResource(R.drawable.shape_bubble_popped);
                         v.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
                         v.postDelayed(() -> {
                             v.animate().scaleX(0.0f).scaleY(0.0f).setDuration(150)
                                 .withEndAction(() -> {
                                     btn.setImageResource(R.drawable.shape_bubble_unpopped);
                                     v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300)
                                         .setInterpolator(new android.view.animation.OvershootInterpolator(2.0f)).start();
                                 }).start();
                         }, 800);
                    });
                }
            }
        }
    }
    
    class FidgetSpinnerViewHolder extends RecyclerView.ViewHolder {
        ImageView spinner;
        float currentVelocity = 0;
        android.animation.ObjectAnimator spinAnim;
        
        FidgetSpinnerViewHolder(View itemView) {
            super(itemView);
            spinner = itemView.findViewById(R.id.iv_fidget_spinner);
            itemView.setOnClickListener(v -> spin());
            spinner.setOnClickListener(v -> spin());
        }
        
        void spin() {
             if (spinAnim != null && spinAnim.isRunning()) spinAnim.cancel();
             
             // Add momentum
             float start = spinner.getRotation();
             float end = start + (360f * 5f) + (float)(Math.random() * 720f); 
             
             spinAnim = android.animation.ObjectAnimator.ofFloat(spinner, "rotation", start, end);
             spinAnim.setDuration(2000);
             spinAnim.setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f));
             spinAnim.start();
             itemView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        }
    }
    
    class FidgetSwitchViewHolder extends RecyclerView.ViewHolder {
        GridLayout grid;
        View btnShuffle, btnReset;
        int[] colors = {0xFFFF5252, 0xFF448AFF, 0xFF69F0AE, 0xFFFFD740, 0xFFE040FB, 0xFF536DFE, 0xFFFF6E40, 0xFF00E5FF};
        
        FidgetSwitchViewHolder(View itemView) {
            super(itemView);
            grid = itemView.findViewById(R.id.gl_tiles);
            btnShuffle = itemView.findViewById(R.id.btn_shuffle);
            btnReset = itemView.findViewById(R.id.btn_reset);
            
            // Manual traversal fallback if ids not found (unlikely now but keeping safety)
            if (grid == null && itemView instanceof ViewGroup) {
                ViewGroup card = (ViewGroup) itemView;
                if (card.getChildCount() > 0) {
                     ViewGroup rootLinear = (ViewGroup) card.getChildAt(0);
                     // rootLinear has Header(Linear) and Grid(Grid)
                     if (rootLinear.getChildCount() > 1 && rootLinear.getChildAt(1) instanceof GridLayout) {
                         grid = (GridLayout) rootLinear.getChildAt(1);
                         ViewGroup header = (ViewGroup) rootLinear.getChildAt(0);
                         if (header.getChildCount() >= 3) {
                             btnShuffle = header.getChildAt(1);
                             btnReset = header.getChildAt(2);
                         }
                     }
                }
            }

            if (grid != null) {
                // Initialize tiles
                for(int i=0; i<grid.getChildCount(); i++) {
                     final View tile = grid.getChildAt(i);
                     tile.setOnClickListener(v -> {
                         int randomColor = colors[(int)(Math.random() * colors.length)];
                         animateTile(v, randomColor);
                         try { v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP); } catch (Exception e){} 
                     });
                     tile.setOnLongClickListener(v -> {
                         animateTile(v, Color.parseColor("#EEEEEE")); // Erase
                         v.performHapticFeedback(0);
                         return true;
                     });
                }
                
                if (btnShuffle != null) {
                    btnShuffle.setOnClickListener(v -> {
                        v.animate().rotationBy(360).setDuration(500).start();
                        for(int i=0; i<grid.getChildCount(); i++) {
                            View tile = grid.getChildAt(i);
                            int randomColor = colors[(int)(Math.random() * colors.length)];
                            // Stagger animation slightly
                            tile.postDelayed(() -> animateTile(tile, randomColor), i * 20L);
                        }
                    });
                }
                
                if (btnReset != null) {
                    btnReset.setOnClickListener(v -> {
                        v.animate().rotationBy(-360).setDuration(500).start();
                        for(int i=0; i<grid.getChildCount(); i++) {
                            View tile = grid.getChildAt(i);
                             tile.postDelayed(() -> animateTile(tile, Color.parseColor("#EEEEEE")), i * 10L);
                        }
                    });
                }
            }
        }
        
        private void animateTile(View v, int color) {
            // Apply color to drawable so we keep rounded corners
            if (v.getBackground() != null) {
                v.getBackground().mutate().setTint(color);
            }
            // Pop animation
            v.animate().scaleX(0.8f).scaleY(0.8f).setDuration(100)
                .withEndAction(() -> {
                     v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).setInterpolator(new android.view.animation.OvershootInterpolator()).start();
                }).start();
        }
    }
    
    class FidgetGravityViewHolder extends RecyclerView.ViewHolder {
        FidgetGravityViewHolder(View itemView) { super(itemView); }
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

            // Always set placeholder first to handle recycling correctly
            ivNoteImage.setImageResource(R.drawable.placeholder_image);
            
            String imageBase64 = note.getImageBase64(); 
            if (imageBase64 != null && !imageBase64.isEmpty()) {
                com.visiboard.app.utils.ImageCache.getInstance()
                    .loadBase64Image(note.getId(), imageBase64, ivNoteImage, R.drawable.placeholder_image);
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
            
            // Explicitly set placeholder first for recycling safety
            ivAvatar.setImageResource(R.drawable.ic_profile_placeholder);

            String userProfilePic = note.getUserProfilePic();
            if (userProfilePic != null && !userProfilePic.isEmpty()) {
                // For profile pics, we can use userId as key prefix
                String key = "user_" + note.getUserId();
                com.visiboard.app.utils.ImageCache.getInstance()
                    .loadBase64Image(key, userProfilePic, ivAvatar, R.drawable.ic_profile_placeholder);
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
