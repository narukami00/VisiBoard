package com.visiboard.app.ui.map;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.visiboard.app.R;
import com.visiboard.app.data.Comment;

import java.text.SimpleDateFormat;

import org.json.JSONArray;
import org.json.JSONObject;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.OnMapReadyCallback;
import org.maplibre.android.maps.Style;
import org.maplibre.android.plugins.annotation.Symbol;
import org.maplibre.android.plugins.annotation.SymbolManager;
import org.maplibre.android.plugins.annotation.SymbolOptions;

import java.util.HashMap;
import java.util.Map;

public class MapFragment extends Fragment {

    private static final String TAG = "MapFragment";
    private static final int LOCATION_PERMISSION_REQUEST = 100;
    private static final String PREFS_NAME = "notes_prefs";
    private static final String NOTES_KEY = "notes_array";
    
    // Nice vibrant colors for note cards
    private static final int[] NOTE_COLORS = {
        0xFF6C5CE7, // Purple
        0xFF74B9FF, // Sky Blue
        0xFF00B894, // Teal
        0xFFFF6B6B, // Coral Red
        0xFFFDCB6E, // Yellow
        0xFFE17055, // Orange
        0xFFA29BFE, // Light Purple
        0xFF55EFC4, // Mint
        0xFFFF7675, // Pink
        0xFFFD79A8, // Rose
        0xFF00CEC9, // Cyan
        0xFF81ECEC  // Aqua
    };
    
    private static final int[] NOTE_BORDER_COLORS = {
        0xFF5849C7, // Dark Purple
        0xFF5A9DE8, // Dark Sky Blue
        0xFF00966D, // Dark Teal
        0xFFE84545, // Dark Coral
        0xFFE9B949, // Dark Yellow
        0xFFCB5A3E, // Dark Orange
        0xFF8B7EE8, // Dark Light Purple
        0xFF3ACF98, // Dark Mint
        0xFFE85454, // Dark Pink
        0xFFE35B89, // Dark Rose
        0xFF00A8A5, // Dark Cyan
        0xFF5FD4D4  // Dark Aqua
    };

    private MapView mapView;
    private MapLibreMap mapLibreMap;
    private SymbolManager symbolManager;
    private Symbol userLocationSymbol;

    private FusedLocationProviderClient fusedLocationClient;
    private SharedPreferences sharedPreferences;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private boolean useCloudMode = true; // true: Firestore, false: SharedPreferences

    private final String GEOAPIFY_STYLE_URL =
            "https://maps.geoapify.com/v1/styles/osm-bright/style.json?apiKey=4034ef4942f146d6b43fd4a9871cfdc3";

