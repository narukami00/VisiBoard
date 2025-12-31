package com.visiboard.app.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.visiboard.app.R;
import com.visiboard.app.ui.auth.LoginActivity;

public class SettingsActivity extends AppCompatActivity {

    private SwitchMaterial switchPrivateAccount;
    private SwitchMaterial switchGhostMode;
    private TextView tvPrivateHint;
    private LinearLayout btnFollowRequests;
    private LinearLayout btnHiddenNotes;
    private LinearLayout btnSavedNotes;
    private LinearLayout btnBlockedUsers;
    private LinearLayout btnHelp;
    private LinearLayout btnAbout;
    private TextView tvRequestsCount;
    private LinearLayout btnLogout;
    private androidx.appcompat.widget.Toolbar toolbar;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        if (auth.getCurrentUser() == null) {
            finish();
            return;
        }

        // Init Views
        switchPrivateAccount = findViewById(R.id.switch_private_account);
        switchGhostMode = findViewById(R.id.switch_ghost_mode);
        tvPrivateHint = findViewById(R.id.tv_private_hint);
        btnFollowRequests = findViewById(R.id.btn_follow_requests);
        btnHiddenNotes = findViewById(R.id.btn_hidden_notes);
        btnSavedNotes = findViewById(R.id.btn_saved_notes);
        btnBlockedUsers = findViewById(R.id.btn_blocked_users);
        btnHelp = findViewById(R.id.btn_help);
        btnAbout = findViewById(R.id.btn_about);
        tvRequestsCount = findViewById(R.id.tv_requests_count);
        btnLogout = findViewById(R.id.btn_logout);
        toolbar = findViewById(R.id.toolbar);

        setupListeners();
        loadUserSettings();
        loadRequestsCount();
    }

    private void setupListeners() {
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        btnLogout.setOnClickListener(v -> {
            auth.signOut();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });
        
        btnFollowRequests.setOnClickListener(v -> {
            startActivity(new Intent(this, FollowRequestsActivity.class));
        });

        btnHiddenNotes.setOnClickListener(v -> {
            startActivity(new Intent(this, HiddenNotesActivity.class));
        });

        btnSavedNotes.setOnClickListener(v -> {
            startActivity(new Intent(this, SavedNotesActivity.class));
        });

        btnBlockedUsers.setOnClickListener(v -> {
            startActivity(new Intent(this, BlockedUsersActivity.class));
        });

        btnHelp.setOnClickListener(v -> {
            startActivity(new Intent(this, HelpActivity.class));
        });

        btnAbout.setOnClickListener(v -> {
            startActivity(new Intent(this, AboutActivity.class));
        });

        switchPrivateAccount.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updatePrivacySetting(isChecked);
        });

        switchGhostMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateGhostModeSetting(isChecked);
        });
    }

    private void loadUserSettings() {
        String userId = auth.getCurrentUser().getUid();
        // Disable listeners temporarily to prevent firing update on read
        switchPrivateAccount.setOnCheckedChangeListener(null);
        switchGhostMode.setOnCheckedChangeListener(null);
        
        db.collection("users").document(userId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Boolean isPrivate = documentSnapshot.getBoolean("isPrivate");
                        switchPrivateAccount.setChecked(isPrivate != null && isPrivate);
                        
                        Boolean isGhostMode = documentSnapshot.getBoolean("isGhostMode");
                        switchGhostMode.setChecked(isGhostMode != null && isGhostMode);
                    }
                    // Re-enable listeners
                    switchPrivateAccount.setOnCheckedChangeListener((buttonView, isChecked) -> updatePrivacySetting(isChecked));
                    switchGhostMode.setOnCheckedChangeListener((buttonView, isChecked) -> updateGhostModeSetting(isChecked));
                });
    }

    private void updateGhostModeSetting(boolean isGhostMode) {
        String userId = auth.getCurrentUser().getUid();
        
        db.collection("users").document(userId)
                .update("isGhostMode", isGhostMode)
                .addOnSuccessListener(aVoid -> {
                    com.visiboard.app.utils.UiHelper.showSuccess(findViewById(android.R.id.content), isGhostMode ? "Ghost Mode enabled" : "Ghost Mode disabled");
                })
                .addOnFailureListener(e -> {
                    com.visiboard.app.utils.UiHelper.showError(findViewById(android.R.id.content), "Failed to update Ghost Mode");
                    // Revert switch
                    switchGhostMode.setOnCheckedChangeListener(null);
                    switchGhostMode.setChecked(!isGhostMode); 
                    switchGhostMode.setOnCheckedChangeListener((buttonView, isChecked) -> updateGhostModeSetting(isChecked));
                });
    }

    private void updatePrivacySetting(boolean isPrivate) {
        String userId = auth.getCurrentUser().getUid();
        
        // 1. Update User Profile
        db.collection("users").document(userId)
                .update("isPrivate", isPrivate)
                .addOnSuccessListener(aVoid -> {
                    if (isPrivate) {
                        tvPrivateHint.setText("Only people you approve can see your notes");
                    } else {
                        tvPrivateHint.setText("Anyone can see your public notes");
                    }
                    
                    // 2. Batch Update all User's Notes to reflect new privacy
                    updateUserNotesPrivacy(userId, isPrivate);
                })
                .addOnFailureListener(e -> {
                    com.visiboard.app.utils.UiHelper.showError(findViewById(android.R.id.content), "Failed to update setting");
                    // Revert switch
                    switchPrivateAccount.setOnCheckedChangeListener(null);
                    switchPrivateAccount.setChecked(!isPrivate); 
                    switchPrivateAccount.setOnCheckedChangeListener((buttonView, isChecked) -> updatePrivacySetting(isChecked));
                });
    }

    private void updateUserNotesPrivacy(String userId, boolean isPrivate) {
        // Query all notes by this user
        db.collection("notes")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) return;

                    com.google.firebase.firestore.WriteBatch batch = db.batch();
                    int count = 0;
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot) {
                        batch.update(doc.getReference(), "isOwnerPrivate", isPrivate);
                        count++;
                        // Batch limit is 500
                        if (count % 490 == 0) {
                            batch.commit();
                            batch = db.batch();
                        }
                    }
                    // Commit remaining
                    if (count > 0) {
                        batch.commit().addOnSuccessListener(aVoid -> 
                            com.visiboard.app.utils.UiHelper.showSuccess(findViewById(android.R.id.content), "Privacy settings applied to existing notes")
                        );
                    }
                });
    }

    private void loadRequestsCount() {
        String userId = auth.getCurrentUser().getUid();
        db.collection("users").document(userId).collection("follow_requests")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    int count = queryDocumentSnapshots.size();
                    if (count > 0) {
                        tvRequestsCount.setText(String.valueOf(count));
                        tvRequestsCount.setVisibility(View.VISIBLE);
                    } else {
                        tvRequestsCount.setVisibility(View.GONE);
                    }
                });
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        loadRequestsCount(); // Refresh count on return
    }
}
