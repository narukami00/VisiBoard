package com.visiboard.app.ui.map;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.visiboard.app.R;
import com.visiboard.app.data.Comment;
import com.visiboard.app.data.FollowerSuggestion;
import com.visiboard.app.data.Mention;
import com.visiboard.app.ui.mentions.MentionAutocompleteAdapter;
import com.visiboard.app.utils.MentionHelper;
import com.visiboard.app.utils.MentionNotificationHelper;
import com.visiboard.app.utils.MentionTextWatcher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private View inputContainer;

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    
    private com.google.firebase.firestore.ListenerRegistration commentsListener;

    private CommentsListAdapter.OnUserClickListener userClickListener;

    public void setOnUserClickListener(CommentsListAdapter.OnUserClickListener listener) {
        this.userClickListener = listener;
    }

    private static final String ARG_IS_COMMENTS_DISABLED = "is_comments_disabled";
    private boolean isCommentsDisabled;

    // Mention autocomplete
    private PopupWindow mentionPopup;
    private MentionAutocompleteAdapter mentionAdapter;
    private MentionTextWatcher mentionTextWatcher;
    private List<FollowerSuggestion> followingList = new ArrayList<>();
    private List<MentionHelper.SelectedMention> selectedMentions = new ArrayList<>();

    public static CommentsBottomSheetFragment newInstance(String noteId, String noteOwnerId, String noteText, double lat, double lng, boolean isCommentsDisabled) {
        CommentsBottomSheetFragment fragment = new CommentsBottomSheetFragment();
        Bundle args = new Bundle();
        args.putString(ARG_NOTE_ID, noteId);
        args.putString(ARG_NOTE_OWNER_ID, noteOwnerId);
        args.putString(ARG_NOTE_TEXT, noteText);
        args.putDouble(ARG_NOTE_LAT, lat);
        args.putDouble(ARG_NOTE_LNG, lng);
        args.putBoolean(ARG_IS_COMMENTS_DISABLED, isCommentsDisabled);
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
            isCommentsDisabled = getArguments().getBoolean(ARG_IS_COMMENTS_DISABLED);
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
        inputContainer = view.findViewById(R.id.input_container);

        setupRecyclerView();
        
        if (noteId == null) {
            Toast.makeText(requireContext(), "Error: Note ID is missing", Toast.LENGTH_LONG).show();
            tvNoComments.setVisibility(View.VISIBLE);
            tvNoComments.setText("Error loading comments");
        } else {
            loadComments();
        }

        if (isCommentsDisabled) {
            btnSend.setVisibility(View.GONE);
            
            // Reuse existing EditText to show message
            etComment.setText("Comments are turned off");
            etComment.setEnabled(false);
            etComment.setFocusable(false); 
            etComment.setGravity(android.view.Gravity.CENTER);
            etComment.setTextColor(getResources().getColor(R.color.text_secondary, null));
            etComment.setBackground(null); // Remove input background
        } else {
            btnSend.setOnClickListener(v -> postComment());
            loadFollowingList();
            setupMentionAutocomplete();
        }
    }

    /**
     * Load the current user's following list for mention suggestions
     */
    private void loadFollowingList() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        db.collection("users").document(uid)
            .collection("following").get()
            .addOnSuccessListener(docs -> {
                followingList = new ArrayList<>();
                // Add @following option first
                followingList.add(FollowerSuggestion.createFollowingAll());
                
                for (DocumentSnapshot doc : docs) {
                    String followedName = doc.getString("followedName");
                    String followedPic = doc.getString("followedProfilePic");
                    followingList.add(new FollowerSuggestion(
                        doc.getId(),
                        followedName != null ? followedName : "Unknown",
                        followedPic
                    ));
                }
                android.util.Log.d("CommentsSheet", "Loaded " + followingList.size() + " following for mentions");
            })
            .addOnFailureListener(e -> {
                android.util.Log.e("CommentsSheet", "Failed to load following list", e);
            });
    }

    /**
     * Setup the mention autocomplete popup and text watcher
     */
    private void setupMentionAutocomplete() {
        // Create popup
        View popupView = LayoutInflater.from(requireContext())
            .inflate(R.layout.popup_mention_suggestions, null);
        RecyclerView rvSuggestions = popupView.findViewById(R.id.rv_suggestions);
        rvSuggestions.setLayoutManager(new LinearLayoutManager(requireContext()));

        mentionAdapter = new MentionAutocompleteAdapter(requireContext(), suggestion -> {
            // User selected a suggestion
            if (mentionTextWatcher != null) {
                mentionTextWatcher.insertMention(suggestion.getName());
            }
            
            // Track selected mention for later
            selectedMentions.add(new MentionHelper.SelectedMention(
                suggestion.getUserId(),
                suggestion.getName(),
                suggestion.isFollowingAll()
            ));
            
            hideMentionPopup();
        });
        rvSuggestions.setAdapter(mentionAdapter);

        mentionPopup = new PopupWindow(
            popupView,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            false
        );
        mentionPopup.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        mentionPopup.setOutsideTouchable(true);

        // Setup text watcher
        mentionTextWatcher = new MentionTextWatcher(etComment, new MentionTextWatcher.MentionCallback() {
            @Override
            public void onMentionQueryChanged(String query) {
                if (query.isEmpty()) {
                    hideMentionPopup();
                } else {
                    showMentionPopup(filterSuggestions(query));
                }
            }

            @Override
            public int getCursorPosition() {
                return etComment.getSelectionStart();
            }
        });
        etComment.addTextChangedListener(mentionTextWatcher);
    }

    private List<FollowerSuggestion> filterSuggestions(String query) {
        if (followingList == null) return new ArrayList<>();
        
        return followingList.stream()
            .filter(s -> s.matchesQuery(query))
            .collect(Collectors.toList());
    }

    private void showMentionPopup(List<FollowerSuggestion> suggestions) {
        if (suggestions.isEmpty()) {
            hideMentionPopup();
            return;
        }

        mentionAdapter.setSuggestions(suggestions);

        if (!mentionPopup.isShowing() && inputContainer != null) {
            // Measure the popup to know its height
            View popupView = mentionPopup.getContentView();
            popupView.measure(
                View.MeasureSpec.makeMeasureSpec(inputContainer.getWidth(), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            );
            int popupHeight = Math.min(popupView.getMeasuredHeight(), 
                (int) (168 * getResources().getDisplayMetrics().density));  // max 168dp
            
            // Get the location of input container on screen
            int[] location = new int[2];
            inputContainer.getLocationOnScreen(location);
            
            // Show popup ABOVE the input container
            mentionPopup.showAtLocation(inputContainer, Gravity.NO_GRAVITY,
                location[0],  // x position
                location[1] - popupHeight - 8);  // y position (above input with 8px gap)
        }
    }

    private void hideMentionPopup() {
        if (mentionPopup != null && mentionPopup.isShowing()) {
            mentionPopup.dismiss();
        }
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
                                    // Filter Blocked Users
                                    if (comment != null && com.visiboard.app.utils.UserCache.getInstance().isBlocked(comment.userId)) {
                                        continue;
                                    }
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

            // Process mentions
            List<Mention> mentions = MentionHelper.createMentionsForPosting(text, selectedMentions);
            if (!mentions.isEmpty()) {
                comment.setMentionsFromList(mentions);
            }

            final String userProfilePic = userDoc.getString("profilePic");

            noteRef.collection("comments").document(commentId).set(comment)
                    .addOnSuccessListener(aVoid -> {
                        android.util.Log.d("CommentsSheet", "Comment posted successfully!");
                        
                        // Clear input and mentions
                        etComment.setText("");
                        selectedMentions.clear();
                        btnSend.setEnabled(true);
                        
                        // Update comment count
                        noteRef.update("commentsCount", FieldValue.increment(1));

                        // Send notification to note owner if not self
                        if (!uid.equals(noteOwnerId)) {
                            createNotification(noteOwnerId, uid, userName, userProfilePic);
                        }

                        // Send mention notifications
                        if (!mentions.isEmpty()) {
                            MentionNotificationHelper.sendMentionNotifications(
                                uid, userName, userProfilePic,
                                noteId, noteText, noteLat, noteLng,
                                commentId, mentions
                            );
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

        // Dismiss mention popup
        hideMentionPopup();
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
