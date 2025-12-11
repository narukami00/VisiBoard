package com.visiboard.app.ui.feed;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Button;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.visiboard.app.R;
import com.visiboard.app.data.NearbyNote;
import com.visiboard.app.data.Notification;
import com.visiboard.app.data.UserInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FeedFragment extends Fragment implements DiscoverTabFragment.NoteClickListener, NotificationTabFragment.NotificationActionListener {
    
    private static final String TAG = "FeedFragment";
    
    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private RecyclerView rvSearchResults;
    private ImageButton btnMessages;
    private TextInputEditText etSearchUsers;
    private UserSearchAdapter userSearchAdapter;
    private FollowingAdapter followingAdapter;
    
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    
    private View customNotificationTab;
    private TextView tvNotificationBadge;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_feed, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        
        tabLayout = view.findViewById(R.id.tab_layout_feed);
        viewPager = view.findViewById(R.id.view_pager_feed);
        rvSearchResults = view.findViewById(R.id.rv_search_results);
        btnMessages = view.findViewById(R.id.btn_messages);
        etSearchUsers = view.findViewById(R.id.et_search_users);
        
        setupTabs();
        setupSearchBar();
        setupMessagesButton();
        setupSearchRecyclerView();
    }
    
    private void setupTabs() {
        FeedPagerAdapter pagerAdapter = new FeedPagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);
        
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            View tabView = LayoutInflater.from(requireContext()).inflate(R.layout.layout_tab_custom, null);
            TextView tvTitle = tabView.findViewById(R.id.tv_tab_title);
            TextView tvBadge = tabView.findViewById(R.id.tv_tab_badge);
            
            if (position == 0) {
                tvTitle.setText("Discover");
                tvBadge.setVisibility(View.GONE);
            } else {
                tvTitle.setText("Notifications");
                tvBadge.setVisibility(View.GONE);
                customNotificationTab = tabView;
                tvNotificationBadge = tvBadge;
            }
            tab.setCustomView(tabView);
        }).attach();
    }
    
    private void setupSearchRecyclerView() {
        rvSearchResults.setLayoutManager(new LinearLayoutManager(getContext()));
        userSearchAdapter = new UserSearchAdapter(user -> {
            showUserInfoDialog(user.getUserId());
        });
        userSearchAdapter.setOnMessageClickListener(user -> {
            showSendMessageDialog(user);
        });
        rvSearchResults.setAdapter(userSearchAdapter);
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

    private void searchUsers(String query) {
        db.collection("users")
            .orderBy("name")
            .startAt(query)
            .endAt(query + "\uf8ff")
            .limit(10)
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                List<UserInfo> users = new ArrayList<>();
                for (DocumentSnapshot doc : queryDocumentSnapshots) {
                    UserInfo user = doc.toObject(UserInfo.class);
                    if (user != null) {
                        user.setUserId(doc.getId());
                        if (!user.getUserId().equals(auth.getCurrentUser().getUid())) {
                            users.add(user);
                        }
                    }
                }
                if (!users.isEmpty()) {
                    rvSearchResults.setVisibility(View.VISIBLE);
                    userSearchAdapter.setUsers(users);
                } else {
                    rvSearchResults.setVisibility(View.GONE);
                }
            })
            .addOnFailureListener(e -> Log.e(TAG, "Error searching users", e));
    }

    private void setupMessagesButton() {
        btnMessages.setOnClickListener(v -> showFollowingDialog(null));
    }

    // Callbacks
    @Override
    public void onNoteClick(NearbyNote note) {
        navigateToNoteOnMap(note.getLat(), note.getLng(), note.getId());
    }

    @Override
    public void onShareClick(NearbyNote note) {
        showFollowingDialog(note);
    }

    @Override
    public void onNotificationClick(Notification notification) {
        handleNotificationClick(notification);
    }

    @Override
    public void onReplyClick(Notification notification) {
        if (notification.getFromUserId() != null && !notification.getFromUserId().equals("anonymous")) {
            UserInfo recipient = new UserInfo();
            recipient.setUserId(notification.getFromUserId());
            recipient.setName(notification.getFromUserName());
            recipient.setProfilePic(notification.getFromUserProfilePic());
            showSendMessageDialog(recipient);
        }
    }

    @Override
    public void onUnreadCountChanged(int count) {
        if (tvNotificationBadge != null) {
            if (count > 0) {
                tvNotificationBadge.setText(String.valueOf(count));
                tvNotificationBadge.setVisibility(View.VISIBLE);
            } else {
                tvNotificationBadge.setVisibility(View.GONE);
            }
        }
    }

    private void navigateToNoteOnMap(double lat, double lng, String noteId) {
        Bundle args = new Bundle();
        args.putDouble("target_lat", lat);
        args.putDouble("target_lng", lng);
        args.putString("target_note_id", noteId);
        args.putBoolean("open_note_window", true);
        Navigation.findNavController(requireView()).navigate(R.id.mapFragment, args);
    }

    private void handleNotificationClick(Notification notification) {
        String type = notification.getType();
        Log.d(TAG, "Notification clicked: type=" + type + ", noteId=" + notification.getNoteId() + 
              ", messageId=" + notification.getMessageId() + ", fromUser=" + notification.getFromUserId());

        if (!notification.isRead()) {
            db.collection("notifications").document(notification.getId())
                .update("read", true);
        }
        
        if ("follow".equals(type)) {
            showUserInfoDialog(notification.getFromUserId());
        } else if ("message".equals(type)) {
            showMessageDialog(notification);
        } else if ("shared_note".equals(type)) {
            showSharedNoteView(notification);
        } else {
             // Default to note navigation
             String targetId = notification.getNoteId();
             if (targetId != null) {
                 navigateToNoteOnMap(notification.getNoteLat(), notification.getNoteLng(), targetId);
             }
        }
    }

    private class FeedPagerAdapter extends FragmentStateAdapter {
        public FeedPagerAdapter(Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            if (position == 0) return new DiscoverTabFragment();
            return new NotificationTabFragment();
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }
    
    private void showMessageDialog(Notification notification) {
        View dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_view_message, null);
        
        de.hdodenhof.circleimageview.CircleImageView ivSender = dialogView.findViewById(R.id.iv_sender_avatar);
        TextView tvSender = dialogView.findViewById(R.id.tv_sender_name);
        TextView tvTime = dialogView.findViewById(R.id.tv_message_time);
        TextView tvMessage = dialogView.findViewById(R.id.tv_message_text);
        Button btnClose = dialogView.findViewById(R.id.btn_close);
        
        // Use data from Notification object (No Fetching!)
        String senderName = notification.getFromUserName();
        String senderPic = notification.getFromUserProfilePic();
        Long timestamp = notification.getTimestamp();
        String messageText = notification.getMessageText();
        
        tvSender.setText(senderName != null ? senderName : "Anonymous");
        tvMessage.setText(messageText != null ? messageText : "Message not available");
        tvTime.setText(getTimeAgo(timestamp != null ? timestamp : 0));
        
        // Load profile picture
        if (senderPic != null && !senderPic.isEmpty()) {
            try {
                // Use ImageCache if possible, or manual decode
                com.visiboard.app.utils.ImageCache.getInstance()
                    .loadBase64Image(senderPic, ivSender, R.drawable.ic_profile);
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
        
        // Mark message as read in background
        if (notification.getMessageId() != null) {
            db.collection("messages").document(notification.getMessageId()).update("read", true);
        }
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
    

    
    private void showFollowingDialog(@Nullable NearbyNote noteToShare) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_following, null);
        
        RecyclerView rvDialog = dialogView.findViewById(R.id.rv_following_dialog);
        android.widget.ProgressBar pbLoading = dialogView.findViewById(R.id.pb_loading_following);
        TextView tvNoData = dialogView.findViewById(R.id.tv_no_following_dialog);
        ImageButton btnCloseHeader = dialogView.findViewById(R.id.btn_close_header);
        TextInputEditText etSearch = dialogView.findViewById(R.id.et_search_following);
        TextView tvTitle = dialogView.findViewById(R.id.dialog_title); // ID was dialog_title
        
        // Update Title if sharing
        if (noteToShare != null) {
            if (tvTitle != null) {
                tvTitle.setText("Share Note");
            }
        }
        
        // List to hold all users for filtering
        final List<UserInfo> allFollowingList = new ArrayList<>();
        
        rvDialog.setLayoutManager(new LinearLayoutManager(getContext()));
        
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();

        FollowingAdapter dialogAdapter = new FollowingAdapter(
            user -> showUserInfoDialog(user.getUserId()),
            user -> {
                if (noteToShare != null) {
                     sendSharedNote(user, noteToShare);
                     dialog.dismiss();
                } else {
                     showSendMessageDialog(user);
                     // dialog.dismiss(); // Optional: keep open to message multiple?
                }
            }
        );
        rvDialog.setAdapter(dialogAdapter);
        

        
        btnCloseHeader.setOnClickListener(v -> dialog.dismiss());
        
        // Search Filter
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().toLowerCase().trim();
                if (allFollowingList.isEmpty()) return;
                
                List<UserInfo> filtered = new ArrayList<>();
                for (UserInfo user : allFollowingList) {
                    if (user.getName() != null && user.getName().toLowerCase().contains(query)) {
                        filtered.add(user);
                    }
                }
                dialogAdapter.setUsers(filtered);
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
        
        dialog.show();
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        
        loadFollowingUsers(dialogAdapter, pbLoading, tvNoData, rvDialog, allFollowingList);
    }

    private void loadFollowingUsers(FollowingAdapter adapter, View pbLoading, View tvNoData, View rvContent, List<UserInfo> allFollowingList) {
        pbLoading.setVisibility(View.VISIBLE);
        tvNoData.setVisibility(View.GONE);
        rvContent.setVisibility(View.GONE);
        
        String userId = auth.getCurrentUser().getUid();
        
        db.collection("users").document(userId)
            .collection("following")
            .get()
            .addOnSuccessListener(querySnapshot -> {
                int totalFollowing = querySnapshot.size();
                
                if (totalFollowing == 0) {
                    pbLoading.setVisibility(View.GONE);
                    tvNoData.setVisibility(View.VISIBLE);
                    return;
                }
                
                // Clear list just in case
                allFollowingList.clear();
                
                // Show list container immediately for progressive loading
                pbLoading.setVisibility(View.GONE);
                rvContent.setVisibility(View.VISIBLE);
                
                for (DocumentSnapshot doc : querySnapshot) {
                    String followedId = doc.getId();
                    db.collection("users").document(followedId).get()
                        .addOnSuccessListener(userDoc -> {
                            if (userDoc.exists()) {
                                UserInfo user = new UserInfo();
                                user.setUserId(followedId);
                                user.setName(userDoc.getString("name"));
                                user.setProfilePic(userDoc.getString("profilePic"));
                                user.setLastKnownLocation(userDoc.getString("lastKnownLocation"));
                                
                                allFollowingList.add(user);
                                
                                // Sort alphabetically
                                java.util.Collections.sort(allFollowingList, (u1, u2) -> {
                                    String n1 = u1.getName() != null ? u1.getName() : "";
                                    String n2 = u2.getName() != null ? u2.getName() : "";
                                    return n1.compareToIgnoreCase(n2);
                                });
                                
                                // Update adapter incrementally
                                adapter.setUsers(new ArrayList<>(allFollowingList));
                            }
                        })
                        .addOnFailureListener(e -> {
                            // handle error silently for individual user
                        });
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error loading following", e);
                pbLoading.setVisibility(View.GONE);
                tvNoData.setVisibility(View.VISIBLE);
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
    

    private void sendSharedNote(UserInfo recipient, NearbyNote note) {
        String fromUserId = auth.getCurrentUser().getUid();
        
        db.collection("users").document(fromUserId).get()
            .addOnSuccessListener(doc -> {
                String fromUserName = doc.getString("name");
                String fromUserProfilePic = doc.getString("profilePic");
                
                String messageText = "Shared a note: " + (note.getText() != null ? note.getText() : "Image Note");
                
                Map<String, Object> message = new HashMap<>();
                message.put("fromUserId", fromUserId);
                message.put("fromUserName", fromUserName);
                message.put("fromUserProfilePic", fromUserProfilePic);
                message.put("toUserId", recipient.getUserId());
                message.put("messageText", messageText);
                message.put("timestamp", System.currentTimeMillis());
                message.put("anonymous", false);
                message.put("read", false);
                message.put("type", "shared_note");
                
                // Note Data Snapshot
                message.put("noteId", note.getId());
                message.put("noteText", note.getText());
                message.put("noteImage", note.getImageBase64());
                message.put("noteLat", note.getLat());
                message.put("noteLng", note.getLng());
                message.put("noteLikes", note.getLikesCount());
                message.put("noteComments", note.getCommentsCount());
                
                // Save message
                db.collection("messages").add(message)
                    .addOnSuccessListener(docRef -> {
                        // Create notification
                        Map<String, Object> notification = new HashMap<>();
                        notification.put("toUserId", recipient.getUserId());
                        notification.put("fromUserId", fromUserId);
                        notification.put("fromUserName", fromUserName);
                        notification.put("fromUserProfilePic", fromUserProfilePic);
                        notification.put("type", "shared_note");
                        notification.put("messageId", docRef.getId());
                        notification.put("messageText", "Shared a note with you");
                        notification.put("timestamp", System.currentTimeMillis());
                        notification.put("read", false);
                        
                        // Note extras for notification
                        notification.put("noteId", note.getId());
                        notification.put("noteLat", note.getLat());
                        notification.put("noteLng", note.getLng());
                        notification.put("noteText", note.getText());
                        notification.put("noteImage", note.getImageBase64());
                        notification.put("noteLikes", note.getLikesCount());
                        notification.put("noteComments", note.getCommentsCount());
                        
                        db.collection("notifications").add(notification);
                        
                        android.widget.Toast.makeText(requireContext(), 
                            "Note shared!", android.widget.Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        android.widget.Toast.makeText(requireContext(), 
                            "Failed to share note", android.widget.Toast.LENGTH_SHORT).show();
                    });
            });
    }

    private void showSharedNoteView(Notification notification) {
        View dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_view_shared_note, null);
        
        de.hdodenhof.circleimageview.CircleImageView ivSender = dialogView.findViewById(R.id.iv_sender_avatar);
        TextView tvSender = dialogView.findViewById(R.id.tv_sender_name);
        TextView tvTime = dialogView.findViewById(R.id.tv_message_time);
        Button btnClose = dialogView.findViewById(R.id.btn_close);
        
        // Note Card Views
        androidx.cardview.widget.CardView cvNote = dialogView.findViewById(R.id.cv_shared_note);
        ImageView ivNoteImage = dialogView.findViewById(R.id.iv_note_image);
        TextView tvNoteText = dialogView.findViewById(R.id.tv_note_text);
        TextView tvLikes = dialogView.findViewById(R.id.tv_likes_count);
        TextView tvComments = dialogView.findViewById(R.id.tv_comments_count);
        
        // Sender Info
        tvSender.setText(notification.getFromUserName() != null ? notification.getFromUserName() : "User");
        tvTime.setText("Shared a note • " + getTimeAgo(notification.getTimestamp()));
        
        String senderPic = notification.getFromUserProfilePic();
        if (senderPic != null && !senderPic.isEmpty()) {
            com.visiboard.app.utils.ImageCache.getInstance()
                .loadBase64Image(senderPic, ivSender, R.drawable.ic_profile);
        }

        // Note Content
        String text = notification.getMessageText(); // Might be "Shared a note.." or actual text depending on field
        // Actually we put note details in extras
        
        // Extract note details from notification fields I added
        // The Notification model might need to support accessing the dynamic map or we just use specific getters if they existed. 
        // Since Notification class is simple, I should rely on the fact that Firestore doc -> Notification object mapping 
        // might miss extra fields if they aren't in the class. 
        // BUT, the Notification object passed to handleNotificationClick comes from Firestore.
        // Wait, Notification.java (Step 37 was Message.java... I didn't see Notification.java fully).
        // If Notification.java doesn't have these fields, I can't access them via getters.
        // However, I can fetch the MESSAGE document which definitely has them.
        
        // BETTER APPROACH: Fetch the message document using messageId to get full details.
        
        if (notification.getMessageId() != null) {
             db.collection("messages").document(notification.getMessageId()).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String noteText = doc.getString("noteText");
                        String noteImage = doc.getString("noteImage");
                        String noteId = doc.getString("noteId");
                        Double lat = doc.getDouble("noteLat");
                        Double lng = doc.getDouble("noteLng");
                        
                        Long likes = doc.getLong("noteLikes");
                        Long comments = doc.getLong("noteComments");
                        
                        tvNoteText.setText(noteText != null && !noteText.isEmpty() ? noteText : "");
                        tvLikes.setText(String.valueOf(likes != null ? likes : 0));
                        tvComments.setText(String.valueOf(comments != null ? comments : 0));
                        
                        if (noteImage != null && !noteImage.isEmpty()) {
                            ivNoteImage.setVisibility(View.VISIBLE);
                            com.visiboard.app.utils.ImageCache.getInstance()
                                .loadBase64Image(noteImage, ivNoteImage, R.drawable.placeholder_image);
                        } else {
                            ivNoteImage.setVisibility(View.GONE);
                        }
                        
                        // Click navigation
                        cvNote.setOnClickListener(v -> {
                             if (lat != null && lng != null && noteId != null) {
                                  navigateToNoteOnMap(lat, lng, noteId);
                                  // Close dialog?
                             }
                        });
                    }
                });
        }
        
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create();
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        
        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        
        // Mark read
        if (notification.getId() != null) { // Notification ID might be missing if constructed locally? 
            // The notification passed to handleNotificationClick usually comes from Adapter which gets it from Firestore with ID.
             db.collection("notifications").document(notification.getId()).update("read", true);
        }
    }
}
