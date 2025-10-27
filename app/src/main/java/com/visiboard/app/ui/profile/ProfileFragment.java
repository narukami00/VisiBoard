package com.visiboard.app.ui.profile;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.visiboard.app.R;
import com.visiboard.app.ui.auth.LoginActivity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class ProfileFragment extends Fragment {

    private CircleImageView profileImage;
    private TextView nameText, emailText, tvTotalNotes, tvTotalLikes, tvRecentNote, tvMilestone, tvMilestoneProgress;
    private Button logoutBtn;
    private ImageView ivTierIcon;
    private ProgressBar progressMilestone;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private final int PICK_IMAGE = 101;
    private String base64Image = "";

    private final int[] milestones = {100, 500, 1000, 5000, 10000};
    private final String[] milestoneTiers = {"Bronze", "Silver", "Gold", "Diamond", "Platinum"};
    private final int[] milestoneIcons = {
            R.drawable.ic_bronze,
            R.drawable.ic_silver,
            R.drawable.ic_gold,
            R.drawable.ic_diamond,
            R.drawable.ic_platinum
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        profileImage = view.findViewById(R.id.profileImage);
        nameText = view.findViewById(R.id.profileName);
        emailText = view.findViewById(R.id.profileEmail);
        tvTotalNotes = view.findViewById(R.id.tv_total_notes);
        tvTotalLikes = view.findViewById(R.id.tv_total_likes);
        tvRecentNote = view.findViewById(R.id.tv_recent_note);
        tvMilestone = view.findViewById(R.id.tv_milestone);
        tvMilestoneProgress = view.findViewById(R.id.tv_milestone_progress);
        ivTierIcon = view.findViewById(R.id.iv_tier_icon);
        progressMilestone = view.findViewById(R.id.progress_milestone);
        logoutBtn = view.findViewById(R.id.logoutBtn);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        loadUserData();
        loadUserStats();

        profileImage.setOnClickListener(v -> pickImage());
        logoutBtn.setOnClickListener(v -> logoutUser());

        return view;
    }

    private void loadUserData() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        emailText.setText(user.getEmail());

        String uid = user.getUid();
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String name = doc.getString("name");
                        nameText.setText(name != null ? name : "User");

                        String pic = doc.getString("profilePic");
                        if (pic != null && !pic.isEmpty()) {
                            try {
                                byte[] bytes = Base64.decode(pic, Base64.DEFAULT);
                                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                                profileImage.setImageBitmap(bitmap);
                            } catch (IllegalArgumentException e) {
                                e.printStackTrace();
                                Toast.makeText(getContext(), "Error loading profile picture", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });
    }

    private void loadUserStats() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        String uid = user.getUid();
        db.collection("users").document(uid).collection("notes")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    int totalNotes = querySnapshot.size();
                    tvTotalNotes.setText(String.valueOf(totalNotes));

                    if (!querySnapshot.isEmpty()) {
                        List<com.google.firebase.firestore.DocumentSnapshot> docs = querySnapshot.getDocuments();
                        String recent = docs.get(docs.size() - 1).getString("note");
                        tvRecentNote.setText("Recent Note: " + (recent.length() > 30 ? recent.substring(0, 30) + "..." : recent));
                    } else {
                        tvRecentNote.setText("No notes yet.");
                    }

                    // Determine tier and progress
                    int tierIndex = -1;
                    for (int i = 0; i < milestones.length; i++) {
                        if (totalNotes >= milestones[i]) tierIndex = i;
                    }

                    String currentTier;

                    if (tierIndex == -1) {
                        currentTier = "None";
                        tvMilestone.setText("No Tier Yet");
                        ivTierIcon.setImageResource(R.drawable.ic_default_tier);
                        progressMilestone.setMax(milestones[0]);
                        progressMilestone.setProgress(totalNotes);
                        tvMilestoneProgress.setText(totalNotes + " / " + milestones[0] + " to Bronze");
                    } else if (tierIndex < milestones.length - 1) {
                        currentTier = milestoneTiers[tierIndex];
                        tvMilestone.setText("Current Tier: " + currentTier);
                        ivTierIcon.setImageResource(milestoneIcons[tierIndex]);
                        int nextGoal = milestones[tierIndex + 1];
                        progressMilestone.setMax(nextGoal);
                        progressMilestone.setProgress(totalNotes);
                        tvMilestoneProgress.setText(totalNotes + " / " + nextGoal + " to " + milestoneTiers[tierIndex + 1]);
                    } else {
                        currentTier = "Platinum";
                        tvMilestone.setText("Max Tier: Platinum");
                        ivTierIcon.setImageResource(milestoneIcons[milestoneIcons.length - 1]);
                        progressMilestone.setMax(milestones[milestones.length - 1]);
                        progressMilestone.setProgress(milestones[milestones.length - 1]);
                        tvMilestoneProgress.setText("Maxed Out");
                    }

                    tvTotalLikes.setText("0"); // Placeholder

                    // 🔹 UPDATE TIER IN DATABASE
                    db.collection("users").document(uid)
                            .update("currentTier", currentTier)
                            .addOnFailureListener(e ->
                                    Toast.makeText(getContext(), "Tier update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                            );
                });
    }


    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_IMAGE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE && resultCode == getActivity().RESULT_OK && data != null) {
            Uri imgUri = data.getData();
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getActivity().getContentResolver(), imgUri);
                profileImage.setImageBitmap(bitmap);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos);
                base64Image = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);

                updateProfilePic(base64Image);
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(getContext(), "Failed to load image", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updateProfilePic(String base64Image) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        String uid = user.getUid();
        db.collection("users").document(uid).update("profilePic", base64Image)
                .addOnSuccessListener(unused -> Toast.makeText(getContext(), "Profile picture updated", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(getContext(), "Failed to update: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void logoutUser() {
        auth.signOut();
        startActivity(new Intent(getActivity(), LoginActivity.class));
        getActivity().finish();
    }
}
