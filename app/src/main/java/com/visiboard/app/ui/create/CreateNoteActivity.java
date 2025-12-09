package com.visiboard.app.ui.create;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.visiboard.app.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CreateNoteActivity extends AppCompatActivity {

    private EditText etNoteContent;
    private CardView cardImagePreview;
    private ImageView ivPreview;
    private ImageButton btnRemoveImage;
    private Button btnPost;
    private com.google.android.material.button.MaterialButton btnAddImage;
    private ProgressBar progressBar;
    private ImageButton btnBack;

    private Uri selectedImageUri;
    private FusedLocationProviderClient fusedLocationClient;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;

    private final ActivityResultLauncher<String> pickImage = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    selectedImageUri = uri;
                    updateImagePreview();
                }
            }
    );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_note);

        // Initialize Firebase
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Initialize Views
        etNoteContent = findViewById(R.id.et_note_content);
        cardImagePreview = findViewById(R.id.card_image_preview);
        ivPreview = findViewById(R.id.iv_preview);
        btnRemoveImage = findViewById(R.id.btn_remove_image);
        btnPost = findViewById(R.id.btn_post);
        btnAddImage = findViewById(R.id.btn_add_image);
        progressBar = findViewById(R.id.progress_bar);
        btnBack = findViewById(R.id.btn_back);

        // Check for passed image URI (from Camera)
        if (getIntent().hasExtra("image_uri")) {
            String uriString = getIntent().getStringExtra("image_uri");
            if (uriString != null) {
                selectedImageUri = Uri.parse(uriString);
            }
        }

        updateImagePreview();
        setupListeners();
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());
        
        btnAddImage.setOnClickListener(v -> pickImage.launch("image/*"));
        
        btnRemoveImage.setOnClickListener(v -> {
            selectedImageUri = null;
            updateImagePreview();
        });

        // Aspect Ratio Toggle
        ivPreview.setOnClickListener(v -> {
            if (selectedImageUri == null) return;
            
            // Check if LayoutParams are of ConstraintLayout type
            if (ivPreview.getLayoutParams() instanceof androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) {
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams params = 
                    (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) ivPreview.getLayoutParams();
                
                String currentRatio = params.dimensionRatio;
                // Default handling if null
                if (currentRatio == null) currentRatio = "1:1";
                
                String nextRatio = "1:1";
                if ("1:1".equals(currentRatio)) nextRatio = "4:3";
                else if ("4:3".equals(currentRatio)) nextRatio = "16:9";
                else if ("16:9".equals(currentRatio)) nextRatio = "3:4";
                else if ("3:4".equals(currentRatio)) nextRatio = "9:16";
                else if ("9:16".equals(currentRatio)) nextRatio = "1:1";
                
                params.dimensionRatio = nextRatio;
                ivPreview.setLayoutParams(params);
                Toast.makeText(this, "Ratio: " + nextRatio, Toast.LENGTH_SHORT).show();
            }
        });

        btnPost.setOnClickListener(v -> postNote());
    }

    private void updateImagePreview() {
        if (selectedImageUri != null) {
            cardImagePreview.setVisibility(View.VISIBLE);
            btnAddImage.setVisibility(View.GONE);
            ivPreview.setImageURI(selectedImageUri);
        } else {
            cardImagePreview.setVisibility(View.GONE);
            btnAddImage.setVisibility(View.VISIBLE);
        }
    }

    private void postNote() {
        String content = etNoteContent.getText().toString().trim();
        if (content.isEmpty() && selectedImageUri == null) {
            Toast.makeText(this, "Please add some text or an image", Toast.LENGTH_SHORT).show();
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Location permission required to post note", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                if (selectedImageUri != null) {
                    processImageAndSaveNote(content, location);
                } else {
                    saveNoteToFirestore(content, location, null);
                }
            } else {
                setLoading(false);
                Toast.makeText(this, "Could not get current location", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void processImageAndSaveNote(String content, android.location.Location location) {
        setLoading(true);
        new Thread(() -> {
            try {
                // Resize and compress image
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(
                        getContentResolver().openInputStream(selectedImageUri));
                
                if (bitmap == null) {
                    runOnUiThread(() -> {
                        setLoading(false);
                        Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                // Scale down if too large (max 800px width/height to be safe for Firestore 1MB limit)
                int maxDimension = 800; 
                float scale = Math.min((float) maxDimension / bitmap.getWidth(), (float) maxDimension / bitmap.getHeight());
                
                if (scale < 1.0f) {
                    int newWidth = Math.round(bitmap.getWidth() * scale);
                    int newHeight = Math.round(bitmap.getHeight() * scale);
                    bitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
                }

                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                // Compress to JPEG, quality 70
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, baos);
                byte[] imageBytes = baos.toByteArray();
                
                String base64Image = android.util.Base64.encodeToString(imageBytes, android.util.Base64.DEFAULT);
                
                // Firestore document limit is 1MB (= 1,048,576 bytes). 
                // Base64 is ~1.33x size of bytes. 
                // If base64 length > ~1,000,000, it might fail or be too big along with other fields.
                if (base64Image.length() > 1000000) {
                     runOnUiThread(() -> {
                        setLoading(false);
                        Toast.makeText(this, "Image too large even after compression. Please choose a smaller image.", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                runOnUiThread(() -> saveNoteToFirestore(content, location, base64Image));
                
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    setLoading(false);
                    Toast.makeText(this, "Error processing image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void saveNoteToFirestore(String content, android.location.Location location, @Nullable String imageBase64) {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        db.collection("users").document(uid).get().addOnSuccessListener(userDoc -> {
            String userName = userDoc.getString("name");
            String userProfilePic = userDoc.getString("profilePic");

            Map<String, Object> noteMap = new HashMap<>();
            noteMap.put("userId", uid);
            noteMap.put("userName", userName);
            noteMap.put("userProfilePic", userProfilePic);
            noteMap.put("lat", location.getLatitude());
            noteMap.put("lon", location.getLongitude());
            noteMap.put("location", new GeoPoint(location.getLatitude(), location.getLongitude()));
            noteMap.put("note", content);
            noteMap.put("summary", content.length() > 100 ? content.substring(0, 100) + "..." : content);
            noteMap.put("timestamp", System.currentTimeMillis());
            noteMap.put("likeCount", 0);
            noteMap.put("likedBy", new ArrayList<String>());
            noteMap.put("commentsCount", 0);
            
            if (imageBase64 != null) {
                noteMap.put("imageBase64", imageBase64); // Storing Base64 directly
            }

            db.collection("notes").add(noteMap)
                    .addOnSuccessListener(docRef -> {
                        Toast.makeText(this, "Note posted!", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        setLoading(false);
                        Toast.makeText(this, "Failed to post note: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        }).addOnFailureListener(e -> {
            setLoading(false);
            Toast.makeText(this, "Failed to fetch user profile: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void setLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnPost.setEnabled(!isLoading);
        etNoteContent.setEnabled(!isLoading);
        btnBack.setEnabled(!isLoading);
    }
}
