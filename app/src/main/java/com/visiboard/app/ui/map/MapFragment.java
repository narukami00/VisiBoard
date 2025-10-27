package com.visiboard.app.ui.map;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.visiboard.app.R;

import org.json.JSONArray;
import org.json.JSONObject;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.OnMapReadyCallback;
import org.maplibre.android.maps.Style;
import org.maplibre.android.plugins.annotation.Symbol;
import org.maplibre.android.plugins.annotation.SymbolManager;
import org.maplibre.android.plugins.annotation.SymbolOptions;

import java.util.HashMap;
import java.util.Map;

public class MapFragment extends Fragment {

    private static final int LOCATION_PERMISSION_REQUEST = 100;
    private static final String PREFS_NAME = "notes_prefs";
    private static final String NOTES_KEY = "notes_array";

    private MapView mapView;
    private MapLibreMap mapLibreMap;
    private SymbolManager symbolManager;
    private Symbol userLocationSymbol;

    private FusedLocationProviderClient fusedLocationClient;
    private SharedPreferences sharedPreferences;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private boolean useCloudMode = true; // true: Firestore, false: SharedPreferences

    private final String GEOAPIFY_STYLE_URL =
            "https://maps.geoapify.com/v1/styles/osm-bright/style.json?apiKey=4034ef4942f146d6b43fd4a9871cfdc3";

