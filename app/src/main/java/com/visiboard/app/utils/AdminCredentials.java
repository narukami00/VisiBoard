package com.visiboard.app.utils;

import com.google.firebase.firestore.FirebaseFirestore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class AdminCredentials {

    public interface ValidationCallback {
        void onResult(boolean isValid);
        void onError(String error);
    }

    /**
     * Validates admin credentials against Firebase Firestore.
     */
    public static void validateCredentials(String id, String password, ValidationCallback callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        db.collection("admin_config").document("credentials").get()
            .addOnSuccessListener(document -> {
                if (document.exists()) {
                    String storedId = document.getString("adminId");
                    String storedHash = document.getString("passwordHash");
                    
                    if (storedId == null || storedHash == null) {
                        callback.onError("Invalid admin configuration");
                        return;
                    }
                    
                    String inputHash = hashPassword(password);
                    boolean isValid = id.equals(storedId) && inputHash.equals(storedHash);
                    callback.onResult(isValid);
                } else {
                    callback.onError("Admin configuration not found");
                }
            })
            .addOnFailureListener(e -> {
                callback.onError("Failed to verify credentials: " + e.getMessage());
            });
    }

    /**
     * Hashes a password using SHA-256.
     */
    public static String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }
}
