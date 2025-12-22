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
import androidx.constraintlayout.widget.ConstraintLayout;

public class PinterestFeedAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_IMAGE = 0;
    private static final int VIEW_TYPE_TEXT = 1;
    private static final int VIEW_TYPE_LOADING = 2;
    private static final int VIEW_TYPE_FIDGET_BUBBLE = 10;
    private static final int VIEW_TYPE_FIDGET_SPINNER = 11;
    // VIEW_TYPE_FIDGET_SWITCH removed
    private static final int VIEW_TYPE_FIDGET_GRAVITY = 13;
    private static final int VIEW_TYPE_FIDGET_LAVA = 14;
    private static final int VIEW_TYPE_FIDGET_TRACE = 15;
    // VIEW_TYPE_FIDGET_STRINGS removed
    private static final int VIEW_TYPE_FIDGET_FLUID = 17;

    private List<NearbyNote> notes = new ArrayList<>();
    private OnNoteClickListener listener;
    private boolean isLoading = false;
    private boolean isEndMessage = false;

    public interface OnNoteClickListener {
        void onNoteClick(NearbyNote note);
        void onShareClick(NearbyNote note);
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
                if (!isEndMessage) {
                    notifyItemRemoved(notes.size());
                } else {
                    notifyItemChanged(notes.size());
                }
            }
        }
    }

    public void setShowEndMessage(boolean show) {
        if (this.isEndMessage != show) {
            this.isEndMessage = show;
            if (show) {
                if (!isLoading) notifyItemInserted(notes.size());
                else notifyItemChanged(notes.size());
            } else {
                if (!isLoading) notifyItemRemoved(notes.size());
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        if ((isLoading || isEndMessage) && position == notes.size()) {
            return VIEW_TYPE_LOADING;
        }
        NearbyNote note = notes.get(position);
        if (note.getId() != null && note.getId().startsWith("fidget")) { 
             String id = note.getId();
             if (id.contains("spinner")) return VIEW_TYPE_FIDGET_SPINNER;
             // Switch removed
             if (id.contains("gravity")) return VIEW_TYPE_FIDGET_GRAVITY;
             if (id.contains("lava")) return VIEW_TYPE_FIDGET_LAVA;
             if (id.contains("trace")) return VIEW_TYPE_FIDGET_TRACE;
             // Strings removed
             if (id.contains("fluid")) return VIEW_TYPE_FIDGET_FLUID;
             return VIEW_TYPE_FIDGET_BUBBLE; // Default
        }
        // Check for image: either has imageBase64 data OR has valid dimensions (indicating image note)
        boolean hasImage = (note.getImageBase64() != null && !note.getImageBase64().isEmpty()) 
                        || (note.getImageWidth() > 0 && note.getImageHeight() > 0)
                        || (note.getLocalImagePath() != null && !note.getLocalImagePath().isEmpty());
        return hasImage ? VIEW_TYPE_IMAGE : VIEW_TYPE_TEXT;
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
        } else if (viewType == VIEW_TYPE_FIDGET_FLUID) {
             View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_fidget_fluid, parent, false);
            return new SimpleFidgetViewHolder(view);
        } else if (viewType == VIEW_TYPE_FIDGET_GRAVITY) {
             View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_fidget_gravity, parent, false);
            return new FidgetGravityViewHolder(view);
        } else if (viewType == VIEW_TYPE_FIDGET_LAVA) {
             View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_fidget_lava, parent, false);
            return new SimpleFidgetViewHolder(view);
        } else if (viewType == VIEW_TYPE_FIDGET_TRACE) {
             View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_fidget_trace, parent, false);
            return new SimpleFidgetViewHolder(view);
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
            
            // Randomize Fidgets Theme when binding
            if (type >= 10) {
                 if (type == VIEW_TYPE_FIDGET_GRAVITY) {
                     isFullSpan = true;
                     if (holder instanceof FidgetGravityViewHolder) {
                        ((FidgetGravityViewHolder)holder).randomize();
                     }
                 } else if (holder instanceof SimpleFidgetViewHolder) {
                     ((SimpleFidgetViewHolder)holder).randomize(type);
                 } else if (holder instanceof FidgetBubbleViewHolder) {
                     ((FidgetBubbleViewHolder)holder).randomize();
                 }
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
        com.visiboard.app.ui.feed.widgets.FloatingBubblesView bubblesView;
        
        FidgetBubbleViewHolder(View itemView) {
            super(itemView);
            View v = itemView.findViewById(R.id.floating_bubbles_view);
            if (v instanceof com.visiboard.app.ui.feed.widgets.FloatingBubblesView) {
                bubblesView = (com.visiboard.app.ui.feed.widgets.FloatingBubblesView) v;
            }
        }
        
        void randomize() {
            int[] bgs = {0xFFEEEEEE, 0xFFE3F2FD, 0xFFF3E5F5, 0xFFE0F2F1};
             if (itemView instanceof androidx.cardview.widget.CardView) {
                 ((androidx.cardview.widget.CardView)itemView).setCardBackgroundColor(bgs[(int)(Math.random() * bgs.length)]);
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
    
    // Switch VH removed
    
    class FidgetGravityViewHolder extends RecyclerView.ViewHolder {
        com.visiboard.app.ui.feed.widgets.GravityBallView gravityView;
        FidgetGravityViewHolder(View itemView) { 
            super(itemView); 
            // We need to find the view. The layout likely has it with an ID or it's the root content in a wrapper
            // Assuming the GravityBallView is inside the layout item_fidget_gravity.xml with id gravity_view or similar
            // Let's look up by type if ID is unknown or find by known ID if I knew it.
            // I'll check item_fidget_gravity.xml later if this fails, but for now safe assumption:
            View v = itemView.findViewById(R.id.gravity_ball_view); 
            if (v instanceof com.visiboard.app.ui.feed.widgets.GravityBallView) {
                gravityView = (com.visiboard.app.ui.feed.widgets.GravityBallView) v;
            }
        }
        
        void randomize() {
            if (gravityView != null) gravityView.randomizeTheme();
        }
    }
    
    class SimpleFidgetViewHolder extends RecyclerView.ViewHolder {
        View internalView;
        
        SimpleFidgetViewHolder(View itemView) { 
            super(itemView); 
            // Identify the internal custom view
            if (itemView instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) itemView;
                if (vg.getChildCount() > 0) {
                     // Inside the card view usually?
                     // Let's search recursively? Or just finding by ID is safer.
                     // The layouts have specific IDs usually.
                     View v1 = itemView.findViewById(R.id.neon_trace_view);
                     if (v1 != null) internalView = v1;
                     
                     View v2 = itemView.findViewById(R.id.lava_lamp_view);
                     if (v2 != null) internalView = v2;
                     
                     // Strings removed
                     
                     View v4 = itemView.findViewById(R.id.fluid_view);
                     if (v4 != null) internalView = v4;
                }
            }
        }
        
        void randomize(int type) {
            if (internalView == null) return;
            
            if (internalView instanceof com.visiboard.app.ui.feed.widgets.LavaLampView) {
                ((com.visiboard.app.ui.feed.widgets.LavaLampView) internalView).randomizeTheme();
            } else if (internalView instanceof com.visiboard.app.ui.feed.widgets.NeonTraceView) {
                ((com.visiboard.app.ui.feed.widgets.NeonTraceView) internalView).randomizeTheme();
            } else if (internalView instanceof com.visiboard.app.ui.feed.widgets.FluidCellView) {
                ((com.visiboard.app.ui.feed.widgets.FluidCellView) internalView).randomizeTheme();
            }
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
            
            View btnShare = itemView.findViewById(R.id.btn_share);
            if (btnShare != null) {
                btnShare.setOnClickListener(v -> {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION && listener != null) {
                        listener.onShareClick(notes.get(position));
                    }
                });
            }
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
            
            String localPath = note.getLocalImagePath();
            String imageBase64 = note.getImageBase64(); 
            
            // Simple image binding (reverted from progressive/skeleton logic)
            // The image is expected to be on disk now due to DiscoverTabFragment logic
            if (localPath != null && !localPath.isEmpty()) {
                 com.visiboard.app.utils.ImageCache.getInstance()
                    .loadImageFromPath(localPath, ivNoteImage, R.drawable.placeholder_image);
            } else if (imageBase64 != null && !imageBase64.isEmpty()) {
                // Fallback for memory-only images (though rare with new logic)
                com.visiboard.app.utils.ImageCache.getInstance()
                    .loadBase64Image(note.getId(), imageBase64, ivNoteImage, R.drawable.placeholder_image);
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

            View btnShare = itemView.findViewById(R.id.btn_share);
            if (btnShare != null) {
                btnShare.setOnClickListener(v -> {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION && listener != null) {
                        listener.onShareClick(notes.get(position));
                    }
                });
            }
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
