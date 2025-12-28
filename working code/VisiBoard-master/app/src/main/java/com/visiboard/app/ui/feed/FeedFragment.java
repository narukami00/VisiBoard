package com.visiboard.app.ui.feed;

import android.content.Intent;
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
import android.widget.Toast;

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
    // private RecyclerView rvSearchResults;
    // private ImageButton btnMessages;
    // private TextInputEditText etSearchUsers;
    // private UserSearchAdapter userSearchAdapter;
    private FollowingAdapter followingAdapter;
    
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    
    private View customNotificationTab;
    private TextView tvNotificationBadge;
    
    private java.util.List<String> followingIds = new java.util.ArrayList<>();

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
        // rvSearchResults = view.findViewById(R.id.rv_search_results);
        // btnMessages = view.findViewById(R.id.btn_messages);
        // etSearchUsers = view.findViewById(R.id.et_search_users);
        
        setupTabs();
        // setupSearchBar();
        // setupMessagesButton();
        // setupSearchRecyclerView();
        
        loadFollowingList();
    }
    
    private void loadFollowingList() {
        if (auth.getCurrentUser() == null) return;
        db.collection("users").document(auth.getCurrentUser().getUid())
                .collection("following")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) return;
                    if (snapshots != null) {
                        followingIds.clear();
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            followingIds.add(doc.getId());
                        }
                    }
                });
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
        
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                try {
                    tabLayout.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                } catch (Exception e) {}
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }
    
    /*
    private void setupSearchRecyclerView() {
        rvSearchResults.setLayoutManager(new LinearLayoutManager(getContext()));
        userSearchAdapter = new UserSearchAdapter(user -> {
            showUserInfoDialog(user.getUserId());
        });
        userSearchAdapter.setOnMessageClickListener(user -> {
            showSendMessageDialog(user);
        });
        rvSearchResults.setAdapter(userSearchAdapter);
        
        // Add layout animation
        android.view.animation.LayoutAnimationController controller =
            android.view.animation.AnimationUtils.loadLayoutAnimation(getContext(), R.anim.layout_animation_fall_down);
        rvSearchResults.setLayoutAnimation(controller);
    }
    */

    /*
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
    */

    /*
    private void setupMessagesButton() {
        btnMessages.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
            v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100)
                .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(100).start())
                .start();
            showFollowingDialog(null);
        });
    }
    */

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
        } else if ("follow_request".equals(type)) {
             startActivity(new android.content.Intent(requireContext(), com.visiboard.app.ui.settings.FollowRequestsActivity.class));
        } else if ("follow_accepted".equals(type)) {
             showUserInfoDialog(notification.getFromUserId());
        } else {
             // Default to note navigation
             String targetId = notification.getNoteId();
             if (targetId != null) {
                 verifyNoteAccessAndNavigate(targetId, notification.getNoteLat(), notification.getNoteLng());
             }
        }
    }

    private void verifyNoteAccessAndNavigate(String noteId, double lat, double lng) {
        if (auth.getCurrentUser() == null) return;
        String currentUid = auth.getCurrentUser().getUid();

        db.collection("notes").document(noteId).get()
            .addOnSuccessListener(doc -> {
                if (!doc.exists()) {
                    Toast.makeText(getContext(), "Note no longer exists", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                String ownerId = doc.getString("userId");
                String visibility = doc.getString("visibility");
                if (visibility == null) visibility = "public";
                
                boolean isAllowed = false;
                
                if (currentUid.equals(ownerId)) {
                    isAllowed = true;
                } else if ("public".equals(visibility)) {
                    isAllowed = true;
                } else if ("followers".equals(visibility)) {
                    if (followingIds.contains(ownerId)) {
                        isAllowed = true;
                    }
                } else if ("private".equals(visibility)) {
                     // Only owner allowed (handled above)
                    isAllowed = false;
                }
                
                if (isAllowed) {
                    navigateToNoteOnMap(lat, lng, noteId);
                } else {
                    Toast.makeText(getContext(), "This note is private", Toast.LENGTH_SHORT).show();
                }
            })
            .addOnFailureListener(e -> {
                 Toast.makeText(getContext(), "Failed to verify note access", Toast.LENGTH_SHORT).show();
            });
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
        Bundle args = new Bundle();
        args.putString("userId", userId);
        androidx.navigation.Navigation.findNavController(requireView()).navigate(R.id.userProfileFragment, args);
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
        TextView tvTitle = dialogView.findViewById(R.id.dialog_title); 
        
        if (noteToShare != null) {
            if (tvTitle != null) {
                tvTitle.setText("Share Note");
            }
        }
        
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
                }
            }
        );
        rvDialog.setAdapter(dialogAdapter);
        
        btnCloseHeader.setOnClickListener(v -> dialog.dismiss());

        // Social Share Buttons
        ImageButton btnShareFacebook = dialogView.findViewById(R.id.btn_share_facebook);
        ImageButton btnShareInstagram = dialogView.findViewById(R.id.btn_share_instagram);
        ImageButton btnShareWhatsapp = dialogView.findViewById(R.id.btn_share_whatsapp);
        ImageButton btnShareEmail = dialogView.findViewById(R.id.btn_share_email);

        View.OnClickListener socialShareListener = v -> {
            String shareText = "";
            if (noteToShare != null) {
                shareText = "Check out this note on VisiBoard!\n\n";
                if (noteToShare.getText() != null) {
                    shareText += "\"" + noteToShare.getText() + "\"\n\n";
                }
                shareText += "📍 Location: https://maps.google.com/?q=" + noteToShare.getLat() + "," + noteToShare.getLng();
            } else {
                shareText = "Check out VisiBoard - an AR note-taking app!";
            }

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);

            String packageName = null;
            int viewId = v.getId();
            if (viewId == R.id.btn_share_facebook) {
                packageName = "com.facebook.katana";
            } else if (viewId == R.id.btn_share_instagram) {
                packageName = "com.instagram.android";
            } else if (viewId == R.id.btn_share_whatsapp) {
                packageName = "com.whatsapp";
            } else if (viewId == R.id.btn_share_email) {
                shareIntent = new Intent(Intent.ACTION_SENDTO);
                shareIntent.setData(android.net.Uri.parse("mailto:"));
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Check out this VisiBoard Note!");
                shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
            }

            if (packageName != null) {
                shareIntent.setPackage(packageName);
            }

            try {
                startActivity(shareIntent);
                dialog.dismiss();
            } catch (Exception e) {
                if (packageName != null) {
                    Intent chooser = Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText), "Share via");
                    startActivity(chooser);
                } else {
                    Toast.makeText(requireContext(), "No email app found", Toast.LENGTH_SHORT).show();
                }
            }
        };

        if (btnShareFacebook != null) btnShareFacebook.setOnClickListener(socialShareListener);
        if (btnShareInstagram != null) btnShareInstagram.setOnClickListener(socialShareListener);
        if (btnShareWhatsapp != null) btnShareWhatsapp.setOnClickListener(socialShareListener);
        if (btnShareEmail != null) btnShareEmail.setOnClickListener(socialShareListener);
        
        // Messenger Button
        ImageButton btnShareMessenger = dialogView.findViewById(R.id.btn_share_messenger);
        if (btnShareMessenger != null) {
            btnShareMessenger.setOnClickListener(v -> {
                String shareText = "";
                if (noteToShare != null) {
                    shareText = "Check out this note on VisiBoard!\n\n";
                    if (noteToShare.getText() != null) {
                        shareText += "\"" + noteToShare.getText() + "\"\n\n";
                    }
                    shareText += "📍 Location: https://maps.google.com/?q=" + noteToShare.getLat() + "," + noteToShare.getLng();
                } else {
                    shareText = "Check out VisiBoard - an AR note-taking app!";
                }
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
                shareIntent.setPackage("com.facebook.orca");
                try {
                    startActivity(shareIntent);
                    dialog.dismiss();
                } catch (Exception e) {
                    Intent chooser = Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText), "Share via");
                    startActivity(chooser);
                }
            });
        }
        
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
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
            @Override public void afterTextChanged(Editable s) {}
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
                
                allFollowingList.clear();
                
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
                                
                                java.util.Collections.sort(allFollowingList, (u1, u2) -> {
                                    String n1 = u1.getName() != null ? u1.getName() : "";
                                    String n2 = u2.getName() != null ? u2.getName() : "";
                                    return n1.compareToIgnoreCase(n2);
                                });
                                
                                adapter.setUsers(new ArrayList<>(allFollowingList));
                            }
                        })
                        .addOnFailureListener(e -> {});
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error loading following", e);
                pbLoading.setVisibility(View.GONE);
                tvNoData.setVisibility(View.VISIBLE);
            });
    }

    private void showSendMessageDialog(UserInfo recipient) {
        String currentUserId = auth.getCurrentUser().getUid();

        db.collection("users").document(recipient.getUserId()).get()
            .addOnSuccessListener(userDoc -> {
                boolean isPrivate = userDoc.getBoolean("isPrivate") != null && userDoc.getBoolean("isPrivate");

                if (isPrivate) {
                     db.collection("users").document(currentUserId)
                         .collection("following").document(recipient.getUserId())
                         .get()
                         .addOnSuccessListener(followDoc -> {
                             if (followDoc.exists()) {
                                 performShowSendMessageDialog(recipient);
                             } else {
                                 new AlertDialog.Builder(requireContext())
                                     .setTitle("Private Account")
                                     .setMessage("You must be following " + (recipient.getName() != null ? recipient.getName() : "this user") + " to send them a message.")
                                     .setPositiveButton("OK", null)
                                     .show();
                             }
                         });
                } else {
                    performShowSendMessageDialog(recipient);
                }
            });
    }

    private void performShowSendMessageDialog(UserInfo recipient) {
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
                
                db.collection("messages").add(message)
                    .addOnSuccessListener(docRef -> {
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
                
                message.put("noteId", note.getId());
                message.put("noteText", note.getText());
                message.put("noteImage", note.getImageBase64());
                message.put("noteLat", note.getLat());
                message.put("noteLng", note.getLng());
                message.put("noteLikes", note.getLikesCount());
                message.put("noteComments", note.getCommentsCount());
                
                db.collection("messages").add(message)
                    .addOnSuccessListener(docRef -> {
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
        
        androidx.cardview.widget.CardView cvNote = dialogView.findViewById(R.id.cv_shared_note);
        ImageView ivNoteImage = dialogView.findViewById(R.id.iv_note_image);
        TextView tvNoteText = dialogView.findViewById(R.id.tv_note_text);
        TextView tvLikes = dialogView.findViewById(R.id.tv_likes_count);
        TextView tvComments = dialogView.findViewById(R.id.tv_comments_count);
        
        tvSender.setText(notification.getFromUserName() != null ? notification.getFromUserName() : "User");
        tvTime.setText("Shared a note • " + getTimeAgo(notification.getTimestamp()));
        
        String senderPic = notification.getFromUserProfilePic();
        if (senderPic != null && !senderPic.isEmpty()) {
            com.visiboard.app.utils.ImageCache.getInstance()
                .loadBase64Image(senderPic, ivSender, R.drawable.ic_profile);
        }

        if (notification.getMessageId() != null) {
             db.collection("messages").document(notification.getMessageId()).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String noteId = doc.getString("noteId");
                        
                        // FETCH REAL NOTE to check privacy
                        if (noteId != null) {
                            db.collection("notes").document(noteId).get().addOnSuccessListener(noteDoc -> {
                                if (!noteDoc.exists()) {
                                    tvNoteText.setText("Note deleted or unavailable");
                                    cvNote.setVisibility(View.GONE); 
                                    return;
                                }
                                
                                String visibility = noteDoc.getString("visibility");
                                String ownerId = noteDoc.getString("userId");
                                String currentUserId = auth.getCurrentUser().getUid();
                                
                                boolean access = false;
                                if (ownerId != null && ownerId.equals(currentUserId)) access = true;
                                else if ("public".equals(visibility)) access = true;
                                else if ("followers".equals(visibility)) {
                                     // Check if following
                                     if (followingIds.contains(ownerId)) access = true;
                                }
                                
                                if (access) {
                                    String noteText = noteDoc.getString("text");
                                    String noteImage = noteDoc.getString("imageBase64");
                                    Double lat = noteDoc.getDouble("latitude");
                                    Double lng = noteDoc.getDouble("longitude");
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
                                    
                                    cvNote.setVisibility(View.VISIBLE);
                                    cvNote.setOnClickListener(v -> {
                                         if (lat != null && lng != null) {
                                              navigateToNoteOnMap(lat, lng, noteId);
                                              // dialog.dismiss();
                                         }
                                    });
                                } else {
                                    // Access Denied
                                    tvNoteText.setText("Shared note is private.\nYou must follow the user to view it.");
                                    tvNoteText.setVisibility(View.VISIBLE);
                                    ivNoteImage.setVisibility(View.GONE);
                                    cvNote.setVisibility(View.VISIBLE);
                                    cvNote.setOnClickListener(null); 
                                    tvLikes.setText("-");
                                    tvComments.setText("-");
                                }
                            }); 
                        } else {
                             tvNoteText.setText("Note unavailable");
                             cvNote.setVisibility(View.GONE);
                        }
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
        
        if (notification.getId() != null) { 
             db.collection("notifications").document(notification.getId()).update("read", true);
        }
    }


    // --- Follow Logic Helpers ---

    private void followUser(String targetUserId, Button btn) {
        String currentUserId = auth.getCurrentUser().getUid();
        
        // Loading State
        String originalText = btn.getText().toString();
        btn.setText("...");
        btn.setEnabled(false);
        
        db.collection("users").document(targetUserId).get()
            .addOnSuccessListener(targetUserDoc -> {
                boolean isPrivate = targetUserDoc.getBoolean("isPrivate") != null && targetUserDoc.getBoolean("isPrivate");
                
                if (isPrivate) {
                    // Check Rejection Status First (5-Strike Rule)
                    db.collection("users").document(targetUserId).collection("rejections").document(currentUserId)
                        .get()
                        .addOnSuccessListener(rejectionDoc -> {
                            boolean blocked = false;
                            if (rejectionDoc.exists()) {
                                Long count = rejectionDoc.getLong("count");
                                Long time = rejectionDoc.getLong("lastRejectionTime");
                                if (count != null && count >= 5 && time != null) {
                                     if (System.currentTimeMillis() - time < 24 * 60 * 60 * 1000) {
                                         blocked = true;
                                     }
                                }
                            }
                            
                            if (blocked) {
                                android.widget.Toast.makeText(requireContext(), "Too many follow requests. Try again later.", android.widget.Toast.LENGTH_LONG).show();
                                btn.setText(originalText);
                                btn.setEnabled(true);
                            } else {
                                sendFollowRequest(targetUserId, btn, currentUserId);
                            }
                        })
                        .addOnFailureListener(e -> {
                            btn.setText(originalText);
                            btn.setEnabled(true);
                        });
                } else {
                    performDirectFollow(targetUserId, btn, currentUserId);
                }
            })
            .addOnFailureListener(e -> {
                btn.setText(originalText);
                btn.setEnabled(true);
            });
    }

    private void sendFollowRequest(String targetUserId, Button btn, String currentUserId) {
        db.collection("users").document(currentUserId).get().addOnSuccessListener(currentUserDoc -> {
             String myName = currentUserDoc.getString("name");
             String myProfilePic = currentUserDoc.getString("profilePic");
             Map<String, Object> requestData = new HashMap<>();
             requestData.put("timestamp", System.currentTimeMillis());
             requestData.put("requesterName", myName);
             requestData.put("requesterProfilePic", myProfilePic);
             
             db.collection("users").document(targetUserId).collection("follow_requests").document(currentUserId)
                 .set(requestData)
                 .addOnSuccessListener(aVoid -> {
                     btn.setText("Requested");
                     Toast.makeText(requireContext(), "Request sent", Toast.LENGTH_SHORT).show();
                     createNotification(targetUserId, currentUserId, "follow_request");
                     btn.setEnabled(true);
                 })
                 .addOnFailureListener(e -> btn.setEnabled(true));
        });
    }

    private void performDirectFollow(String targetUserId, Button btn, String currentUserId) {
        db.collection("users").document(currentUserId).get().addOnSuccessListener(currentUserDoc -> {
             String myName = currentUserDoc.getString("name");
             String myProfilePic = currentUserDoc.getString("profilePic");
             
             // 1. Add to followers (Target)
             Map<String, Object> followerData = new HashMap<>();
             followerData.put("timestamp", System.currentTimeMillis());
             followerData.put("followerName", myName);
             followerData.put("followerProfilePic", myProfilePic);
             db.collection("users").document(targetUserId).collection("followers").document(currentUserId).set(followerData);
             db.collection("users").document(targetUserId).update("followersCount", com.google.firebase.firestore.FieldValue.increment(1));
             
             // 2. Add to following (Me)
             db.collection("users").document(targetUserId).get().addOnSuccessListener(targetUserDoc -> {
                 String targetName = targetUserDoc.getString("name");
                 String targetProfilePic = targetUserDoc.getString("profilePic");
                 
                 Map<String, Object> followingData = new HashMap<>();
                 followingData.put("timestamp", System.currentTimeMillis());
                 followingData.put("followedName", targetName);
                 followingData.put("followedProfilePic", targetProfilePic);
                 
                 db.collection("users").document(currentUserId).collection("following").document(targetUserId).set(followingData);
                 db.collection("users").document(currentUserId).update("followingCount", com.google.firebase.firestore.FieldValue.increment(1));
                 
                 // Update UI
                 btn.setText("Following");
                 btn.setEnabled(true);
                 createNotification(targetUserId, currentUserId, "follow");
                 Toast.makeText(requireContext(), "Following " + targetName, Toast.LENGTH_SHORT).show();
             });
        })
        .addOnFailureListener(e -> btn.setEnabled(true));
    }

    private void cancelFollowRequest(String targetUserId, Button btn) {
        String currentUserId = auth.getCurrentUser().getUid();
        db.collection("users").document(targetUserId).collection("follow_requests").document(currentUserId).delete()
            .addOnSuccessListener(aVoid -> {
                btn.setText("Follow");
                Toast.makeText(requireContext(), "Request canceled", Toast.LENGTH_SHORT).show();
            });
    }

    private void unfollowUser(String targetUserId, Button btn) {
        String currentUserId = auth.getCurrentUser().getUid();
        db.collection("users").document(targetUserId).collection("followers").document(currentUserId).delete();
        db.collection("users").document(targetUserId).update("followersCount", com.google.firebase.firestore.FieldValue.increment(-1));
        db.collection("users").document(currentUserId).collection("following").document(targetUserId).delete();
        db.collection("users").document(currentUserId).update("followingCount", com.google.firebase.firestore.FieldValue.increment(-1));
        btn.setText("Follow");
        Toast.makeText(requireContext(), "Unfollowed", Toast.LENGTH_SHORT).show();
    }

    private void createNotification(String toUserId, String fromUserId, String type) {
        createNotification(toUserId, fromUserId, type, null, null, null);
    }

    private void createNotification(String toUserId, String fromUserId, String type, String noteId, String noteText, String noteImage) {
        db.collection("users").document(fromUserId).get().addOnSuccessListener(doc -> {
             String name = doc.getString("name");
             String pic = doc.getString("profilePic");
             Map<String, Object> notif = new HashMap<>();
             notif.put("type", type);
             notif.put("fromUserId", fromUserId);
             notif.put("fromUserName", name);
             notif.put("fromUserProfilePic", pic);
             notif.put("toUserId", toUserId);
             notif.put("timestamp", System.currentTimeMillis());
             notif.put("read", false);
             
             if (noteId != null) notif.put("noteId", noteId);
             if (noteText != null) notif.put("noteText", noteText);
             if (noteImage != null) notif.put("noteImage", noteImage);
             
             db.collection("notifications").add(notif);
        });
    }
}
