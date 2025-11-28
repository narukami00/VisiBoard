package com.visiboard.app.ui.map;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.visiboard.app.R;
import com.visiboard.app.data.Comment;

import java.util.ArrayList;
import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class CommentsAdapter extends RecyclerView.Adapter<CommentsAdapter.CommentViewHolder> {

    private List<Comment> comments = new ArrayList<>();
    private Context context;
    private FirebaseFirestore db;
    private OnUserClickListener onUserClickListener;
    private OnCommentLongClickListener onCommentLongClickListener;

    public interface OnUserClickListener {
        void onUserClick(String userId);
    }

    public interface OnCommentLongClickListener {
        void onCommentLongClick(Comment comment, int position);
    }

    public CommentsAdapter(Context context, OnUserClickListener listener, OnCommentLongClickListener longClickListener) {
        this.context = context;
        this.onUserClickListener = listener;
        this.onCommentLongClickListener = longClickListener;
        this.db = FirebaseFirestore.getInstance();
    }

    public void removeComment(int position) {
        if (position >= 0 && position < comments.size()) {
            comments.remove(position);
            notifyItemRemoved(position);
        }
    }

    public void setComments(List<Comment> comments) {
        this.comments = comments;
        notifyDataSetChanged();
    }

    public void addComment(Comment comment) {
        this.comments.add(comment);
        notifyItemInserted(comments.size() - 1);
    }

    @NonNull
    @Override
    public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_comment, parent, false);
        return new CommentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
        Comment comment = comments.get(position);
        holder.bind(comment);
    }

    @Override
    public int getItemCount() {
        return comments.size();
    }

    class CommentViewHolder extends RecyclerView.ViewHolder {
        CircleImageView commentUserPic;
        TextView tvUser;
        TextView tvText;
        TextView tvTime;

        public CommentViewHolder(@NonNull View itemView) {
            super(itemView);
            commentUserPic = itemView.findViewById(R.id.comment_user_pic);
            tvUser = itemView.findViewById(R.id.tv_comment_user);
            tvText = itemView.findViewById(R.id.tv_comment_text);
            tvTime = itemView.findViewById(R.id.tv_comment_time);
        }

        void bind(Comment comment) {
            tvUser.setText(comment.userName);
            tvText.setText(comment.text);
            tvTime.setText(getTimeAgo(comment.timestamp));

            // Load commenter's profile pic
            if (comment.userId != null) {
                db.collection("users").document(comment.userId).get()
                        .addOnSuccessListener(doc -> {
                            if (doc.exists()) {
                                String pic = doc.getString("profilePic");
                                if (pic != null && !pic.isEmpty()) {
                                    try {
                                        byte[] bytes = android.util.Base64.decode(pic, android.util.Base64.DEFAULT);
                                        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                                        commentUserPic.setImageBitmap(bitmap);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        });

                // Make avatar and name clickable to show user info
                View.OnClickListener showUserInfo = v -> {
                    if (onUserClickListener != null) {
                        onUserClickListener.onUserClick(comment.userId);
                    }
                };
                commentUserPic.setOnClickListener(showUserInfo);
                tvUser.setOnClickListener(showUserInfo);
            }

            // Handle long press for delete
            itemView.setOnLongClickListener(v -> {
                if (onCommentLongClickListener != null) {
                    onCommentLongClickListener.onCommentLongClick(comment, getAdapterPosition());
                    return true;
                }
                return false;
            });

            // Scale in animation (only run once)
            if (getAdapterPosition() > -1) {
                ScaleAnimation scaleIn = new ScaleAnimation(
                        0.5f, 1.0f, 0.5f, 1.0f,
                        Animation.RELATIVE_TO_SELF, 0.0f,
                        Animation.RELATIVE_TO_SELF, 0.0f);
                scaleIn.setDuration(300);
                itemView.startAnimation(scaleIn);
            }
        }

        private String getTimeAgo(long timestamp) {
            long diff = System.currentTimeMillis() - timestamp;
            long seconds = diff / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;
            long days = hours / 24;

            if (days > 0) return days + "d ago";
            if (hours > 0) return hours + "h ago";
            if (minutes > 0) return minutes + "m ago";
            return "just now";
        }
    }
}
