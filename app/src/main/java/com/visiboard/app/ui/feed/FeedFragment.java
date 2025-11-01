package com.visiboard.app.ui.feed;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.Query;
import com.visiboard.app.R;
import com.visiboard.app.data.NearbyNote;
import com.visiboard.app.data.Notification;
import com.visiboard.app.data.UserInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FeedFragment extends Fragment {
    
    private static final String TAG = "FeedFragment";
    private static final double NEARBY_RADIUS_KM = 10.0;
    
    private RecyclerView rvNotifications, rvNearbyNotes, rvSearchResults, rvFollowing;
    private TextView tvNoNotifications, tvNoNearbyNotes, tvNoFollowing;
    private Button btnClearNotifications;
    private TextInputEditText etSearchUsers;
    private NotificationAdapter notificationAdapter;
    private NearbyNotesAdapter nearbyNotesAdapter;
    private UserSearchAdapter userSearchAdapter;
    private FollowingAdapter followingAdapter;
    
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private FusedLocationProviderClient fusedLocationClient;
    
    private Location currentLocation;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_feed, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        
        rvNotifications = view.findViewById(R.id.rv_notifications);
        rvNearbyNotes = view.findViewById(R.id.rv_nearby_notes);
        rvSearchResults = view.findViewById(R.id.rv_search_results);
        rvFollowing = view.findViewById(R.id.rv_following);
        tvNoNotifications = view.findViewById(R.id.tv_no_notifications);
        tvNoNearbyNotes = view.findViewById(R.id.tv_no_nearby_notes);
        tvNoFollowing = view.findViewById(R.id.tv_no_following);
        btnClearNotifications = view.findViewById(R.id.btn_clear_notifications);
        etSearchUsers = view.findViewById(R.id.et_search_users);
        
        setupRecyclerViews();
        setupSearchBar();
        setupClearButton();
        loadUserLocation();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume - reloading data");
        loadUserLocation();
    }
    
    private void setupRecyclerViews() {
        rvNotifications.setLayoutManager(new LinearLayoutManager(getContext()));
        notificationAdapter = new NotificationAdapter(notification -> {
            handleNotificationClick(notification);
        });
        rvNotifications.setAdapter(notificationAdapter);
        
        rvNearbyNotes.setLayoutManager(new LinearLayoutManager(getContext()));
        nearbyNotesAdapter = new NearbyNotesAdapter(note -> {
            navigateToNoteOnMap(note.getLat(), note.getLng(), note.getId());
        });
        rvNearbyNotes.setAdapter(nearbyNotesAdapter);
        
        rvSearchResults.setLayoutManager(new LinearLayoutManager(getContext()));
        userSearchAdapter = new UserSearchAdapter(user -> {
            showUserInfoDialog(user.getUserId());
        });
        rvSearchResults.setAdapter(userSearchAdapter);
        
        rvFollowing.setLayoutManager(new LinearLayoutManager(getContext()));
        followingAdapter = new FollowingAdapter(
            user -> showUserInfoDialog(user.getUserId()),
            user -> showSendMessageDialog(user)
        );
        rvFollowing.setAdapter(followingAdapter);
    }
    
    private void setupSearchBar() {
        etSearchUsers.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0) {
                    searchUsers(s.toString());
                } else {
                    rvSearchResults.setVisibility(View.GONE);
                    userSearchAdapter.setUsers(new ArrayList<>());
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }
    
    private void setupClearButton() {
        btnClearNotifications.setOnClickListener(v -> {
            View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_confirmation, null);
            TextView title = dialogView.findViewById(R.id.dialog_title);
            TextView message = dialogView.findViewById(R.id.dialog_message);
            Button btnConfirm = dialogView.findViewById(R.id.btn_confirm);
            Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
            
            title.setText("Clear Notifications");
            message.setText("Are you sure you want to clear all notifications?");
            btnConfirm.setText("Clear");
            
            AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();
            
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }
            
            btnConfirm.setOnClickListener(v2 -> {
                clearAllNotifications();
                dialog.dismiss();
            });
            
            btnCancel.setOnClickListener(v2 -> dialog.dismiss());
            
            dialog.show();
        });
    }
    
    private void handleNotificationClick(Notification notification) {
        if ("follow".equals(notification.getType())) {
            showUserInfoDialog(notification.getFromUserId());
        } else if ("message".equals(notification.getType())) {
            showMessageDialog(notification.getMessageId());
        } else {
            navigateToNoteOnMap(notification.getNoteLat(), notification.getNoteLng(), notification.getNoteId());
        }
    }
    
    private void showMessageDialog(String messageId) {
        db.collection("messages").document(messageId).get()
            .addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    View dialogView = LayoutInflater.from(requireContext())
                        .inflate(R.layout.dialog_view_message, null);
                    
                    de.hdodenhof.circleimageview.CircleImageView ivSender = dialogView.findViewById(R.id.iv_sender_avatar);
                    TextView tvSender = dialogView.findViewById(R.id.tv_sender_name);
                    TextView tvTime = dialogView.findViewById(R.id.tv_message_time);
                    TextView tvMessage = dialogView.findViewById(R.id.tv_message_text);
                    Button btnClose = dialogView.findViewById(R.id.btn_close);
                    
                    String senderName = doc.getString("fromUserName");
                    String senderPic = doc.getString("fromUserProfilePic");
                    Long timestamp = doc.getLong("timestamp");
                    String messageText = doc.getString("messageText");
                    
                    tvSender.setText(senderName != null ? senderName : "Anonymous");
                    tvMessage.setText(messageText);
                    tvTime.setText(getTimeAgo(timestamp != null ? timestamp : 0));
                    
                    // Load profile picture
                    if (senderPic != null && !senderPic.isEmpty()) {
                        try {
                            byte[] bytes = android.util.Base64.decode(senderPic, android.util.Base64.DEFAULT);
                            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                            ivSender.setImageBitmap(bitmap);
                        } catch (Exception e) {
                            ivSender.setImageResource(R.drawable.ic_profile);
                        }
                    } else {
                        ivSender.setImageResource(R.drawable.ic_profile);
                    }
                    
                    AlertDialog dialog = new AlertDialog.Builder(requireContext())
                        .setView(dialogView)
                        .create();
                    
                    if (dialog.getWindow() != null) {
                        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                    }
                    
                    btnClose.setOnClickListener(v -> dialog.dismiss());
                    
                    dialog.show();
                    
                    // Mark as read
                    doc.getReference().update("read", true);
                }
            })
            .addOnFailureListener(e -> {
                android.widget.Toast.makeText(requireContext(), 
                    "Failed to load message", android.widget.Toast.LENGTH_SHORT).show();
            });
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
    
    private void searchUsers(String query) {
        String searchQuery = query.toLowerCase().trim();
        Log.d(TAG, "Searching users with query: " + searchQuery);
        
        db.collection("users")
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                List<UserInfo> users = new ArrayList<>();
                String currentUserId = auth.getCurrentUser().getUid();
                
                for (DocumentSnapshot doc : queryDocumentSnapshots) {
                    try {
                        String userId = doc.getId();
                        if (userId.equals(currentUserId)) continue;
                        
                        String name = doc.getString("name");
                        if (name != null && name.toLowerCase().contains(searchQuery)) {
                            UserInfo user = doc.toObject(UserInfo.class);
                            if (user != null) {
                                user.setUserId(userId);
                                users.add(user);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing user: " + e.getMessage());
                    }
                }
                
                if (users.isEmpty()) {
                    rvSearchResults.setVisibility(View.GONE);
                } else {
                    rvSearchResults.setVisibility(View.VISIBLE);
                    userSearchAdapter.setUsers(users);
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error searching users", e);
                rvSearchResults.setVisibility(View.GONE);
            });
    }
    
    private void clearAllNotifications() {
        String userId = auth.getCurrentUser().getUid();
        
        db.collection("notifications")
            .whereEqualTo("toUserId", userId)
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                for (DocumentSnapshot doc : queryDocumentSnapshots) {
                    doc.getReference().delete();
                }
                notificationAdapter.setNotifications(new ArrayList<>());
                tvNoNotifications.setVisibility(View.VISIBLE);
                rvNotifications.setVisibility(View.GONE);
            })
            .addOnFailureListener(e -> Log.e(TAG, "Error clearing notifications", e));
    }
    
    private void showUserInfoDialog(String userId) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_user_info, null);
        
        de.hdodenhof.circleimageview.CircleImageView profilePic = dialogView.findViewById(R.id.dialog_user_profile_pic);
        TextView userName = dialogView.findViewById(R.id.dialog_user_name);
        TextView userLocation = dialogView.findViewById(R.id.dialog_user_location);
        android.widget.LinearLayout locationContainer = dialogView.findViewById(R.id.dialog_location_container);
        TextView userRank = dialogView.findViewById(R.id.dialog_user_rank);
        android.widget.ImageView rankIcon = dialogView.findViewById(R.id.dialog_user_rank_icon);
        TextView followersCount = dialogView.findViewById(R.id.dialog_followers_count);
        TextView followingCount = dialogView.findViewById(R.id.dialog_following_count);
        Button followBtn = dialogView.findViewById(R.id.dialog_follow_btn);
        
        db.collection("users").document(userId).get()
                .addOnSuccessListener(userDoc -> {
                    if (userDoc.exists()) {
                        String name = userDoc.getString("name");
                        userName.setText(name != null ? name : "Anonymous");
                        
                        String location = userDoc.getString("lastKnownLocation");
                        if (location != null && !location.isEmpty()) {
                            locationContainer.setVisibility(View.VISIBLE);
                            userLocation.setText(location);
                        }
                        
                        String tier = userDoc.getString("currentTier");
                        if (tier != null) {
                            userRank.setText(tier);
                            int iconRes = getTierIcon(tier);
                            if (iconRes != 0) rankIcon.setImageResource(iconRes);
                        }
                        
                        Long followers = userDoc.getLong("followersCount");
                        Long following = userDoc.getLong("followingCount");
                        followersCount.setText(String.valueOf(followers != null ? followers : 0));
                        followingCount.setText(String.valueOf(following != null ? following : 0));
                        
                        String pic = userDoc.getString("profilePic");
                        if (pic != null && !pic.isEmpty()) {
                            try {
                                byte[] bytes = android.util.Base64.decode(pic, android.util.Base64.DEFAULT);
                                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                                profilePic.setImageBitmap(bitmap);
                            } catch (Exception e) {
                                Log.e(TAG, "Error loading profile pic", e);
                            }
                        }
                    }
                    
                    String currentUserId = auth.getCurrentUser().getUid();
                    if (!currentUserId.equals(userId)) {
                        followBtn.setVisibility(View.VISIBLE);
                        
                        db.collection("users").document(currentUserId)
                                .collection("following").document(userId)
                                .get()
                                .addOnSuccessListener(doc -> {
                                    if (doc.exists()) {
                                        followBtn.setText("Following");
                                    } else {
                                        followBtn.setText("Follow");
                                    }
                                });
                    }
                });
        
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        
        dialog.show();
    }
    
    private int getTierIcon(String tier) {
        switch (tier.toLowerCase()) {
            case "bronze": return R.drawable.ic_bronze;
            case "silver": return R.drawable.ic_silver;
            case "gold": return R.drawable.ic_gold;
            case "platinum": return R.drawable.ic_platinum;
            case "diamond": return R.drawable.ic_diamond;
            default: return R.drawable.ic_default_tier;
        }
    }
    
    private void loadUserLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(), 
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Location permission not granted");
            return;
        }
        
        Log.d(TAG, "Getting user location...");
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                currentLocation = location;
                Log.d(TAG, "Location obtained: " + location.getLatitude() + ", " + location.getLongitude());
                loadNotifications();
                loadNearbyNotes();
                loadFollowingUsers();
            } else {
                Log.e(TAG, "Location is null");
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Error getting location", e);
        });
    }
    
    private void loadNotifications() {
        String userId = auth.getCurrentUser().getUid();
        Log.d(TAG, "Loading notifications for user: " + userId);
        
        db.collection("notifications")
            .whereEqualTo("toUserId", userId)
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                List<Notification> notifications = new ArrayList<>();
                Log.d(TAG, "Found " + queryDocumentSnapshots.size() + " notification documents");
                
                for (DocumentSnapshot doc : queryDocumentSnapshots) {
                    try {
                        Notification notification = doc.toObject(Notification.class);
                        if (notification != null) {
                            notification.setId(doc.getId());
                            notifications.add(notification);
                            Log.d(TAG, "Parsed notification: " + notification.getType() + " from " + notification.getFromUserName());
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing notification: " + e.getMessage());
                    }
                }
                
                // Sort by timestamp descending
                notifications.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
                
                if (notifications.isEmpty()) {
                    tvNoNotifications.setVisibility(View.VISIBLE);
                    rvNotifications.setVisibility(View.GONE);
                } else {
                    tvNoNotifications.setVisibility(View.GONE);
                    rvNotifications.setVisibility(View.VISIBLE);
                    notificationAdapter.setNotifications(notifications);
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error loading notifications", e);
                tvNoNotifications.setVisibility(View.VISIBLE);
                rvNotifications.setVisibility(View.GONE);
            });
    }
    
    private void loadNearbyNotes() {
        if (currentLocation == null) {
            Log.e(TAG, "Current location is null");
            tvNoNearbyNotes.setVisibility(View.VISIBLE);
            rvNearbyNotes.setVisibility(View.GONE);
            return;
        }
        
        String userId = auth.getCurrentUser().getUid();
        
        db.collection("notes")
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                List<NearbyNote> nearbyNotes = new ArrayList<>();
                
                for (DocumentSnapshot doc : queryDocumentSnapshots) {
                    try {
                        String noteUserId = doc.getString("userId");
                        if (noteUserId != null && noteUserId.equals(userId)) {
                            continue;
                        }
                        
                        GeoPoint location = doc.getGeoPoint("location");
                        if (location == null) {
                            // Try fallback to lat/lon
                            Double lat = doc.getDouble("lat");
                            Double lon = doc.getDouble("lon");
                            if (lat != null && lon != null) {
                                location = new GeoPoint(lat, lon);
                            }
                        }
                        
                        if (location != null) {
                            double distance = calculateDistance(
                                currentLocation.getLatitude(), 
                                currentLocation.getLongitude(),
                                location.getLatitude(), 
                                location.getLongitude()
                            );
                            
                            if (distance <= NEARBY_RADIUS_KM) {
                                final NearbyNote note = new NearbyNote();
                                note.setId(doc.getId());
                                
                                String text = doc.getString("text");
                                if (text == null) text = doc.getString("note");
                                
                                note.setText(text);
                                note.setSummary(doc.getString("summary"));
                                note.setUserId(noteUserId);
                                note.setLat(location.getLatitude());
                                note.setLng(location.getLongitude());
                                note.setTimestamp(doc.getLong("timestamp") != null ? doc.getLong("timestamp") : 0);
                                
                                // Get likes count
                                Long likesCount = doc.getLong("likesCount");
                                if (likesCount == null) likesCount = doc.getLong("likeCount");
                                note.setLikesCount(likesCount != null ? likesCount.intValue() : 0);
                                
                                // Get comments count from subcollection for accuracy
                                String noteId = doc.getId();
                                db.collection("notes").document(noteId)
                                    .collection("comments").get()
                                    .addOnSuccessListener(comments -> {
                                        note.setCommentsCount(comments.size());
                                        nearbyNotesAdapter.notifyDataSetChanged();
                                    })
                                    .addOnFailureListener(e -> {
                                        // Fallback to stored count
                                        Long commentsCount = doc.getLong("commentsCount");
                                        note.setCommentsCount(commentsCount != null ? commentsCount.intValue() : 0);
                                    });
                                
                                note.setDistance(distance);
                                
                                // Get userName - if not in note, fetch from user profile
                                String userName = doc.getString("userName");
                                String userProfilePic = doc.getString("userProfilePic");
                                
                                if ((userName == null || userName.isEmpty()) && noteUserId != null) {
                                    // Fetch from user collection
                                    db.collection("users").document(noteUserId).get()
                                        .addOnSuccessListener(userDoc -> {
                                            String name = userDoc.getString("name");
                                            String pic = userDoc.getString("profilePic");
                                            note.setUserName(name != null ? name : "Anonymous");
                                            note.setUserProfilePic(pic);
                                            nearbyNotesAdapter.notifyDataSetChanged();
                                        });
                                } else {
                                    note.setUserName(userName != null ? userName : "Anonymous");
                                    note.setUserProfilePic(userProfilePic);
                                }
                                
                                nearbyNotes.add(note);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing note: " + e.getMessage());
                    }
                }
                
                nearbyNotes.sort((a, b) -> Double.compare(a.getDistance(), b.getDistance()));
                
                if (nearbyNotes.isEmpty()) {
                    tvNoNearbyNotes.setVisibility(View.VISIBLE);
                    rvNearbyNotes.setVisibility(View.GONE);
                } else {
                    tvNoNearbyNotes.setVisibility(View.GONE);
                    rvNearbyNotes.setVisibility(View.VISIBLE);
                    nearbyNotesAdapter.setNotes(nearbyNotes);
                }
                
                Log.d(TAG, "Found " + nearbyNotes.size() + " nearby notes");
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error loading nearby notes", e);
                tvNoNearbyNotes.setVisibility(View.VISIBLE);
                rvNearbyNotes.setVisibility(View.GONE);
            });
    }
    
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371;
        
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return R * c;
    }
    
    private void loadFollowingUsers() {
        String userId = auth.getCurrentUser().getUid();
        Log.d(TAG, "Loading following users for: " + userId);
        
        db.collection("users").document(userId)
            .collection("following")
            .get()
            .addOnSuccessListener(querySnapshot -> {
                List<UserInfo> following = new ArrayList<>();
                int totalFollowing = querySnapshot.size();
                Log.d(TAG, "Found " + totalFollowing + " following users");
                
                if (totalFollowing == 0) {
                    rvFollowing.setVisibility(View.GONE);
                    tvNoFollowing.setVisibility(View.VISIBLE);
                    return;
                }
                
                for (DocumentSnapshot doc : querySnapshot) {
                    String followedId = doc.getId();
                    // Fetch user details
                    db.collection("users").document(followedId).get()
                        .addOnSuccessListener(userDoc -> {
                            if (userDoc.exists()) {
                                UserInfo user = new UserInfo();
                                user.setUserId(followedId);
                                user.setName(userDoc.getString("name"));
                                user.setProfilePic(userDoc.getString("profilePic"));
                                user.setLastKnownLocation(userDoc.getString("lastKnownLocation"));
                                following.add(user);
                                
                                // Update adapter when all users are loaded
                                if (following.size() == totalFollowing) {
                                    rvFollowing.setVisibility(View.VISIBLE);
                                    tvNoFollowing.setVisibility(View.GONE);
                                    followingAdapter.setUsers(following);
                                    Log.d(TAG, "Loaded " + following.size() + " following users");
                                }
                            }
                        })
                        .addOnFailureListener(e -> Log.e(TAG, "Error loading user: " + followedId, e));
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error loading following list", e);
                rvFollowing.setVisibility(View.GONE);
                tvNoFollowing.setVisibility(View.VISIBLE);
            });
    }
    
    private void showSendMessageDialog(UserInfo recipient) {
        View dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_send_message, null);
        
        TextView tvRecipient = dialogView.findViewById(R.id.tv_recipient_name);
        com.google.android.material.textfield.TextInputEditText etMessage = dialogView.findViewById(R.id.et_message);
        android.widget.CheckBox cbAnonymous = dialogView.findViewById(R.id.cb_anonymous);
        Button btnSend = dialogView.findViewById(R.id.btn_send);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        
        tvRecipient.setText("To: " + recipient.getName());
        
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create();
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        
        btnSend.setOnClickListener(v -> {
            String messageText = etMessage.getText().toString().trim();
            boolean isAnonymous = cbAnonymous.isChecked();
            
            if (!messageText.isEmpty()) {
                sendMessage(recipient.getUserId(), messageText, isAnonymous);
                dialog.dismiss();
            } else {
                android.widget.Toast.makeText(requireContext(), "Please enter a message", 
                    android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
    }
    
    private void sendMessage(String toUserId, String messageText, boolean anonymous) {
        String fromUserId = auth.getCurrentUser().getUid();
        
        db.collection("users").document(fromUserId).get()
            .addOnSuccessListener(doc -> {
                String fromUserName = doc.getString("name");
                String fromUserProfilePic = doc.getString("profilePic");
                
                Map<String, Object> message = new HashMap<>();
                message.put("fromUserId", fromUserId);
                message.put("fromUserName", anonymous ? "Anonymous" : fromUserName);
                message.put("fromUserProfilePic", anonymous ? null : fromUserProfilePic);
                message.put("toUserId", toUserId);
                message.put("messageText", messageText);
                message.put("timestamp", System.currentTimeMillis());
                message.put("anonymous", anonymous);
                message.put("read", false);
                
                // Save message
                db.collection("messages").add(message)
                    .addOnSuccessListener(docRef -> {
                        // Create notification
                        createMessageNotification(toUserId, fromUserId, 
                            anonymous ? "Anonymous" : fromUserName, 
                            anonymous ? null : fromUserProfilePic,
                            messageText, docRef.getId());
                        
                        android.widget.Toast.makeText(requireContext(), 
                            "Message sent!", android.widget.Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        android.widget.Toast.makeText(requireContext(), 
                            "Failed to send message", android.widget.Toast.LENGTH_SHORT).show();
                    });
            });
    }
    
    private void createMessageNotification(String toUserId, String fromUserId, 
                                         String fromUserName, String fromUserProfilePic,
                                         String messageText, String messageId) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("toUserId", toUserId);
        notification.put("fromUserId", fromUserId);
        notification.put("fromUserName", fromUserName);
        notification.put("fromUserProfilePic", fromUserProfilePic);
        notification.put("type", "message");
        notification.put("messageId", messageId);
        notification.put("messageText", messageText);
        notification.put("timestamp", System.currentTimeMillis());
        notification.put("read", false);
        
        db.collection("notifications").add(notification);
    }
    
    private void navigateToNoteOnMap(double lat, double lng, String noteId) {
        Bundle args = new Bundle();
        args.putDouble("target_lat", lat);
        args.putDouble("target_lng", lng);
        args.putString("target_note_id", noteId);
        args.putBoolean("open_note_window", true);
        
        Navigation.findNavController(requireView())
            .navigate(R.id.mapFragment, args);
    }
}
