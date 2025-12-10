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
import android.widget.ProgressBar;
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
import com.facebook.shimmer.ShimmerFrameLayout;

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

    private boolean isNavigating = false; // Flag to prevent location updates from overriding navigation

    private MapView mapView;
    private MapLibreMap mapLibreMap;
    private SymbolManager symbolManager;
    private Symbol userLocationSymbol;

    private FusedLocationProviderClient fusedLocationClient;
    private com.google.android.gms.location.LocationCallback locationCallback;
    private SharedPreferences sharedPreferences;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private com.google.firebase.firestore.ListenerRegistration notesListener;

    private boolean useCloudMode = true; // true: Firestore, false: SharedPreferences
    
    private String currentMapStyle;
    private double currentZoom = 15.0;
    


    private final String GEOAPIFY_LIGHT_STYLE_URL =
            "https://maps.geoapify.com/v1/styles/osm-bright/style.json?apiKey=4034ef4942f146d6b43fd4a9871cfdc3";
    private final String GEOAPIFY_DARK_STYLE_URL =
            "https://maps.geoapify.com/v1/styles/dark-matter-dark-grey/style.json?apiKey=4034ef4942f146d6b43fd4a9871cfdc3";

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

        // Determine map style based on theme
        boolean isDarkMode = com.visiboard.app.utils.ThemeManager.getInstance(requireContext()).isDarkMode();
        currentMapStyle = isDarkMode ? GEOAPIFY_DARK_STYLE_URL : GEOAPIFY_LIGHT_STYLE_URL;

        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(@NonNull MapLibreMap mapLibreMapReady) {
                mapLibreMap = mapLibreMapReady;
                mapLibreMap.setStyle(new Style.Builder().fromUri(currentMapStyle), style -> {
                    style.addImage(MARKER_ICON_ID_USER_LOCATION, getBitmapFromVectorDrawable(R.drawable.ic_user_location));

                    symbolManager = new SymbolManager(mapView, mapLibreMap, style);
                    symbolManager.setIconAllowOverlap(true);
                    symbolManager.setTextAllowOverlap(true);

                    enableUserLocation();
                    loadSavedNotes();
                    
                    handleNavigationArguments();
                    
                    // Add camera listener to update user location marker size based on zoom
                    mapLibreMap.addOnCameraIdleListener(() -> {
                        currentZoom = mapLibreMap.getCameraPosition().zoom;
                        updateUserLocationMarkerSize();
                    });

                    symbolManager.addClickListener(symbol -> {
                        if (symbol.getData() != null) {
                            try {
                                JSONObject data = new JSONObject(symbol.getData().toString());
                                String noteText = data.getString("note");
                                long timestamp = data.getLong("timestamp");
                                String docId = data.has("docId") ? data.getString("docId") : null;
                                String ownerId = data.has("userId") ? data.getString("userId") : null;
                                String imageBase64 = data.has("imageBase64") ? data.getString("imageBase64") : null;
                                boolean hasImage = data.has("hasImage") && data.getBoolean("hasImage");

                                showCustomInfoWindow(noteText, timestamp, symbol.getLatLng(), symbol, docId, ownerId, imageBase64, hasImage);
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

            // Launch CreateNoteActivity
            android.content.Intent intent = new android.content.Intent(requireContext(), com.visiboard.app.ui.create.CreateNoteActivity.class);
            startActivity(intent);
        });

        // Switch between local and firestore
        switchMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Check network if switching to cloud mode
            if (isChecked && !com.visiboard.app.utils.NetworkManager.isNetworkAvailable(requireContext())) {
                // Revert switch
                buttonView.setChecked(false);
                
                // Show network banner
                com.visiboard.app.utils.NetworkManager.showNoInternetBanner(
                    requireActivity(),
                    () -> {
                        // Retry - switch to cloud mode again
                        buttonView.setChecked(true);
                    }
                );
                return;
            }
            
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
    private void addNoteMarker(LatLng position, String fullNote, String shortNote, long timestamp, String docId, String userId, boolean hasImage) {
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
            data.put("hasImage", hasImage);
            // We pass imageBase64 if available so we can show it immediately
            // Note: Storing large base64 strings in symbol data might be heavy for the map. 
            // If it causes performance issues, we should fetch it on click instead.
            // But for now, let's assume we can fetch it on click if it's not in data.
            // ACTUALLY, let's NOT put large base64 in symbol data. It will bloat the map style source.
            // We should fetch the note document on click if we want the image, OR just pass null here 
            // and let showCustomInfoWindow fetch it? 
            // showCustomInfoWindow takes the string directly.
            // Let's modify the click listener in onCreateView to fetch the document if needed?
            // Existing logic: symbolManager.addClickListener parses data.
            // Decision: Do NOT put base64 in symbol data. Fetch it from Firestore in user interaction if not present,
            // or better yet, the loadSavedNotes listener already has the doc.
            // BUT the symbols are created from snapshots.
            // Let's rely on fetching the document in showCustomInfoWindow or before calling it?
            // The existing code for 'openNoteWindowById' fetches the doc.
            // The 'click' listener uses symbol data. 
            // Let's UPDATE the click listener to fetch the document fresh to get the image, 
            // to avoid storing megabytes of data in the map source.
            
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
    private void showCustomInfoWindow(String noteText, long timestamp, LatLng position, Symbol symbol, String docId, String noteOwnerId, String imageBase64, boolean hasImage) {
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
        android.widget.ImageButton btnEdit = infoWindow.findViewById(R.id.btn_edit_note);
        androidx.cardview.widget.CardView imageContainer = infoWindow.findViewById(R.id.cv_note_image_container);
    ImageView noteImage = infoWindow.findViewById(R.id.iv_note_image);
    com.facebook.shimmer.ShimmerFrameLayout shimmer = infoWindow.findViewById(R.id.shimmer_view_container);

    final String[] currentBase64Wrapper = {imageBase64};

        if (imageBase64 != null && !imageBase64.isEmpty()) {
            imageContainer.setVisibility(View.VISIBLE);
            noteImage.setVisibility(View.VISIBLE);
            // Decode Base64
            try {
                byte[] decodedString = android.util.Base64.decode(imageBase64, android.util.Base64.DEFAULT);
                Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                noteImage.setImageBitmap(decodedByte);
                
                // Aspect Ratio Toggle logic
                noteImage.setOnClickListener(v -> {
                    androidx.constraintlayout.widget.ConstraintLayout.LayoutParams params = 
                        (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) noteImage.getLayoutParams();
                    
                    String currentRatio = params.dimensionRatio;
                    // Start cycle
                    String nextRatio = "1:1";
                    if ("1:1".equals(currentRatio)) nextRatio = "4:3";
                    else if ("4:3".equals(currentRatio)) nextRatio = "16:9";
                    else if ("16:9".equals(currentRatio)) nextRatio = "3:4";
                    else if ("3:4".equals(currentRatio)) nextRatio = "9:16";
                    else if ("9:16".equals(currentRatio)) nextRatio = "1:1"; // Loop back
                    
                    params.dimensionRatio = nextRatio;
                    noteImage.setLayoutParams(params);
                // Toast.makeText(requireContext(), "Ratio: " + nextRatio, Toast.LENGTH_SHORT).show();
            });
            } catch (Exception e) {
                e.printStackTrace();
                imageContainer.setVisibility(View.GONE);
            }
        } else if (docId != null && useCloudMode) {
         // Fetch from cloud
         if (hasImage) {
             imageContainer.setVisibility(View.VISIBLE);
             shimmer.setVisibility(View.VISIBLE);
             shimmer.startShimmer();
         } else {
             imageContainer.setVisibility(View.GONE);
             shimmer.setVisibility(View.GONE);
             shimmer.stopShimmer();
         }
         db.collection("notes").document(docId).get().addOnSuccessListener(doc -> {
             String base64 = doc.getString("imageBase64");
             if (base64 != null) {
                 currentBase64Wrapper[0] = base64;
             }
             // Check backward compatibility
             shimmer.stopShimmer();
             shimmer.setVisibility(View.GONE);
             
             if (base64 != null && !base64.isEmpty()) {
                 noteImage.setVisibility(View.VISIBLE);
                 try {
                        byte[] decodedString = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
                        Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                        noteImage.setImageBitmap(decodedByte);
                        
                        noteImage.setOnClickListener(v -> {
                             androidx.constraintlayout.widget.ConstraintLayout.LayoutParams params = 
                                (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) noteImage.getLayoutParams();
                            String currentRatio = params.dimensionRatio;
                            String nextRatio = "1:1";
                            if ("1:1".equals(currentRatio)) nextRatio = "4:3";
                            else if ("4:3".equals(currentRatio)) nextRatio = "16:9";
                            else if ("16:9".equals(currentRatio)) nextRatio = "3:4";
                            else if ("3:4".equals(currentRatio)) nextRatio = "9:16";
                            else if ("9:16".equals(currentRatio)) nextRatio = "1:1";
                            params.dimensionRatio = nextRatio;
                            noteImage.setLayoutParams(params);
                            Toast.makeText(requireContext(), "Ratio: " + nextRatio, Toast.LENGTH_SHORT).show();
                        });
                     } catch (Exception e) { e.printStackTrace(); }
                 } else {
                     imageContainer.setVisibility(View.GONE);
                 }
                 shimmer.setVisibility(View.GONE);
                 shimmer.stopShimmer();
             }).addOnFailureListener(e -> {
                 shimmer.setVisibility(View.GONE);
                 shimmer.stopShimmer();
                 imageContainer.setVisibility(View.GONE);
             });
             
        } else {
            imageContainer.setVisibility(View.GONE);
            shimmer.setVisibility(View.GONE);
            shimmer.stopShimmer();
        }


        noteTextView.setText(noteText);
        timestampTextView.setText(new SimpleDateFormat("dd MMM yyyy • hh:mm a", java.util.Locale.getDefault())
                .format(new java.util.Date(timestamp)));

        // Check if current user owns this note
        String currentUserId = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
        boolean isOwner = currentUserId != null && currentUserId.equals(noteOwnerId);

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setView(infoWindow)
                .setNegativeButton("Close", (d, w) -> d.dismiss());

        // Show delete button if user owns the note OR if it's local mode
        if (isOwner || !useCloudMode) {
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
        
        // Show Edit button if owner and cloud mode
        if (isOwner && useCloudMode && docId != null) {
            btnEdit.setVisibility(View.VISIBLE);
            btnEdit.setOnClickListener(v -> {
                android.content.Intent intent = new android.content.Intent(requireContext(), com.visiboard.app.ui.create.CreateNoteActivity.class);
                intent.putExtra("edit_note_id", docId);
                intent.putExtra("edit_content", noteTextView.getText().toString());
                intent.putExtra("edit_image_base64", currentBase64Wrapper[0]);
                startActivity(intent);
                dialog.dismiss();
            });
        }

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
                        
                        // Load comment count
                        Long commentCount = doc.getLong("commentsCount");
                        tvCommentCount.setText(String.valueOf(commentCount != null ? commentCount : 0));
                    }
                });



                // Like button click with double-click prevention using Firestore transaction
                likeSection.setOnClickListener(v -> {
                    if (isProcessingLike[0]) return; // Prevent double-click
                    isProcessingLike[0] = true;
                    
                    // Optimistic UI update
                    noteRef.get().addOnSuccessListener(doc -> {
                        java.util.List<String> likedBy = (java.util.List<String>) doc.get("likedBy");
                        boolean isLiked = likedBy != null && likedBy.contains(currentUserId);
                        boolean willLike = !isLiked;
                        
                        // Update UI immediately
                        if (willLike) {
                            btnLike.setImageResource(R.drawable.ic_heart);
                            animateLike(btnLike);
                            int count = Integer.parseInt(tvLikeCount.getText().toString());
                            tvLikeCount.setText(String.valueOf(count + 1));
                        } else {
                            btnLike.setImageResource(R.drawable.ic_heart_outline);
                            int count = Integer.parseInt(tvLikeCount.getText().toString());
                            tvLikeCount.setText(String.valueOf(Math.max(0, count - 1)));
                        }
                        
                        // Perform update with transaction to prevent race conditions
                        db.runTransaction(transaction -> {
                            com.google.firebase.firestore.DocumentSnapshot snapshot = transaction.get(noteRef);
                            java.util.List<String> currentLikedBy = (java.util.List<String>) snapshot.get("likedBy");
                            boolean currentlyLiked = currentLikedBy != null && currentLikedBy.contains(currentUserId);
                            
                            if (currentlyLiked && willLike) {
                                // Already liked, user is trying to like again - do nothing
                                return null;
                            } else if (!currentlyLiked && !willLike) {
                                // Not liked, user is trying to unlike - do nothing
                                return null;
                            }
                            
                            if (willLike) {
                                transaction.update(noteRef, "likedBy", FieldValue.arrayUnion(currentUserId));
                                transaction.update(noteRef, "likeCount", FieldValue.increment(1));
                            } else {
                                transaction.update(noteRef, "likedBy", FieldValue.arrayRemove(currentUserId));
                                transaction.update(noteRef, "likeCount", FieldValue.increment(-1));
                            }
                            return null;
                        }).addOnSuccessListener(aVoid -> {
                            isProcessingLike[0] = false;
                            if (willLike && !isOwner) {
                                createNotification(noteOwnerId, currentUserId, "like", docId, noteText, position);
                            }
                        }).addOnFailureListener(e -> {
                            // Revert UI on failure
                            if (willLike) {
                                btnLike.setImageResource(R.drawable.ic_heart_outline);
                                int count = Integer.parseInt(tvLikeCount.getText().toString());
                                tvLikeCount.setText(String.valueOf(Math.max(0, count - 1)));
                            } else {
                                btnLike.setImageResource(R.drawable.ic_heart);
                                int count = Integer.parseInt(tvLikeCount.getText().toString());
                                tvLikeCount.setText(String.valueOf(count + 1));
                            }
                            isProcessingLike[0] = false;
                            Toast.makeText(requireContext(), "Failed to update like", Toast.LENGTH_SHORT).show();
                        });
                    }).addOnFailureListener(e -> isProcessingLike[0] = false);
                });

                // Comment button click
                commentSection.setOnClickListener(v -> {
                    // Don't dismiss the dialog, just open the bottom sheet
                    CommentsBottomSheetFragment bottomSheet = CommentsBottomSheetFragment.newInstance(
                            docId, noteOwnerId, noteText, position.getLatitude(), position.getLongitude()
                    );
                    
                    // Set listener to show user info when a profile pic is clicked in comments
                    bottomSheet.setOnUserClickListener(this::showUserInfoDialog);
                    
                    bottomSheet.show(getParentFragmentManager(), "CommentsBottomSheet");
                });
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
                    noteMap.put("note", note);
                    noteMap.put("summary", note.length() > 100 ? note.substring(0, 100) + "..." : note);
                    noteMap.put("timestamp", timestamp);
                    noteMap.put("likeCount", 0);
                    noteMap.put("likedBy", new java.util.ArrayList<String>());
                    noteMap.put("commentsCount", 0);

                    // Save to global notes collection
                    db.collection("notes")
                            .add(noteMap)
                            .addOnSuccessListener(docRef -> {
                                Log.d("MapFragment", "Note saved: " + docRef.getId());
                                Toast.makeText(requireContext(), "Note placed!", Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e -> {
                                Log.e("MapFragment", "Error saving note: " + e.getMessage());
                                Toast.makeText(requireContext(), "Failed to save note", Toast.LENGTH_SHORT).show();
                            });
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
            
            // Reload notes to reflect deletion
            loadSavedNotes();
            
            Toast.makeText(requireContext(), "Note deleted", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Note deleted locally");
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(requireContext(), "Failed to delete note", Toast.LENGTH_SHORT).show();
        }
    }

    // Load notes
    private void loadSavedNotes() {
        if (useCloudMode && auth.getCurrentUser() != null) {
            // Remove old listener if exists
            if (notesListener != null) {
                notesListener.remove();
            }
            
            // Add real-time listener to load ALL notes from the global notes collection
            notesListener = db.collection("notes")
                    .addSnapshotListener((querySnapshot, error) -> {
                        if (error != null) {
                            Log.e("MapFragment", "Error loading notes: " + error.getMessage());
                            return;
                        }
                        
                        if (querySnapshot != null) {
                            // Clear existing note markers (keep user location marker)
                            if (symbolManager != null) {
                                java.util.List<Symbol> symbolsToRemove = new java.util.ArrayList<>();
                                androidx.collection.LongSparseArray<Symbol> annotations = symbolManager.getAnnotations();
                                for (int i = 0; i < annotations.size(); i++) {
                                    Symbol symbol = annotations.valueAt(i);
                                    if (symbol != userLocationSymbol) {
                                        symbolsToRemove.add(symbol);
                                    }
                                }
                                symbolManager.delete(symbolsToRemove);
                            }
                            
                            // Add all notes
                            for (var doc : querySnapshot) {
                                try {
                                    double lat = doc.getDouble("lat");
                                    double lon = doc.getDouble("lon");
                                    // Try "note" first, fallback to "text" for backward compatibility
                                    String note = doc.getString("note");
                                    if (note == null) note = doc.getString("text");
                                    String userId = doc.getString("userId");
                                    long timestamp = doc.getLong("timestamp") != null ? doc.getLong("timestamp") : 0L;
                                    String b64 = doc.getString("imageBase64");
                                    boolean hasImage = b64 != null && !b64.isEmpty();
                                    LatLng pos = new LatLng(lat, lon);
                                    addNoteMarker(pos, note, note.length() > 30 ? note.substring(0, 30) + "..." : note,
                                            timestamp, doc.getId(), userId, hasImage);
                                } catch (Exception e) {
                                    Log.e("MapFragment", "Error processing note: " + e.getMessage());
                                }
                            }
                        }
                    });
        } else {
            try {
                JSONArray array = new JSONArray(sharedPreferences.getString(NOTES_KEY, "[]"));
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    LatLng pos = new LatLng(obj.getDouble("lat"), obj.getDouble("lon"));
                    String note = obj.getString("note");
                    long timestamp = obj.has("timestamp") ? obj.getLong("timestamp") : 0L;
                    addNoteMarker(pos, note, note.length() > 30 ? note.substring(0, 30) + "..." : note, timestamp, null, null, false);
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

        // Get initial location
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null && mapLibreMap != null) {
                LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                updateUserLocationMarker(latLng);
                // Only move camera if NOT navigating to a note
                if (!isNavigating) {
                    currentZoom = 15.0;
                    mapLibreMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, currentZoom));
                }
            }
        });
        
        // Start continuous location updates for accuracy
        startLocationUpdates();
    }
    
    // Start continuous location updates
    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        
        com.google.android.gms.location.LocationRequest locationRequest = 
            com.google.android.gms.location.LocationRequest.create()
                .setPriority(com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(5000)  // Update every 5 seconds
                .setFastestInterval(2000);  // Can update as fast as 2 seconds
        
        locationCallback = new com.google.android.gms.location.LocationCallback() {
            @Override
            public void onLocationResult(com.google.android.gms.location.LocationResult locationResult) {
                if (locationResult != null && locationResult.getLastLocation() != null) {
                    android.location.Location location = locationResult.getLastLocation();
                    LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                    updateUserLocationMarker(latLng);
                }
            }
        };
        
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
    }
    
    // Stop location updates
    private void stopLocationUpdates() {
        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }
    
    // Update user location marker position
    private void updateUserLocationMarker(LatLng latLng) {
        if (symbolManager == null) return;
        
        if (userLocationSymbol != null) {
            userLocationSymbol.setLatLng(latLng);
            symbolManager.update(userLocationSymbol);
        } else {
            // Create marker if it doesn't exist
            float iconSize = calculateIconSize(currentZoom);
            userLocationSymbol = symbolManager.create(new SymbolOptions()
                    .withLatLng(latLng)
                    .withIconImage(MARKER_ICON_ID_USER_LOCATION)
                    .withIconSize(iconSize));
        }
    }
    
    // Calculate icon size based on zoom level
    private float calculateIconSize(double zoom) {
        // Larger base size for better visibility
        // Zoom ranges typically: 0 (world) to 22 (street level)
        if (zoom <= 8) {
            return 2.5f; // Large for zoomed out
        } else if (zoom <= 12) {
            return 2.0f; // Medium-large
        } else if (zoom <= 15) {
            return 1.5f; // Medium
        } else if (zoom <= 18) {
            return 1.3f; // Normal
        } else {
            return 1.0f; // Small for zoomed in (still larger than before)
        }
    }
    
    // Update user location marker size when zoom changes
    private void updateUserLocationMarkerSize() {
        if (userLocationSymbol != null && symbolManager != null) {
            float newSize = calculateIconSize(currentZoom);
            userLocationSymbol.setIconSize(newSize);
            symbolManager.update(userLocationSymbol);
        }
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
    void showUserInfoDialog(String userId) {
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
                isNavigating = true;
                LatLng targetLocation = new LatLng(targetLat, targetLng);
                navigateAndOpen(targetLocation, targetNoteId, openNoteWindow);
            } else if (targetNoteId != null && !targetNoteId.isEmpty()) {
                // Coordinates missing, fetch them
                db.collection("notes").document(targetNoteId).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            com.google.firebase.firestore.GeoPoint gp = documentSnapshot.getGeoPoint("location");
                            if (gp != null) {
                                isNavigating = true;
                                LatLng targetLocation = new LatLng(gp.getLatitude(), gp.getLongitude());
                                navigateAndOpen(targetLocation, targetNoteId, openNoteWindow);
                            }
                        }
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Error fetching note location", e));
            }
            
            getArguments().clear();
        }
    }

    private void navigateAndOpen(LatLng targetLocation, String noteId, boolean openWindow) {
        if (mapLibreMap != null) {
             // Animate zoom to 18.0
             mapLibreMap.animateCamera(CameraUpdateFactory.newLatLngZoom(targetLocation, 18.0), 1000);
             
             if (openWindow && noteId != null) {
                 // Delay opening the window to allow animation to finish
                 new android.os.Handler().postDelayed(() -> {
                     openNoteWindowById(noteId, targetLocation);
                 }, 1200);
             }

             // Reset navigation flag after animation
             new android.os.Handler().postDelayed(() -> {
                 isNavigating = false;
             }, 2000);
        }
    }
    
    private void openNoteWindowById(String noteId, LatLng location) {
        db.collection("notes").document(noteId).get()
            .addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    // Try "note" first, fallback to "text" for backward compatibility
                    String noteText = doc.getString("note");
                    if (noteText == null) noteText = doc.getString("text");
                    
                    Long timestamp = doc.getLong("timestamp");
                    String userId = doc.getString("userId");
                    String imageBase64 = doc.getString("imageBase64");
                    
                    if (noteText != null && timestamp != null) {
                        Symbol targetSymbol = findSymbolAtLocation(location);
                        boolean hasImage = imageBase64 != null && !imageBase64.isEmpty();
                        showCustomInfoWindow(noteText, timestamp, location, targetSymbol, noteId, userId, imageBase64, hasImage);
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
    @Override 
    public void onResume() { 
        super.onResume(); 
        mapView.onResume();
        // Restart location updates for accuracy
        startLocationUpdates();
    }
    @Override 
    public void onPause() { 
        super.onPause(); 
        mapView.onPause();
        // Stop location updates to save battery
        stopLocationUpdates();
    }
    @Override public void onStop() { super.onStop(); mapView.onStop(); }
    @Override public void onLowMemory() { super.onLowMemory(); mapView.onLowMemory(); }
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Reset symbol when view is destroyed so it's recreated on next load
        userLocationSymbol = null;
        if (symbolManager != null) {
            symbolManager.onDestroy();
            symbolManager = null;
        }
    }

    @Override 
    public void onDestroy() { 
        super.onDestroy();
        // Clean up location updates
        stopLocationUpdates();
        if (notesListener != null) notesListener.remove();
        // symbolManager cleanup is now in onDestroyView, but safe to keep check here
        if (symbolManager != null) symbolManager.onDestroy(); 
        userLocationSymbol = null; 
        mapView.onDestroy(); 
    }
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
