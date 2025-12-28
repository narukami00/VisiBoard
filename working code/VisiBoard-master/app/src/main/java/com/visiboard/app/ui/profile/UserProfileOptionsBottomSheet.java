package com.visiboard.app.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.visiboard.app.R;
import com.visiboard.app.ui.report.ReportBottomSheetFragment;

import java.util.HashMap;
import java.util.Map;

public class UserProfileOptionsBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_USER_ID = "userId";
    private static final String ARG_USER_NAME = "userName";

    private String userId;
    private String userName;

    public static UserProfileOptionsBottomSheet newInstance(String userId, String userName) {
        UserProfileOptionsBottomSheet fragment = new UserProfileOptionsBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_USER_ID, userId);
        args.putString(ARG_USER_NAME, userName);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            userId = getArguments().getString(ARG_USER_ID);
            userName = getArguments().getString(ARG_USER_NAME);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_user_profile_options, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        LinearLayout btnBlock = view.findViewById(R.id.btn_block_user);
        LinearLayout btnReport = view.findViewById(R.id.btn_report_user);

        btnBlock.setOnClickListener(v -> {
            showBlockConfirmation();
        });

        btnReport.setOnClickListener(v -> {
            dismiss();
            ReportBottomSheetFragment reportSheet = ReportBottomSheetFragment.newInstance(
                userId,
                userName,
                "USER",
                0, 0
            );
            reportSheet.show(getParentFragmentManager(), "ReportBottomSheet");
        });
    }

    private void showBlockConfirmation() {
        dismiss();
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Block " + userName + "?")
            .setMessage("They won't be notified. You can unblock them anytime from Settings.")
            .setPositiveButton("Block", (d, w) -> blockUser())
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void blockUser() {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Map<String, Object> blockData = new HashMap<>();
        blockData.put("blockedAt", System.currentTimeMillis());

        // Check existing relationships
        db.collection("users").document(currentUserId).collection("following").document(userId).get()
            .addOnSuccessListener(followingDoc -> {
                db.collection("users").document(currentUserId).collection("followers").document(userId).get()
                    .addOnSuccessListener(followerDoc -> {
                        String relationship = "none";
                        if (followingDoc.exists() && followerDoc.exists()) {
                            relationship = "mutual";
                        } else if (followingDoc.exists()) {
                            relationship = "following";
                        } else if (followerDoc.exists()) {
                            relationship = "follower";
                        }
                        blockData.put("previousRelationship", relationship);

                        com.google.firebase.firestore.WriteBatch batch = db.batch();

                        // Add to blocked_users
                        batch.set(db.collection("users").document(currentUserId)
                            .collection("blocked_users").document(userId), blockData);

                        // Remove from MY following
                        if (followingDoc.exists()) {
                            batch.delete(db.collection("users").document(currentUserId)
                                .collection("following").document(userId));
                            batch.update(db.collection("users").document(currentUserId),
                                "followingCount", FieldValue.increment(-1));
                            batch.delete(db.collection("users").document(userId)
                                .collection("followers").document(currentUserId));
                            batch.update(db.collection("users").document(userId),
                                "followersCount", FieldValue.increment(-1));
                        }

                        // Remove from MY followers
                        if (followerDoc.exists()) {
                            batch.delete(db.collection("users").document(currentUserId)
                                .collection("followers").document(userId));
                            batch.update(db.collection("users").document(currentUserId),
                                "followersCount", FieldValue.increment(-1));
                            batch.delete(db.collection("users").document(userId)
                                .collection("following").document(currentUserId));
                            batch.update(db.collection("users").document(userId),
                                "followingCount", FieldValue.increment(-1));
                        }

                        // Delete pending follow requests
                        batch.delete(db.collection("users").document(currentUserId)
                            .collection("follow_requests").document(userId));
                        batch.delete(db.collection("users").document(userId)
                            .collection("follow_requests").document(currentUserId));

                        batch.commit()
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(getContext(), "User blocked", Toast.LENGTH_SHORT).show();
                                // Navigate back
                                if (getActivity() != null) {
                                    getActivity().onBackPressed();
                                }
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(getContext(), "Failed to block user", Toast.LENGTH_SHORT).show();
                            });
                    });
            });
    }
}
