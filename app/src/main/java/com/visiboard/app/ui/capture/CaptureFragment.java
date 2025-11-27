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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import de.hdodenhof.circleimageview.CircleImageView;

public class CaptureFragment extends Fragment implements SensorEventListener {

    private static final String TAG = "CaptureFragment";
    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final double AR_RADIUS_METERS = 10.0;
    
    private PreviewView cameraPreview;
    private FrameLayout arOverlay;
    private TextView tvNotesCount, tvRange;
    private ProgressBar progressLoading;
    
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private FusedLocationProviderClient fusedLocationClient;
    
    private Location currentLocation;
    private List<ARNote> nearbyNotes = new ArrayList<>();
    
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor magnetometer;
    private float[] gravity;
    private float[] geomagnetic;
    private float azimuth = 0f;
    private float lastUpdateAzimuth = 0f;
    private static final float AZIMUTH_UPDATE_THRESHOLD = 10.0f; // Only update if heading changes by 10 degrees
    
    private Camera camera;
    private boolean isUpdatingView = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_capture_new, container, false);
        
        cameraPreview = view.findViewById(R.id.camera_preview);
        arOverlay = view.findViewById(R.id.ar_overlay);
        tvNotesCount = view.findViewById(R.id.tv_notes_count);
        tvRange = view.findViewById(R.id.tv_range);
        progressLoading = view.findViewById(R.id.progress_loading);
        
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        
        sensorManager = (SensorManager) requireActivity().getSystemService(android.content.Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        
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
    }
    
    @Override
    public void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }
    
    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        } else {
            startCamera();
            loadUserLocation();
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
        Preview preview = new Preview.Builder().build();
        
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();
        
        preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());
        
        cameraProvider.unbindAll();
        camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview);
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
                nearbyNotes.clear();
                
                for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                    try {
                        String noteUserId = doc.getString("userId");
                        if (noteUserId != null && noteUserId.equals(userId)) {
                            continue;
                        }
                        
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
                                currentLocation.getLatitude(),
                                currentLocation.getLongitude(),
                                location.getLatitude(),
                                location.getLongitude()
                            );
                            
                            if (distance <= AR_RADIUS_METERS) {
                                ARNote arNote = new ARNote();
                                arNote.id = doc.getId();
                                arNote.text = doc.getString("text");
                                if (arNote.text == null) arNote.text = doc.getString("note");
                                arNote.latitude = location.getLatitude();
                                arNote.longitude = location.getLongitude();
                                arNote.distance = distance;
                                arNote.userId = noteUserId;
                                
                                arNote.bearing = calculateBearing(
                                    currentLocation.getLatitude(),
                                    currentLocation.getLongitude(),
                                    location.getLatitude(),
                                    location.getLongitude()
                                );
                                
                                // Get userName and profilePic - always fetch from users collection for consistency
                                if (noteUserId != null && !noteUserId.isEmpty()) {
                                    nearbyNotes.add(arNote); // Add first, update later
                                    
                                    final int noteIndex = nearbyNotes.size() - 1;
                                    db.collection("users").document(noteUserId).get()
                                        .addOnSuccessListener(userDoc -> {
                                            if (userDoc.exists()) {
                                                arNote.userName = userDoc.getString("name");
                                                arNote.userProfilePic = userDoc.getString("profilePic");
                                                
                                                if (arNote.userName == null) {
                                                    arNote.userName = "User";
                                                }
                                                
                                                // Update the view if already displayed
                                                if (noteIndex < arOverlay.getChildCount()) {
                                                    requireActivity().runOnUiThread(() -> {
                                                        View noteView = arOverlay.getChildAt(noteIndex);
                                                        if (noteView != null) {
                                                            updateNoteViewData(noteView, arNote);
                                                        }
                                                    });
                                                }
                                            } else {
                                                arNote.userName = "User";
                                            }
                                        })
                                        .addOnFailureListener(e -> {
                                            arNote.userName = "User";
                                            Log.e(TAG, "Error fetching user info", e);
                                        });
                                } else {
                                    arNote.userName = "User";
                                    arNote.userProfilePic = null;
                                    nearbyNotes.add(arNote);
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing note", e);
                    }
                }
                
                progressLoading.setVisibility(View.GONE);
                updateARView();
                
            }).addOnFailureListener(e -> {
                Log.e(TAG, "Error loading notes", e);
                progressLoading.setVisibility(View.GONE);
                tvNotesCount.setText("Error loading notes");
            });
    }
    
    private void updateARView() {
        if (isUpdatingView) return;
        
        arOverlay.removeAllViews();
        
        if (nearbyNotes.isEmpty()) {
            tvNotesCount.setText("No notes nearby");
            return;
        }
        
        tvNotesCount.setText(nearbyNotes.size() + " notes in " + (int)AR_RADIUS_METERS + "m radius");
        
        // Wait for layout before adding views
        arOverlay.post(() -> {
            for (ARNote note : nearbyNotes) {
                addNoteToARView(note);
            }
        });
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
        
        float verticalPosition = (float) (1.0 - (note.distance / AR_RADIUS_METERS));
        verticalPosition = Math.max(0.2f, Math.min(0.8f, verticalPosition));
        
        int y = (int) (verticalPosition * screenHeight * 0.6f) + 100;
        
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) noteView.getLayoutParams();
        params.leftMargin = Math.max(10, Math.min(screenWidth - 210, x));
        params.topMargin = y;
        noteView.setLayoutParams(params);
    }
    
    private void addNoteToARView(ARNote note) {
        View noteView = LayoutInflater.from(requireContext()).inflate(R.layout.item_ar_note, arOverlay, false);
        
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
        
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        
        float relativeBearing = note.bearing - azimuth;
        
        while (relativeBearing > 180) relativeBearing -= 360;
        while (relativeBearing < -180) relativeBearing += 360;
        
        // Only show notes within field of view
        if (Math.abs(relativeBearing) > 90) {
            noteView.setVisibility(View.GONE);
        }
        
        float normalizedBearing = Math.max(-90, Math.min(90, relativeBearing));
        float horizontalPosition = (normalizedBearing + 90) / 180.0f;
        
        int x = (int) (horizontalPosition * screenWidth) - 100;
        
        float verticalPosition = (float) (1.0 - (note.distance / AR_RADIUS_METERS));
        verticalPosition = Math.max(0.2f, Math.min(0.8f, verticalPosition));
        
        int y = (int) (verticalPosition * screenHeight * 0.6f) + 100;
        
        params.leftMargin = Math.max(10, Math.min(screenWidth - 210, x));
        params.topMargin = y;
        
        noteView.setLayoutParams(params);
        noteView.setAlpha(0f);
        noteView.animate().alpha(1f).setDuration(300).start();
        
        arOverlay.addView(noteView);
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
            gravity = event.values.clone();
        }
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            geomagnetic = event.values.clone();
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
                
                // Only update if heading changed significantly
                float azimuthDiff = Math.abs(newAzimuth - lastUpdateAzimuth);
                if (azimuthDiff > 180) {
                    azimuthDiff = 360 - azimuthDiff;
                }
                
                if (azimuthDiff >= AZIMUTH_UPDATE_THRESHOLD && !isUpdatingView) {
                    azimuth = newAzimuth;
                    lastUpdateAzimuth = newAzimuth;
                    updateARViewPositions();
                }
            }
        }
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
