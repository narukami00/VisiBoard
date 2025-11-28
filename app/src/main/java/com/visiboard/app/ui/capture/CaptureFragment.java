package com.visiboard.app.ui.capture;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.visiboard.app.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;

import de.hdodenhof.circleimageview.CircleImageView;

public class CaptureFragment extends Fragment implements SensorEventListener {

    private static final String TAG = "CaptureFragment";
    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final int ACTIVITY_RECOGNITION_PERMISSION_CODE = 101;
    private double currentRadiusMeters = 10.0;
    private int[] radiusOptions = {10, 20, 50};
    private int currentRadiusIndex = 0;
    
    private PreviewView cameraPreview;
    private FrameLayout arOverlay;
    private TextView tvNotesCount;
    private TextView tvRadiusValue;
    private ProgressBar progressLoading;
    private ImageButton btnCameraToggle;
    private ImageButton btnCapture;
    private ImageButton btnRefreshLocation;
    private FrameLayout btnRadius;
    private RadarView radarView;
    
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private FusedLocationProviderClient fusedLocationClient;
    
    private Location currentLocation;
    private Location virtualLocation; // For dead reckoning
    private List<ARNote> nearbyNotes = new ArrayList<>();
    
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor magnetometer;
    private Sensor stepDetector;
    private float[] gravity;
    private float[] geomagnetic;
    private float azimuth = 0f;
    private float pitch = 0f;
    private Float initialPitch = null; // Baseline pitch for vertical calibration
    private float lastUpdateAzimuth = 0f;
    private static final float AZIMUTH_UPDATE_THRESHOLD = 0.5f; // Reduced for smoother updates
    // Dynamic filter constants
    private static final float ALPHA_STEADY = 0.03f; // Very smooth for steady hand
    private static final float ALPHA_MOVE = 0.3f;    // Fast response for movement
    private static final float MOVEMENT_THRESHOLD = 0.8f; // Threshold to switch between steady and move
    private static final float PITCH_VERTICAL_THRESHOLD = 50.0f; // Degrees away from vertical (-90) where notes hide
    private static final double STEP_LENGTH_METERS = 0.75; // Average step length
    private static final float VERTICAL_FOV_DEGREES = 60.0f; // Approximate vertical field of view
    private static final float DEPTH_EFFECT_FACTOR = 0.4f; // Increased factor for depth movement
    
