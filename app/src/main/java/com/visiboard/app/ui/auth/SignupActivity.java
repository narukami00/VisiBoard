package com.visiboard.app.ui.auth;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.visiboard.app.MainActivity;
import com.visiboard.app.R;
import com.visiboard.app.utils.UiHelper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import android.util.Base64;

public class SignupActivity extends AppCompatActivity {

    private EditText nameInput, emailInput, passInput, confirmPassInput;
    private ImageView profilePic;
    private Button signupBtn;
    private ProgressBar signupProgressBar;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private String base64Image = "";

    private final int PICK_IMAGE = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        nameInput = findViewById(R.id.signupName);
        emailInput = findViewById(R.id.signupEmail);
        passInput = findViewById(R.id.signupPassword);
        confirmPassInput = findViewById(R.id.signupConfirmPassword);
        profilePic = findViewById(R.id.profileImage);
        signupBtn = findViewById(R.id.signupBtn);
        signupProgressBar = findViewById(R.id.signupProgressBar);

        setupDecoyBubbles();

        profilePic.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
            pickImage();
        });

        signupBtn.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
            registerUser();
        });
        
        findViewById(R.id.goToLogin).setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
            finish();
        });
    }

    private void setupDecoyBubbles() {
        android.view.View d1 = findViewById(R.id.bubble_decoy_s1);
        android.view.View d2 = findViewById(R.id.bubble_decoy_s2);
        
        android.view.View.OnClickListener hapticOnly = v -> 
            v.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
            
        if (d1 != null) d1.setOnClickListener(hapticOnly);
        if (d2 != null) d2.setOnClickListener(hapticOnly);
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri imgUri = data.getData();
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imgUri);
                profilePic.setImageBitmap(bitmap);

                // Convert to base64
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos);
                base64Image = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);

            } catch (IOException e) {
                e.printStackTrace();
                UiHelper.showError(findViewById(android.R.id.content), "Failed to load image");
            }
        }
    }

    private void registerUser() {
        String name = nameInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String pass = passInput.getText().toString().trim();
        String confirmPass = confirmPassInput.getText().toString().trim();

        // Validation
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email) || TextUtils.isEmpty(pass) || TextUtils.isEmpty(confirmPass)) {
            UiHelper.showWarning(findViewById(android.R.id.content), "Fill all fields");
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.setError("Enter a valid email");
            emailInput.requestFocus();
            return;
        }

        if (!pass.equals(confirmPass)) {
            confirmPassInput.setError("Passwords do not match");
            confirmPassInput.requestFocus();
            return;
        }

        if (pass.length() < 6) {
            passInput.setError("Password must be at least 6 characters");
            passInput.requestFocus();
            return;
        }

        // Firebase Auth signup
        signupProgressBar.setVisibility(android.view.View.VISIBLE);
        signupBtn.setEnabled(false);
        
        auth.createUserWithEmailAndPassword(email, pass)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        String uid = auth.getCurrentUser().getUid();

                        Map<String, Object> userMap = new HashMap<>();
                        userMap.put("name", name);
                        userMap.put("email", email);
                        userMap.put("currentTier", "None");
                        userMap.put("createdAt", System.currentTimeMillis());
                        if (!base64Image.isEmpty()) {
                            userMap.put("profilePic", base64Image);
                        }

                        db.collection("users").document(uid).set(userMap)
                                .addOnSuccessListener(unused -> {
                                    signupProgressBar.setVisibility(android.view.View.GONE);
                                    signupBtn.setEnabled(true);
                                    UiHelper.showSuccess(findViewById(android.R.id.content), "Account created!");
                                    startActivity(new Intent(this, MainActivity.class));
                                    finish();
                                })
                                .addOnFailureListener(e -> {
                                        signupProgressBar.setVisibility(android.view.View.GONE);
                                        signupBtn.setEnabled(true);
                                        UiHelper.showError(findViewById(android.R.id.content), "Firestore error: " + e.getMessage());
                                });
                    } else {
                        signupProgressBar.setVisibility(android.view.View.GONE);
                        signupBtn.setEnabled(true);
                        String error = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                        
                        // Provide user-friendly error messages
                        String errorMessage;
                        if (error.contains("already in use") || error.contains("email-already-in-use") || error.contains("ERROR_EMAIL_ALREADY_IN_USE")) {
                            errorMessage = "Email is already registered. Please login instead.";
                        } else if (error.contains("weak password") || error.contains("ERROR_WEAK_PASSWORD")) {
                            errorMessage = "Password is too weak. Use a stronger password.";
                        } else if (error.contains("badly formatted") || error.contains("invalid-email") || error.contains("ERROR_INVALID_EMAIL")) {
                            errorMessage = "Invalid email format.";
                        } else if (error.contains("network error") || error.contains("NETWORK_ERROR")) {
                            errorMessage = "Network error. Please check your connection.";
                        } else {
                            errorMessage = "Signup failed: " + error;
                        }
                        
                        UiHelper.showError(findViewById(android.R.id.content), errorMessage);
                    }
                });
    }
}
