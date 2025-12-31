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

    private String editNoteId;
    private boolean isImageRemoved = false;
    private boolean isRemote = false;
    private double remoteLat;
    private double remoteLon;

    private EditText etNoteContent;
    private CardView cardImagePreview;
    private ImageView ivPreview;
    private ImageButton btnRemoveImage;
    private ImageButton btnRotateImage;
    private float currentRotation = 0f;
    private android.graphics.Bitmap currentBitmap;
    private com.google.android.material.button.MaterialButton btnPost;
    private com.google.android.material.button.MaterialButton btnAddImage;
    private ProgressBar progressBar;
    private ImageButton btnClose;
    private android.widget.Spinner spinnerVisibility;
    private android.widget.TextView tvUserName;
    private de.hdodenhof.circleimageview.CircleImageView ivUserAvatar;

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
                    currentRotation = 0f;
                    currentBitmap = null;
                    ivPreview.setRotation(0);
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
        
        // Check if user is restricted before allowing note creation
        if (auth.getCurrentUser() != null) {
            checkRestrictionStatus(auth.getCurrentUser().getUid());
        }

        // Initialize Views
        etNoteContent = findViewById(R.id.et_note_content);
        cardImagePreview = findViewById(R.id.card_image_preview);
        ivPreview = findViewById(R.id.iv_preview);
        btnRemoveImage = findViewById(R.id.btn_remove_image);
        btnRotateImage = findViewById(R.id.btn_rotate_image);
        btnPost = findViewById(R.id.btn_post);
        btnAddImage = findViewById(R.id.btn_add_image);
        progressBar = findViewById(R.id.progress_bar);
        btnClose = findViewById(R.id.btn_close);
        spinnerVisibility = findViewById(R.id.spinner_visibility);
        tvUserName = findViewById(R.id.tv_user_name);
        ivUserAvatar = findViewById(R.id.iv_user_avatar);

        fetchUserInfo();

        // Setup Spinner
        String[] items = new String[]{"Public", "Followers", "Private"};
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items);
        spinnerVisibility.setAdapter(adapter);

        // Check for Remote Mode
        if (getIntent().getBooleanExtra("isRemote", false)) {
            isRemote = true;
            remoteLat = getIntent().getDoubleExtra("lat", 0.0);
            remoteLon = getIntent().getDoubleExtra("lon", 0.0);
            
            android.widget.TextView tvTitle = findViewById(R.id.tv_create_note_title); 
            if (tvTitle != null) tvTitle.setText("Post Remote Note");
            // btnPost.setText("Post Remote Note"); // Reverted per user request
            // Toast.makeText(this, "Creating Remote Note", Toast.LENGTH_SHORT).show();
        }

        // Check for passed image URI (from Camera)
        if (getIntent().hasExtra("image_uri")) {
            String uriString = getIntent().getStringExtra("image_uri");
            if (uriString != null) {
                selectedImageUri = Uri.parse(uriString);
            }
        }

        // Check for Edit Mode
        if (getIntent().hasExtra("edit_note_id")) {
            editNoteId = getIntent().getStringExtra("edit_note_id");
            String content = getIntent().getStringExtra("edit_content");
            String b64 = getIntent().getStringExtra("edit_image_base64");
            
            etNoteContent.setText(content);
            btnPost.setText("Update Note");
            
            if (b64 != null && !b64.isEmpty()) {
                try {
                    byte[] decodedString = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
                    android.graphics.Bitmap decodedByte = android.graphics.BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                    ivPreview.setImageBitmap(decodedByte);
                    currentBitmap = decodedByte;
                    cardImagePreview.setVisibility(View.VISIBLE);
                    btnAddImage.setVisibility(View.GONE);
                } catch (Exception e) { 
                    e.printStackTrace(); 
                }
            }
        }

        if (selectedImageUri != null) {
             updateImagePreview();
        }

        // Fetch Note Details if Editing (for Visibility and fresh data)
        if (editNoteId != null) {
            fetchNoteDetails(editNoteId);
        }
        
        setupListeners();
    }



    private void setupListeners() {
        btnClose.setOnClickListener(v -> finish());
        
        btnAddImage.setOnClickListener(v -> pickImage.launch("image/*"));
        
        btnRotateImage.setOnClickListener(v -> {
            currentRotation = (currentRotation + 90) % 360;
            ivPreview.animate().rotation(currentRotation).setDuration(200).start();
        });

        btnRemoveImage.setOnClickListener(v -> {
            selectedImageUri = null;
            isImageRemoved = true;
            currentRotation = 0f;
            currentBitmap = null;
            ivPreview.setRotation(0);
            cardImagePreview.setVisibility(View.GONE);
            btnAddImage.setVisibility(View.VISIBLE);
        });

        // Aspect Ratio Toggle
        ivPreview.setOnClickListener(v -> {
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
            // Toast.makeText(this, "Ratio: " + nextRatio, Toast.LENGTH_SHORT).show();
            }
        });

        btnPost.setOnClickListener(v -> postNote());
    }

    private void updateImagePreview() {
        if (selectedImageUri != null) {
            cardImagePreview.setVisibility(View.VISIBLE);
            btnAddImage.setVisibility(View.GONE);
            ivPreview.setImageURI(selectedImageUri);
            isImageRemoved = false; // Reset if new image picked
            btnRotateImage.setVisibility(View.VISIBLE);
        } else {
            // Don't hide if editing and we have existing image logic handled in onCreate
            // but here updateImagePreview is called when selectedImageUri changes.
            // If selectedImageUri is null, we usually hide.
            // In onCreate, we set Visibility manually.
            // This method is called from activity result.
            cardImagePreview.setVisibility(View.GONE);
            btnAddImage.setVisibility(View.VISIBLE);
        }
    }

    private void postNote() {
        String content = etNoteContent.getText().toString().trim();
        // Relax check for Edit Mode: might keep existing image
        boolean hasExistingImage = (editNoteId != null && !isImageRemoved && cardImagePreview.getVisibility() == View.VISIBLE);
        boolean isRotated = currentRotation % 360 != 0;
        
        if (content.isEmpty() && selectedImageUri == null && !hasExistingImage) {
            com.visiboard.app.utils.UiHelper.showWarning(findViewById(android.R.id.content), "Please add some text or an image");
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            com.visiboard.app.utils.UiHelper.showWarning(findViewById(android.R.id.content), "Location permission required to post note");
            return;
        }

        setLoading(true);

        if (editNoteId != null) {
            // Update Flow
            if (selectedImageUri != null || (currentBitmap != null && isRotated)) {
                 processImageAndSaveNote(content, null); 
            } else {
                 int w = 0;
                 int h = 0;
                 if (currentBitmap != null) {
                    w = currentBitmap.getWidth();
                    h = currentBitmap.getHeight();
                 }
                 saveNoteToFirestore(content, null, null, w, h);
            }
        } else {
            // Create Flow
            if (isRemote) {
                // Use remote location
                android.location.Location location = new android.location.Location("remote");
                location.setLatitude(remoteLat);
                location.setLongitude(remoteLon);
                
                if (selectedImageUri != null) {
                    processImageAndSaveNote(content, location);
                } else {
                    saveNoteToFirestore(content, location, null, 0, 0);
                }
            } else {
                // Use GPS
                fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                    if (location != null) {
                        if (selectedImageUri != null) {
                            processImageAndSaveNote(content, location);
                        } else {
                            saveNoteToFirestore(content, location, null, 0, 0);
                        }
                    } else {
                        setLoading(false);
                        com.visiboard.app.utils.UiHelper.showError(findViewById(android.R.id.content), "Could not get current location");
                    }
                });
            }
        }
    }

    private void processImageAndSaveNote(String content, @Nullable android.location.Location location) {
        setLoading(true);
        new Thread(() -> {
            try {
                android.graphics.Bitmap bitmap = null;
                
                if (selectedImageUri != null) {
                     bitmap = android.graphics.BitmapFactory.decodeStream(
                        getContentResolver().openInputStream(selectedImageUri));
                } else if (currentBitmap != null) {
                    bitmap = currentBitmap;
                }
                
                if (bitmap == null) {
                    runOnUiThread(() -> {
                        setLoading(false);
                        com.visiboard.app.utils.UiHelper.showError(findViewById(android.R.id.content), "Failed to load image");
                    });
                    return;
                }

                // Apply Rotation
                if (currentRotation % 360 != 0) {
                    android.graphics.Matrix matrix = new android.graphics.Matrix();
                    matrix.postRotate(currentRotation);
                    bitmap = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                }

                // Scale down if too large (max 800px width/height)
                int maxDimension = 800; 
                float scale = Math.min((float) maxDimension / bitmap.getWidth(), (float) maxDimension / bitmap.getHeight());
                
                if (scale < 1.0f) {
                    int newWidth = Math.round(bitmap.getWidth() * scale);
                    int newHeight = Math.round(bitmap.getHeight() * scale);
                    bitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
                }

                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, baos);
                byte[] imageBytes = baos.toByteArray();
                
                String base64Image = android.util.Base64.encodeToString(imageBytes, android.util.Base64.DEFAULT);
                int finalWidth = bitmap.getWidth();
                int finalHeight = bitmap.getHeight();
                
                if (base64Image.length() > 1000000) {
                     runOnUiThread(() -> {
                        setLoading(false);
                        com.visiboard.app.utils.UiHelper.showWarning(findViewById(android.R.id.content), "Image too large. Please choose a smaller image.");
                    });
                    return;
                }

                runOnUiThread(() -> saveNoteToFirestore(content, location, base64Image, finalWidth, finalHeight));
                
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    setLoading(false);
                    com.visiboard.app.utils.UiHelper.showError(findViewById(android.R.id.content), "Error processing image: " + e.getMessage());
                });
            }
        }).start();
    }

    private void saveNoteToFirestore(String content, @Nullable android.location.Location location, @Nullable String imageBase64, int width, int height) {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        if (editNoteId != null) {
            // UPDATE EXISTING NOTE
            Map<String, Object> updates = new HashMap<>();
            updates.put("note", content);
            updates.put("summary", content.length() > 100 ? content.substring(0, 100) + "..." : content);
            updates.put("note", content);
            updates.put("summary", content.length() > 100 ? content.substring(0, 100) + "..." : content);
            updates.put("text", content); // Ensure both fields updated just in case
            updates.put("visibility", spinnerVisibility.getSelectedItem().toString().toLowerCase());
            
            if (imageBase64 != null) {
                updates.put("imageBase64", imageBase64);
                updates.put("imageWidth", width);
                updates.put("imageHeight", height);
            } else if (isImageRemoved) {
                updates.put("imageBase64", com.google.firebase.firestore.FieldValue.delete());
                updates.put("imageWidth", com.google.firebase.firestore.FieldValue.delete());
                updates.put("imageHeight", com.google.firebase.firestore.FieldValue.delete());
            } else {
                if (width > 0 && height > 0) {
                    updates.put("imageWidth", width);
                    updates.put("imageHeight", height);
                }
            }

            db.collection("notes").document(editNoteId).update(updates)
                .addOnSuccessListener(aVoid -> {
                    com.visiboard.app.utils.UiHelper.showSuccess(findViewById(android.R.id.content), "Note updated!");
                    finish();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    com.visiboard.app.utils.UiHelper.showError(findViewById(android.R.id.content), "Failed to update note: " + e.getMessage());
                });
                
        } else {
            // CREATE NEW NOTE
            if (location == null) { // Should not happen for new notes
                 setLoading(false);
                 return;
            }
            
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
                noteMap.put("text", content); // Add text field for consistency
                noteMap.put("summary", content.length() > 100 ? content.substring(0, 100) + "..." : content);
                noteMap.put("timestamp", System.currentTimeMillis());
                noteMap.put("likeCount", 0);
                noteMap.put("likedBy", new ArrayList<String>());
                noteMap.put("likeCount", 0);
                noteMap.put("likedBy", new ArrayList<String>());
                noteMap.put("commentsCount", 0);
                noteMap.put("visibility", spinnerVisibility.getSelectedItem().toString().toLowerCase());
                noteMap.put("allowComments", true); // Default: comments enabled
                
                if (isRemote) {
                    noteMap.put("isVirtual", true);
                }
                
                if (imageBase64 != null) {
                    noteMap.put("imageBase64", imageBase64);
                    noteMap.put("imageWidth", width);
                    noteMap.put("imageHeight", height);
                }
    
                db.collection("notes").add(noteMap)
                        .addOnSuccessListener(docRef -> {
                            com.visiboard.app.utils.UiHelper.showSuccess(findViewById(android.R.id.content), "Note posted successfully!");
                            setResult(RESULT_OK);
                            finish();
                        })
                        .addOnFailureListener(e -> {
                            setLoading(false);
                            com.visiboard.app.utils.UiHelper.showError(findViewById(android.R.id.content), "Failed to post note: " + e.getMessage());
                        });
            }).addOnFailureListener(e -> {
                setLoading(false);
                com.visiboard.app.utils.UiHelper.showError(findViewById(android.R.id.content), "Failed to fetch user profile: " + e.getMessage());
            });
        }
    }

    private void setLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnPost.setEnabled(!isLoading);
        etNoteContent.setEnabled(!isLoading);
        btnClose.setEnabled(!isLoading);
    }

    private void fetchUserInfo() {
        if (auth.getCurrentUser() != null) {
            String uid = auth.getCurrentUser().getUid();
            db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    String name = doc.getString("name");
                    String profilePic = doc.getString("profilePic");
                    
                    tvUserName.setText(name != null ? name : "User");
                    
                    if (profilePic != null && !profilePic.isEmpty()) {
                        byte[] decodedString = android.util.Base64.decode(profilePic, android.util.Base64.DEFAULT);
                        android.graphics.Bitmap decodedByte = android.graphics.BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                        ivUserAvatar.setImageBitmap(decodedByte);
                    }
                }
            });
        }
    }

    private void fetchNoteDetails(String noteId) {
        db.collection("notes").document(noteId).get()
            .addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    // Set Visibility Spinner
                    String visibility = doc.getString("visibility");
                    if (visibility != null) {
                        if (visibility.equalsIgnoreCase("followers")) {
                            spinnerVisibility.setSelection(1);
                        } else if (visibility.equalsIgnoreCase("private")) {
                            spinnerVisibility.setSelection(2);
                        } else {
                            spinnerVisibility.setSelection(0); // Public default
                        }
                    }
                }
            })
            .addOnFailureListener(e -> {
                com.visiboard.app.utils.UiHelper.showError(findViewById(android.R.id.content), "Failed to load note details");
            });
    }
    
    private void checkRestrictionStatus(String userId) {
        db.collection("users").document(userId).get()
            .addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    Boolean restricted = doc.getBoolean("restricted");
                    Long restrictionExpiryDate = doc.getLong("restrictionExpiryDate");
                    
                    if (restricted != null && restricted) {
                        long now = System.currentTimeMillis();
                        
                        if (restrictionExpiryDate != null && restrictionExpiryDate > now) {
                            // User is restricted and restriction hasn't expired
                            showRestrictedDialog(restrictionExpiryDate);
                        } else if (restrictionExpiryDate != null && restrictionExpiryDate <= now) {
                            // Restriction has expired, auto-lift
                            db.collection("users").document(userId)
                                .update("restricted", false)
                                .addOnSuccessListener(aVoid -> {
                                    // Restriction lifted, allow creation
                                });
                        } else {
                            // Permanent restriction (no expiry date)
                            showRestrictedDialog(0);
                        }
                    }
                }
            });
    }
    
    private void showRestrictedDialog(long expiryDate) {
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_restricted, null);
        
        android.widget.TextView tvExpiry = dialogView.findViewById(R.id.tv_restrict_expiry);
        if (expiryDate > 0) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMMM dd, yyyy", java.util.Locale.getDefault());
            tvExpiry.setText(sdf.format(new java.util.Date(expiryDate)));
        } else {
            tvExpiry.setText("Indefinite");
        }
        
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create();
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        
        dialogView.findViewById(R.id.btn_ok).setOnClickListener(v -> {
            dialog.dismiss();
            finish(); // Close CreateNoteActivity
        });
        
        dialog.show();
    }
}
