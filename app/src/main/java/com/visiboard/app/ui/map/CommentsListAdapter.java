package com.visiboard.app.ui.map;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.visiboard.app.R;
import com.visiboard.app.data.Comment;
import com.visiboard.app.data.Mention;
import com.visiboard.app.utils.MentionHelper;

import java.util.ArrayList;
import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class CommentsListAdapter extends BaseAdapter {

    private List<Comment> comments = new ArrayList<>();
    private Context context;
    private FirebaseFirestore db;
    private OnUserClickListener onUserClickListener;
    private OnCommentLongClickListener onCommentLongClickListener;
    
    // Cache for profile pictures to avoid redundant database queries
    private java.util.HashMap<String, Bitmap> profilePicCache = new java.util.HashMap<>();
    private java.util.HashSet<String> loadingUserIds = new java.util.HashSet<>();

    public interface OnUserClickListener {
        void onUserClick(String userId);
    }

    public interface OnCommentLongClickListener {
        void onCommentLongClick(Comment comment, int position);
    }

    public CommentsListAdapter(Context context, OnUserClickListener listener, OnCommentLongClickListener longClickListener) {
        this.context = context;
        this.onUserClickListener = listener;
        this.onCommentLongClickListener = longClickListener;
        this.db = FirebaseFirestore.getInstance();
    }
    
    public void clearCache() {
        profilePicCache.clear();
        loadingUserIds.clear();
    }

    public void setComments(List<Comment> comments) {
        this.comments = comments;
        notifyDataSetChanged();
    }

    public void addComment(Comment comment) {
        this.comments.add(comment);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return comments.size();
    }

    @Override
    public Comment getItem(int position) {
        return comments.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_comment, parent, false);
            holder = new ViewHolder();
            holder.commentUserPic = convertView.findViewById(R.id.comment_user_pic);
            holder.tvUser = convertView.findViewById(R.id.tv_comment_user);
            holder.tvText = convertView.findViewById(R.id.tv_comment_text);
            holder.tvTime = convertView.findViewById(R.id.tv_comment_time);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        Comment comment = getItem(position);
        holder.bind(comment, position, convertView);
        
        return convertView;
    }

    private class ViewHolder {
        CircleImageView commentUserPic;
        TextView tvUser;
        TextView tvText;
        TextView tvTime;

        void bind(Comment comment, int position, View itemView) {
            tvUser.setText(comment.userName);
            tvTime.setText(getTimeAgo(comment.timestamp));

            // Display comment text with styled mentions
            List<Mention> mentions = comment.getMentionsParsed();
            if (mentions != null && !mentions.isEmpty()) {
                // Apply mention styling
                MentionHelper.applyMentionsToTextView(context, tvText, comment.text, mentions,
                    userId -> {
                        if (onUserClickListener != null) {
                            onUserClickListener.onUserClick(userId);
                        }
                    });
            } else {
                tvText.setText(comment.text);
            }

            // Reset to default image first
            commentUserPic.setImageResource(R.drawable.ic_profile_placeholder);
            commentUserPic.setTag(comment.userId); // Tag with current user ID

            // Load commenter's profile pic with caching
            if (comment.userId != null) {
                // Check if already in cache
                if (profilePicCache.containsKey(comment.userId)) {
                    Bitmap cachedBitmap = profilePicCache.get(comment.userId);
                    if (cachedBitmap != null) {
                        commentUserPic.setImageBitmap(cachedBitmap);
                    }
                } else if (!loadingUserIds.contains(comment.userId)) {
                    // Not in cache and not currently loading - fetch it
                    loadingUserIds.add(comment.userId);
                    
                    db.collection("users").document(comment.userId).get()
                            .addOnSuccessListener(doc -> {
                                loadingUserIds.remove(comment.userId);
                                
                                if (doc.exists()) {
                                    String pic = doc.getString("profilePic");
                                    if (pic != null && !pic.isEmpty()) {
                                        try {
                                            byte[] bytes = android.util.Base64.decode(pic, android.util.Base64.DEFAULT);
                                            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                                            
                                            // Store in cache
                                            profilePicCache.put(comment.userId, bitmap);
                                            
                                            // Update UI only if view is still bound to this user
                                            if (comment.userId.equals(commentUserPic.getTag())) {
                                                commentUserPic.setImageBitmap(bitmap);
                                            }
                                            
                                            // Notify adapter to update other visible rows for this user
                                            notifyDataSetChanged();
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    } else {
                                        // Store null to indicate no profile pic available
                                        profilePicCache.put(comment.userId, null);
                                    }
                                } else {
                                    profilePicCache.put(comment.userId, null);
                                }
                            })
                            .addOnFailureListener(e -> {
                                loadingUserIds.remove(comment.userId);
                                e.printStackTrace();
                            });
                }

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
                    onCommentLongClickListener.onCommentLongClick(comment, position);
                    return true;
                }
                return false;
            });
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
