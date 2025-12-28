package com.visiboard.app.ui.connect;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.visiboard.app.R;
import com.visiboard.app.chat.ChatActivity;
import com.visiboard.app.chat.ChatManager;
import com.visiboard.app.data.Chat;
import com.visiboard.app.utils.ImageCache;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ChatsFragment extends Fragment {
    
    private static final String TAG = "ChatsFragment";

    private RecyclerView rvActiveChats;
    private RecyclerView rvFollowing;
    private EditText etSearch;
    private LinearLayout llActiveChatsSection;
    private LinearLayout llFollowingSection;
    private LinearLayout llEmptyState;
    private TextView tvEmptyTitle;
    private TextView tvEmptySubtitle;
    private SwipeRefreshLayout swipeRefresh;

    private ActiveChatsAdapter activeChatsAdapter;
    private FollowingAdapter followingAdapter;
    
    private FirebaseFirestore db;
    private ChatManager chatManager;
    private String currentUserId; // Restored field
    private List<Chat> allActiveChats = new ArrayList<>();
    private List<FollowingUser> allFollowing = new ArrayList<>();
    private Set<String> activeChatUserIds = new HashSet<>();
    
    private ValueEventListener userChatsListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chats, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        db = FirebaseFirestore.getInstance();
        chatManager = ChatManager.getInstance();
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            currentUserId = auth.getCurrentUser().getUid();
        }

        initViews(view);
        setupRecyclerViews();

        setupSearch();
        setupSwipeRefresh();
        loadActiveChats();
        loadFollowing();
    }

    private void initViews(View view) {
        rvActiveChats = view.findViewById(R.id.rv_active_chats);
        rvFollowing = view.findViewById(R.id.rv_following);
        etSearch = view.findViewById(R.id.et_search);
        llActiveChatsSection = view.findViewById(R.id.ll_active_chats_section);
        llFollowingSection = view.findViewById(R.id.ll_following_section);
        llEmptyState = view.findViewById(R.id.ll_empty_state);
        tvEmptyTitle = view.findViewById(R.id.tv_empty_title);
        tvEmptySubtitle = view.findViewById(R.id.tv_empty_subtitle);
        swipeRefresh = view.findViewById(R.id.swipe_refresh);
    }



    private void setupSwipeRefresh() {
        swipeRefresh.setColorSchemeResources(R.color.primary);
        swipeRefresh.setOnRefreshListener(() -> {
            loadFollowing();
            // Active chats are real-time, so just stop refreshing
            swipeRefresh.postDelayed(() -> swipeRefresh.setRefreshing(false), 1000);
        });
    }

    private void setupRecyclerViews() {
        // Active Chats
        rvActiveChats.setLayoutManager(new LinearLayoutManager(getContext()));
        activeChatsAdapter = new ActiveChatsAdapter(chat -> openChat(
            chat.getRecipientId(), 
            chat.getRecipientName(), 
            chat.getRecipientProfilePic()
        ));
        rvActiveChats.setAdapter(activeChatsAdapter);

        // Following
        rvFollowing.setLayoutManager(new LinearLayoutManager(getContext()));
        followingAdapter = new FollowingAdapter(user -> openChat(
            user.userId, 
            user.name, 
            user.profilePic
        ));
        rvFollowing.setAdapter(followingAdapter);
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter(s.toString());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void filter(String query) {
        String q = query.toLowerCase(Locale.ROOT).trim();
        
        // Filter active chats
        List<Chat> filteredChats = new ArrayList<>();
        for (Chat chat : allActiveChats) {
            if (chat.getRecipientName() != null && 
                chat.getRecipientName().toLowerCase(Locale.ROOT).contains(q)) {
                filteredChats.add(chat);
            }
        }
        activeChatsAdapter.setChats(filteredChats);
        
        // Filter following
        List<FollowingUser> filteredFollowing = new ArrayList<>();
        for (FollowingUser user : allFollowing) {
            if (user.name != null && user.name.toLowerCase(Locale.ROOT).contains(q)) {
                // Also exclude users who already have active chats
                if (!activeChatUserIds.contains(user.userId)) {
                    filteredFollowing.add(user);
                }
            }
        }
        followingAdapter.setUsers(filteredFollowing);
        
        updateSectionVisibility();
    }

    private void loadActiveChats() {
        if (currentUserId == null) return;

        userChatsListener = chatManager.listenForUserChats(currentUserId, snapshot -> {
            if (!isAdded()) return;
            
            allActiveChats.clear();
            activeChatUserIds.clear();
            
            for (DataSnapshot chatSnapshot : snapshot.getChildren()) {
                Chat chat = chatSnapshot.getValue(Chat.class);
                if (chat != null) {
                    chat.setChatId(chatSnapshot.getKey());
                    
                    // Filter out invalid chats or "Null User"
                    String name = chat.getRecipientName();
                    if (name != null && !name.isEmpty() && !name.equalsIgnoreCase("null")) {
                         allActiveChats.add(chat);
                         activeChatUserIds.add(chat.getRecipientId());
                    }
                }
            }
            
            // Sort by last message time (most recent first)
            Collections.sort(allActiveChats, (c1, c2) -> 
                Long.compare(c2.getLastMessageTime(), c1.getLastMessageTime()));
            
            activeChatsAdapter.setChats(allActiveChats);
            calculateEmptyState();
            
            // Refresh following list to hide users with active chats
            filterFollowingList();
        });
    }

    private void loadFollowing() {
        if (currentUserId == null) return;

        db.collection("users").document(currentUserId)
            .collection("following")
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                if (!isAdded()) return;
                
                allFollowing.clear();
                for (DocumentSnapshot doc : queryDocumentSnapshots) {
                    FollowingUser user = new FollowingUser();
                    user.userId = doc.getId();
                    user.name = doc.getString("followedName");
                    user.profilePic = doc.getString("followedProfilePic");
                    allFollowing.add(user);
                }
                
                filterFollowingList();
                calculateEmptyState();
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Failed to load following", e);
                if (isAdded()) {
                    Toast.makeText(getContext(), "Failed to load contacts", Toast.LENGTH_SHORT).show();
                }
            });
    }

    private void filterFollowingList() {
        // Show only following users who don't have active chats
        List<FollowingUser> filtered = new ArrayList<>();
        for (FollowingUser user : allFollowing) {
            if (!activeChatUserIds.contains(user.userId)) {
                filtered.add(user);
            }
        }
        followingAdapter.setUsers(filtered);
    }

    private void updateSectionVisibility() {
        calculateEmptyState();
    }
    
    private void calculateEmptyState() {
        boolean noActiveChats = activeChatsAdapter.getItemCount() == 0;
        boolean noFollowing = followingAdapter.getItemCount() == 0;
        
        // We only show full empty state if BOTH are empty
        if (noActiveChats && noFollowing) {
            llEmptyState.setVisibility(View.VISIBLE);
            llActiveChatsSection.setVisibility(View.GONE);
            llFollowingSection.setVisibility(View.GONE);
            
            tvEmptyTitle.setText("No connections yet");
            tvEmptySubtitle.setText("Follow people to start chatting");
        } else {
            llEmptyState.setVisibility(View.GONE);
            llActiveChatsSection.setVisibility(View.VISIBLE); // Always show section so we can see empty state implied by empty list
            llFollowingSection.setVisibility(View.VISIBLE);
        }
    }

    private void openChat(String userId, String name, String profilePic) {
        Intent intent = new Intent(getContext(), ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_RECIPIENT_ID, userId);
        intent.putExtra(ChatActivity.EXTRA_RECIPIENT_NAME, name);
        // Note: Don't pass profile pic through Intent - it can cause TransactionTooLargeException
        // ChatActivity will fetch it from Firestore or cache if needed
        startActivity(intent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (userChatsListener != null && currentUserId != null) {
            chatManager.removeUserChatsListener(currentUserId, userChatsListener);
        }
    }

    // --- Models & Interfaces ---

    private static class FollowingUser {
        String userId;
        String name;
        String profilePic;
    }

    private interface OnChatClickListener {
        void onClick(Chat chat);
    }

    private interface OnUserClickListener {
        void onClick(FollowingUser user);
    }

    // --- Active Chats Adapter ---

    private class ActiveChatsAdapter extends RecyclerView.Adapter<ActiveChatsAdapter.ViewHolder> {
        private List<Chat> chats = new ArrayList<>();
        private final OnChatClickListener listener;
        private final SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d", Locale.getDefault());

        public ActiveChatsAdapter(OnChatClickListener listener) {
            this.listener = listener;
        }

        public void setChats(List<Chat> chats) {
            this.chats = chats;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_active_chat, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Chat chat = chats.get(position);
            holder.bind(chat);
        }

        @Override
        public int getItemCount() {
            return chats.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private final TextView tvName, tvLastMessage, tvTime, tvUnreadCount;
            private final ImageView ivAvatar;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tv_name);
                tvLastMessage = itemView.findViewById(R.id.tv_last_message);
                tvTime = itemView.findViewById(R.id.tv_time);
                tvUnreadCount = itemView.findViewById(R.id.tv_unread_count);
                ivAvatar = itemView.findViewById(R.id.iv_avatar);
            }

            public void bind(Chat chat) {
                tvName.setText(chat.getRecipientName() != null ? chat.getRecipientName() : "User");
                tvLastMessage.setText(chat.getLastMessageDisplay());
                
                // Format time
                long time = chat.getLastMessageTime();
                if (time > 0) {
                    long now = System.currentTimeMillis();
                    long diff = now - time;
                    
                    if (diff < 24 * 60 * 60 * 1000) {
                        // Today - show time
                        tvTime.setText(timeFormat.format(new Date(time)));
                    } else {
                        // Older - show date
                        tvTime.setText(dateFormat.format(new Date(time)));
                    }
                } else {
                    tvTime.setText("");
                }
                
                // Unread count
                if (chat.getUnreadCount() > 0) {
                    tvUnreadCount.setVisibility(View.VISIBLE);
                    tvUnreadCount.setText(String.valueOf(Math.min(chat.getUnreadCount(), 99)));
                    tvLastMessage.setTextColor(getResources().getColor(R.color.text_primary));
                } else {
                    tvUnreadCount.setVisibility(View.GONE);
                    tvLastMessage.setTextColor(getResources().getColor(R.color.text_secondary));
                }
                
                // Avatar
                if (chat.getRecipientProfilePic() != null && !chat.getRecipientProfilePic().isEmpty()) {
                    ImageCache.getInstance().loadBase64Image(
                        "chat_" + chat.getRecipientId(), 
                        chat.getRecipientProfilePic(), 
                        ivAvatar, 
                        R.drawable.ic_profile
                    );
                } else {
                    ivAvatar.setImageResource(R.drawable.ic_profile);
                }
                
                itemView.setOnClickListener(v -> listener.onClick(chat));
            }
        }
    }

    // --- Following Adapter ---

    private class FollowingAdapter extends RecyclerView.Adapter<FollowingAdapter.ViewHolder> {
        private List<FollowingUser> users = new ArrayList<>();
        private final OnUserClickListener listener;

        public FollowingAdapter(OnUserClickListener listener) {
            this.listener = listener;
        }

        public void setUsers(List<FollowingUser> users) {
            this.users = users;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_user, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            FollowingUser user = users.get(position);
            holder.bind(user);
        }

        @Override
        public int getItemCount() {
            return users.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private final TextView tvName, tvStatus;
            private final ImageView ivAvatar;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tv_name);
                tvStatus = itemView.findViewById(R.id.tv_status);
                ivAvatar = itemView.findViewById(R.id.iv_avatar);
            }

            public void bind(FollowingUser user) {
                tvName.setText(user.name != null ? user.name : "User");
                tvStatus.setText("Tap to start chatting");
                
                if (user.profilePic != null && !user.profilePic.isEmpty()) {
                    ImageCache.getInstance().loadBase64Image(
                        "user_" + user.userId, 
                        user.profilePic, 
                        ivAvatar, 
                        R.drawable.ic_profile
                    );
                } else {
                    ivAvatar.setImageResource(R.drawable.ic_profile);
                }
                
                itemView.setOnClickListener(v -> listener.onClick(user));
            }
        }
    }
}