    private static final String MARKER_ICON_ID_USER_LOCATION = "MARKER_ICON_USER_LOCATION";

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_map, container, false);

        mapView = view.findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        Switch switchMode = view.findViewById(R.id.switch_mode);
        switchMode.setChecked(useCloudMode); // initialize


        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(@NonNull MapLibreMap mapLibreMapReady) {
                mapLibreMap = mapLibreMapReady;
                mapLibreMap.setStyle(new Style.Builder().fromUri(GEOAPIFY_STYLE_URL), style -> {
                    style.addImage(MARKER_ICON_ID_USER_LOCATION, getBitmapFromVectorDrawable(R.drawable.ic_marker));

                    symbolManager = new SymbolManager(mapView, mapLibreMap, style);
                    symbolManager.setIconAllowOverlap(true);
                    symbolManager.setTextAllowOverlap(true);

                    enableUserLocation();
                    loadSavedNotes();

                    symbolManager.addClickListener(symbol -> {
                        if (symbol.getData() != null) {
                            try {
                                JSONObject data = new JSONObject(symbol.getData().toString());
                                String noteText = data.getString("note");
                                long timestamp = data.getLong("timestamp");
                                String docId = data.has("docId") ? data.getString("docId") : null;

                                showCustomInfoWindow(noteText, timestamp, symbol.getLatLng(), symbol, docId);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        return true;
                    });
                });
            }
        });

        // Floating button to add note
        view.findViewById(R.id.btnAddNote).setOnClickListener(v -> {
            if (ActivityCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(requireContext(), "Location permission not granted", Toast.LENGTH_SHORT).show();
                return;
            }

            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                    showAddNoteDialog(currentLatLng); // 👈 add note exactly at your location
                } else {
                    Toast.makeText(requireContext(), "Unable to get current location", Toast.LENGTH_SHORT).show();
                }
            });
        });

        // Switch between local and firestore
        switchMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            useCloudMode = isChecked;
            Toast.makeText(requireContext(), useCloudMode ? "Cloud Mode" : "Local Mode", Toast.LENGTH_SHORT).show();

            if (symbolManager != null) {
                symbolManager.deleteAll(); // clears all markers
            }

            // Reload notes
            loadSavedNotes();

            // Re-add user location marker
            enableUserLocation();
        });



        return view;
    }

    // Convert vector drawable to bitmap
    private Bitmap getBitmapFromVectorDrawable(int drawableId) {
        Drawable drawable = ContextCompat.getDrawable(requireContext(), drawableId);
        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    // Add note marker
    private void addNoteMarker(LatLng position, String fullNote, String shortNote, long timestamp, String docId) {
        if (symbolManager == null || mapLibreMap == null) return;

        View noteCardView = LayoutInflater.from(requireContext()).inflate(R.layout.note_card_layout, null);
        TextView noteTextView = noteCardView.findViewById(R.id.note_text_view);
        noteTextView.setText(shortNote);

        Bitmap noteBitmap = getBitmapFromView(noteCardView);
        String iconId = "note_icon_" + System.currentTimeMillis();
        mapLibreMap.getStyle().addImage(iconId, noteBitmap);

        try {
            JSONObject data = new JSONObject();
            data.put("note", fullNote);
            data.put("timestamp", timestamp);
            if (docId != null) data.put("docId", docId);
            Gson gson = new Gson();
            JsonElement jsonData = gson.fromJson(data.toString(), JsonElement.class);

            symbolManager.create(new SymbolOptions()
                    .withLatLng(position)
                    .withIconImage(iconId)
                    .withData(jsonData));
        } catch (Exception e) { e.printStackTrace(); }
    }

    // Convert view to bitmap
    private Bitmap getBitmapFromView(View view) {
        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
        Bitmap bitmap = Bitmap.createBitmap(view.getMeasuredWidth(), view.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        view.draw(canvas);
        return bitmap;
    }

    // Show info window with delete
    private void showCustomInfoWindow(String noteText, long timestamp, LatLng position, Symbol symbol, String docId) {
        View infoWindow = LayoutInflater.from(requireContext()).inflate(R.layout.custom_info_window, null);

        TextView noteTextView = infoWindow.findViewById(R.id.note_text);
        TextView timestampTextView = infoWindow.findViewById(R.id.note_timestamp);

        noteTextView.setText(noteText);
        timestampTextView.setText(new java.text.SimpleDateFormat("dd MMM yyyy • hh:mm a", java.util.Locale.getDefault())
                .format(new java.util.Date(timestamp)));

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setView(infoWindow)
                .setPositiveButton("Delete", (d, w) -> {
                    if (useCloudMode && docId != null) {
                        deleteNoteFirestore(docId);
                    } else {
                        deleteNoteLocally(position);
                    }
                    symbolManager.delete(symbol);
                })
                .setNegativeButton("Close", null)
                .create();

        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        dialog.show();
    }

    // Add note dialog
    private void showAddNoteDialog(LatLng position) {
        EditText editText = new EditText(requireContext());
        editText.setHint("Enter your note");

        FrameLayout container = new FrameLayout(requireContext());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = 50; params.rightMargin = 50;
        editText.setLayoutParams(params);
        container.addView(editText);

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Add Note")
                .setView(container)
                .setPositiveButton("Save", (d, w) -> {
                    String note = editText.getText().toString().trim();
                    if (!note.isEmpty()) {
                        long timestamp = System.currentTimeMillis();
                        String shortNote = note.length() > 30 ? note.substring(0, 30) + "..." : note;
                        addNoteMarker(position, note, shortNote, timestamp, null);
                        saveNote(position, note, timestamp);
                    } else {
                        Toast.makeText(requireContext(), "Note is empty!", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Save note
    private void saveNote(LatLng position, String note, long timestamp) {
        if (useCloudMode && auth.getCurrentUser() != null) {
            String uid = auth.getCurrentUser().getUid();
            Map<String, Object> noteMap = new HashMap<>();
            noteMap.put("lat", position.getLatitude());
            noteMap.put("lon", position.getLongitude());
            noteMap.put("note", note);
            noteMap.put("timestamp", timestamp);

            db.collection("users").document(uid).collection("notes")
                    .add(noteMap)
                    .addOnSuccessListener(docRef -> {
                        Log.d("MapFragment", "Note saved: " + docRef.getId());
                        // Update marker with docId
                        addNoteMarker(position, note, note.length() > 30 ? note.substring(0, 30) + "..." : note,
                                timestamp, docRef.getId());
                    })
                    .addOnFailureListener(e -> Log.e("MapFragment", "Error saving note: " + e.getMessage()));
        } else {
            saveNoteLocally(position, note, timestamp);
        }
    }

    private void saveNoteLocally(LatLng position, String note, long timestamp) {
        try {
            JSONArray array = new JSONArray(sharedPreferences.getString(NOTES_KEY, "[]"));
            JSONObject obj = new JSONObject();
            obj.put("lat", position.getLatitude());
            obj.put("lon", position.getLongitude());
            obj.put("note", note);
            obj.put("timestamp", timestamp);
            array.put(obj);
            sharedPreferences.edit().putString(NOTES_KEY, array.toString()).apply();
        } catch (Exception e) { e.printStackTrace(); }
    }

    // Delete note from Firestore
    private void deleteNoteFirestore(String docId) {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();
        db.collection("users").document(uid).collection("notes")
                .document(docId)
                .delete()
                .addOnSuccessListener(aVoid -> Log.d("MapFragment", "Note deleted from Firestore"))
                .addOnFailureListener(e -> Log.e("MapFragment", "Error deleting note: " + e.getMessage()));
    }

    // Delete local note
    private void deleteNoteLocally(LatLng position) {
        try {
            JSONArray array = new JSONArray(sharedPreferences.getString(NOTES_KEY, "[]"));
            JSONArray newArray = new JSONArray();
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                if (!(obj.getDouble("lat") == position.getLatitude() && obj.getDouble("lon") == position.getLongitude())) {
                    newArray.put(obj);
                }
            }
            sharedPreferences.edit().putString(NOTES_KEY, newArray.toString()).apply();
        } catch (Exception e) { e.printStackTrace(); }
    }

    // Load notes
    private void loadSavedNotes() {
        if (useCloudMode && auth.getCurrentUser() != null) {
            String uid = auth.getCurrentUser().getUid();
            db.collection("users").document(uid).collection("notes")
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        for (var doc : querySnapshot) {
                            double lat = doc.getDouble("lat");
                            double lon = doc.getDouble("lon");
                            String note = doc.getString("note");
                            long timestamp = doc.getLong("timestamp") != null ? doc.getLong("timestamp") : 0L;
                            LatLng pos = new LatLng(lat, lon);
                            addNoteMarker(pos, note, note.length() > 30 ? note.substring(0, 30) + "..." : note,
                                    timestamp, doc.getId());
                        }
                    })
                    .addOnFailureListener(e -> Log.e("MapFragment", "Error loading notes: " + e.getMessage()));
        } else {
            try {
                JSONArray array = new JSONArray(sharedPreferences.getString(NOTES_KEY, "[]"));
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    LatLng pos = new LatLng(obj.getDouble("lat"), obj.getDouble("lon"));
                    String note = obj.getString("note");
                    long timestamp = obj.has("timestamp") ? obj.getLong("timestamp") : 0L;
                    addNoteMarker(pos, note, note.length() > 30 ? note.substring(0, 30) + "..." : note, timestamp, null);
                }
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    // Enable user location
    private void enableUserLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST);
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null && mapLibreMap != null) {
                LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                mapLibreMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15));
                if (symbolManager != null) {
                    if (userLocationSymbol != null) symbolManager.delete(userLocationSymbol);
                    userLocationSymbol = symbolManager.create(new SymbolOptions()
                            .withLatLng(latLng)
                            .withIconImage(MARKER_ICON_ID_USER_LOCATION)
                            .withTextOffset(new Float[]{0f, -2.5f}));
                }
            }
        });
    }

    // Lifecycle
    @Override public void onStart() { super.onStart(); mapView.onStart(); }
    @Override public void onResume() { super.onResume(); mapView.onResume(); }
    @Override public void onPause() { super.onPause(); mapView.onPause(); }
    @Override public void onStop() { super.onStop(); mapView.onStop(); }
    @Override public void onLowMemory() { super.onLowMemory(); mapView.onLowMemory(); }
    @Override public void onDestroy() { super.onDestroy(); if (symbolManager != null) symbolManager.onDestroy(); mapView.onDestroy(); }
    @Override public void onSaveInstanceState(@NonNull Bundle outState) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState); }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) enableUserLocation();
            else Toast.makeText(requireContext(), "Permission denied.", Toast.LENGTH_SHORT).show();
        }
    }
}