    private static final String MARKER_ICON_ID_USER_LOCATION = "MARKER_ICON_USER_LOCATION";

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_map, container, false);

        mapView = view.findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        Switch switchMode = view.findViewById(R.id.switch_mode);
        switchMode.setChecked(useCloudMode); // initialize


        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(@NonNull MapLibreMap mapLibreMapReady) {
                mapLibreMap = mapLibreMapReady;
                mapLibreMap.setStyle(new Style.Builder().fromUri(GEOAPIFY_STYLE_URL), style -> {
                    style.addImage(MARKER_ICON_ID_USER_LOCATION, getBitmapFromVectorDrawable(R.drawable.ic_marker));

                    symbolManager = new SymbolManager(mapView, mapLibreMap, style);
                    symbolManager.setIconAllowOverlap(true);
                    symbolManager.setTextAllowOverlap(true);

                    enableUserLocation();
                    loadSavedNotes();
                    
                    handleNavigationArguments();

                    symbolManager.addClickListener(symbol -> {
                        if (symbol.getData() != null) {
                            try {
                                JSONObject data = new JSONObject(symbol.getData().toString());
                                String noteText = data.getString("note");
                                long timestamp = data.getLong("timestamp");
                                String docId = data.has("docId") ? data.getString("docId") : null;
                                String ownerId = data.has("userId") ? data.getString("userId") : null;

                                showCustomInfoWindow(noteText, timestamp, symbol.getLatLng(), symbol, docId, ownerId);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        return true;
                    });
                });
            }
        });

        // Floating button to add note
        view.findViewById(R.id.btnAddNote).setOnClickListener(v -> {
            if (ActivityCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(requireContext(), "Location permission not granted", Toast.LENGTH_SHORT).show();
                return;
            }

            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                    showAddNoteDialog(currentLatLng); // 👈 add note exactly at your location
                } else {
                    Toast.makeText(requireContext(), "Unable to get current location", Toast.LENGTH_SHORT).show();
                }
            });
        });

        // Switch between local and firestore
        switchMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            useCloudMode = isChecked;
            Toast.makeText(requireContext(), useCloudMode ? "Cloud Mode" : "Local Mode", Toast.LENGTH_SHORT).show();

            if (symbolManager != null) {
                symbolManager.deleteAll(); // clears all markers
            }

            // Reload notes
            loadSavedNotes();

            // Re-add user location marker
            enableUserLocation();
        });



        return view;
    }

    // Convert vector drawable to bitmap
    private Bitmap getBitmapFromVectorDrawable(int drawableId) {
        Drawable drawable = ContextCompat.getDrawable(requireContext(), drawableId);
        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    // Add note marker
    private void addNoteMarker(LatLng position, String fullNote, String shortNote, long timestamp, String docId, String userId) {
        if (symbolManager == null || mapLibreMap == null) return;

        View noteCardView = LayoutInflater.from(requireContext()).inflate(R.layout.note_card_layout, null);
        TextView noteTextView = noteCardView.findViewById(R.id.note_text_view);
        noteTextView.setText(shortNote);
        
        // Generate random color index based on note ID or timestamp
        int colorIndex = (docId != null ? docId.hashCode() : (int) timestamp) % NOTE_COLORS.length;
        if (colorIndex < 0) colorIndex = -colorIndex;
        
        // Apply random colors
        int backgroundColor = NOTE_COLORS[colorIndex];
        int borderColor = NOTE_BORDER_COLORS[colorIndex];
        
        // Create gradient drawable programmatically
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(16 * getResources().getDisplayMetrics().density);
        drawable.setColor(backgroundColor);
        drawable.setStroke((int)(2 * getResources().getDisplayMetrics().density), borderColor);
        noteTextView.setBackground(drawable);
        noteTextView.setTextColor(0xFFFFFFFF); // White text for better contrast

        Bitmap noteBitmap = getBitmapFromView(noteCardView);
        String iconId = "note_icon_" + System.currentTimeMillis() + "_" + (docId != null ? docId : timestamp);
        mapLibreMap.getStyle().addImage(iconId, noteBitmap);

        try {
            JSONObject data = new JSONObject();
            data.put("note", fullNote);
            data.put("timestamp", timestamp);
            if (docId != null) data.put("docId", docId);
            if (userId != null) data.put("userId", userId);
            Gson gson = new Gson();
            JsonElement jsonData = gson.fromJson(data.toString(), JsonElement.class);

            symbolManager.create(new SymbolOptions()
                    .withLatLng(position)
                    .withIconImage(iconId)
                    .withData(jsonData));
        } catch (Exception e) { e.printStackTrace(); }
    }

    // Convert view to bitmap
    private Bitmap getBitmapFromView(View view) {
        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
        Bitmap bitmap = Bitmap.createBitmap(view.getMeasuredWidth(), view.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        view.draw(canvas);
        return bitmap;
    }

    // Show info window with delete, like, and comment
    private void showCustomInfoWindow(String noteText, long timestamp, LatLng position, Symbol symbol, String docId, String noteOwnerId) {
        View infoWindow = LayoutInflater.from(requireContext()).inflate(R.layout.custom_info_window, null);

        // Find views
        de.hdodenhof.circleimageview.CircleImageView ownerProfilePic = infoWindow.findViewById(R.id.owner_profile_pic);
        TextView ownerName = infoWindow.findViewById(R.id.owner_name);
        TextView noteTextView = infoWindow.findViewById(R.id.note_text);
        TextView timestampTextView = infoWindow.findViewById(R.id.note_timestamp);
        LinearLayout ownerSection = infoWindow.findViewById(R.id.owner_section);
        android.widget.Button btnFollowOwner = infoWindow.findViewById(R.id.btn_follow_owner);
        LinearLayout interactionSection = infoWindow.findViewById(R.id.interaction_section);
        LinearLayout likeSection = infoWindow.findViewById(R.id.like_section);
        ImageView btnLike = infoWindow.findViewById(R.id.btn_like);
        TextView tvLikeCount = infoWindow.findViewById(R.id.tv_like_count);
        LinearLayout commentSection = infoWindow.findViewById(R.id.comment_section);
        ImageView btnComment = infoWindow.findViewById(R.id.btn_comment);
        TextView tvCommentCount = infoWindow.findViewById(R.id.tv_comment_count);
        LinearLayout commentsContainer = infoWindow.findViewById(R.id.comments_container);
        TextView tvCommentsHeader = infoWindow.findViewById(R.id.tv_comments_header);

        noteTextView.setText(noteText);
        timestampTextView.setText(new SimpleDateFormat("dd MMM yyyy • hh:mm a", java.util.Locale.getDefault())
                .format(new java.util.Date(timestamp)));

        // Check if current user owns this note
        String currentUserId = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
        boolean isOwner = currentUserId != null && currentUserId.equals(noteOwnerId);

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setView(infoWindow)
                .setNegativeButton("Close", (d, w) -> d.dismiss());

        // Only show delete button if user owns the note
        if (isOwner) {
            builder.setPositiveButton("Delete", (d, w) -> {
                if (useCloudMode && docId != null) {
                    deleteNoteFirestore(docId, noteOwnerId);
                } else {
                    deleteNoteLocally(position);
                }
                symbolManager.delete(symbol);
            });
        }

        androidx.appcompat.app.AlertDialog dialog = builder.create();

        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        // Load owner info and show interactions only in cloud mode
        if (useCloudMode && noteOwnerId != null) {
            // Load owner info
            db.collection("users").document(noteOwnerId).get()
                    .addOnSuccessListener(userDoc -> {
                        if (userDoc.exists()) {
                            String name = userDoc.getString("name");
                            ownerName.setText(name != null ? name : "Anonymous");
                            
                            String pic = userDoc.getString("profilePic");
                            if (pic != null && !pic.isEmpty()) {
                                try {
                                    byte[] bytes = android.util.Base64.decode(pic, android.util.Base64.DEFAULT);
                                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                                    ownerProfilePic.setImageBitmap(bitmap);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    });

            // Make owner section clickable to show user info
            ownerSection.setOnClickListener(v -> showUserInfoDialog(noteOwnerId));

            // Show follow button only if viewing someone else's note
            if (!isOwner && currentUserId != null) {
                btnFollowOwner.setVisibility(View.VISIBLE);
                
                // Check if already following
                db.collection("users").document(currentUserId)
                        .collection("following").document(noteOwnerId)
                        .get()
                        .addOnSuccessListener(doc -> {
                            if (doc.exists()) {
                                btnFollowOwner.setText("Following");
                                btnFollowOwner.setBackgroundResource(R.drawable.btn_following_selector);
                                btnFollowOwner.setTextColor(getResources().getColor(R.color.button_text_following, null));
                            } else {
                                btnFollowOwner.setText("Follow");
                                btnFollowOwner.setBackgroundResource(R.drawable.btn_primary_selector);
                                btnFollowOwner.setTextColor(getResources().getColor(R.color.button_text_primary, null));
                            }
                        });
                
                btnFollowOwner.setOnClickListener(v -> {
                    if (btnFollowOwner.getText().equals("Follow")) {
                        followUser(noteOwnerId, btnFollowOwner);
                    } else {
                        showUnfollowConfirmation(noteOwnerId, btnFollowOwner);
                    }
                });
            }

            // Show interaction section and load likes/comments
            if (docId != null && currentUserId != null) {
                interactionSection.setVisibility(View.VISIBLE);
                DocumentReference noteRef = db.collection("notes").document(docId);

                // Track if like button is being processed to prevent double-clicks
                final boolean[] isProcessingLike = {false};

                // Load like count and check if user liked
                noteRef.get().addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Long likeCount = doc.getLong("likeCount");
                        tvLikeCount.setText(String.valueOf(likeCount != null ? likeCount : 0));
                        
                        java.util.List<String> likedBy = (java.util.List<String>) doc.get("likedBy");
                        boolean isLiked = likedBy != null && likedBy.contains(currentUserId);
                        btnLike.setImageResource(isLiked ? R.drawable.ic_heart : R.drawable.ic_heart_outline);
                    }
                });

                // Load comments
                noteRef.collection("comments").orderBy("timestamp").get()
                        .addOnSuccessListener(querySnapshot -> {
                            int commentCount = querySnapshot.size();
                            tvCommentCount.setText(String.valueOf(commentCount));
                            
                            if (commentCount > 0) {
                                tvCommentsHeader.setVisibility(View.VISIBLE);
                                for (var commentDoc : querySnapshot) {
                                    Comment comment = commentDoc.toObject(Comment.class);
                                    addCommentView(commentsContainer, comment);
                                }
                            }
                        });

                // Like button click with double-click prevention
                likeSection.setOnClickListener(v -> {
                    if (isProcessingLike[0]) return; // Prevent double-click
                    isProcessingLike[0] = true;
                    
                    noteRef.get().addOnSuccessListener(doc -> {
                        java.util.List<String> likedBy = (java.util.List<String>) doc.get("likedBy");
                        boolean isLiked = likedBy != null && likedBy.contains(currentUserId);
                        
                        if (isLiked) {
                            // Unlike
                            noteRef.update("likedBy", FieldValue.arrayRemove(currentUserId),
                                    "likeCount", FieldValue.increment(-1))
                                    .addOnSuccessListener(aVoid -> {
                                        btnLike.setImageResource(R.drawable.ic_heart_outline);
                                        int count = Integer.parseInt(tvLikeCount.getText().toString());
                                        tvLikeCount.setText(String.valueOf(Math.max(0, count - 1)));
                                        isProcessingLike[0] = false;
                                    })
                                    .addOnFailureListener(e -> isProcessingLike[0] = false);
                        } else {
                            // Like
                            noteRef.update("likedBy", FieldValue.arrayUnion(currentUserId),
                                    "likeCount", FieldValue.increment(1))
                                    .addOnSuccessListener(aVoid -> {
                                        btnLike.setImageResource(R.drawable.ic_heart);
                                        animateLike(btnLike);
                                        int count = Integer.parseInt(tvLikeCount.getText().toString());
                                        tvLikeCount.setText(String.valueOf(count + 1));
                                        isProcessingLike[0] = false;
                                        
                                        if (!isOwner) {
                                            createNotification(noteOwnerId, currentUserId, "like", docId, noteText, position);
                                        }
                                    })
                                    .addOnFailureListener(e -> isProcessingLike[0] = false);
                        }
                    }).addOnFailureListener(e -> isProcessingLike[0] = false);
                });

                // Comment button click
                commentSection.setOnClickListener(v -> showAddCommentDialog(noteRef, commentsContainer, tvCommentCount, tvCommentsHeader, noteOwnerId, position, noteText));
            }
        } else {
            // Offline mode - hide interaction section and show default owner info
            interactionSection.setVisibility(View.GONE);
            ownerName.setText("Local User");
        }

        dialog.show();
    }

    // Animate like button
    private void animateLike(ImageView likeBtn) {
        ScaleAnimation scaleAnimation = new ScaleAnimation(
                1.0f, 1.3f, 1.0f, 1.3f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);
        scaleAnimation.setDuration(200);
        scaleAnimation.setRepeatCount(1);
        scaleAnimation.setRepeatMode(Animation.REVERSE);
        likeBtn.startAnimation(scaleAnimation);
    }

    // Add comment view to container
    private void addCommentView(LinearLayout container, Comment comment) {
        View commentView = LayoutInflater.from(requireContext()).inflate(R.layout.item_comment, container, false);
        
        de.hdodenhof.circleimageview.CircleImageView commentUserPic = commentView.findViewById(R.id.comment_user_pic);
        TextView tvUser = commentView.findViewById(R.id.tv_comment_user);
        TextView tvText = commentView.findViewById(R.id.tv_comment_text);
        TextView tvTime = commentView.findViewById(R.id.tv_comment_time);
        
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
            View.OnClickListener showUserInfo = v -> showUserInfoDialog(comment.userId);
            commentUserPic.setOnClickListener(showUserInfo);
            tvUser.setOnClickListener(showUserInfo);
        }
        
        // Scale in animation
        ScaleAnimation scaleIn = new ScaleAnimation(
                0.5f, 1.0f, 0.5f, 1.0f,
                Animation.RELATIVE_TO_SELF, 0.0f,
                Animation.RELATIVE_TO_SELF, 0.0f);
        scaleIn.setDuration(300);
        commentView.startAnimation(scaleIn);
        
        container.addView(commentView);
    }

    // Show add comment dialog
    private void showAddCommentDialog(DocumentReference noteRef, LinearLayout commentsContainer, 
                                      TextView tvCommentCount, TextView tvCommentsHeader,
                                      String noteOwnerId, LatLng notePosition, String noteText) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_comment, null);
        
        com.google.android.material.textfield.TextInputEditText commentInput = dialogView.findViewById(R.id.comment_input);
        
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        
        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());
        
        dialogView.findViewById(R.id.btn_post).setOnClickListener(v -> {
            String commentText = commentInput.getText().toString().trim();
            if (!commentText.isEmpty() && auth.getCurrentUser() != null) {
                String uid = auth.getCurrentUser().getUid();
                
                // Get user name from Firestore
                db.collection("users").document(uid).get()
                        .addOnSuccessListener(userDoc -> {
                            String userName = userDoc.exists() && userDoc.getString("name") != null 
                                    ? userDoc.getString("name") : "Anonymous";
                            
                            Comment comment = new Comment(
                                    noteRef.collection("comments").document().getId(),
                                    uid,
                                    userName,
                                    commentText,
                                    System.currentTimeMillis()
                            );
                            
                            noteRef.collection("comments").add(comment)
                                    .addOnSuccessListener(docRef -> {
                                        addCommentView(commentsContainer, comment);
                                        tvCommentsHeader.setVisibility(View.VISIBLE);
                                        int count = Integer.parseInt(tvCommentCount.getText().toString());
                                        tvCommentCount.setText(String.valueOf(count + 1));
                                        
                                        noteRef.update("commentsCount", FieldValue.increment(1));
                                        
                                        if (!uid.equals(noteOwnerId)) {
                                            createNotification(noteOwnerId, uid, "comment", noteRef.getId(), noteText, notePosition);
                                        }
                                        
                                        Toast.makeText(requireContext(), "Comment added!", Toast.LENGTH_SHORT).show();
                                        dialog.dismiss();
                                    });
                        });
            } else {
                Toast.makeText(requireContext(), "Please write something", Toast.LENGTH_SHORT).show();
            }
        });
        
        dialog.show();
        
        // Show keyboard
        commentInput.requestFocus();
        android.view.inputmethod.InputMethodManager imm = 
            (android.view.inputmethod.InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(commentInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
    }

    // Get time ago string
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

    // Add note dialog
    private void showAddNoteDialog(LatLng position) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_note, null);
        EditText editText = dialogView.findViewById(R.id.et_note_input);
        Button btnSave = dialogView.findViewById(R.id.btn_save_note);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel_note);

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnSave.setOnClickListener(v -> {
            String note = editText.getText().toString().trim();
            if (!note.isEmpty()) {
                long timestamp = System.currentTimeMillis();
                String shortNote = note.length() > 30 ? note.substring(0, 30) + "..." : note;
                String currentUserId = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
                addNoteMarker(position, note, shortNote, timestamp, null, currentUserId);
                saveNote(position, note, timestamp);
                dialog.dismiss();
            } else {
                Toast.makeText(requireContext(), "Note is empty!", Toast.LENGTH_SHORT).show();
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
        
        // Show keyboard
        editText.requestFocus();
        android.view.inputmethod.InputMethodManager imm = 
            (android.view.inputmethod.InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(editText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
    }

    // Save note
    private void saveNote(LatLng position, String note, long timestamp) {
        if (useCloudMode && auth.getCurrentUser() != null) {
            String uid = auth.getCurrentUser().getUid();
            
            db.collection("users").document(uid).get()
                .addOnSuccessListener(userDoc -> {
                    String userName = userDoc.getString("name");
                    String userProfilePic = userDoc.getString("profilePic");
                    
                    Map<String, Object> noteMap = new HashMap<>();
                    noteMap.put("userId", uid);
                    noteMap.put("userName", userName);
                    noteMap.put("userProfilePic", userProfilePic);
                    noteMap.put("lat", position.getLatitude());
                    noteMap.put("lon", position.getLongitude());
                    noteMap.put("location", new com.google.firebase.firestore.GeoPoint(position.getLatitude(), position.getLongitude()));
                    noteMap.put("text", note);
                    noteMap.put("summary", note.length() > 100 ? note.substring(0, 100) + "..." : note);
                    noteMap.put("timestamp", timestamp);
                    noteMap.put("likesCount", 0);
                    noteMap.put("likedBy", new java.util.ArrayList<String>());
                    noteMap.put("commentsCount", 0);

                    // Save to global notes collection
                    db.collection("notes")
                            .add(noteMap)
                            .addOnSuccessListener(docRef -> {
                                Log.d("MapFragment", "Note saved: " + docRef.getId());
                                // Update marker with docId
                                addNoteMarker(position, note, note.length() > 30 ? note.substring(0, 30) + "..." : note,
                                        timestamp, docRef.getId(), uid);
                            })
                            .addOnFailureListener(e -> Log.e("MapFragment", "Error saving note: " + e.getMessage()));
                });
        } else {
            saveNoteLocally(position, note, timestamp);
        }
    }

    private void saveNoteLocally(LatLng position, String note, long timestamp) {
        try {
            JSONArray array = new JSONArray(sharedPreferences.getString(NOTES_KEY, "[]"));
            JSONObject obj = new JSONObject();
            obj.put("lat", position.getLatitude());
            obj.put("lon", position.getLongitude());
            obj.put("note", note);
            obj.put("timestamp", timestamp);
            array.put(obj);
            sharedPreferences.edit().putString(NOTES_KEY, array.toString()).apply();
        } catch (Exception e) { e.printStackTrace(); }
    }

    // Delete note from Firestore
    private void deleteNoteFirestore(String docId, String noteOwnerId) {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();
        
        // Only allow deletion if user owns the note
        if (!uid.equals(noteOwnerId)) {
            Toast.makeText(requireContext(), "You can only delete your own notes!", Toast.LENGTH_SHORT).show();
            return;
        }
        
        db.collection("notes")
                .document(docId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Log.d("MapFragment", "Note deleted from Firestore");
                    Toast.makeText(requireContext(), "Note deleted", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Log.e("MapFragment", "Error deleting note: " + e.getMessage());
                    Toast.makeText(requireContext(), "Failed to delete note", Toast.LENGTH_SHORT).show();
                });
    }

    // Delete local note
    private void deleteNoteLocally(LatLng position) {
        try {
            JSONArray array = new JSONArray(sharedPreferences.getString(NOTES_KEY, "[]"));
            JSONArray newArray = new JSONArray();
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                if (!(obj.getDouble("lat") == position.getLatitude() && obj.getDouble("lon") == position.getLongitude())) {
                    newArray.put(obj);
                }
            }
            sharedPreferences.edit().putString(NOTES_KEY, newArray.toString()).apply();
        } catch (Exception e) { e.printStackTrace(); }
    }

    // Load notes
    private void loadSavedNotes() {
        if (useCloudMode && auth.getCurrentUser() != null) {
            // Load ALL notes from the global notes collection
            db.collection("notes")
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        for (var doc : querySnapshot) {
                            try {
                                double lat = doc.getDouble("lat");
                                double lon = doc.getDouble("lon");
                                String note = doc.getString("note");
                                String userId = doc.getString("userId");
                                long timestamp = doc.getLong("timestamp") != null ? doc.getLong("timestamp") : 0L;
                                LatLng pos = new LatLng(lat, lon);
                                addNoteMarker(pos, note, note.length() > 30 ? note.substring(0, 30) + "..." : note,
                                        timestamp, doc.getId(), userId);
                            } catch (Exception e) {
                                Log.e("MapFragment", "Error processing note: " + e.getMessage());
                            }
                        }
                    })
                    .addOnFailureListener(e -> Log.e("MapFragment", "Error loading notes: " + e.getMessage()));
        } else {
            try {
                JSONArray array = new JSONArray(sharedPreferences.getString(NOTES_KEY, "[]"));
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    LatLng pos = new LatLng(obj.getDouble("lat"), obj.getDouble("lon"));
                    String note = obj.getString("note");
                    long timestamp = obj.has("timestamp") ? obj.getLong("timestamp") : 0L;
                    addNoteMarker(pos, note, note.length() > 30 ? note.substring(0, 30) + "..." : note, timestamp, null, null);
                }
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    // Enable user location
    private void enableUserLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST);
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null && mapLibreMap != null) {
                LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                mapLibreMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15));
                if (symbolManager != null) {
                    if (userLocationSymbol != null) symbolManager.delete(userLocationSymbol);
                    userLocationSymbol = symbolManager.create(new SymbolOptions()
                            .withLatLng(latLng)
                            .withIconImage(MARKER_ICON_ID_USER_LOCATION)
                            .withTextOffset(new Float[]{0f, -2.5f}));
                }
            }
        });
    }

    // Follow user
    private void followUser(String targetUserId, android.widget.Button btn) {
        String currentUserId = auth.getCurrentUser().getUid();
        
        // Get current user's name and profile pic
        db.collection("users").document(currentUserId).get()
                .addOnSuccessListener(currentUserDoc -> {
                    String myName = currentUserDoc.getString("name");
                    String myProfilePic = currentUserDoc.getString("profilePic");
                    
                    // Add to target user's followers
                    Map<String, Object> followerData = new HashMap<>();
                    followerData.put("timestamp", System.currentTimeMillis());
                    followerData.put("followerName", myName);
                    followerData.put("followerProfilePic", myProfilePic);
                    
                    db.collection("users").document(targetUserId)
                            .collection("followers").document(currentUserId)
                            .set(followerData);
                    
                    // Increment target user's followers count
                    db.collection("users").document(targetUserId)
                            .update("followersCount", FieldValue.increment(1));
                    
                    // Get target user's info
                    db.collection("users").document(targetUserId).get()
                            .addOnSuccessListener(targetUserDoc -> {
                                String targetName = targetUserDoc.getString("name");
                                String targetProfilePic = targetUserDoc.getString("profilePic");
                                
                                // Add to current user's following
                                Map<String, Object> followingData = new HashMap<>();
                                followingData.put("timestamp", System.currentTimeMillis());
                                followingData.put("followedName", targetName);
                                followingData.put("followedProfilePic", targetProfilePic);
                                
                                db.collection("users").document(currentUserId)
                                        .collection("following").document(targetUserId)
                                        .set(followingData);
                                
                                // Increment current user's following count
                                db.collection("users").document(currentUserId)
                                        .update("followingCount", FieldValue.increment(1));
                                
                                // Update button
                                btn.setText("Following");
                                btn.setBackgroundResource(R.drawable.btn_following_selector);
                                btn.setTextColor(getResources().getColor(R.color.button_text_following, null));
                                
                                createNotification(targetUserId, currentUserId, "follow", null, null, null);
                                
                                Toast.makeText(requireContext(), "Following " + targetName, Toast.LENGTH_SHORT).show();
                            });
                });
    }

    // Show unfollow confirmation
    private void showUnfollowConfirmation(String targetUserId, android.widget.Button btn) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_confirmation, null);
        TextView title = dialogView.findViewById(R.id.dialog_title);
        TextView message = dialogView.findViewById(R.id.dialog_message);
        Button btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        
        title.setText("Unfollow User");
        message.setText("Are you sure you want to unfollow this user?");
        btnConfirm.setText("Unfollow");

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnConfirm.setOnClickListener(v -> {
            unfollowUser(targetUserId, btn);
            dialog.dismiss();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
    }

    // Unfollow user
    private void unfollowUser(String targetUserId, android.widget.Button btn) {
        String currentUserId = auth.getCurrentUser().getUid();
        
        // Remove from target user's followers
        db.collection("users").document(targetUserId)
                .collection("followers").document(currentUserId)
                .delete();
        
        // Decrement target user's followers count
        db.collection("users").document(targetUserId)
                .update("followersCount", FieldValue.increment(-1));
        
        // Remove from current user's following
        db.collection("users").document(currentUserId)
                .collection("following").document(targetUserId)
                .delete();
        
        // Decrement current user's following count
        db.collection("users").document(currentUserId)
                .update("followingCount", FieldValue.increment(-1));
        
        // Update button
        btn.setText("Follow");
        btn.setBackgroundResource(R.drawable.btn_primary_selector);
        btn.setTextColor(getResources().getColor(R.color.button_text_primary, null));
        
        Toast.makeText(requireContext(), "Unfollowed", Toast.LENGTH_SHORT).show();
    }

    // Show user info dialog
    private void showUserInfoDialog(String userId) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_user_info, null);
        
        de.hdodenhof.circleimageview.CircleImageView profilePic = dialogView.findViewById(R.id.dialog_user_profile_pic);
        TextView userName = dialogView.findViewById(R.id.dialog_user_name);
        TextView userLocation = dialogView.findViewById(R.id.dialog_user_location);
        LinearLayout locationContainer = dialogView.findViewById(R.id.dialog_location_container);
        TextView userRank = dialogView.findViewById(R.id.dialog_user_rank);
        ImageView rankIcon = dialogView.findViewById(R.id.dialog_user_rank_icon);
        TextView followersCount = dialogView.findViewById(R.id.dialog_followers_count);
        TextView followingCount = dialogView.findViewById(R.id.dialog_following_count);
        android.widget.Button followBtn = dialogView.findViewById(R.id.dialog_follow_btn);
        
        // Load user data
        db.collection("users").document(userId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        // Set name
                        String name = doc.getString("name");
                        userName.setText(name != null ? name : "Anonymous");
                        
                        // Set location
                        String location = doc.getString("lastKnownLocation");
                        if (location != null && !location.isEmpty()) {
                            userLocation.setText(location);
                            locationContainer.setVisibility(View.VISIBLE);
                        }
                        
                        // Set rank
                        String tier = doc.getString("currentTier");
                        userRank.setText(tier != null ? tier : "None");
                        // TODO: Set rank icon based on tier
                        
                        // Set profile pic
                        String pic = doc.getString("profilePic");
                        if (pic != null && !pic.isEmpty()) {
                            try {
                                byte[] bytes = android.util.Base64.decode(pic, android.util.Base64.DEFAULT);
                                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                                profilePic.setImageBitmap(bitmap);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        
                        // Set counts
                        Long followers = doc.getLong("followersCount");
                        Long following = doc.getLong("followingCount");
                        followersCount.setText(String.valueOf(followers != null ? followers : 0));
                        followingCount.setText(String.valueOf(following != null ? following : 0));
                        
                        // Show follow button if not viewing own profile
                        String currentUserId = auth.getCurrentUser().getUid();
                        if (!userId.equals(currentUserId)) {
                            followBtn.setVisibility(View.VISIBLE);
                            
                            // Check if already following
                            db.collection("users").document(currentUserId)
                                    .collection("following").document(userId)
                                    .get()
                                    .addOnSuccessListener(followDoc -> {
                                        if (followDoc.exists()) {
                                            followBtn.setText("Following");
                                            followBtn.setBackgroundResource(R.drawable.btn_following_selector);
                                            followBtn.setTextColor(getResources().getColor(R.color.button_text_following, null));
                                        }
                                    });
                            
                            followBtn.setOnClickListener(v -> {
                                if (followBtn.getText().equals("Follow")) {
                                    followUser(userId, followBtn);
                                    int count = Integer.parseInt(followersCount.getText().toString());
                                    followersCount.setText(String.valueOf(count + 1));
                                } else {
                                    unfollowUser(userId, followBtn);
                                    int count = Integer.parseInt(followersCount.getText().toString());
                                    followersCount.setText(String.valueOf(Math.max(0, count - 1)));
                                }
                            });
                        }
                    }
                });
        
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        
        dialog.show();
    }
    
    private void createNotification(String toUserId, String fromUserId, String type, 
                                   String noteId, String noteText, LatLng noteLocation) {
        db.collection("users").document(fromUserId).get()
            .addOnSuccessListener(userDoc -> {
                String fromUserName = userDoc.getString("name");
                String fromUserProfilePic = userDoc.getString("profilePic");
                
                // For like/comment notifications, check if notification already exists for this note
                if (noteId != null && (type.equals("like") || type.equals("comment"))) {
                    db.collection("notifications")
                        .whereEqualTo("toUserId", toUserId)
                        .whereEqualTo("type", type)
                        .whereEqualTo("noteId", noteId)
                        .get()
                        .addOnSuccessListener(querySnapshot -> {
                            Map<String, Object> notification = new HashMap<>();
                            notification.put("toUserId", toUserId);
                            notification.put("fromUserId", fromUserId);
                            notification.put("fromUserName", fromUserName);
                            notification.put("fromUserProfilePic", fromUserProfilePic);
                            notification.put("type", type);
                            notification.put("timestamp", System.currentTimeMillis());
                            notification.put("read", false);
                            
                            if (noteId != null) {
                                notification.put("noteId", noteId);
                            }
                            if (noteText != null) {
                                notification.put("noteText", noteText);
                            }
                            if (noteLocation != null) {
                                notification.put("noteLat", noteLocation.getLatitude());
                                notification.put("noteLng", noteLocation.getLongitude());
                            }
                            
                            if (!querySnapshot.isEmpty()) {
                                // Update existing notification
                                String docId = querySnapshot.getDocuments().get(0).getId();
                                db.collection("notifications").document(docId)
                                    .update(notification)
                                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Notification updated"))
                                    .addOnFailureListener(e -> Log.e(TAG, "Error updating notification", e));
                            } else {
                                // Create new notification
                                db.collection("notifications").add(notification)
                                    .addOnSuccessListener(docRef -> Log.d(TAG, "Notification created"))
                                    .addOnFailureListener(e -> Log.e(TAG, "Error creating notification", e));
                            }
                        });
                } else {
                    // For follow notifications, always create new
                    Map<String, Object> notification = new HashMap<>();
                    notification.put("toUserId", toUserId);
                    notification.put("fromUserId", fromUserId);
                    notification.put("fromUserName", fromUserName);
                    notification.put("fromUserProfilePic", fromUserProfilePic);
                    notification.put("type", type);
                    notification.put("timestamp", System.currentTimeMillis());
                    notification.put("read", false);
                    
                    db.collection("notifications").add(notification)
                        .addOnSuccessListener(docRef -> Log.d(TAG, "Notification created"))
                        .addOnFailureListener(e -> Log.e(TAG, "Error creating notification", e));
                }
            });
    }
    
    private void handleNavigationArguments() {
        if (getArguments() != null) {
            double targetLat = getArguments().getDouble("target_lat", 0);
            double targetLng = getArguments().getDouble("target_lng", 0);
            String targetNoteId = getArguments().getString("target_note_id");
            boolean openNoteWindow = getArguments().getBoolean("open_note_window", false);
            
            if (targetLat != 0 && targetLng != 0) {
                LatLng targetLocation = new LatLng(targetLat, targetLng);
                
                if (mapLibreMap != null) {
                    mapLibreMap.animateCamera(CameraUpdateFactory.newLatLngZoom(targetLocation, 17), 1000);
                }
                
                if (openNoteWindow && targetNoteId != null) {
                    new android.os.Handler().postDelayed(() -> {
                        openNoteWindowById(targetNoteId, targetLocation);
                    }, 1500);
                }
                
                getArguments().clear();
            }
        }
    }
    
    private void openNoteWindowById(String noteId, LatLng location) {
        db.collection("notes").document(noteId).get()
            .addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    String noteText = doc.getString("text");
                    if (noteText == null) noteText = doc.getString("note");
                    
                    Long timestamp = doc.getLong("timestamp");
                    String userId = doc.getString("userId");
                    
                    if (noteText != null && timestamp != null) {
                        Symbol targetSymbol = findSymbolAtLocation(location);
                        showCustomInfoWindow(noteText, timestamp, location, targetSymbol, noteId, userId);
                    }
                }
            })
            .addOnFailureListener(e -> Log.e(TAG, "Error loading note", e));
    }
    
    private Symbol findSymbolAtLocation(LatLng location) {
        if (symbolManager == null) return null;
        
        androidx.collection.LongSparseArray<Symbol> annotations = symbolManager.getAnnotations();
        for (int i = 0; i < annotations.size(); i++) {
            Symbol symbol = annotations.valueAt(i);
            LatLng symbolLatLng = symbol.getLatLng();
            if (symbolLatLng != null && 
                Math.abs(symbolLatLng.getLatitude() - location.getLatitude()) < 0.0001 &&
                Math.abs(symbolLatLng.getLongitude() - location.getLongitude()) < 0.0001) {
                return symbol;
            }
        }
        return null;
    }

    // Lifecycle
    @Override public void onStart() { super.onStart(); mapView.onStart(); }
    @Override public void onResume() { super.onResume(); mapView.onResume(); }
    @Override public void onPause() { super.onPause(); mapView.onPause(); }
    @Override public void onStop() { super.onStop(); mapView.onStop(); }
    @Override public void onLowMemory() { super.onLowMemory(); mapView.onLowMemory(); }
    @Override public void onDestroy() { super.onDestroy(); if (symbolManager != null) symbolManager.onDestroy(); mapView.onDestroy(); }
    @Override public void onSaveInstanceState(@NonNull Bundle outState) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState); }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) enableUserLocation();
            else Toast.makeText(requireContext(), "Permission denied.", Toast.LENGTH_SHORT).show();
        }
    }
}
