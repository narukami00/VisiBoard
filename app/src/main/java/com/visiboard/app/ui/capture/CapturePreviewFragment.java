package com.visiboard.app.ui.capture;
import android.content.Intent;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions;
import com.visiboard.app.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;

public class CapturePreviewFragment extends Fragment {

    private static final String TAG = "CapturePreviewFragment";
    
    private ImageView ivCapturedImage;
    private Button btnSave;
    private Button btnRetake;
    private Button btnPostNote;
    private Button btnExtractText;
    private ProgressBar progressSaving;
    
    private String imageUri;
    private boolean isOcrMode = false;
    private TextRecognizer latinRecognizer;
    private TextRecognizer devanagariRecognizer; // For Bengali

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_capture_preview, container, false);
        
        ivCapturedImage = view.findViewById(R.id.iv_captured_image);
        btnSave = view.findViewById(R.id.btn_save);
        btnRetake = view.findViewById(R.id.btn_retake);
        btnPostNote = view.findViewById(R.id.btn_post_note);
        btnExtractText = view.findViewById(R.id.btn_extract_text);
        progressSaving = view.findViewById(R.id.progress_saving);
        
        // Initialize both recognizers
        latinRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        devanagariRecognizer = TextRecognition.getClient(new DevanagariTextRecognizerOptions.Builder().build());
        
        if (getArguments() != null) {
            imageUri = getArguments().getString("image_uri");
            isOcrMode = getArguments().getBoolean("ocr_mode", false);
            displayImage();
        }
        
        // Show Extract Text button only in OCR mode
        if (btnExtractText != null) {
            btnExtractText.setVisibility(isOcrMode ? View.VISIBLE : View.GONE);
        }
        
        // Auto-run OCR if in OCR mode
        if (isOcrMode && imageUri != null) {
            runOcrOnImage();
        }
        
        setupListeners(view);
        
        return view;
    }
    
    private void displayImage() {
        if (imageUri != null) {
            try {
                Uri uri = Uri.parse(imageUri);
                File file = new File(uri.getPath());
                Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                ivCapturedImage.setImageBitmap(bitmap);
            } catch (Exception e) {
                Log.e(TAG, "Error loading image", e);
                Toast.makeText(requireContext(), "Error loading image", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private void setupListeners(View view) {
        btnSave.setOnClickListener(v -> saveImage());
        btnRetake.setOnClickListener(v -> {
            // Navigate back to capture fragment
            requireActivity().onBackPressed();
        });
        btnPostNote.setOnClickListener(v -> {
            if (imageUri != null) {
                Intent intent = new Intent(requireActivity(), com.visiboard.app.ui.create.CreateNoteActivity.class);
                intent.putExtra("image_uri", imageUri);
                startActivity(intent);
            }
        });
        
        // Share button removed as per request

        
        if (btnExtractText != null) {
            btnExtractText.setOnClickListener(v -> runOcrOnImage());
        }
    }
    
    private void runOcrOnImage() {
        if (imageUri == null) return;
        
        progressSaving.setVisibility(View.VISIBLE);
        
        try {
            Uri uri = Uri.parse(imageUri);
            File file = new File(uri.getPath());
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            
            if (bitmap == null) {
                Toast.makeText(requireContext(), "Failed to load image for OCR", Toast.LENGTH_SHORT).show();
                progressSaving.setVisibility(View.GONE);
                return;
            }
            
            InputImage inputImage = InputImage.fromBitmap(bitmap, 0);
            
            // Run both recognizers and combine results
            StringBuilder combinedText = new StringBuilder();
            AtomicInteger completedCount = new AtomicInteger(0);
            
            // Latin (English, etc.)
            latinRecognizer.process(inputImage)
                .addOnSuccessListener(latinText -> {
                    for (Text.TextBlock block : latinText.getTextBlocks()) {
                        combinedText.append(block.getText()).append("\n");
                    }
                    if (completedCount.incrementAndGet() == 2) {
                        progressSaving.setVisibility(View.GONE);
                        showOcrResultDialogFromString(combinedText.toString());
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Latin OCR failed", e);
                    if (completedCount.incrementAndGet() == 2) {
                        progressSaving.setVisibility(View.GONE);
                        showOcrResultDialogFromString(combinedText.toString());
                    }
                });
            
            // Devanagari (Bengali, Hindi, etc.)
            devanagariRecognizer.process(inputImage)
                .addOnSuccessListener(devanagariText -> {
                    for (Text.TextBlock block : devanagariText.getTextBlocks()) {
                        String blockText = block.getText();
                        // Only add if not already captured by Latin recognizer
                        if (!combinedText.toString().contains(blockText)) {
                            combinedText.append(blockText).append("\n");
                        }
                    }
                    if (completedCount.incrementAndGet() == 2) {
                        progressSaving.setVisibility(View.GONE);
                        showOcrResultDialogFromString(combinedText.toString());
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Devanagari OCR failed", e);
                    if (completedCount.incrementAndGet() == 2) {
                        progressSaving.setVisibility(View.GONE);
                        showOcrResultDialogFromString(combinedText.toString());
                    }
                });
                
        } catch (Exception e) {
            progressSaving.setVisibility(View.GONE);
            Log.e(TAG, "Error running OCR", e);
            Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showOcrResultDialogFromString(String text) {
        String finalText = text.trim();
        if (finalText.isEmpty()) {
            finalText = "No text detected in this image.";
        }
        
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_ocr_result);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        
        TextView tvExtractedText = dialog.findViewById(R.id.tv_extracted_text);
        Button btnCopy = dialog.findViewById(R.id.btn_copy);
        Button btnClose = dialog.findViewById(R.id.btn_close);
        
        tvExtractedText.setText(finalText);
        
        String textToCopy = finalText;
        btnCopy.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Extracted Text", textToCopy);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(requireContext(), "Text copied to clipboard!", Toast.LENGTH_SHORT).show();
        });
        
        btnClose.setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
    }
    
    private void saveImage() {
        if (imageUri == null) {
            Toast.makeText(requireContext(), "No image to save", Toast.LENGTH_SHORT).show();
            return;
        }
        
        progressSaving.setVisibility(View.VISIBLE);
        btnSave.setEnabled(false);
        btnRetake.setEnabled(false);
        
        new Thread(() -> {
            try {
                Uri uri = Uri.parse(imageUri);
                File sourceFile = new File(uri.getPath());
                
                if (!sourceFile.exists()) {
                    showError("Source file not found");
                    return;
                }
                
                Bitmap bitmap = BitmapFactory.decodeFile(sourceFile.getAbsolutePath());
                
                if (bitmap == null) {
                    showError("Failed to decode image");
                    return;
                }
                
                Uri savedUri;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Use MediaStore for Android 10+
                    savedUri = saveImageToMediaStore(bitmap);
                } else {
                    // Use legacy storage for older versions
                    savedUri = saveImageToLegacyStorage(bitmap);
                }
                
                if (savedUri != null) {
                    requireActivity().runOnUiThread(() -> {
                        progressSaving.setVisibility(View.GONE);
                        // Show dialog asking to share
                        showShareConfirmation(savedUri);
                    });
                } else {
                    showError("Failed to save image");
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error saving image", e);
                showError("Error saving image: " + e.getMessage());
            }
        }).start();
    }
    
    private Uri saveImageToMediaStore(Bitmap bitmap) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "VisiBoard_" + System.currentTimeMillis() + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/VisiBoard Captures");
        
        Uri collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri imageUri = requireContext().getContentResolver().insert(collection, values);
        
        if (imageUri != null) {
            try (OutputStream out = requireContext().getContentResolver().openOutputStream(imageUri)) {
                if (out != null) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out);
                    return imageUri;
                }
            } catch (IOException e) {
                Log.e(TAG, "Error writing to MediaStore", e);
            }
        }
        
        return null;
    }
    
    private Uri saveImageToLegacyStorage(Bitmap bitmap) {
        File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File visiboardDir = new File(picturesDir, "VisiBoard Captures");
        
        if (!visiboardDir.exists()) {
            visiboardDir.mkdirs();
        }
        
        File imageFile = new File(visiboardDir, "VisiBoard_" + System.currentTimeMillis() + ".jpg");
        
        try (OutputStream out = new java.io.FileOutputStream(imageFile)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out);
            
            // Notify media scanner
            MediaStore.Images.Media.insertImage(
                requireContext().getContentResolver(),
                imageFile.getAbsolutePath(),
                imageFile.getName(),
                "VisiBoard Capture"
            );
            
            return Uri.fromFile(imageFile);
        } catch (IOException e) {
            Log.e(TAG, "Error writing to legacy storage", e);
        }
        
        return null;
    }
    
    private void showError(String message) {
        requireActivity().runOnUiThread(() -> {
            progressSaving.setVisibility(View.GONE);
            btnSave.setEnabled(true);
            btnRetake.setEnabled(true);
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        });
    }

    private void showShareConfirmation(Uri savedUri) {
        new android.app.AlertDialog.Builder(requireContext())
            .setTitle("Image Saved! ✨")
            .setMessage("The image has been saved to your gallery.\n\nDo you want to share it?")
            .setCancelable(false)
            .setPositiveButton("Yes, Share", (dialog, which) -> {
                shareImage(savedUri);
            })
            .setNegativeButton("No", (dialog, which) -> {
                // Return to capture screen
                requireActivity().onBackPressed();
            })
            .show();
    }

    private void shareImage(Uri uri) {
        try {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/*");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Captured with VisiBoard 📸");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Share Image"));
            
            // Optionally close the preview screen after launching share, 
            // so user doesn't come back to the "Do you want to share?" dialog or the preview.
            // But let's keep it open or close it? The standard behavior for "Share" usually keeps context.
            // But since "No" closes it, "Yes" should probably also close it to be consistent with "Done".
            // However, startActivity is async-ish. Let's close it so subsequent back press goes to camera.
            requireActivity().onBackPressed();
            
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error sharing image", Toast.LENGTH_SHORT).show();
            // Even if error, close? Maybe not.
        }
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (latinRecognizer != null) {
            latinRecognizer.close();
        }
        if (devanagariRecognizer != null) {
            devanagariRecognizer.close();
        }
    }
}

