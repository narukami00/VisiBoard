package com.visiboard.app.ui.map;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.widget.ListView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.visiboard.app.R;
import com.visiboard.app.data.Comment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommentsBottomSheetFragment extends BottomSheetDialogFragment {

    private static final String ARG_NOTE_ID = "note_id";
    private static final String ARG_NOTE_OWNER_ID = "note_owner_id";
    private static final String ARG_NOTE_TEXT = "note_text";
    private static final String ARG_NOTE_LAT = "note_lat";
    private static final String ARG_NOTE_LNG = "note_lng";

    private String noteId;
    private String noteOwnerId;
    private String noteText;
    private double noteLat;
    private double noteLng;

    private ListView lvComments;
    private CommentsListAdapter adapter;
    private EditText etComment;
    private ImageButton btnSend;
    private TextView tvNoComments;

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    
    private com.google.firebase.firestore.ListenerRegistration commentsListener;

    private CommentsListAdapter.OnUserClickListener userClickListener;

    public void setOnUserClickListener(CommentsListAdapter.OnUserClickListener listener) {
        this.userClickListener = listener;
    }

    public static CommentsBottomSheetFragment newInstance(String noteId, String noteOwnerId, String noteText, double lat, double lng) {
        CommentsBottomSheetFragment fragment = new CommentsBottomSheetFragment();
        Bundle args = new Bundle();
        args.putString(ARG_NOTE_ID, noteId);
        args.putString(ARG_NOTE_OWNER_ID, noteOwnerId);
        args.putString(ARG_NOTE_TEXT, noteText);
        args.putDouble(ARG_NOTE_LAT, lat);
        args.putDouble(ARG_NOTE_LNG, lng);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            noteId = getArguments().getString(ARG_NOTE_ID);
            noteOwnerId = getArguments().getString(ARG_NOTE_OWNER_ID);
            noteText = getArguments().getString(ARG_NOTE_TEXT);
            noteLat = getArguments().getDouble(ARG_NOTE_LAT);
            noteLng = getArguments().getDouble(ARG_NOTE_LNG);
        }
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_comments_bottom_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        lvComments = view.findViewById(R.id.lv_comments);
        etComment = view.findViewById(R.id.et_comment);
        btnSend = view.findViewById(R.id.btn_send);
        tvNoComments = view.findViewById(R.id.tv_no_comments);

        setupRecyclerView();
        
        if (noteId == null) {
            Toast.makeText(requireContext(), "Error: Note ID is missing", Toast.LENGTH_LONG).show();
            tvNoComments.setVisibility(View.VISIBLE);
            tvNoComments.setText("Error loading comments");
        } else {
            loadComments();
        }

        btnSend.setOnClickListener(v -> postComment());
    }

    private void setupRecyclerView() {
        adapter = new CommentsListAdapter(requireContext(), 
            userId -> {
                if (userClickListener != null) {
                    userClickListener.onUserClick(userId);
                }
            },
            (comment, position) -> {
                // Handle long click - check if user owns this comment
                String currentUserId = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
                if (currentUserId != null && currentUserId.equals(comment.userId)) {
                    showDeleteCommentDialog(comment, position);
                } else {
                    Toast.makeText(requireContext(), "You can only delete your own comments", Toast.LENGTH_SHORT).show();
                }
            });
        lvComments.setAdapter(adapter);
        
        // Performance optimizations
        lvComments.setScrollingCacheEnabled(true);
        lvComments.setSmoothScrollbarEnabled(true);
        
        lvComments.setVisibility(View.GONE); // Initially hidden until comments load
        tvNoComments.setVisibility(View.VISIBLE); // Show "loading" state
    }

    private void loadComments() {
        if (noteId == null) {
            android.util.Log.e("CommentsSheet", "Note ID is null!");
            return;
        }

        android.util.Log.d("CommentsSheet", "Loading comments for note: " + noteId);

        commentsListener = db.collection("notes").document(noteId).collection("comments")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        android.util.Log.e("CommentsSheet", "Error loading comments", error);
                        tvNoComments.setText("Error loading comments");
                        tvNoComments.setVisibility(View.VISIBLE);
                        lvComments.setVisibility(View.GONE);
                        return;
                    }

                    if (value != null) {
                        android.util.Log.d("CommentsSheet", "Found " + value.size() + " comments");
                        if (!value.isEmpty()) {
                            List<Comment> comments = new ArrayList<>();
                            for (var doc : value) {
                                Comment comment = doc.toObject(Comment.class);
                                android.util.Log.d("CommentsSheet", "Comment: " + comment.text + " by " + comment.userName);
                                comments.add(comment);
                            }
                            adapter.setComments(comments);
                            tvNoComments.setVisibility(View.GONE);
                            lvComments.setVisibility(View.VISIBLE);
                            // Scroll to bottom
                            lvComments.post(() -> {
                                if (comments.size() > 0) {
                                    lvComments.setSelection(comments.size() - 1);
                                }
                            });
                        } else {
                            android.util.Log.d("CommentsSheet", "No comments found");
                            adapter.setComments(new ArrayList<>());
                            tvNoComments.setText("No comments yet. Be the first!");
                            tvNoComments.setVisibility(View.VISIBLE);
                            lvComments.setVisibility(View.GONE);
                        }
                    } else {
                        android.util.Log.e("CommentsSheet", "Value is null");
                    }
                });
    }

    private void postComment() {
        android.util.Log.d("CommentsSheet", "postComment called");
        
        if (noteId == null) {
            android.util.Log.e("CommentsSheet", "Cannot post - noteId is null");
            Toast.makeText(requireContext(), "Error: Cannot post comment (No Note ID)", Toast.LENGTH_SHORT).show();
            return;
        }

        String text = etComment.getText().toString().trim();
        android.util.Log.d("CommentsSheet", "Comment text: '" + text + "'");
        
        if (TextUtils.isEmpty(text)) {
            android.util.Log.d("CommentsSheet", "Comment text is empty, ignoring");
            return;
        }

        if (auth.getCurrentUser() == null) {
            android.util.Log.e("CommentsSheet", "User not logged in");
            Toast.makeText(requireContext(), "You must be logged in to comment", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSend.setEnabled(false);
        String uid = auth.getCurrentUser().getUid();
        android.util.Log.d("CommentsSheet", "Posting comment for user: " + uid);

        db.collection("users").document(uid).get().addOnSuccessListener(userDoc -> {
            String name = userDoc.getString("name");
            final String userName = name != null ? name : "Anonymous";
            android.util.Log.d("CommentsSheet", "User name: " + userName);

            DocumentReference noteRef = db.collection("notes").document(noteId);
            String commentId = noteRef.collection("comments").document().getId();
            android.util.Log.d("CommentsSheet", "Generated comment ID: " + commentId);

            Comment comment = new Comment(
                    commentId,
                    uid,
                    userName,
                    text,
                    System.currentTimeMillis()
            );

            noteRef.collection("comments").document(commentId).set(comment)
                    .addOnSuccessListener(aVoid -> {
                        android.util.Log.d("CommentsSheet", "Comment posted successfully!");
                        etComment.setText("");
                        btnSend.setEnabled(true);
                        
                        // Update comment count
                        noteRef.update("commentsCount", FieldValue.increment(1));

                        // Send notification if not owner
                        if (!uid.equals(noteOwnerId)) {
                            createNotification(noteOwnerId, uid, userName, userDoc.getString("profilePic"));
                        }
                        
                        // Hide keyboard
                        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) {
                            imm.hideSoftInputFromWindow(etComment.getWindowToken(), 0);
                        }
                    })
                    .addOnFailureListener(e -> {
                        android.util.Log.e("CommentsSheet", "Failed to post comment", e);
                        btnSend.setEnabled(true);
                        Toast.makeText(requireContext(), "Failed to post comment", Toast.LENGTH_SHORT).show();
                    });
        }).addOnFailureListener(e -> {
            android.util.Log.e("CommentsSheet", "Failed to get user data", e);
            btnSend.setEnabled(true);
            Toast.makeText(requireContext(), "Failed to load user data", Toast.LENGTH_SHORT).show();
        });
    }

    private void createNotification(String toUserId, String fromUserId, String fromUserName, String fromUserPic) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("toUserId", toUserId);
        notification.put("fromUserId", fromUserId);
        notification.put("fromUserName", fromUserName);
        notification.put("fromUserProfilePic", fromUserPic);
        notification.put("type", "comment");
        notification.put("timestamp", System.currentTimeMillis());
        notification.put("read", false);
        notification.put("noteId", noteId);
        notification.put("noteText", noteText);
        notification.put("noteLat", noteLat);
        notification.put("noteLng", noteLng);

        db.collection("notifications").add(notification);
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        
        // Remove Firestore listener to prevent memory leak
        if (commentsListener != null) {
            commentsListener.remove();
            commentsListener = null;
        }
        
        // Clear cache to free memory
        if (adapter != null) {
            adapter.clearCache();
        }
    }

    private void showDeleteCommentDialog(Comment comment, int position) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_confirmation, null);
        TextView title = dialogView.findViewById(R.id.dialog_title);
        TextView message = dialogView.findViewById(R.id.dialog_message);
        android.widget.Button btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        android.widget.Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        
        title.setText("Delete Comment");
        message.setText("Are you sure you want to delete this comment?");
        btnConfirm.setText("Delete");

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnConfirm.setOnClickListener(v -> {
            deleteComment(comment, position);
            dialog.dismiss();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
    }

    private void deleteComment(Comment comment, int position) {
        if (noteId == null || comment.id == null) {
            android.util.Log.e("CommentsSheet", "Cannot delete - noteId or comment.id is null");
            return;
        }

        DocumentReference noteRef = db.collection("notes").document(noteId);
        
        android.util.Log.d("CommentsSheet", "Deleting comment: " + comment.id);
        
        // Use transaction to prevent race conditions
        db.runTransaction(transaction -> {
            // Read first (required by Firestore transactions)
            com.google.firebase.firestore.DocumentSnapshot snapshot = transaction.get(noteRef);
            
            // Delete the comment document
            transaction.delete(noteRef.collection("comments").document(comment.id));
            
            // Decrement the comment count
            Long currentCount = snapshot.getLong("commentsCount");
            if (currentCount != null && currentCount > 0) {
                transaction.update(noteRef, "commentsCount", currentCount - 1);
            } else {
                transaction.update(noteRef, "commentsCount", 0);
            }
            
            return null;
        }).addOnSuccessListener(aVoid -> {
            android.util.Log.d("CommentsSheet", "Comment deleted successfully");
            Toast.makeText(requireContext(), "Comment deleted", Toast.LENGTH_SHORT).show();
            // Don't manually remove from adapter - the snapshot listener will handle it
        }).addOnFailureListener(e -> {
            android.util.Log.e("CommentsSheet", "Failed to delete comment", e);
            Toast.makeText(requireContext(), "Failed to delete comment", Toast.LENGTH_SHORT).show();
        });
    }
}
