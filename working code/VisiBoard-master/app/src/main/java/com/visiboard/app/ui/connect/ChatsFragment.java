package com.visiboard.app.ui.connect;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.visiboard.app.R;
import com.visiboard.app.utils.ImageCache;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ChatsFragment extends Fragment {

    private RecyclerView rvFollowing;
    private EditText etSearch;
    private ChatUserAdapter adapter;
    private FirebaseFirestore db;
    private String currentUserId;
    private List<ChatUser> allFollowing = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chats, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        db = FirebaseFirestore.getInstance();
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            currentUserId = auth.getCurrentUser().getUid();
        }

        rvFollowing = view.findViewById(R.id.rv_following);
        etSearch = view.findViewById(R.id.et_search_following);
        
        setupRecyclerView();
        setupSearch();
        loadFollowing();
    }

    private void setupRecyclerView() {
        rvFollowing.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ChatUserAdapter();
        rvFollowing.setAdapter(adapter);
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
        if (allFollowing == null) return;
        List<ChatUser> filtered = new ArrayList<>();
        String q = query.toLowerCase(Locale.ROOT).trim();
        for (ChatUser user : allFollowing) {
            if (user.name.toLowerCase(Locale.ROOT).contains(q)) {
                filtered.add(user);
            }
        }
        adapter.setUsers(filtered);
    }

    private void loadFollowing() {
        if (currentUserId == null) return;

        db.collection("users").document(currentUserId)
            .collection("following")
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                allFollowing.clear();
                for (DocumentSnapshot doc : queryDocumentSnapshots) {
                    ChatUser user = new ChatUser();
                    user.userId = doc.getId();
                    user.name = doc.getString("followedName");
                    user.profilePic = doc.getString("followedProfilePic");
                    allFollowing.add(user);
                }
                adapter.setUsers(allFollowing);
            })
            .addOnFailureListener(e -> Toast.makeText(getContext(), "Failed to load chats", Toast.LENGTH_SHORT).show());
    }

    // --- Models & Adapter ---

    private static class ChatUser {
        String userId;
        String name;
        String profilePic;
    }

    private class ChatUserAdapter extends RecyclerView.Adapter<ChatUserAdapter.ViewHolder> {
        private List<ChatUser> users = new ArrayList<>();

        public void setUsers(List<ChatUser> users) {
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
            ChatUser user = users.get(position);
            holder.tvName.setText(user.name);
            holder.tvStatus.setText("Tap to message");
            
            if (user.profilePic != null) {
                ImageCache.getInstance().loadBase64Image("user_" + user.userId, user.profilePic, holder.ivAvatar, R.drawable.ic_profile);
            } else {
                holder.ivAvatar.setImageResource(R.drawable.ic_profile);
            }

            holder.itemView.setOnClickListener(v -> {
                // Open Chat Logic - Reuse existing logic or trigger dialog
                // For now, implementing basic logic to show message dialog (copied from FeedFragment/MessageDialog logic conceptually)
                showMessageDialog(user.userId, user.name);
            });
        }

        @Override
        public int getItemCount() {
            return users.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvStatus;
            ImageView ivAvatar;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tv_name);
                tvStatus = itemView.findViewById(R.id.tv_status);
                ivAvatar = itemView.findViewById(R.id.iv_avatar);
            }
        }
    }
    
    private void showMessageDialog(String targetUserId, String targetName) {
        // Since we are in a fragment, we'll delegate to the MessageDialog Logic
        // For now, prompt the user or show a toast as "Chat not implemented fully yet" or invoke the existing dialog if accessible.
        // User said "messaging section in feed fragment". 
        // I will implement a quick dialog here or reuse logic.
        // To save time and keep it clean, I'll invoke the Messaging Dialog if I can access it.
        // It seems Messaging is handled via `com.visiboard.app.ui.feed.FeedFragment` logic usually.
        // But here I should start a new Activity or Dialog.
        // I'll leave it as a Toast for "Opening Chat..." for this step, as the user said "chats for now will be just your basic show following cards... realtime chatting later".
        Toast.makeText(getContext(), "Opening chat with " + targetName, Toast.LENGTH_SHORT).show();
        // TODO: Launch Message Activity or Dialog
    }
}