    private Camera camera;
    private ImageCapture imageCapture;
    private CameraSelector currentCameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
    private boolean isUpdatingView = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_capture, container, false);
        
        cameraPreview = view.findViewById(R.id.camera_preview);
        // Use COMPATIBLE mode (TextureView) to enable simple bitmap capture
        cameraPreview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        arOverlay = view.findViewById(R.id.ar_overlay);
        tvNotesCount = view.findViewById(R.id.tv_notes_count);
        tvRadiusValue = view.findViewById(R.id.tv_radius_value);
        progressLoading = view.findViewById(R.id.progress_loading);
        btnCameraToggle = view.findViewById(R.id.btn_camera_toggle);
        btnCapture = view.findViewById(R.id.btn_capture);
        btnRefreshLocation = view.findViewById(R.id.btn_refresh_location);
        btnRadius = view.findViewById(R.id.btn_radius);
        radarView = view.findViewById(R.id.radar_view);
        
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        
        sensorManager = (SensorManager) requireActivity().getSystemService(android.content.Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        
        setupButtonListeners();
        updateRadiusDisplay();
        checkCameraPermission();
        
        return view;
    }
    
    @Override
    public void onResume() {
        super.onResume();
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }
        if (magnetometer != null) {
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
        }
        if (stepDetector != null) {
            sensorManager.registerListener(this, stepDetector, SensorManager.SENSOR_DELAY_UI);
        }
    }
    
    @Override
    public void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }
    
    private void setupButtonListeners() {
        // Camera toggle button
        btnCameraToggle.setOnClickListener(v -> {
            v.animate().scaleX(0.8f).scaleY(0.8f).setDuration(100)
                .withEndAction(() -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                    toggleCamera();
                });
        });
        
        // Capture button
        btnCapture.setOnClickListener(v -> {
            v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100)
                .withEndAction(() -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                    capturePhoto();
                });
        });
        
        // Radius selection button
        btnRadius.setOnClickListener(v -> {
            v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100)
                .withEndAction(() -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                    cycleRadius();
                });
        });
        
        // Refresh location button
        if (btnRefreshLocation != null) {
            btnRefreshLocation.setOnClickListener(v -> {
                v.animate().rotationBy(360).setDuration(500).start();
                loadUserLocation();
            });
        }
    }
    
    private void toggleCamera() {
        if (currentCameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            currentCameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
        } else {
            currentCameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
        }
        startCamera();
    }
    
    private void cycleRadius() {
        currentRadiusIndex = (currentRadiusIndex + 1) % radiusOptions.length;
        currentRadiusMeters = radiusOptions[currentRadiusIndex];
        updateRadiusDisplay();
        
        // Reload notes with new radius
        if (currentLocation != null) {
            loadNearbyNotes();
        }
    }
    
    private void updateRadiusDisplay() {
        tvRadiusValue.setText(String.valueOf(radiusOptions[currentRadiusIndex]));
        if (radarView != null) {
            radarView.setMaxDistance((float) currentRadiusMeters);
        }
    }
    
    private void capturePhoto() {
        Bitmap previewBitmap = cameraPreview.getBitmap();
        if (previewBitmap != null) {
            Log.d(TAG, "Captured bitmap: " + previewBitmap.getWidth() + "x" + previewBitmap.getHeight());
            Bitmap finalBitmap = compositeBitmapWithOverlay(previewBitmap);
            saveBitmapAndNavigate(finalBitmap);
        } else {
            Toast.makeText(requireContext(), "Failed to capture camera view", Toast.LENGTH_SHORT).show();
        }
    }

    // Removed captureWithPixelCopy as we are now using TextureView.getBitmap()
    
    private android.view.SurfaceView findSurfaceView(ViewGroup viewGroup) {
        Log.d(TAG, "Searching for SurfaceView in: " + viewGroup.getClass().getSimpleName() + 
                   " with " + viewGroup.getChildCount() + " children");
        
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            Log.d(TAG, "  Child " + i + ": " + child.getClass().getSimpleName());
            
            if (child instanceof android.view.SurfaceView) {
                Log.d(TAG, "Found SurfaceView!");
                return (android.view.SurfaceView) child;
            } else if (child instanceof ViewGroup) {
                android.view.SurfaceView surfaceView = findSurfaceView((ViewGroup) child);
                if (surfaceView != null) {
                    return surfaceView;
                }
            }
        }
        Log.d(TAG, "SurfaceView not found in this branch");
        return null;
    }
    
    private android.view.TextureView findTextureView(ViewGroup viewGroup) {
        Log.d(TAG, "Searching for TextureView in: " + viewGroup.getClass().getSimpleName() + 
                   " with " + viewGroup.getChildCount() + " children");
        
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            Log.d(TAG, "  Child " + i + ": " + child.getClass().getSimpleName());
            
            if (child instanceof android.view.TextureView) {
                Log.d(TAG, "Found TextureView!");
                return (android.view.TextureView) child;
            } else if (child instanceof ViewGroup) {
                android.view.TextureView textureView = findTextureView((ViewGroup) child);
                if (textureView != null) {
                    return textureView;
                }
            }
        }
        Log.d(TAG, "TextureView not found in this branch");
        return null;
    }
    
    private Bitmap compositeBitmapWithOverlay(Bitmap cameraBitmap) {
        // Create a mutable bitmap to draw on
        Bitmap resultBitmap = Bitmap.createBitmap(
            cameraBitmap.getWidth(),
            cameraBitmap.getHeight(),
            Bitmap.Config.ARGB_8888
        );
        
        android.graphics.Canvas canvas = new android.graphics.Canvas(resultBitmap);
        
        // Draw camera view first
        canvas.drawBitmap(cameraBitmap, 0, 0, null);
        
        // Draw AR overlay on top
        arOverlay.setDrawingCacheEnabled(true);
        arOverlay.buildDrawingCache(true);
        Bitmap overlayBitmap = Bitmap.createBitmap(arOverlay.getDrawingCache());
        arOverlay.setDrawingCacheEnabled(false);
        
        if (overlayBitmap != null) {
            // Don't stretch - instead center crop to maintain aspect ratio
            if (overlayBitmap.getWidth() != cameraBitmap.getWidth() || 
                overlayBitmap.getHeight() != cameraBitmap.getHeight()) {
                
                // Calculate source rect to center-crop overlay
                int srcWidth = overlayBitmap.getWidth();
                int srcHeight = overlayBitmap.getHeight();
                float srcAspect = (float) srcWidth / srcHeight;
                float dstAspect = (float) cameraBitmap.getWidth() / cameraBitmap.getHeight();
                
                android.graphics.Rect srcRect;
                if (srcAspect > dstAspect) {
                    // Overlay is wider, crop sides
                    int newWidth = (int) (srcHeight * dstAspect);
                    int left = (srcWidth - newWidth) / 2;
                    srcRect = new android.graphics.Rect(left, 0, left + newWidth, srcHeight);
                } else {
                    // Overlay is taller, crop top/bottom
                    int newHeight = (int) (srcWidth / dstAspect);
                    int top = (srcHeight - newHeight) / 2;
                    srcRect = new android.graphics.Rect(0, top, srcWidth, top + newHeight);
                }
                
                android.graphics.Rect dstRect = new android.graphics.Rect(0, 0, cameraBitmap.getWidth(), cameraBitmap.getHeight());
                canvas.drawBitmap(overlayBitmap, srcRect, dstRect, null);
            } else {
                canvas.drawBitmap(overlayBitmap, 0, 0, null);
            }
            overlayBitmap.recycle();
        }
        
        cameraBitmap.recycle();
        return resultBitmap;
    }
    
    private void saveBitmapAndNavigate(Bitmap bitmap) {
        try {
            File cacheFile = new File(requireContext().getCacheDir(), "captured_" + System.currentTimeMillis() + ".jpg");
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(cacheFile)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out);
                out.flush();
            }
            bitmap.recycle();
            
            requireActivity().runOnUiThread(() -> {
                navigateToPreview("file://" + cacheFile.getAbsolutePath());
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to save bitmap", e);
            requireActivity().runOnUiThread(() -> {
                Toast.makeText(requireContext(), "Failed to save photo", Toast.LENGTH_SHORT).show();
            });
        }
    }
    
    private void navigateToPreview(String imageUri) {
        Bundle args = new Bundle();
        args.putString("image_uri", imageUri);
        
        androidx.navigation.Navigation.findNavController(requireView())
                .navigate(R.id.action_captureFragment_to_capturePreviewFragment, args);
    }
    
    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        } else {
            startCamera();
            loadUserLocation();
            checkActivityPermission();
        }
    }
    
    private void checkActivityPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACTIVITY_RECOGNITION)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACTIVITY_RECOGNITION}, 
                    ACTIVITY_RECOGNITION_PERMISSION_CODE);
            }
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                          @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
                loadUserLocation();
            } else {
                Toast.makeText(requireContext(), "Camera permission is required for AR view",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(requireContext());
        
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindCameraPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error starting camera", e);
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }
    
    private void bindCameraPreview(ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .build();
        
        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .build();
        
        preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());
        
        cameraProvider.unbindAll();
        camera = cameraProvider.bindToLifecycle(this, currentCameraSelector, preview, imageCapture);
    }
    
    private void loadUserLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        
        progressLoading.setVisibility(View.VISIBLE);
        
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                currentLocation = location;
                virtualLocation = new Location(location); // Initialize virtual location
                loadNearbyNotes();
            } else {
                progressLoading.setVisibility(View.GONE);
                tvNotesCount.setText("Unable to get location");
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Error getting location", e);
            progressLoading.setVisibility(View.GONE);
            tvNotesCount.setText("Location error");
        });
    }
    
    private void loadNearbyNotes() {
        String userId = auth.getCurrentUser().getUid();
        
        db.collection("notes")
            .get()
            .addOnSuccessListener(querySnapshot -> {
                List<ARNote> newNotes = new ArrayList<>();
                
                for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                    try {
                        String noteUserId = doc.getString("userId");
                        
                        GeoPoint location = doc.getGeoPoint("location");
                        if (location == null) {
                            Double lat = doc.getDouble("lat");
                            Double lon = doc.getDouble("lon");
                            if (lat != null && lon != null) {
                                location = new GeoPoint(lat, lon);
                            }
                        }
                        
                        if (location != null) {
                            double distance = calculateDistance(
                                virtualLocation.getLatitude(),
                                virtualLocation.getLongitude(),
                                location.getLatitude(),
                                location.getLongitude()
                            );
                            
                            if (distance <= currentRadiusMeters) {
                                ARNote arNote = new ARNote();
                                arNote.id = doc.getId();
                                arNote.text = doc.getString("text");
                                if (arNote.text == null) arNote.text = doc.getString("note");
                                arNote.latitude = location.getLatitude();
                                arNote.longitude = location.getLongitude();
                                arNote.distance = distance;
                                arNote.userId = noteUserId;
                                arNote.userName = "User"; // Default
                                
                                arNote.bearing = calculateBearing(
                                    virtualLocation.getLatitude(),
                                    virtualLocation.getLongitude(),
                                    location.getLatitude(),
                                    location.getLongitude()
                                );
                                
                                newNotes.add(arNote);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing note", e);
                    }
                }
                
                // Sort by distance descending (Far -> Near) so closer notes are drawn last (on top)
                Collections.sort(newNotes, (n1, n2) -> Double.compare(n2.distance, n1.distance));
                
                nearbyNotes.clear();
                nearbyNotes.addAll(newNotes);
                
                progressLoading.setVisibility(View.GONE);
                updateARView();
                
                // Fetch user info for all notes
                for (ARNote note : nearbyNotes) {
                    if (note.userId != null && !note.userId.isEmpty()) {
                        db.collection("users").document(note.userId).get()
                            .addOnSuccessListener(userDoc -> {
                                if (userDoc.exists()) {
                                    note.userName = userDoc.getString("name");
                                    note.userProfilePic = userDoc.getString("profilePic");
                                    if (note.userName == null) note.userName = "User";
                                    
                                    // Update view if it exists
                                    View view = arOverlay.findViewWithTag(note.id);
                                    if (view != null) {
                                        updateNoteViewData(view, note);
                                    }
                                }
                            });
                    }
                }
                
            }).addOnFailureListener(e -> {
                Log.e(TAG, "Error loading notes", e);
                progressLoading.setVisibility(View.GONE);
                tvNotesCount.setText("Error loading notes");
            });
    }
    
    private void updateARView() {
        if (isUpdatingView) return;
        
        if (nearbyNotes.isEmpty()) {
            tvNotesCount.setText("No notes nearby");
            arOverlay.removeAllViews();
            return;
        }
        
        tvNotesCount.setText(nearbyNotes.size() + " notes in " + (int)currentRadiusMeters + "m radius");
        
        // Wait for layout before adding views
        arOverlay.post(() -> {
            // 1. Identify which notes are currently displayed
            List<String> currentNoteIds = new ArrayList<>();
            for (int i = 0; i < arOverlay.getChildCount(); i++) {
                View child = arOverlay.getChildAt(i);
                if (child.getTag() instanceof String) {
                    currentNoteIds.add((String) child.getTag());
                }
            }
            
            // 2. Add new notes or update existing ones
            for (ARNote note : nearbyNotes) {
                View existingView = arOverlay.findViewWithTag(note.id);
                if (existingView != null) {
                    // Update existing view
                    updateNoteViewContent(existingView, note);
                    updateNotePosition(existingView, note);
                    currentNoteIds.remove(note.id); // Mark as processed
                } else {
                    // Add new view
                    addNoteToARView(note);
                }
            }
            
            // 3. Remove views that are no longer nearby
            for (String idToRemove : currentNoteIds) {
                View viewToRemove = arOverlay.findViewWithTag(idToRemove);
                if (viewToRemove != null) {
                    arOverlay.removeView(viewToRemove);
                }
            }
            
            // 4. Force Z-Order by bringing views to front in order (Far -> Near)
            // nearbyNotes is already sorted by distance descending (Far -> Near)
            // So we iterate and bringToFront, which puts the last one (Nearest) on top
            for (ARNote note : nearbyNotes) {
                View view = arOverlay.findViewWithTag(note.id);
                if (view != null) {
                    view.bringToFront();
                }
            }
        });
        
        // Update Radar
        List<RadarView.RadarDot> radarDots = new ArrayList<>();
        for (ARNote note : nearbyNotes) {
            radarDots.add(new RadarView.RadarDot((float)note.distance, note.bearing, 0xFF00BCD4)); // Cyan
        }
        radarView.setDots(radarDots);
    }
    
    private void updateNoteViewContent(View noteView, ARNote note) {
        TextView tvDistance = noteView.findViewById(R.id.tv_distance);
        if (tvDistance != null) {
            tvDistance.setText(String.format("%.1fm", note.distance));
        }
    }
    
    private void updateARViewPositions() {
        if (isUpdatingView || arOverlay.getChildCount() == 0) return;
        
        isUpdatingView = true;
        
        // Update existing views instead of recreating them
        for (int i = 0; i < arOverlay.getChildCount() && i < nearbyNotes.size(); i++) {
            View noteView = arOverlay.getChildAt(i);
            ARNote note = nearbyNotes.get(i);
            updateNotePosition(noteView, note);
        }
        
        isUpdatingView = false;
    }
    
    private void updateNoteViewData(View noteView, ARNote note) {
        TextView tvUserName = noteView.findViewById(R.id.tv_user_name);
        CircleImageView ivUserAvatar = noteView.findViewById(R.id.iv_user_avatar);
        
        if (tvUserName != null && note.userName != null) {
            tvUserName.setText(note.userName);
        }
        
        if (ivUserAvatar != null && note.userProfilePic != null && !note.userProfilePic.isEmpty()) {
            try {
                byte[] bytes = Base64.decode(note.userProfilePic, Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                ivUserAvatar.setImageBitmap(bitmap);
            } catch (Exception e) {
                Log.e(TAG, "Error loading profile pic", e);
            }
        }
    }
    
    private void updateNotePosition(View noteView, ARNote note) {
        int screenWidth = arOverlay.getWidth();
        int screenHeight = arOverlay.getHeight();
        
        if (screenWidth == 0 || screenHeight == 0) return;
        
        // Hide notes when phone is not held in upright position
        // When phone is upright (normal AR viewing), pitch is around -90 degrees
        // Hide if deviating more than 50 degrees from upright position
        float deviationFromVertical = Math.abs(pitch + 90);
        if (deviationFromVertical > PITCH_VERTICAL_THRESHOLD) {
            noteView.setVisibility(View.GONE);
            return;
        }
        
        float relativeBearing = note.bearing - azimuth;
        
        while (relativeBearing > 180) relativeBearing -= 360;
        while (relativeBearing < -180) relativeBearing += 360;
        
        // Only show notes within field of view (-90 to +90 degrees)
        if (Math.abs(relativeBearing) > 90) {
            noteView.setVisibility(View.GONE);
            return;
        }
        
        noteView.setVisibility(View.VISIBLE);
        
        float normalizedBearing = Math.max(-90, Math.min(90, relativeBearing));
        float horizontalPosition = (normalizedBearing + 90) / 180.0f;
        
        int x = (int) (horizontalPosition * screenWidth) - 100;
        
        float verticalPosition = (float) (1.0 - (note.distance / currentRadiusMeters));
        verticalPosition = Math.max(0.2f, Math.min(0.8f, verticalPosition));
        
        int y = (int) (verticalPosition * screenHeight * 0.6f) + 100;
        
        // Depth Effect Logic
        // 1. Calculate pitch change from initial baseline
        if (initialPitch == null) {
            initialPitch = pitch;
        }
        float pitchChange = pitch - initialPitch;
        
        // 2. Calculate distance ratio (0 = Close, 1 = Far)
        // Note: note.distance is in meters, currentRadiusMeters is max radius
        float distRatio = (float) (note.distance / currentRadiusMeters);
        distRatio = Math.max(0f, Math.min(1f, distRatio));
        
        // 3. Calculate vertical movement
        // Pixels per degree
        float pixelsPerDegree = screenHeight / VERTICAL_FOV_DEGREES;
        float rawMovement = pitchChange * pixelsPerDegree;
        
        // 4. Apply depth factor: Far notes move more, Close notes stay put
        // We want:
        // Tilt Up (pitchChange > 0) -> Far notes move UP (y decreases)
        // Tilt Down (pitchChange < 0) -> Far notes move DOWN (y increases)
        float depthMovement = rawMovement * distRatio * DEPTH_EFFECT_FACTOR;
        
        // Subtract because Y increases downwards
        y -= (int) depthMovement;
        
        // Scale based on distance - closer notes appear larger
        float targetScale = 1.4f - (float)(note.distance / currentRadiusMeters) * 1.7f;
        targetScale = Math.max(0.4f, Math.min(2.5f, targetScale));
        
        // Animate scale change if difference is significant
        if (Math.abs(noteView.getScaleX() - targetScale) > 0.05f) {
            noteView.animate()
                .scaleX(targetScale)
                .scaleY(targetScale)
                .setDuration(200)
                .start();
        } else {
            noteView.setScaleX(targetScale);
            noteView.setScaleY(targetScale);
        }
        
        // Explicit Z-Order: Closer notes (smaller distance) get higher elevation
        // Use a base elevation (e.g., 1000) and subtract distance
        // This ensures closer notes are drawn on top of farther notes
        float zIndex = Math.max(0f, (float)(currentRadiusMeters - note.distance));
        noteView.setElevation(zIndex);
        
        // Debug Log for Scaling Issue
        Log.d(TAG, String.format("Note: %s, Dist: %.1f, Radius: %.1f, Scale: %.2f, Z: %.1f", 
            note.id, note.distance, currentRadiusMeters, targetScale, zIndex));
        
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) noteView.getLayoutParams();
        params.leftMargin = Math.max(10, Math.min(screenWidth - 210, x));
        params.topMargin = y;
        noteView.setLayoutParams(params);
    }
    
    private void addNoteToARView(ARNote note) {
        View noteView = LayoutInflater.from(requireContext()).inflate(R.layout.item_ar_note, arOverlay, false);
        noteView.setTag(note.id); // Set tag to find it later
        
        TextView tvUserName = noteView.findViewById(R.id.tv_user_name);
        TextView tvNoteText = noteView.findViewById(R.id.tv_note_text);
        TextView tvDistance = noteView.findViewById(R.id.tv_distance);
        CircleImageView ivUserAvatar = noteView.findViewById(R.id.iv_user_avatar);
        androidx.cardview.widget.CardView cardView = noteView.findViewById(R.id.ar_note_card);
        
        tvUserName.setText(note.userName != null ? note.userName : "User");
        tvNoteText.setText(note.text != null ? note.text : "");
        tvDistance.setText(String.format("%.1fm", note.distance));
        
        // Assign random color to card
        int[] colors = {
            0xFFE91E63, // Pink
            0xFF9C27B0, // Purple
            0xFF673AB7, // Deep Purple
            0xFF3F51B5, // Indigo
            0xFF2196F3, // Blue
            0xFF00BCD4, // Cyan
            0xFF009688, // Teal
            0xFF4CAF50, // Green
            0xFFFF9800, // Orange
            0xFFFF5722  // Deep Orange
        };
        int randomColor = colors[(int) (Math.random() * colors.length)];
        cardView.setCardBackgroundColor(randomColor);
        
        // Click listener to navigate to map
        noteView.setOnClickListener(v -> {
            navigateToNoteOnMap(note.latitude, note.longitude, note.id);
        });
        
        if (note.userProfilePic != null && !note.userProfilePic.isEmpty()) {
            try {
                byte[] bytes = Base64.decode(note.userProfilePic, Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                ivUserAvatar.setImageBitmap(bitmap);
            } catch (Exception e) {
                Log.e(TAG, "Error loading profile pic", e);
            }
        }
        
        int screenWidth = arOverlay.getWidth();
        int screenHeight = arOverlay.getHeight();
        
        if (screenWidth == 0 || screenHeight == 0) {
            Log.w(TAG, "Overlay not ready, skipping note");
            return;
        }
        
        // Add view first so we can update its position
        arOverlay.addView(noteView);
        
        // Use unified logic for position and scale
        updateNotePosition(noteView, note);
        
        noteView.setAlpha(0f);
        noteView.animate().alpha(1f).setDuration(300).start();
    }
    
    private void navigateToNoteOnMap(double lat, double lng, String noteId) {
        Bundle args = new Bundle();
        args.putDouble("target_lat", lat);
        args.putDouble("target_lng", lng);
        args.putString("target_note_id", noteId);
        args.putBoolean("open_note_window", true);
        
        androidx.navigation.Navigation.findNavController(requireView())
                .navigate(R.id.mapFragment, args);
    }
    
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            gravity = applyDynamicFilter(event.values, gravity);
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            geomagnetic = applyDynamicFilter(event.values, geomagnetic);
        } else if (event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
            handleStep();
        }
        
        if (gravity != null && geomagnetic != null) {
            float[] R = new float[9];
            float[] I = new float[9];
            
            if (SensorManager.getRotationMatrix(R, I, gravity, geomagnetic)) {
                float[] orientation = new float[3];
                SensorManager.getOrientation(R, orientation);
                
                float newAzimuth = (float) Math.toDegrees(orientation[0]);
                if (newAzimuth < 0) {
                    newAzimuth += 360;
                }
                
                // Get pitch (tilt up/down)
                pitch = (float) Math.toDegrees(orientation[1]);
                
                // Set initial pitch if not set
                if (initialPitch == null) {
                    initialPitch = pitch;
                }
                
                // Only update if heading changed significantly
                float azimuthDiff = Math.abs(newAzimuth - lastUpdateAzimuth);
                if (azimuthDiff > 180) {
                    azimuthDiff = 360 - azimuthDiff;
                }
                
                if (azimuthDiff >= AZIMUTH_UPDATE_THRESHOLD && !isUpdatingView) {
                    azimuth = newAzimuth;
                    lastUpdateAzimuth = newAzimuth;
                    updateARViewPositions();
                    if (radarView != null) {
                        radarView.setAzimuth(azimuth);
                    }
                } else {
                    // Update for pitch changes (depth effect) even if azimuth hasn't changed much
                    updateARViewPositions();
                }
            }
        }
    }

    private void handleStep() {
        if (virtualLocation == null) return;
        
        // Dead reckoning: Move virtual location based on azimuth and step length
        // Azimuth is in degrees, convert to radians
        // 0 degrees = North, 90 = East, etc.
        double bearingRad = Math.toRadians(azimuth);
        
        // Earth radius in meters
        final double R = 6378137.0;
        
        double lat1 = Math.toRadians(virtualLocation.getLatitude());
        double lon1 = Math.toRadians(virtualLocation.getLongitude());
        
        // Calculate new lat/lon
        // Formula:
        // lat2 = asin(sin(lat1)*cos(d/R) + cos(lat1)*sin(d/R)*cos(brng))
        // lon2 = lon1 + atan2(sin(brng)*sin(d/R)*cos(lat1), cos(d/R)-sin(lat1)*sin(lat2))
        
        double d = STEP_LENGTH_METERS;
        double angularDistance = d / R;
        
        double lat2 = Math.asin(Math.sin(lat1) * Math.cos(angularDistance) +
                               Math.cos(lat1) * Math.sin(angularDistance) * Math.cos(bearingRad));
                               
        double lon2 = lon1 + Math.atan2(Math.sin(bearingRad) * Math.sin(angularDistance) * Math.cos(lat1),
                                        Math.cos(angularDistance) - Math.sin(lat1) * Math.sin(lat2));
                                        
        virtualLocation.setLatitude(Math.toDegrees(lat2));
        virtualLocation.setLongitude(Math.toDegrees(lon2));
        
        // Recalculate distances for all notes
        updateNoteDistances();
    }
    
    private void updateNoteDistances() {
        if (nearbyNotes.isEmpty()) return;
        
        for (ARNote note : nearbyNotes) {
            // We need the original note location... 
            // Wait, ARNote stores its own lat/lon. We can just recalculate.
            
            note.distance = calculateDistance(
                virtualLocation.getLatitude(),
                virtualLocation.getLongitude(),
                note.latitude,
                note.longitude
            );
            
            note.bearing = calculateBearing(
                virtualLocation.getLatitude(),
                virtualLocation.getLongitude(),
                note.latitude,
                note.longitude
            );
        }
        
        // Re-sort by distance
        Collections.sort(nearbyNotes, (n1, n2) -> Double.compare(n2.distance, n1.distance));
        
        // Update UI
        requireActivity().runOnUiThread(() -> {
            updateARView(); // Full refresh to update distance text and scaling
        });
    }

    private float[] applyDynamicFilter(float[] input, float[] output) {
        if (output == null) return input.clone();

        for (int i = 0; i < input.length; i++) {
            float diff = Math.abs(input[i] - output[i]);
            
            // Dynamic alpha:
            // If change is large (intentional movement), use higher alpha for responsiveness.
            // If change is small (jitter), use lower alpha for stability.
            float alpha = (diff > MOVEMENT_THRESHOLD) ? ALPHA_MOVE : ALPHA_STEADY;
            
            output[i] = alpha * input[i] + (1 - alpha) * output[i];
        }
        return output;
    }
    
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
    
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000;
        
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return R * c;
    }
    
    private float calculateBearing(double lat1, double lon1, double lat2, double lon2) {
        double dLon = Math.toRadians(lon2 - lon1);
        double y = Math.sin(dLon) * Math.cos(Math.toRadians(lat2));
        double x = Math.cos(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2)) -
                   Math.sin(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(dLon);
        double bearing = Math.toDegrees(Math.atan2(y, x));
        
        return (float) ((bearing + 360) % 360);
    }
    
    private static class ARNote {
        String id;
        String text;
        String userId;
        String userName;
        String userProfilePic;
        double latitude;
        double longitude;
        double distance;
        float bearing;
    }
}
