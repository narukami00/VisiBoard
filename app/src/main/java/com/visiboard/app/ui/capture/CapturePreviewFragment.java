package com.visiboard.app.ui.capture;

import android.content.ContentValues;
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
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.visiboard.app.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;

public class CapturePreviewFragment extends Fragment {

    private static final String TAG = "CapturePreviewFragment";
    
    private ImageView ivCapturedImage;
    private Button btnSave;
    private Button btnRetake;
    private ProgressBar progressSaving;
    
    private String imageUri;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_capture_preview, container, false);
        
        ivCapturedImage = view.findViewById(R.id.iv_captured_image);
        btnSave = view.findViewById(R.id.btn_save);
        btnRetake = view.findViewById(R.id.btn_retake);
        progressSaving = view.findViewById(R.id.progress_saving);
        
        if (getArguments() != null) {
            imageUri = getArguments().getString("image_uri");
            displayImage();
        }
        
        setupListeners();
        
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
    
    private void setupListeners() {
        btnSave.setOnClickListener(v -> saveImage());
        btnRetake.setOnClickListener(v -> {
            // Navigate back to capture fragment
            requireActivity().onBackPressed();
        });
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
                        Toast.makeText(requireContext(), "Image saved to VisiBoard Captures", Toast.LENGTH_LONG).show();
                        // Navigate back to capture fragment
                        requireActivity().onBackPressed();
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
}
