package com.visiboard.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.visiboard.app.MainActivity;
import com.visiboard.app.R;

import android.app.Dialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private EditText emailInput, passwordInput;
    private Button loginBtn;
    private TextView goToSignup, forgotPassword;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance();
        Log.d(TAG, "FirebaseAuth instance: " + (auth != null ? "Initialized" : "NULL"));

        // Find views
        emailInput = findViewById(R.id.loginEmail);
        passwordInput = findViewById(R.id.loginPassword);
        loginBtn = findViewById(R.id.loginBtn);
        goToSignup = findViewById(R.id.goToSignup);
        forgotPassword = findViewById(R.id.forgotPassword);

        // If user is already logged in, go straight to MainActivity
        if (auth.getCurrentUser() != null) {
            goToMain();
        }

        // Set click listeners
        loginBtn.setOnClickListener(v -> loginUser());
        goToSignup.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, SignupActivity.class))
        );
        forgotPassword.setOnClickListener(v -> showForgotPasswordDialog());
    }

    private void loginUser() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        // Basic validation
        if (TextUtils.isEmpty(email)) {
            emailInput.setError("Email is required");
            emailInput.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            passwordInput.setError("Password is required");
            passwordInput.requestFocus();
            return;
        }

        if (password.length() < 6) {
            passwordInput.setError("Password must be at least 6 characters");
            passwordInput.requestFocus();
            return;
        }

        Log.d(TAG, "Attempting to login with email: " + email);
        
        // Firebase login
        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Login successful for: " + email);
                        Toast.makeText(LoginActivity.this, "Login successful", Toast.LENGTH_SHORT).show();
                        goToMain();
                    } else {
                        String error = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                        Log.e(TAG, "Login failed: " + error);
                        Toast.makeText(LoginActivity.this, "Login failed: " + error,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void goToMain() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void showForgotPasswordDialog() {
        Dialog dialog = new Dialog(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_forgot_password, null);
        dialog.setContentView(dialogView);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        EditText etEmail = dialogView.findViewById(R.id.et_reset_email);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        Button btnVerify = dialogView.findViewById(R.id.btn_verify_email);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnVerify.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            
            if (TextUtils.isEmpty(email)) {
                etEmail.setError("Email is required");
                etEmail.requestFocus();
                return;
            }

            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.setError("Enter a valid email");
                etEmail.requestFocus();
                return;
            }

            // Send password reset email directly
            auth.sendPasswordResetEmail(email)
                    .addOnSuccessListener(aVoid -> {
                        dialog.dismiss();
                        Toast.makeText(this, 
                            "Password reset email sent! Check your inbox.", 
                            Toast.LENGTH_LONG).show();
                        Log.d(TAG, "Password reset email sent to: " + email);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Password reset failed: " + e.getMessage());
                        Toast.makeText(this, 
                            "Error: " + e.getMessage(), 
                            Toast.LENGTH_SHORT).show();
                    });
        });

        dialog.show();
    }


}
