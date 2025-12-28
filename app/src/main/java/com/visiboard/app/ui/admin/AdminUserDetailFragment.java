package com.visiboard.app.ui.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.firebase.firestore.FirebaseFirestore;
import com.visiboard.app.R;
import com.visiboard.app.data.ReportRepository;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AdminUserDetailFragment extends Fragment {

    private static final String ARG_USER_ID = "user_id";
    
    private TextView tvUserName, tvUserEmail, tvUserId, tvBanStatus, tvRestrictStatus;
    private TextView tvNotesCount, tvFollowersCount, tvJoinedDate;
    private ImageView ivUserAvatar, btnMenu, btnBack;
    
    private FirebaseFirestore db;
    private ReportRepository repository;
    private String userId;
    
    // User state
    private boolean isBanned = false;
    private boolean isRestricted = false;

    public static AdminUserDetailFragment newInstance(String userId) {
        AdminUserDetailFragment fragment = new AdminUserDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG_USER_ID, userId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_user_detail, container, false);
        
        db = FirebaseFirestore.getInstance();
        repository = new ReportRepository();
        if (getArguments() != null) {
            userId = getArguments().getString(ARG_USER_ID);
        }

        initializeViews(view);
        fetchUserDetails();

        return view;
    }

    private void initializeViews(View view) {
        tvUserName = view.findViewById(R.id.tvUserName);
        tvUserEmail = view.findViewById(R.id.tvUserEmail);
        tvUserId = view.findViewById(R.id.tvUserId);
        tvBanStatus = view.findViewById(R.id.tvBanStatus);
        tvRestrictStatus = view.findViewById(R.id.tvRestrictStatus);
        tvNotesCount = view.findViewById(R.id.tvNotesCount);
        tvFollowersCount = view.findViewById(R.id.tvFollowersCount);
        tvJoinedDate = view.findViewById(R.id.tvJoinedDate);
        ivUserAvatar = view.findViewById(R.id.ivUserAvatar);
        btnMenu = view.findViewById(R.id.btnMenu);
        btnBack = view.findViewById(R.id.btnBack);

        btnMenu.setOnClickListener(v -> showAdminActionsBottomSheet());
        btnBack.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());
    }

    private void fetchUserDetails() {
        if (userId == null) return;

        db.collection("users").document(userId).get()
            .addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    String name = doc.getString("name");
                    String email = doc.getString("email");
                    String profilePic = doc.getString("profilePic"); // Correct field name
                    Boolean banned = doc.getBoolean("banned");
                    Boolean restricted = doc.getBoolean("restricted");
                    Long createdAt = doc.getLong("createdAt");
                    Long followersCount = doc.getLong("followersCount");

                    tvUserName.setText(name != null ? name : "Unknown");
                    tvUserEmail.setText(email != null ? email : "-");
                    tvUserId.setText("ID: " + userId.substring(0, Math.min(userId.length(), 12)) + "...");
                    
                    isBanned = banned != null && banned;
                    isRestricted = restricted != null && restricted;
                    
                    updateStatusUI();
                    
                    if (followersCount != null) {
                        tvFollowersCount.setText(String.valueOf(followersCount));
                    }
                    
                    if (createdAt != null) {
                        SimpleDateFormat sdf = new SimpleDateFormat("MMM yyyy", Locale.getDefault());
                        tvJoinedDate.setText(sdf.format(new Date(createdAt)));
                    }

                    // Load avatar
                    if (profilePic != null && !profilePic.isEmpty()) {
                        if (profilePic.startsWith("http")) {
                            Glide.with(this).load(profilePic).placeholder(R.drawable.ic_person).circleCrop().into(ivUserAvatar);
                        } else {
                            try {
                                String base64Image = profilePic;
                                if (base64Image.startsWith("data:image")) {
                                    if (base64Image.contains(",")) {
                                        base64Image = base64Image.split(",")[1];
                                    }
                                }
                                byte[] decoded = android.util.Base64.decode(base64Image, android.util.Base64.DEFAULT);
                                Glide.with(this).load(decoded).placeholder(R.drawable.ic_person).circleCrop().into(ivUserAvatar);
                            } catch (Exception e) {
                                ivUserAvatar.setImageResource(R.drawable.ic_person);
                            }
                        }
                    }
                    
                    // Fetch notes count and list
                    fetchNotesCountAndList();
                }
            })
            .addOnFailureListener(e -> Toast.makeText(getContext(), "Error loading user", Toast.LENGTH_SHORT).show());
    }
    
    private void updateStatusUI() {
        if (isBanned) {
            tvBanStatus.setText("Banned");
            tvBanStatus.setTextColor(getResources().getColor(R.color.error, null));
        } else {
            tvBanStatus.setText("Active");
            tvBanStatus.setTextColor(getResources().getColor(R.color.accent, null));
        }
        
        if (isRestricted) {
            tvRestrictStatus.setText("Restricted");
            tvRestrictStatus.setTextColor(getResources().getColor(R.color.warning, null));
        } else {
            tvRestrictStatus.setText("Not Restricted");
            tvRestrictStatus.setTextColor(getResources().getColor(R.color.accent, null));
        }
    }
    
    private void fetchNotesCountAndList() {
        db.collection("notes").whereEqualTo("userId", userId).get()
            .addOnSuccessListener(snapshots -> {
                tvNotesCount.setText(String.valueOf(snapshots.size()));
                
                // Show/hide notes list
                androidx.recyclerview.widget.RecyclerView rvUserNotes = getView().findViewById(R.id.rvUserNotes);
                TextView tvNoNotes = getView().findViewById(R.id.tvNoNotes);
                
                if (rvUserNotes != null && tvNoNotes != null) {
                    if (snapshots.isEmpty()) {
                        rvUserNotes.setVisibility(android.view.View.GONE);
                        tvNoNotes.setVisibility(android.view.View.VISIBLE);
                    } else {
                        rvUserNotes.setVisibility(android.view.View.VISIBLE);
                        tvNoNotes.setVisibility(android.view.View.GONE);
                        
                        // Use NearbyNote and RecentNotesAdapter like AdminProfileFragment
                        java.util.List<com.visiboard.app.data.NearbyNote> notes = new java.util.ArrayList<>();
                        for (com.google.firebase.firestore.QueryDocumentSnapshot doc : snapshots) {
                            com.visiboard.app.data.NearbyNote note = new com.visiboard.app.data.NearbyNote();
                            note.setId(doc.getId());
                            
                            String noteText = doc.getString("text");
                            if (noteText == null) noteText = doc.getString("note");
                            note.setText(noteText);
                            
                            String summary = doc.getString("summary");
                            if (summary == null && noteText != null && noteText.length() > 100) {
                                summary = noteText.substring(0, 100) + "...";
                            }
                            note.setSummary(summary);
                            
                            note.setUserId(userId);
                            note.setImageBase64(doc.getString("imageBase64"));
                            
                            Long timestamp = doc.getLong("timestamp");
                            note.setTimestamp(timestamp != null ? timestamp : 0);
                            
                            Long likeCount = doc.getLong("likeCount");
                            if (likeCount == null) likeCount = doc.getLong("likesCount");
                            note.setLikesCount(likeCount != null ? likeCount.intValue() : 0);
                            
                            Long commentsCount = doc.getLong("commentsCount");
                            note.setCommentsCount(commentsCount != null ? commentsCount.intValue() : 0);
                            
                            notes.add(note);
                        }
                        
                        notes.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
                        
                        rvUserNotes.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(getContext()));
                        com.visiboard.app.ui.profile.RecentNotesAdapter adapter = new com.visiboard.app.ui.profile.RecentNotesAdapter(null);
                        adapter.setNotes(notes);
                        rvUserNotes.setAdapter(adapter);
                    }
                }
            });
    }

    private void showAdminActionsBottomSheet() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(requireContext());
        View bottomSheetView = LayoutInflater.from(getContext()).inflate(R.layout.bottom_sheet_admin_user_actions, null);
        
        TextView actionWarn = bottomSheetView.findViewById(R.id.action_warn);
        TextView actionRestrict = bottomSheetView.findViewById(R.id.action_restrict);
        TextView actionBan = bottomSheetView.findViewById(R.id.action_ban);
        
        // Update text based on current state
        actionRestrict.setText(isRestricted ? "Remove Restriction" : "Restrict Account");
        actionBan.setText(isBanned ? "Remove Ban" : "Ban User");
        
        actionWarn.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            warnUser();
        });

        actionRestrict.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            if (isRestricted) {
                removeRestriction();
            } else {
                showDatePickerForAction("restrict");
            }
        });

        actionBan.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            if (isBanned) {
                removeBan();
            } else {
                showDatePickerForAction("ban");
            }
        });

        bottomSheetDialog.setContentView(bottomSheetView);
        bottomSheetDialog.show();
    }
    
    private void warnUser() {
        repository.updateUserStatus(userId, "warned", true, new ReportRepository.OnActionListener() {
            @Override
            public void onSuccess() {
                repository.notifyUser(userId, "You have been warned for violating community guidelines.", "admin");
                Toast.makeText(getContext(), "Warning sent", Toast.LENGTH_SHORT).show();
            }
            @Override
            public void onFailure(String error) {
                Toast.makeText(getContext(), "Failed: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void showDatePickerForAction(String actionType) {
        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select " + (actionType.equals("ban") ? "Ban" : "Restriction") + " Expiry Date")
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds() + (7L * 24 * 60 * 60 * 1000))
            .build();
        
        datePicker.addOnPositiveButtonClickListener(selection -> {
            if (actionType.equals("ban")) {
                banUser(selection);
            } else {
                restrictUser(selection);
            }
        });
        
        datePicker.show(getChildFragmentManager(), "DATE_PICKER");
    }
    
    private void banUser(long expiryDate) {
        db.collection("users").document(userId)
            .update("banned", true, "banExpiryDate", expiryDate)
            .addOnSuccessListener(aVoid -> {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                String dateStr = sdf.format(new Date(expiryDate));
                Toast.makeText(getContext(), "User banned until " + dateStr, Toast.LENGTH_SHORT).show();
                repository.notifyUser(userId, "You have been banned until " + dateStr, "admin");
                isBanned = true;
                updateStatusUI();
            })
            .addOnFailureListener(e -> Toast.makeText(getContext(), "Failed to ban", Toast.LENGTH_SHORT).show());
    }
    
    private void removeBan() {
        db.collection("users").document(userId)
            .update("banned", false)
            .addOnSuccessListener(aVoid -> {
                Toast.makeText(getContext(), "Ban removed", Toast.LENGTH_SHORT).show();
                repository.notifyUser(userId, "Your ban has been lifted.", "admin");
                isBanned = false;
                updateStatusUI();
            })
            .addOnFailureListener(e -> Toast.makeText(getContext(), "Failed", Toast.LENGTH_SHORT).show());
    }
    
    private void restrictUser(long expiryDate) {
        db.collection("users").document(userId)
            .update("restricted", true, "restrictionExpiryDate", expiryDate)
            .addOnSuccessListener(aVoid -> {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                String dateStr = sdf.format(new Date(expiryDate));
                Toast.makeText(getContext(), "User restricted until " + dateStr, Toast.LENGTH_SHORT).show();
                repository.notifyUser(userId, "You have been restricted from posting until " + dateStr, "admin");
                isRestricted = true;
                updateStatusUI();
            })
            .addOnFailureListener(e -> Toast.makeText(getContext(), "Failed to restrict", Toast.LENGTH_SHORT).show());
    }
    
    private void removeRestriction() {
        db.collection("users").document(userId)
            .update("restricted", false)
            .addOnSuccessListener(aVoid -> {
                Toast.makeText(getContext(), "Restriction removed", Toast.LENGTH_SHORT).show();
                repository.notifyUser(userId, "Your posting restriction has been lifted.", "admin");
                isRestricted = false;
                updateStatusUI();
            })
            .addOnFailureListener(e -> Toast.makeText(getContext(), "Failed", Toast.LENGTH_SHORT).show());
    }
}
