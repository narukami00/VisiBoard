package com.visiboard.app.ui.map;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.app.Activity;
import android.location.Location;
import android.location.Geocoder;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.view.animation.OvershootInterpolator;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.visiboard.app.R;
import com.visiboard.app.data.Comment;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.visiboard.app.data.UserInfo;
import com.visiboard.app.ui.map.LegendAdapter;
import com.visiboard.app.utils.UserCache;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;

import java.text.SimpleDateFormat;

import org.json.JSONArray;
import org.json.JSONObject;

import com.visiboard.app.ui.feed.FollowingAdapter;
import com.visiboard.app.data.NearbyNote;
import com.google.android.material.textfield.TextInputEditText;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import android.text.TextWatcher;
import android.text.Editable;
import android.widget.ImageButton;

import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.OnMapReadyCallback;
import org.maplibre.android.maps.Style;
import org.maplibre.android.plugins.annotation.Symbol;
import org.maplibre.android.plugins.annotation.SymbolManager;
import org.maplibre.android.plugins.annotation.SymbolOptions;
import org.maplibre.android.style.layers.HeatmapLayer;
import org.maplibre.android.style.layers.CircleLayer;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.geojson.Feature;
import org.maplibre.geojson.FeatureCollection;
import org.maplibre.geojson.Point;
import org.maplibre.geojson.LineString;
import org.maplibre.android.style.layers.LineLayer;
import static org.maplibre.android.style.layers.PropertyFactory.lineColor;
import static org.maplibre.android.style.layers.PropertyFactory.lineWidth;
import static org.maplibre.android.style.layers.PropertyFactory.lineCap;
import static org.maplibre.android.style.layers.PropertyFactory.lineJoin;
import org.maplibre.android.style.layers.Property;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Call;
import okhttp3.Callback;
import java.io.IOException;

import java.util.HashMap;
import java.util.Map;
import com.visiboard.app.ui.map.NoteOptionsBottomSheetFragment;

public class MapFragment extends Fragment {

    private static final String TAG = "MapFragment";
    private static final int LOCATION_PERMISSION_REQUEST = 100;
    private static final String PREFS_NAME = "notes_prefs";
    private static final String NOTES_KEY = "notes_array";

    // Nice vibrant colors for note cards
    private static final int[] NOTE_COLORS = {
            0xFF6C5CE7, // Purple
            0xFF74B9FF, // Sky Blue
            0xFF00B894, // Teal
            0xFFFF6B6B, // Coral Red
            0xFFFDCB6E, // Yellow
            0xFFE17055, // Orange
            0xFFA29BFE, // Light Purple
            0xFF55EFC4, // Mint
            0xFFFF7675, // Pink
            0xFFFD79A8, // Rose
            0xFF00CEC9, // Cyan
            0xFF81ECEC  // Aqua
    };

    private static final int[] NOTE_BORDER_COLORS = {
            0xFF5849C7, // Dark Purple
            0xFF5A9DE8, // Dark Sky Blue
            0xFF00966D, // Dark Teal
            0xFFE84545, // Dark Coral
            0xFFE9B949, // Dark Yellow
            0xFFCB5A3E, // Dark Orange
            0xFF8B7EE8, // Dark Light Purple
            0xFF3ACF98, // Dark Mint
            0xFFE85454, // Dark Pink
            0xFFE35B89, // Dark Rose
            0xFF00A8A5, // Dark Cyan
            0xFF5FD4D4  // Dark Aqua
    };

    private boolean isNavigating = false; // Flag to prevent location updates from overriding navigation

    private MapView mapView;
    private MapLibreMap mapLibreMap;
    private SymbolManager symbolManager;
    private Symbol userLocationSymbol;

    private FusedLocationProviderClient fusedLocationClient;
    private com.google.android.gms.location.LocationCallback locationCallback;
    private SharedPreferences sharedPreferences;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private com.google.firebase.firestore.ListenerRegistration notesListener;

    // UI Elements
    private FloatingActionButton fabMenu;
    private MaterialButton fabRecenter, fabFriends, fabHeatmap, fabRefresh, fabSatellite, fabHideMyNotes;
    private MaterialButton btnTimeTravel; // New Time Travel Button
    private LinearLayout fabMenuContainer;
    private boolean isFabMenuOpen = false;
    private boolean isHideMyNotesEnabled = false;

    private View cvLegendWidget;
    private ImageView ivLegendAvatar;
    private TextView tvLegendName;
    private LinearLayout llLegendContent;
    private android.widget.ProgressBar pbLegendLoading;

    // Map Style & Toggle
    private boolean isSatelliteEnabled = false;
    private final String GEOAPIFY_SATELLITE_STYLE_URL = "asset://satellite_style.json";


    private boolean useCloudMode = true; // true: Firestore, false: SharedPreferences

    private String currentMapStyle;
    private double currentZoom = 19.0;
    
    // Data Cache for seamless style switching
    private List<DocumentSnapshot> cachedNotesSnapshot;
    private CameraPosition savedCameraPosition;
    private boolean isStyleSwitching = false;

    // Navigation State
    private boolean isNavigatingToNote = false;
    private LatLng navigationDestination;
    private String pendingTargetNoteId;
    
    // Remote Drop
    private boolean isRemotePinPlaced = false;
    private LatLng remoteDropCoordinates;
    private ActivityResultLauncher<Intent> createNoteLauncher;
    private GeoJsonSource navigationRouteSource;
    private View cvNavigationOverlay;
    private TextView tvNavDistance, tvNavTime;
    private ImageView btnStopNav;
    private Location lastRouteFetchLocation;
    private static final float MIN_DISTANCE_FOR_RECALCULATION = 20.0f; // meters
    private OkHttpClient httpClient = new OkHttpClient();
    private static final String NAVIGATION_SOURCE_ID = "navigation-source";
    private static final String NAVIGATION_LAYER_ID = "navigation-layer";

    // Hidden notes from others (Hide for Me feature)
    private java.util.Set<String> hiddenNoteOtherIds = new java.util.HashSet<>();

    // Blocked users
    private java.util.Set<String> blockedUserIds = new java.util.HashSet<>();



    private final String GEOAPIFY_LIGHT_STYLE_URL =
            "https://maps.geoapify.com/v1/styles/osm-bright/style.json?apiKey=4034ef4942f146d6b43fd4a9871cfdc3";
    private final String GEOAPIFY_DARK_STYLE_URL =
            "https://maps.geoapify.com/v1/styles/dark-matter-dark-grey/style.json?apiKey=4034ef4942f146d6b43fd4a9871cfdc3";

    private static final String MARKER_ICON_ID_USER_LOCATION = "MARKER_ICON_USER_LOCATION";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize Create Note Launcher
        createNoteLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    resetRemoteDropState();
                    loadSavedNotes(); // Refresh notes on map
                }
            }
        );
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_map, container, false);

        mapView = view.findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Initialize New UI Elements
        fabMenu = view.findViewById(R.id.fab_menu);
        fabRecenter = view.findViewById(R.id.fab_recenter);
        fabFriends = view.findViewById(R.id.fab_friends);
        fabHeatmap = view.findViewById(R.id.fab_heatmap);
        fabSatellite = view.findViewById(R.id.fab_satellite);
        fabHideMyNotes = view.findViewById(R.id.fab_hide_my_notes);
        fabRefresh = view.findViewById(R.id.fab_refresh);
        fabMenuContainer = view.findViewById(R.id.fab_menu_container);

        btnTimeTravel = view.findViewById(R.id.btn_time_travel);
        setupTimeTravelButton();

        cvLegendWidget = view.findViewById(R.id.cv_legend_widget);
        ivLegendAvatar = view.findViewById(R.id.iv_legend_avatar);
        tvLegendName = view.findViewById(R.id.tv_legend_name);
        llLegendContent = view.findViewById(R.id.ll_legend_content);
        pbLegendLoading = view.findViewById(R.id.pb_legend_loading);

        // Navigation UI
        cvNavigationOverlay = view.findViewById(R.id.cv_navigation_overlay);
        tvNavDistance = view.findViewById(R.id.tv_nav_distance);
        tvNavTime = view.findViewById(R.id.tv_nav_time);
        btnStopNav = view.findViewById(R.id.btn_stop_nav);

        btnStopNav.setOnClickListener(v -> stopNavigation());

        // FAB Menu Interaction
        fabMenu.setOnClickListener(v -> toggleFabMenu());
        
        // Apply Press Effects to all interactive buttons
        addPressEffect(fabMenu);
        addPressEffect(fabRecenter);
        addPressEffect(fabFriends);
        addPressEffect(fabHeatmap);
        addPressEffect(fabSatellite);
        addPressEffect(fabRefresh);
        addPressEffect(btnTimeTravel);
        addPressEffect(btnStopNav);

        fabFriends.setOnClickListener(v -> {
            performHapticClick(v);
            toggleFriendsRadar(!isFriendsRadarEnabled);
        });
        fabHeatmap.setOnClickListener(v -> {
            performHapticClick(v);
            toggleHeatmap(!isHeatmapEnabled);
        });
        fabSatellite.setOnClickListener(v -> {
            performHapticClick(v);
            toggleSatelliteMode(!isSatelliteEnabled);
        });
        fabHideMyNotes.setOnClickListener(v -> {
            performHapticClick(v);
            toggleHideMyNotes(!isHideMyNotesEnabled);
        });
        
        checkPermissions();

        fabRefresh.setOnClickListener(v -> {
            performHapticClick(v);
            if (getView() != null) com.visiboard.app.utils.UiHelper.showInfo(getView(), "Refreshing notes...");
            // Preserve userLocationSymbol when refreshing
            deleteAllSymbolsExceptUserLocation();
            // Ensure userLocationSymbol is recreated if it was accidentally removed
            if (userLocationSymbol == null && fusedLocationClient != null) {
                fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                    if (location != null) {
                        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                        updateUserLocationMarker(latLng);
                    }
                });
            }
            loadSavedNotes();
            toggleFabMenu(); // Close after action
        });

        fabRecenter.setOnClickListener(v -> {
            performHapticClick(v);
            if (mapLibreMap != null && fusedLocationClient != null) {
                if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                        if (location != null) {
                            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                            mapLibreMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 19.0));
                            updateUserLocationMarker(latLng);
                            updateUserLocationInFirestore(latLng); // Save location when recentered
                        } else {
                            if (userLocationSymbol != null) {
                                mapLibreMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLocationSymbol.getLatLng(), 19.0));
                            } else {
                                if (getView() != null) com.visiboard.app.utils.UiHelper.showInfo(getView(), "Waiting for location...");
                            }
                        }
                    });
                }
            }
            toggleFabMenu(); // Close after action
        });

        // Setup Widget Click - Launch Leaderboard Activity
        cvLegendWidget.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), LeaderboardActivity.class);
            startActivity(intent);
        });

        // Determine map style based on theme
        boolean isDarkMode = com.visiboard.app.utils.ThemeManager.getInstance(requireContext()).isDarkMode();
        currentMapStyle = isDarkMode ? GEOAPIFY_DARK_STYLE_URL : GEOAPIFY_LIGHT_STYLE_URL;

        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(@NonNull MapLibreMap mapLibreMapReady) {
                mapLibreMap = mapLibreMapReady;
                
                // Load non-map dependent data once
                loadFollowingList(); 
                loadBlockedUsers();
                loadLegends(); 

                // Set initial style
                mapLibreMap.setStyle(new Style.Builder().fromUri(currentMapStyle), style -> setupMapStyle(style, true));
            }
        });

        // Initialize Remote Drop Button
        View btnRemoteDrop = view.findViewById(R.id.btnRemoteDrop);
        addPressEffect(btnRemoteDrop);
        
        btnRemoteDrop.setOnClickListener(v -> {
            performHapticClick(v);
            if (isRemotePinPlaced) {
                resetRemoteDropState();
            } else {
                android.view.animation.Animation shake = android.view.animation.AnimationUtils.loadAnimation(requireContext(), R.anim.pin_jump);
                v.startAnimation(shake);
                if (getView() != null) com.visiboard.app.utils.UiHelper.showInfo(getView(), "Hold and drag to place a remote note!");
            }
        });

        btnRemoteDrop.setOnLongClickListener(v -> {
            if (isRemotePinPlaced) return true; // Disable drag if already placed
            
            performHapticClick(v);
            
            // Create drag shadow
            View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(v) {
                @Override
                public void onProvideShadowMetrics(android.graphics.Point outShadowSize, android.graphics.Point outShadowTouchPoint) {
                    outShadowSize.set(v.getWidth(), v.getHeight());
                    outShadowTouchPoint.set(v.getWidth() / 2, v.getHeight() / 2);
                }
                
                @Override
                public void onDrawShadow(Canvas canvas) {
                    // Draw the icon as shadow
                    Drawable icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_remote_drop);
                    if (icon != null) {
                        icon.setBounds(0, 0, v.getWidth(), v.getHeight());
                        icon.setTint(getResources().getColor(R.color.text_primary));
                        icon.draw(canvas);
                    }
                }
            };
            
            // Hide icon immediately (empty box effect)
            if (v instanceof androidx.cardview.widget.CardView) {
                androidx.cardview.widget.CardView cv = (androidx.cardview.widget.CardView) v;
                if (cv.getChildCount() > 0) cv.getChildAt(0).setVisibility(View.INVISIBLE);
            }
            
            v.startDragAndDrop(null, shadowBuilder, null, 0);
            return true;
        });
        
        // Handle Drop on Map
        view.setOnDragListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.DragEvent.ACTION_DROP:
                    if (mapLibreMap != null) {
                        float x = event.getX();
                        float y = event.getY(); // Adjust for offset if needed
                        
                        // Check if dropped back onto the button (Cancel)
                        View btn = view.findViewById(R.id.btnRemoteDrop);
                        android.graphics.Rect hitRect = new android.graphics.Rect();
                        btn.getHitRect(hitRect);
                        if (hitRect.contains((int)x, (int)y)) {
                             // Restore Icon Visibility (Snap Back)
                             if (btn instanceof androidx.cardview.widget.CardView) {
                                 androidx.cardview.widget.CardView cv = (androidx.cardview.widget.CardView) btn;
                                 if (cv.getChildCount() > 0) cv.getChildAt(0).setVisibility(View.VISIBLE);
                             }
                             return true; // Cancelled
                        }

                        // Convert screen point to LatLng
                        LatLng latLng = mapLibreMap.getProjection().fromScreenLocation(new android.graphics.PointF(x, y));
                        handleRemoteDrop(latLng);
                    }
                    return true;
                case android.view.DragEvent.ACTION_DRAG_STARTED:
                    return true;
            }
            return false;
        });

        // Floating button to add note
        com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton btnAddNote = view.findViewById(R.id.btnAddNote);
        this.btnAddNoteRef = btnAddNote; // Save reference
        addPressEffect(btnAddNote);
        btnAddNote.setOnClickListener(v -> {
            performHapticClick(v);
            if (ActivityCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                if (getView() != null) com.visiboard.app.utils.UiHelper.showWarning(getView(), "Location permission not granted");
                return;
            }

            Intent intent = new Intent(requireContext(), com.visiboard.app.ui.create.CreateNoteActivity.class);
            if (remoteDropCoordinates != null) {
                 intent.putExtra("isRemote", true);
                 intent.putExtra("lat", remoteDropCoordinates.getLatitude());
                 intent.putExtra("lon", remoteDropCoordinates.getLongitude());
            }
            createNoteLauncher.launch(intent);
        });





        return view;
    }
    
    private com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton btnAddNoteRef;
    private LatLng remoteDropLatLng;
    private Symbol remoteDropSymbol;
    
    private void handleRemoteDrop(LatLng latLng) {
        if (mapLibreMap == null || symbolManager == null) return;
        
        isRemotePinPlaced = true;
        remoteDropCoordinates = latLng;

        // Add Pin at Drop Location
        SymbolOptions options = new SymbolOptions()
                .withLatLng(latLng)
                .withIconImage("remote_drop_pin")
                .withIconSize(1.5f);
        symbolManager.create(options);
        
        // Update Button UI to 'X' (Cancel)
        View btn = getView().findViewById(R.id.btnRemoteDrop);
        if (btn instanceof androidx.cardview.widget.CardView) {
             androidx.cardview.widget.CardView cv = (androidx.cardview.widget.CardView) btn;
             if (cv.getChildCount() > 0) {
                 ImageView iconView = (ImageView) cv.getChildAt(0);
                 iconView.setImageResource(R.drawable.ic_close); // Show X
                 iconView.setVisibility(View.VISIBLE);
             }
        }

        // Update 'Add Note' Button
        ExtendedFloatingActionButton btnAddNote = getView().findViewById(R.id.btnAddNote);
        btnAddNote.setText("Post Remote Note");
        btnAddNote.setIconResource(R.drawable.ic_remote_drop);
        btnAddNote.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.accent)));
        
        // Show Animation/Feedback
        // Toast.makeText(requireContext(), "Location set! Tap 'Post Remote Note'", Toast.LENGTH_SHORT).show();
    }
    
    private void resetRemoteDropState() {
        isRemotePinPlaced = false;
        remoteDropCoordinates = null;
        
        // Remove temporary pin (reload notes which clears temp symbols)
        renderNotesFromCache(); 
        
        // Reset Button UI
        View btn = getView().findViewById(R.id.btnRemoteDrop);
        if (btn instanceof androidx.cardview.widget.CardView) {
             androidx.cardview.widget.CardView cv = (androidx.cardview.widget.CardView) btn;
             if (cv.getChildCount() > 0) {
                 ImageView iconView = (ImageView) cv.getChildAt(0);
                 iconView.setImageResource(R.drawable.ic_remote_drop); // Reset to Pin
                 iconView.setVisibility(View.VISIBLE);
                 
                 // Animate "Return" (Pop in)
                 ScaleAnimation scaleAnim = new ScaleAnimation(
                     0f, 1f, 0f, 1f, 
                     Animation.RELATIVE_TO_SELF, 0.5f, 
                     Animation.RELATIVE_TO_SELF, 0.5f
                 );
                 scaleAnim.setDuration(300);
                 scaleAnim.setInterpolator(new OvershootInterpolator());
                 iconView.startAnimation(scaleAnim);
             }
        }
        
        // Reset Add Note Button
        ExtendedFloatingActionButton btnAddNote = getView().findViewById(R.id.btnAddNote);
        btnAddNote.setText("Add Note");
        btnAddNote.setIconResource(R.drawable.ic_add);
        btnAddNote.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.primary)));
        
        // Toast.makeText(requireContext(), "Remote drop cancelled", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Listen for requests to close note window from other fragments
        getParentFragmentManager().setFragmentResultListener("close_note_window", this, (requestKey, bundle) -> {
            if (bundle.getBoolean("close_note_window", false)) {
                if (currentNoteDialog != null && currentNoteDialog.isShowing()) {
                    currentNoteDialog.dismiss();
                    currentNoteDialog = null;
                }
            }
        });
        
        // Listen for navigation requests when fragment is already visible
        getParentFragmentManager().setFragmentResultListener("navigate_to_note", this, (requestKey, bundle) -> {
            String targetNoteId = bundle.getString("note_id");
            if (targetNoteId != null) {
                double lat = bundle.getDouble("latitude", 0);
                double lng = bundle.getDouble("longitude", 0);
                boolean openWindow = bundle.getBoolean("open_note_window", false);
                navigateToNote(new LatLng(lat, lng), targetNoteId, openWindow);
            }
        });
        
        // Handle Navigation Arguments
        if (getArguments() != null) {
            String targetNoteId = getArguments().getString("note_id");
            if (targetNoteId != null) {
                double lat = getArguments().getDouble("latitude", 0);
                double lng = getArguments().getDouble("longitude", 0);
                boolean openWindow = getArguments().getBoolean("open_note_window", false);
                
                // Save for use when map is ready
                navigateToNote(new LatLng(lat, lng), targetNoteId, openWindow);
                
                // Clear arguments to prevent re-navigation on config changes
                getArguments().remove("note_id");
            }
        }
    }

    private void navigateToNote(LatLng position, String noteId, boolean openWindow) {
        // Store for later use
        this.pendingTargetNoteId = noteId;
        this.pendingOpenWindow = openWindow;
    }

    private boolean pendingOpenWindow;
    private androidx.appcompat.app.AlertDialog currentNoteDialog;
    
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
    private void addNoteMarker(LatLng position, String fullNote, String shortNote, long timestamp, String docId, String userId, boolean hasImage, boolean isVirtual) {
        if (symbolManager == null || mapLibreMap == null) return;

        View noteCardView;
        long timeDiff = System.currentTimeMillis() - timestamp;
        boolean isNewNote = timeDiff < 3600000; // 1 hour in milliseconds

        if (isNewNote) {
             noteCardView = LayoutInflater.from(requireContext()).inflate(R.layout.note_card_glow_layout, null);
             // Apply Vibrant Gradient Glow
             applyGradientGlow(noteCardView);
        } else {
             noteCardView = LayoutInflater.from(requireContext()).inflate(R.layout.note_card_layout, null);
        }

        TextView noteTextView = noteCardView.findViewById(R.id.note_text_view);
        noteTextView.setText(shortNote);

        // Generate random color index based on note ID or timestamp
        int colorIndex = (docId != null ? docId.hashCode() : (int) timestamp) % NOTE_COLORS.length;
        if (colorIndex < 0) colorIndex = -colorIndex;

        // Apply random colors
        int backgroundColor = NOTE_COLORS[colorIndex];
        int borderColor = NOTE_BORDER_COLORS[colorIndex];

        // Create gradient drawable programmatically
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(16 * getResources().getDisplayMetrics().density);
        drawable.setColor(backgroundColor);
        drawable.setStroke((int)(2 * getResources().getDisplayMetrics().density), borderColor);
        noteTextView.setBackground(drawable);
        noteTextView.setTextColor(0xFFFFFFFF); // White text for better contrast

        Bitmap noteBitmap = getBitmapFromView(noteCardView);
        String iconId = "note_icon_" + System.currentTimeMillis() + "_" + (docId != null ? docId : timestamp);
        mapLibreMap.getStyle().addImage(iconId, noteBitmap);

        try {
            JSONObject data = new JSONObject();
            data.put("note", fullNote);
            data.put("timestamp", timestamp);
            if (docId != null) data.put("docId", docId);
            if (userId != null) data.put("userId", userId);
            data.put("hasImage", hasImage);
            data.put("isVirtual", isVirtual);

            Gson gson = new Gson();
            JsonElement jsonData = gson.fromJson(data.toString(), JsonElement.class);

            symbolManager.create(new SymbolOptions()
                    .withLatLng(position)
                    .withIconImage(iconId)
                    .withData(jsonData));

            // Check if this is the pending target note (Share Guard Success)
            if (docId != null && docId.equals(pendingTargetNoteId)) {
                if (pendingOpenWindow) {
                    showCustomInfoWindow(fullNote, timestamp, position, null, docId, userId, null, hasImage, isVirtual);
                }
                pendingTargetNoteId = null; // Mark as found
                pendingOpenWindow = false;
            }

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

    // Show info window with delete, like, and comment
    private void showCustomInfoWindow(String noteText, long timestamp, LatLng position, Symbol symbol, String docId, String noteOwnerId, String imageBase64, boolean hasImage, boolean isVirtual) {
        View infoWindow = LayoutInflater.from(requireContext()).inflate(R.layout.custom_info_window, null);

        // Find views
        de.hdodenhof.circleimageview.CircleImageView ownerProfilePic = infoWindow.findViewById(R.id.owner_profile_pic);
        TextView ownerName = infoWindow.findViewById(R.id.owner_name);
        TextView noteTextView = infoWindow.findViewById(R.id.note_text);
        TextView timestampTextView = infoWindow.findViewById(R.id.note_timestamp);
        LinearLayout ownerSection = infoWindow.findViewById(R.id.owner_section);
        android.widget.Button btnFollowOwner = infoWindow.findViewById(R.id.btn_follow_owner);
        LinearLayout interactionSection = infoWindow.findViewById(R.id.interaction_section);
        LinearLayout likeSection = infoWindow.findViewById(R.id.like_section);
        ImageView btnLike = infoWindow.findViewById(R.id.btn_like);
        TextView tvLikeCount = infoWindow.findViewById(R.id.tv_like_count);
        LinearLayout commentSection = infoWindow.findViewById(R.id.comment_section);
        ImageView btnComment = infoWindow.findViewById(R.id.btn_comment);
        TextView tvCommentCount = infoWindow.findViewById(R.id.tv_comment_count);
        android.widget.Button btnGoToNote = infoWindow.findViewById(R.id.btn_go_to_note);
        ImageView ivRemoteIndicator = infoWindow.findViewById(R.id.iv_remote_indicator);

        if (isVirtual) {
            ivRemoteIndicator.setVisibility(View.VISIBLE);
        } else {
            ivRemoteIndicator.setVisibility(View.GONE);
        }
        
        // Show Go button if this window was opened from saved notes
        if (pendingTargetNoteId != null && docId != null && docId.equals(pendingTargetNoteId)) {
            btnGoToNote.setVisibility(View.VISIBLE);
            btnGoToNote.setOnClickListener(v -> {
                if (mapLibreMap != null && position != null) {
                    // Close the dialog first
                    if (currentNoteDialog != null && currentNoteDialog.isShowing()) {
                        currentNoteDialog.dismiss();
                    }
                    
                    // Then navigate
                    isNavigating = true;
                    CameraPosition cameraPosition = new CameraPosition.Builder()
                        .target(position)
                        .zoom(18.0)
                        .build();
                    mapLibreMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 1500);
                    if (getView() != null) com.visiboard.app.utils.UiHelper.showInfo(getView(), "Navigating to note...");
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        isNavigating = false;
                    }, 2000);
                }
            });
            // Clear the pending flag
            pendingTargetNoteId = null;
        } else {
            btnGoToNote.setVisibility(View.GONE);
        }
        
        // Removed saveSection, btnSave, btnEdit, btnVisibility as they are now in the bottom sheet or removed
        androidx.cardview.widget.CardView imageContainer = infoWindow.findViewById(R.id.cv_note_image_container);
        ImageView noteImage = infoWindow.findViewById(R.id.iv_note_image);
        com.facebook.shimmer.ShimmerFrameLayout shimmer = infoWindow.findViewById(R.id.shimmer_view_container);

        final String[] currentBase64Wrapper = {imageBase64};

        if (imageBase64 != null && !imageBase64.isEmpty()) {
            imageContainer.setVisibility(View.VISIBLE);
            noteImage.setVisibility(View.VISIBLE);
            // Stop and hide shimmer immediately
            shimmer.stopShimmer();
            shimmer.setVisibility(View.GONE);

            // Decode Base64
            try {
                byte[] decodedString = android.util.Base64.decode(imageBase64, android.util.Base64.DEFAULT);
                Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                noteImage.setImageBitmap(decodedByte);

                // Aspect Ratio Toggle logic
                noteImage.setOnClickListener(v -> {
                    androidx.constraintlayout.widget.ConstraintLayout.LayoutParams params =
                            (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) noteImage.getLayoutParams();

                    String currentRatio = params.dimensionRatio;
                    // Start cycle
                    String nextRatio = "1:1";
                    if ("1:1".equals(currentRatio)) nextRatio = "4:3";
                    else if ("4:3".equals(currentRatio)) nextRatio = "16:9";
                    else if ("16:9".equals(currentRatio)) nextRatio = "3:4";
                    else if ("3:4".equals(currentRatio)) nextRatio = "9:16";
                    else if ("9:16".equals(currentRatio)) nextRatio = "1:1"; // Loop back

                    params.dimensionRatio = nextRatio;
                    noteImage.setLayoutParams(params);
                    // Toast.makeText(requireContext(), "Ratio: " + nextRatio, Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                e.printStackTrace();
                imageContainer.setVisibility(View.GONE);
            }
        } else if (docId != null && useCloudMode) {
            // Fetch from cloud
            if (hasImage) {
                imageContainer.setVisibility(View.VISIBLE);
                shimmer.setVisibility(View.VISIBLE);
                shimmer.startShimmer();
            } else {
                imageContainer.setVisibility(View.GONE);
                shimmer.setVisibility(View.GONE);
                shimmer.stopShimmer();
            }
            db.collection("notes").document(docId).get().addOnSuccessListener(doc -> {
                String base64 = doc.getString("imageBase64");
                if (base64 != null) {
                    currentBase64Wrapper[0] = base64;
                }
                // Check backward compatibility
                shimmer.stopShimmer();
                shimmer.setVisibility(View.GONE);

                if (base64 != null && !base64.isEmpty()) {
                    noteImage.setVisibility(View.VISIBLE);
                    try {
                        byte[] decodedString = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
                        Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                        noteImage.setImageBitmap(decodedByte);

                        noteImage.setOnClickListener(v -> {
                            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams params =
                                    (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) noteImage.getLayoutParams();
                            String currentRatio = params.dimensionRatio;
                            String nextRatio = "1:1";
                            if ("1:1".equals(currentRatio)) nextRatio = "4:3";
                            else if ("4:3".equals(currentRatio)) nextRatio = "16:9";
                            else if ("16:9".equals(currentRatio)) nextRatio = "3:4";
                            else if ("3:4".equals(currentRatio)) nextRatio = "9:16";
                            else if ("9:16".equals(currentRatio)) nextRatio = "1:1";
                            params.dimensionRatio = nextRatio;
                            noteImage.setLayoutParams(params);
                    // com.visiboard.app.utils.UiHelper.showInfo(requireView(), "Ratio: " + nextRatio);
                        });
                    } catch (Exception e) { e.printStackTrace(); }
                } else {
                    imageContainer.setVisibility(View.GONE);
                }
                shimmer.setVisibility(View.GONE);
                shimmer.stopShimmer();
            }).addOnFailureListener(e -> {
                shimmer.setVisibility(View.GONE);
                shimmer.stopShimmer();
                imageContainer.setVisibility(View.GONE);
            });

        } else {
            imageContainer.setVisibility(View.GONE);
            shimmer.setVisibility(View.GONE);
            shimmer.stopShimmer();
        }


        // Saved status logic moved to Bottom Sheet invocation
        // ... but we might want to pre-fetch it efficiently or just fetch on "More" click.
        // For now, let's keep the timestamp formatting.

        noteTextView.setText(noteText);
        timestampTextView.setText(new SimpleDateFormat("dd MMM yyyy • hh:mm a", java.util.Locale.getDefault())
                .format(new java.util.Date(timestamp)));

        // Check if current user owns this note
        String currentUserId = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
        boolean isOwner = currentUserId != null && currentUserId.equals(noteOwnerId);

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setView(infoWindow)
                .setView(infoWindow)
                .setNegativeButton("Close", (d, w) -> d.dismiss());

        androidx.appcompat.app.AlertDialog dialog = builder.create();
        
        // Store current dialog reference to allow closing from other fragments
        currentNoteDialog = dialog;
        
        dialog.setOnShowListener(d -> {
            Button closeBtn = ((androidx.appcompat.app.AlertDialog)d).getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE);
            if (closeBtn != null) {
                closeBtn.setTextColor(android.graphics.Color.WHITE);
            }
        });
        
        dialog.setOnDismissListener(d -> {
            currentNoteDialog = null;
        });

        // Show More Options button for EVERYONE (controls what options are shown)
        android.widget.ImageButton btnMoreOptions = infoWindow.findViewById(R.id.btn_more_options);
        if (useCloudMode && docId != null) { // Only for cloud notes
            btnMoreOptions.setVisibility(View.VISIBLE);
            btnMoreOptions.setOnClickListener(v -> {
                // Determine saved state before opening logic, OR fetch it inside the fragment. 
                // For smoother UI, maybe fetch here or pass unknown.
                // Let's pass checking to fragment? 
                // Actually, our fragment arguments expect the boolean.
                
                String myId = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
                if (myId == null) return;
                
                // Optimized: Show Bottom Sheet immediately, let it fetch data
                NoteOptionsBottomSheetFragment bottomSheet = NoteOptionsBottomSheetFragment.newInstance(
                        docId,
                        isOwner
                );
                
                bottomSheet.setListener(new NoteOptionsBottomSheetFragment.NoteOptionsListener() {
                    @Override
                    public void onSaveNote(String noteId) {
                        // We don't have isSaved status here anymore, so pass a dummy/current guess or handle in helper
                        // But helper needs knows previous state for toggle?
                        // Actually handleSaveNote checks existence usually? No, it toggles.
                        // We should probably trust the BottomSheet to know the state?
                        // Or just let handleSaveNote re-fetch to be safe (it's an action, latency is ok-ish)
                        handleSaveNote(noteId, false, noteText, timestamp, noteOwnerId, position, currentBase64Wrapper[0]);
                    }

                    @Override
                    public void onHideNote(String noteId) {
                        handleHideNote(noteId, isOwner);
                    }

                    @Override
                    public void onEditNote(String noteId) {
                        handleEditNote(noteId, noteTextView.getText().toString(), currentBase64Wrapper[0], dialog);
                    }

                    @Override
                    public void onDeleteNote(String noteId) {
                        handleDeleteNote(noteId, noteOwnerId, position, symbol, dialog);
                    }
                    
                    @Override
                    public void onReportNote(String id) {
                        com.visiboard.app.ui.report.ReportBottomSheetFragment reportSheet =
                                com.visiboard.app.ui.report.ReportBottomSheetFragment.newInstance(
                                        docId,
                                        noteText,
                                        "NOTE",
                                        position.getLatitude(),
                                        position.getLongitude()
                                );
                        reportSheet.show(getParentFragmentManager(), "ReportBottomSheet");
                        dialog.dismiss();
                    }
                    
                    @Override
                    public void onToggleComments(String id) {
                        // Pass current state? We don't know it here. Button will likely just call toggle.
                        // Ideally we pass the boolean. 
                        // Let's assume the BottomSheet knows the state when it calls this?
                        // BottomSheet doesn't pass the boolean back. 
                        // Checking update helper... it just updates the field.
                        handleToggleComments(id, false); // Disabled arg ignored/handled inside
                    }
                });
                
                bottomSheet.show(getParentFragmentManager(), "NoteOptionsBottomSheet"); 
            });
        }

        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        // Load owner info and show interactions only in cloud mode
        if (useCloudMode && noteOwnerId != null) {
            // Load owner info
            db.collection("users").document(noteOwnerId).get()
                    .addOnSuccessListener(userDoc -> {
                        if (userDoc.exists()) {
                            String name = userDoc.getString("name");
                            ownerName.setText(name != null ? name : "Anonymous");

                            String pic = userDoc.getString("profilePic");
                            if (pic != null && !pic.isEmpty()) {
                                try {
                                    byte[] bytes = android.util.Base64.decode(pic, android.util.Base64.DEFAULT);
                                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                                    ownerProfilePic.setImageBitmap(bitmap);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    });

            // Make owner section clickable to show user info
            ownerSection.setOnClickListener(v -> showUserInfoDialog(noteOwnerId));

            // Show follow button only if viewing someone else's note
            if (!isOwner && currentUserId != null) {
                btnFollowOwner.setVisibility(View.VISIBLE);

                // Check if already following
                db.collection("users").document(currentUserId)
                        .collection("following").document(noteOwnerId)
                        .get()
                        .addOnSuccessListener(doc -> {
                            if (doc.exists()) {
                                btnFollowOwner.setText("Following");
                                btnFollowOwner.setBackgroundResource(R.drawable.btn_following_selector);
                                btnFollowOwner.setTextColor(getResources().getColor(R.color.button_text_following, null));
                            } else {
                                // Check if requested
                                db.collection("users").document(noteOwnerId)
                                        .collection("follow_requests").document(currentUserId)
                                        .get()
                                        .addOnSuccessListener(requestDoc -> {
                                            if (requestDoc.exists()) {
                                                btnFollowOwner.setText("Requested");
                                                btnFollowOwner.setBackgroundResource(R.drawable.btn_following_selector);
                                                btnFollowOwner.setTextColor(getResources().getColor(R.color.button_text_following, null));
                                            } else {
                                                btnFollowOwner.setText("Follow");
                                                btnFollowOwner.setBackgroundResource(R.drawable.btn_primary_selector);
                                                btnFollowOwner.setTextColor(getResources().getColor(R.color.button_text_primary, null));
                                            }
                                        });
                            }
                        });

                btnFollowOwner.setOnClickListener(v -> {
                    performHapticClick(v);
                    String text = btnFollowOwner.getText().toString();
                    if (text.equals("Follow")) {
                        followUser(noteOwnerId, btnFollowOwner);
                    } else if (text.equals("Requested")) {
                        cancelFollowRequest(noteOwnerId, btnFollowOwner);
                    } else {
                        showUnfollowConfirmation(noteOwnerId, btnFollowOwner);
                    }
                });
                addPressEffect(btnFollowOwner);
            }

            // Show interaction section and load likes/comments
            if (docId != null && currentUserId != null) {
                interactionSection.setVisibility(View.VISIBLE);
                DocumentReference noteRef = db.collection("notes").document(docId);

                // Track if like button is being processed to prevent double-clicks
                final boolean[] isProcessingLike = {false};

                // Load like count and check if user liked
                noteRef.get().addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Long likeCount = doc.getLong("likeCount");
                        tvLikeCount.setText(String.valueOf(likeCount != null ? likeCount : 0));

                        java.util.List<String> likedBy = (java.util.List<String>) doc.get("likedBy");
                        boolean isLiked = likedBy != null && likedBy.contains(currentUserId);
                        btnLike.setImageResource(isLiked ? R.drawable.ic_heart : R.drawable.ic_heart_outline);

                        // Load comment count
                        Long commentCount = doc.getLong("commentsCount");
                        tvCommentCount.setText(String.valueOf(commentCount != null ? commentCount : 0));
                    }
                });


                addPressEffect(likeSection);
                // Like button click with double-click prevention using Firestore transaction
                likeSection.setOnClickListener(v -> {
                    if (isProcessingLike[0]) return; // Prevent double-click
                    isProcessingLike[0] = true;

                    // Optimistic UI update
                    performHapticClick(v);
                    
                    noteRef.get().addOnSuccessListener(doc -> {
                        java.util.List<String> likedBy = (java.util.List<String>) doc.get("likedBy");
                        boolean isLiked = likedBy != null && likedBy.contains(currentUserId);
                        boolean willLike = !isLiked;

                        // Update UI immediately
                        if (willLike) {
                            btnLike.setImageResource(R.drawable.ic_heart);
                            animateLike(btnLike);
                            int count = Integer.parseInt(tvLikeCount.getText().toString());
                            tvLikeCount.setText(String.valueOf(count + 1));
                        } else {
                            btnLike.setImageResource(R.drawable.ic_heart_outline);
                            int count = Integer.parseInt(tvLikeCount.getText().toString());
                            tvLikeCount.setText(String.valueOf(Math.max(0, count - 1)));
                        }

                        // Perform update with transaction to prevent race conditions
                        db.runTransaction(transaction -> {
                            com.google.firebase.firestore.DocumentSnapshot snapshot = transaction.get(noteRef);
                            java.util.List<String> currentLikedBy = (java.util.List<String>) snapshot.get("likedBy");
                            boolean currentlyLiked = currentLikedBy != null && currentLikedBy.contains(currentUserId);

                            if (currentlyLiked && willLike) {
                                // Already liked, user is trying to like again - do nothing
                                return null;
                            } else if (!currentlyLiked && !willLike) {
                                // Not liked, user is trying to unlike - do nothing
                                return null;
                            }

                            if (willLike) {
                                transaction.update(noteRef, "likedBy", FieldValue.arrayUnion(currentUserId));
                                transaction.update(noteRef, "likeCount", FieldValue.increment(1));
                            } else {
                                transaction.update(noteRef, "likedBy", FieldValue.arrayRemove(currentUserId));
                                transaction.update(noteRef, "likeCount", FieldValue.increment(-1));
                            }
                            return null;
                        }).addOnSuccessListener(aVoid -> {
                            isProcessingLike[0] = false;
                            if (willLike && !isOwner) {
                                createNotification(noteOwnerId, currentUserId, "like", docId, noteText, position);
                            }
                        }).addOnFailureListener(e -> {
                            // Revert UI on failure
                            if (willLike) {
                                btnLike.setImageResource(R.drawable.ic_heart_outline);
                                int count = Integer.parseInt(tvLikeCount.getText().toString());
                                tvLikeCount.setText(String.valueOf(Math.max(0, count - 1)));
                            } else {
                                btnLike.setImageResource(R.drawable.ic_heart);
                                int count = Integer.parseInt(tvLikeCount.getText().toString());
                                tvLikeCount.setText(String.valueOf(count + 1));
                            }
                            isProcessingLike[0] = false;
                            if (getView() != null) com.visiboard.app.utils.UiHelper.showError(getView(), "Failed to update like");
                        });
                    }).addOnFailureListener(e -> isProcessingLike[0] = false);
                });
                addPressEffect(likeSection);

                // Comment button click
                commentSection.setOnClickListener(v -> {
                    // Check disabled status (allowing open, but handled in fragment)
                    db.collection("notes").document(docId).get().addOnSuccessListener(noteSn -> {
                        Boolean disabled = noteSn.getBoolean("commentsDisabled");
                        boolean isCommentsDisabled = disabled != null && disabled;
                        
                        // Open bottom sheet
                        CommentsBottomSheetFragment bottomSheet = CommentsBottomSheetFragment.newInstance(
                                docId, noteOwnerId, noteText, position.getLatitude(), position.getLongitude(), isCommentsDisabled
                        );
    
                        // Set listener to show user info when a profile pic is clicked in comments
                        bottomSheet.setOnUserClickListener(this::showUserInfoDialog);
    
                        bottomSheet.show(getParentFragmentManager(), "CommentsBottomSheet");
                    });
                });
                addPressEffect(commentSection);

                // Share button click
                LinearLayout shareSection = infoWindow.findViewById(R.id.share_section);
                shareSection.setOnClickListener(v -> {
                    NearbyNote tempNote = new NearbyNote();

                    // Populate tempNote with available data
                    tempNote.setId(docId);
                    tempNote.setText(noteText);
                    tempNote.setLat(position.getLatitude());
                    tempNote.setLng(position.getLongitude());
                    tempNote.setTimestamp(timestamp);

                    if (currentBase64Wrapper[0] != null) {
                        tempNote.setImageBase64(currentBase64Wrapper[0]);
                        // tempNote.setHasImage(true); // Method doesn't exist in NearbyNote
                    }

                    // Parse counts safely
                    try {
                        long likes = Long.parseLong(tvLikeCount.getText().toString());
                        long comments = Long.parseLong(tvCommentCount.getText().toString());
                        tempNote.setLikesCount((int) likes);
                        tempNote.setCommentsCount((int) comments);
                    } catch (Exception e) {}

                    showFollowingDialog(tempNote);
                    dialog.dismiss();
                });
                addPressEffect(shareSection);

                // Travel button click
                LinearLayout travelSection = infoWindow.findViewById(R.id.travel_section);
                travelSection.setOnClickListener(v -> {
                    startNavigation(position);
                    dialog.dismiss();
                });
                addPressEffect(travelSection);
            }
        } else {
            // Offline mode - hide interaction section and show default owner info
            interactionSection.setVisibility(View.GONE);
            ownerName.setText("Local User");
        }

        dialog.show();
    }

    // Animate like button
    private void animateLike(ImageView likeBtn) {
        ScaleAnimation scaleAnimation = new ScaleAnimation(
                1.0f, 1.3f, 1.0f, 1.3f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);
        scaleAnimation.setDuration(200);
        scaleAnimation.setRepeatCount(1);
        scaleAnimation.setRepeatMode(Animation.REVERSE);
        likeBtn.startAnimation(scaleAnimation);
    }

    // Quick Actions Popup on Long Press
    private android.widget.PopupWindow quickActionsPopup;
    
    private void showQuickActionsPopup(Symbol symbol, String docId, String noteText, LatLng position) {
        if (quickActionsPopup != null && quickActionsPopup.isShowing()) {
            quickActionsPopup.dismiss();
        }
        
        View popupView = LayoutInflater.from(requireContext()).inflate(R.layout.popup_quick_actions, null);
        quickActionsPopup = new android.widget.PopupWindow(popupView,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, true);
        
        quickActionsPopup.setElevation(12f);
        quickActionsPopup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        
        View actionLike = popupView.findViewById(R.id.action_like);
        View actionShare = popupView.findViewById(R.id.action_share);
        View actionTravel = popupView.findViewById(R.id.action_travel);
        ImageView ivLike = popupView.findViewById(R.id.iv_like);
        
        // Check if already liked
        String currentUserId = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
        if (currentUserId != null) {
            db.collection("notes").document(docId).get().addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    java.util.List<String> likedBy = (java.util.List<String>) doc.get("likedBy");
                    boolean isLiked = likedBy != null && likedBy.contains(currentUserId);
                    ivLike.setColorFilter(isLiked ? 0xFFE84545 : getResources().getColor(R.color.primary, null));
                }
            });
        }
        
        // Haptic feedback on show
        popupView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        
        // Like Action
        actionLike.setOnClickListener(v -> {
            performHapticClick(v);
            if (currentUserId == null) {
                if (getView() != null) com.visiboard.app.utils.UiHelper.showWarning(getView(), "Please log in to like");
                quickActionsPopup.dismiss();
                return;
            }
            
            db.collection("notes").document(docId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        java.util.List<String> likedBy = (java.util.List<String>) doc.get("likedBy");
                        boolean isLiked = likedBy != null && likedBy.contains(currentUserId);
                        
                        db.runTransaction(transaction -> {
                            com.google.firebase.firestore.DocumentReference noteRef = db.collection("notes").document(docId);
                            if (isLiked) {
                                transaction.update(noteRef, "likedBy", com.google.firebase.firestore.FieldValue.arrayRemove(currentUserId));
                            } else {
                                transaction.update(noteRef, "likedBy", com.google.firebase.firestore.FieldValue.arrayUnion(currentUserId));
                            }
                            return null;
                        }).addOnSuccessListener(aVoid -> {
                            animateLike(ivLike);
                            ivLike.setColorFilter(isLiked ? getResources().getColor(R.color.primary, null) : 0xFFE84545);
                            if (getView() != null) com.visiboard.app.utils.UiHelper.showSuccess(getView(), isLiked ? "Unliked" : "Liked!");
                        });
                    }
                });
            quickActionsPopup.dismiss();
        });
        
        // Share Action - Use in-app sharing with followers
        actionShare.setOnClickListener(v -> {
            performHapticClick(v);
            quickActionsPopup.dismiss();
            
            // Create NearbyNote for sharing dialog
            NearbyNote tempNote = new NearbyNote();
            tempNote.setId(docId);
            tempNote.setText(noteText);
            tempNote.setLat(position.getLatitude());
            tempNote.setLng(position.getLongitude());
            tempNote.setTimestamp(System.currentTimeMillis());
            
            showFollowingDialog(tempNote);
        });
        
        // Travel Action
        actionTravel.setOnClickListener(v -> {
            performHapticClick(v);
            quickActionsPopup.dismiss();
            startNavigation(position);
        });
        
        // Show popup at screen center (approximate)
        android.graphics.Point screenCenter = new android.graphics.Point();
        requireActivity().getWindowManager().getDefaultDisplay().getSize(screenCenter);
        quickActionsPopup.showAtLocation(mapView, android.view.Gravity.CENTER, 0, 0);
    }
    
    // Helper to add subtle press effect (scale down on touch)
    private void addPressEffect(View view) {
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator()).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).setInterpolator(new android.view.animation.OvershootInterpolator()).start();
                    break;
            }
            return false; // let click listener handle the actual click
        });
    }

    // Helper for subtle haptic feedback
    private void performHapticClick(View view) {
        // Try CLOCK_TICK for a crisp, subtle tick, fall back to KEYBOARD_TAP
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            }
        } catch (Exception e) {
            // Ignore if fails
        }
    }





    // Get time ago string
    private String getTimeAgo(long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;
        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) return days + "d ago";
        if (hours > 0) return hours + "h ago";
        if (minutes > 0) return minutes + "m ago";
        return "just now";
    }

    // Add note dialog
    private void showAddNoteDialog(LatLng position) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_note, null);
        EditText editText = dialogView.findViewById(R.id.et_note_input);
        Button btnSave = dialogView.findViewById(R.id.btn_save_note);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel_note);

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnSave.setOnClickListener(v -> {
            String note = editText.getText().toString().trim();
            if (!note.isEmpty()) {
                long timestamp = System.currentTimeMillis();
                saveNote(position, note, timestamp);
                dialog.dismiss();
            } else {
                if (getView() != null) com.visiboard.app.utils.UiHelper.showWarning(getView(), "Note is empty!");
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();

        // Show keyboard
        editText.requestFocus();
        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(editText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
    }

    // Save note
    private void saveNote(LatLng position, String note, long timestamp) {
        if (useCloudMode && auth.getCurrentUser() != null) {
            String uid = auth.getCurrentUser().getUid();

            db.collection("users").document(uid).get()
                    .addOnSuccessListener(userDoc -> {
                        String userName = userDoc.getString("name");
                        String userProfilePic = userDoc.getString("profilePic");
                        boolean isPrivate = userDoc.getBoolean("isPrivate") != null && userDoc.getBoolean("isPrivate");

                        Map<String, Object> noteMap = new HashMap<>();
                        noteMap.put("userId", uid);
                        noteMap.put("userName", userName);
                        noteMap.put("userProfilePic", userProfilePic);
                        noteMap.put("isOwnerPrivate", isPrivate);
                        noteMap.put("lat", position.getLatitude());
                        noteMap.put("lon", position.getLongitude());
                        noteMap.put("location", new com.google.firebase.firestore.GeoPoint(position.getLatitude(), position.getLongitude()));
                        noteMap.put("note", note);
                        noteMap.put("summary", note.length() > 100 ? note.substring(0, 100) + "..." : note);
                        noteMap.put("timestamp", timestamp);
                        noteMap.put("likeCount", 0);
                        noteMap.put("likedBy", new java.util.ArrayList<String>());
                        noteMap.put("commentsCount", 0);

                        // Save to global notes collection
                        db.collection("notes")
                                .add(noteMap)
                                .addOnSuccessListener(docRef -> {
                                    Log.d("MapFragment", "Note saved: " + docRef.getId());
                                    if (getView() != null) com.visiboard.app.utils.UiHelper.showSuccess(getView(), "Note placed!");
                                })
                                .addOnFailureListener(e -> {
                                    Log.e("MapFragment", "Error saving note: " + e.getMessage());
                                    if (getView() != null) com.visiboard.app.utils.UiHelper.showError(getView(), "Failed to save note");
                                });
                    });
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
    private void deleteNoteFirestore(String docId, String noteOwnerId) {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        // Only allow deletion if user owns the note
        if (!uid.equals(noteOwnerId)) {
            if (getView() != null) com.visiboard.app.utils.UiHelper.showError(getView(), "You can only delete your own notes!");
            return;
        }

        db.collection("notes")
                .document(docId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Log.d("MapFragment", "Note deleted from Firestore");
                    if (getView() != null) com.visiboard.app.utils.UiHelper.showSuccess(getView(), "Note deleted");
                })
                .addOnFailureListener(e -> {
                    Log.e("MapFragment", "Error deleting note: " + e.getMessage());
                    if (getView() != null) com.visiboard.app.utils.UiHelper.showError(getView(), "Failed to delete note");
                });
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

            // Reload notes to reflect deletion
            loadSavedNotes();
            if (getView() != null) com.visiboard.app.utils.UiHelper.showSuccess(getView(), "Note deleted");
            Log.d(TAG, "Note deleted locally");
        } catch (Exception e) {
            e.printStackTrace();
            if (getView() != null) com.visiboard.app.utils.UiHelper.showError(getView(), "Failed to delete note");
        }
    }

    // Load notes
    private void loadSavedNotes() {
        if (useCloudMode && auth.getCurrentUser() != null) {
            // Remove old listener if exists
            if (notesListener != null) {
                notesListener.remove();
            }

            // Load hidden note IDs from others first
            String currentUserId = auth.getCurrentUser().getUid();
            db.collection("users").document(currentUserId).collection("hidden_notes_others")
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        hiddenNoteOtherIds.clear();
                        for (DocumentSnapshot doc : snapshot) {
                            hiddenNoteOtherIds.add(doc.getId());
                        }
                        // Load blocked users before loading notes
                        loadBlockedUsersAndNotes();
                    })
                    .addOnFailureListener(e -> {
                        // Still load notes even if hidden list fails
                        loadBlockedUsersAndNotes();
                    });
        } else {
            // Local mode
            loadLocalNotes();
        }
    }

    private void loadBlockedUsersAndNotes() {
        String userId = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
        if (userId == null) {
            setupNotesListener();
            return;
        }

        db.collection("users").document(userId).collection("blocked_users")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    blockedUserIds.clear();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot) {
                        blockedUserIds.add(doc.getId());
                    }
                    // Update UserCache
                    UserCache.getInstance().setBlockedUsers(blockedUserIds);
                    
                    // Now proceed to listen for notes
                    setupNotesListener();
                })
                .addOnFailureListener(e -> {
                    // Still load notes even if blocked list fails
                    setupNotesListener();
                });
    }

    private void setupNotesListener() {
        // Add real-time listener to load ALL notes from the global notes collection
        notesListener = db.collection("notes")
                .addSnapshotListener((querySnapshot, error) -> {
                    if (error != null) {
                        Log.e("MapFragment", "Error loading notes: " + error.getMessage());
                        return;
                    }

                    if (querySnapshot != null) {
                        cachedNotesSnapshot = querySnapshot.getDocuments(); // Update Cache

                        // Identify users we need to check privacy for
                        Set<String> usersToCheck = new HashSet<>();
                        for (DocumentSnapshot doc : querySnapshot) {
                            String userId = doc.getString("userId");
                            if (userId != null && !userId.equals(auth.getCurrentUser().getUid()) && !followingIds.contains(userId)) {
                                // If not in cache, we need to fetch
                                if (UserCache.getInstance().get(userId) == null) {
                                    usersToCheck.add(userId);
                                }
                            }
                        }

                        if (usersToCheck.isEmpty()) {
                            processAndRenderNotes(querySnapshot);
                        } else {
                            // Fetch missing users
                            List<Task<DocumentSnapshot>> tasks = new ArrayList<>();
                            for (String uid : usersToCheck) {
                                tasks.add(db.collection("users").document(uid).get());
                            }

                            Tasks.whenAllSuccess(tasks).addOnSuccessListener(results -> {
                                if (!isAdded()) return; // Prevent crash if fragment detached
                                
                                // Update cache
                                for (Object result : results) {
                                    DocumentSnapshot userDoc = (DocumentSnapshot) result;
                                    if (userDoc.exists()) {
                                        UserInfo userInfo = userDoc.toObject(UserInfo.class);
                                        // Manually set ID if not set by fetching
                                        if (userInfo != null) { 
                                            userInfo.setUserId(userDoc.getId());
                                            UserCache.getInstance().put(userDoc.getId(), userInfo);
                                        }
                                    }
                                }
                                processAndRenderNotes(querySnapshot);
                            }).addOnFailureListener(e -> {
                                Log.e("MapFragment", "Failed to batch fetch users", e);
                                if (isAdded()) processAndRenderNotes(querySnapshot);
                            });
                        }
                    }
                });
    }

    private void processAndRenderNotes(com.google.firebase.firestore.QuerySnapshot querySnapshot) {
        // Clear existing note markers (keep user location marker)
        if (symbolManager != null) {
            deleteAllSymbolsExceptUserLocation();
        }

        java.util.List<Feature> heatmapFeatures = new java.util.ArrayList<>();

        // Add all notes
        for (DocumentSnapshot doc : querySnapshot) {
            try {
                double lat = doc.getDouble("lat");
                double lon = doc.getDouble("lon");
                String userId = doc.getString("userId");

                // Visibility Filter
                String visibility = doc.getString("visibility");
                if (visibility == null) visibility = "public";
                boolean isOwnerPrivate = doc.getBoolean("isOwnerPrivate") != null && doc.getBoolean("isOwnerPrivate");
                boolean isHidden = doc.getBoolean("isHidden") != null && doc.getBoolean("isHidden");

                // Filter Hidden Notes (Hidden from everyone including owner on Map)
                if (isHidden) continue;

                if (userId.equals(auth.getCurrentUser().getUid())) {
                    if (isHideMyNotesEnabled) continue;
                }

                // Filter notes hidden by THIS user (Hide for Me)
                if (hiddenNoteOtherIds.contains(doc.getId())) continue;

                // Filter notes from blocked users
                if (blockedUserIds.contains(userId) || com.visiboard.app.utils.UserCache.getInstance().isBlocked(userId)) {
                    if (pendingTargetNoteId != null && pendingTargetNoteId.equals(doc.getId())) {
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> 
                            { if (getView() != null) com.visiboard.app.utils.UiHelper.showInfo(getView(), "You blocked this user"); }
                        );
                        pendingTargetNoteId = null; 
                    }
                    continue;
                }

                if (!userId.equals(auth.getCurrentUser().getUid())) {
                    // 1. Strict Private Note (Only Me)
                    if ("private".equals(visibility)) continue;

                    // 2. Owner Privacy Check (Using Cache Source of Truth)
                    boolean isUserPrivate = false;
                    UserInfo ownerInfo = UserCache.getInstance().get(userId);
                    if (ownerInfo != null) {
                        isUserPrivate = ownerInfo.isPrivate();
                    } else {
                        // Fallback to note's denormalized field if cache missing
                        isUserPrivate = isOwnerPrivate;
                    }

                    boolean isRestricted = isUserPrivate || "followers".equals(visibility);
                    if (isRestricted && !followingIds.contains(userId)) {
                        continue;
                    }
                }

                // Friends Radar Filter
                if (isFriendsRadarEnabled) {
                    String currentUserId = auth.getCurrentUser().getUid();
                    if (!userId.equals(currentUserId) && !followingIds.contains(userId)) {
                        continue;
                    }
                }

                // Heatmap Data
                heatmapFeatures.add(Feature.fromGeometry(Point.fromLngLat(lon, lat)));

                // Try "note" first, fallback to "text" for backward compatibility
                String note = doc.getString("note");
                if (note == null) note = doc.getString("text");
                long timestamp = doc.getLong("timestamp") != null ? doc.getLong("timestamp") : 0L;

                // Time Travel Filter
                if (timeFilterDuration > 0) {
                    if (System.currentTimeMillis() - timestamp > timeFilterDuration) {
                        continue;
                    }
                }

                String b64 = doc.getString("imageBase64");
                boolean hasImage = b64 != null && !b64.isEmpty();
                boolean isVirtual = doc.getBoolean("isVirtual") != null && doc.getBoolean("isVirtual");
                LatLng pos = new LatLng(lat, lon);

                // Only add marker if Heatmap is OFF
                if (!isHeatmapEnabled) {
                    addNoteMarker(pos, note, note.length() > 30 ? note.substring(0, 30) + "..." : note,
                            timestamp, doc.getId(), userId, hasImage, isVirtual);
                }
            } catch (Exception e) {
                Log.e("MapFragment", "Error processing note: " + e.getMessage());
            }
        }

        // Check if pending target note needs to be opened
        if (pendingTargetNoteId != null && pendingOpenWindow) {
            String finalPendingId = pendingTargetNoteId;
            boolean finalPendingOpen = pendingOpenWindow;

            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                // Find and open the note
                if (symbolManager != null && finalPendingId != null) {
                    androidx.collection.LongSparseArray<Symbol> annotations = symbolManager.getAnnotations();
                    for (int i = 0; i < annotations.size(); i++) {
                        Symbol symbol = annotations.valueAt(i);
                        try {
                            JsonElement dataElement = symbol.getData();
                            if (dataElement != null && dataElement.isJsonObject()) {
                                com.google.gson.JsonObject jsonObject = dataElement.getAsJsonObject();
                                if (jsonObject.has("docId")) {
                                    String docId = jsonObject.get("docId").getAsString();
                                    if (docId != null && docId.equals(finalPendingId)) {
                                        // Found the note, open info window
                                        LatLng notePosition = symbol.getLatLng();
                                        String note = jsonObject.get("note").getAsString();
                                        long timestamp = jsonObject.get("timestamp").getAsLong();
                                        String userId = jsonObject.has("userId") ? jsonObject.get("userId").getAsString() : null;
                                        boolean hasImage = jsonObject.has("hasImage") && jsonObject.get("hasImage").getAsBoolean();
                                        boolean isVirtual = jsonObject.has("isVirtual") && jsonObject.get("isVirtual").getAsBoolean();
                                        showCustomInfoWindow(note, timestamp, notePosition, symbol, docId, userId, null, hasImage, isVirtual);
                                        break;
                                    }
                                }
                            }
                        } catch (Exception e) {
                            // Skip
                        }
                    }
                }
                pendingTargetNoteId = null;
                pendingOpenWindow = false;
            }, 500);
        }

        // Update Heatmap Source
        if (mapLibreMap != null && mapLibreMap.getStyle() != null) {
            GeoJsonSource source = mapLibreMap.getStyle().getSourceAs(HEATMAP_SOURCE_ID);
            if (source != null) {
                source.setGeoJson(FeatureCollection.fromFeatures(heatmapFeatures));
            }
        }

        // Ensure userLocationSymbol is still present after notes are loaded
        ensureUserLocationMarkerExists();
    }
    
    // Helper method to ensure userLocationSymbol exists
    private void ensureUserLocationMarkerExists() {
        if (userLocationSymbol == null && symbolManager != null && fusedLocationClient != null) {
            // Check if userLocationSymbol exists in annotations but reference is lost
            androidx.collection.LongSparseArray<Symbol> annotations = symbolManager.getAnnotations();
            boolean found = false;
            for (int i = 0; i < annotations.size(); i++) {
                Symbol symbol = annotations.valueAt(i);
                String iconImage = symbol.getIconImage();
                if (iconImage != null && iconImage.equals(MARKER_ICON_ID_USER_LOCATION)) {
                    userLocationSymbol = symbol;
                    found = true;
                    break;
                }
            }
            
            // If not found, recreate it
            if (!found) {
                fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                    if (location != null && symbolManager != null) {
                        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                        updateUserLocationMarker(latLng);
                    }
                });
            }
        }
    }

    private void loadLocalNotes() {
            try {
                JSONArray array = new JSONArray(sharedPreferences.getString(NOTES_KEY, "[]"));
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    LatLng pos = new LatLng(obj.getDouble("lat"), obj.getDouble("lon"));
                    String note = obj.getString("note");
                    long timestamp = obj.has("timestamp") ? obj.getLong("timestamp") : 0L;
                    addNoteMarker(pos, note, note.length() > 30 ? note.substring(0, 30) + "..." : note, timestamp, null, null, false, false);
                }
            } catch (Exception e) { e.printStackTrace(); }
    }

    // Helper method to safely delete all symbols except userLocationSymbol
    private void deleteAllSymbolsExceptUserLocation() {
        if (symbolManager == null) return;
        
        java.util.List<Symbol> symbolsToRemove = new java.util.ArrayList<>();
        androidx.collection.LongSparseArray<Symbol> annotations = symbolManager.getAnnotations();
        for (int i = 0; i < annotations.size(); i++) {
            Symbol symbol = annotations.valueAt(i);
            // Preserve userLocationSymbol by comparing both reference and checking if it has user location icon
            if (symbol != userLocationSymbol) {
                // Additional safety check: verify it's not the user location by checking icon
                String iconImage = symbol.getIconImage();
                if (iconImage == null || !iconImage.equals(MARKER_ICON_ID_USER_LOCATION)) {
                    symbolsToRemove.add(symbol);
                }
            }
        }
        if (!symbolsToRemove.isEmpty()) {
            symbolManager.delete(symbolsToRemove);
        }
    }

    private void renderNotesFromCache() {
        if (cachedNotesSnapshot == null || mapLibreMap == null || symbolManager == null) return;

        // Clear existing note markers (keep user location marker)
        deleteAllSymbolsExceptUserLocation();

         java.util.List<Feature> heatmapFeatures = new java.util.ArrayList<>();

         // Ensure userLocationSymbol exists before rendering notes
         ensureUserLocationMarkerExists();
         
         for (DocumentSnapshot doc : cachedNotesSnapshot) {
             try {
                double lat = doc.getDouble("lat");
                double lon = doc.getDouble("lon");
                String userId = doc.getString("userId");

                if (isFriendsRadarEnabled) {
                    String currentUserId = auth.getCurrentUser().getUid();
                    if (!userId.equals(currentUserId) && !followingIds.contains(userId)) {
                        continue;
                    }
                }

                // Visibility Filter (Cache)
                String visibility = doc.getString("visibility");
                if (visibility == null) visibility = "public";
                boolean isOwnerPrivate = doc.getBoolean("isOwnerPrivate") != null && doc.getBoolean("isOwnerPrivate");
                boolean isHidden = doc.getBoolean("isHidden") != null && doc.getBoolean("isHidden");

                        // Filter Hidden Notes
                        if (isHidden) continue;

                        if (userId.equals(auth.getCurrentUser().getUid())) {
                             if (isHideMyNotesEnabled) continue;
                        }

                        if (!userId.equals(auth.getCurrentUser().getUid())) {
                    if ("private".equals(visibility)) continue;
                    boolean isRestricted = isOwnerPrivate || "followers".equals(visibility);
                    if (isRestricted && !followingIds.contains(userId)) {
                        continue;
                    }
                }

                heatmapFeatures.add(Feature.fromGeometry(Point.fromLngLat(lon, lat)));

                String note = doc.getString("note");
                if (note == null) note = doc.getString("text");
                long timestamp = doc.getLong("timestamp") != null ? doc.getLong("timestamp") : 0L;

                if (timeFilterDuration > 0) {
                    if (System.currentTimeMillis() - timestamp > timeFilterDuration) {
                        continue;
                    }
                }

                String b64 = doc.getString("imageBase64");
                boolean hasImage = b64 != null && !b64.isEmpty();
                LatLng pos = new LatLng(lat, lon);

                if (!isHeatmapEnabled) {
                    boolean isVirtual = doc.getBoolean("isVirtual") != null && doc.getBoolean("isVirtual");
                    addNoteMarker(pos, note, note.length() > 30 ? note.substring(0, 30) + "..." : note,
                            timestamp, doc.getId(), userId, hasImage, isVirtual);
                }
             } catch (Exception e) {
                 e.printStackTrace();
             }
         }
         
        // Update Heatmap Source
        if (mapLibreMap.getStyle() != null) {
            GeoJsonSource source = mapLibreMap.getStyle().getSourceAs(HEATMAP_SOURCE_ID);
            if (source != null) {
                source.setGeoJson(FeatureCollection.fromFeatures(heatmapFeatures));
            }
        }
        
        // Ensure userLocationSymbol still exists after rendering notes
        ensureUserLocationMarkerExists();
    }

    // Enable user location
    private void enableUserLocation(boolean moveCamera) {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST);
            return;
        }

        // Get initial location
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null && mapLibreMap != null) {
                LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                updateUserLocationMarker(latLng);
                updateUserLocationMarker(latLng);
                // Only move camera if moveCamera is true and NOT navigating
                if (!isNavigating && moveCamera) {
                    currentZoom = 19.0;
                    mapLibreMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, currentZoom));
                }
            }
        });

        // Start continuous location updates for accuracy
        startLocationUpdates();
    }

    // Start continuous location updates
    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        com.google.android.gms.location.LocationRequest locationRequest =
                com.google.android.gms.location.LocationRequest.create()
                        .setPriority(com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY)
                        .setInterval(5000)  // Update every 5 seconds
                        .setFastestInterval(2000);  // Can update as fast as 2 seconds

        locationCallback = new com.google.android.gms.location.LocationCallback() {
            @Override
            public void onLocationResult(com.google.android.gms.location.LocationResult locationResult) {
                if (locationResult != null && locationResult.getLastLocation() != null) {
                    android.location.Location location = locationResult.getLastLocation();
                    LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                    updateUserLocationMarker(latLng);
                    updateNavigation(location);
                }
            }
        };

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
    }

    // Stop location updates
    private void stopLocationUpdates() {
        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    // Update user location marker position
    private void updateUserLocationMarker(LatLng latLng) {
        if (symbolManager == null) return;

        if (userLocationSymbol != null) {
            userLocationSymbol.setLatLng(latLng);
            symbolManager.update(userLocationSymbol);
        } else {
            // Create marker if it doesn't exist
            float iconSize = calculateIconSize(currentZoom);
            userLocationSymbol = symbolManager.create(new SymbolOptions()
                    .withLatLng(latLng)
                    .withIconImage(MARKER_ICON_ID_USER_LOCATION)
                    .withIconSize(iconSize));
        }
    }

    // Calculate icon size based on zoom level
    private float calculateIconSize(double zoom) {
        // Larger base size for better visibility
        // Zoom ranges typically: 0 (world) to 22 (street level)
        if (zoom <= 8) {
            return 2.5f; // Large for zoomed out
        } else if (zoom <= 12) {
            return 2.0f; // Medium-large
        } else if (zoom <= 15) {
            return 1.5f; // Medium
        } else if (zoom <= 18) {
            return 1.3f; // Normal
        } else {
            return 1.0f; // Small for zoomed in (still larger than before)
        }
    }

    // Update user location marker size when zoom changes
    private void updateUserLocationMarkerSize() {
        if (userLocationSymbol != null && symbolManager != null) {
            float newSize = calculateIconSize(currentZoom);
            userLocationSymbol.setIconSize(newSize);
            symbolManager.update(userLocationSymbol);
        }
    }



    // Follow user
    private void followUser(String targetUserId, android.widget.Button btn) {
        String currentUserId = auth.getCurrentUser().getUid();
        
        // Loading State
        String originalText = btn.getText().toString();
        btn.setText("...");
        btn.setEnabled(false);

        // Check if target user is private
        db.collection("users").document(targetUserId).get()
                .addOnSuccessListener(targetUserDoc -> {
                    boolean isPrivate = targetUserDoc.getBoolean("isPrivate") != null && targetUserDoc.getBoolean("isPrivate");
                    
                    if (isPrivate) {
                        // Check Rejection Status First (5-Strike Rule)
                        db.collection("users").document(targetUserId).collection("rejections").document(currentUserId)
                            .get()
                            .addOnSuccessListener(rejectionDoc -> {
                                boolean blocked = false;
                                if (rejectionDoc.exists()) {
                                    Long count = rejectionDoc.getLong("count");
                                    Long time = rejectionDoc.getLong("lastRejectionTime");
                                    if (count != null && count >= 5 && time != null) {
                                         if (System.currentTimeMillis() - time < 24 * 60 * 60 * 1000) {
                                             blocked = true;
                                         }
                                    }
                                }
                                
                                if (blocked) {
                                    if (getView() != null) com.visiboard.app.utils.UiHelper.showWarning(getView(), "Too many follow requests. Try again later.");
                                    btn.setText(originalText);
                                    btn.setEnabled(true);
                                } else {
                                    // Send Follow Request
                                     db.collection("users").document(currentUserId).get()
                                        .addOnSuccessListener(currentUserDoc -> {
                                            String myName = currentUserDoc.getString("name");
                                            String myProfilePic = currentUserDoc.getString("profilePic");
                                            
                                            Map<String, Object> requestData = new HashMap<>();
                                            requestData.put("timestamp", System.currentTimeMillis());
                                            requestData.put("requesterName", myName);
                                            requestData.put("requesterProfilePic", myProfilePic);
                                            
                                            db.collection("users").document(targetUserId)
                                                    .collection("follow_requests").document(currentUserId)
                                                    .set(requestData)
                                                    .addOnSuccessListener(aVoid -> {
                                                        btn.setText("Requested");
                                                        btn.setBackgroundResource(R.drawable.btn_following_selector); 
                                                        btn.setTextColor(getResources().getColor(R.color.button_text_following, null));
                                                        
                                                        createNotification(targetUserId, currentUserId, "follow_request", null, null, (String)null);
                                                        if (getView() != null) com.visiboard.app.utils.UiHelper.showSuccess(getView(), "Request sent");
                                                        btn.setEnabled(true);
                                                    });
                                        });
                                }
                            });
                    } else {
                        // Public: Direct Follow (Existing Logic)
                        performDirectFollow(targetUserId, btn, currentUserId);
                    }
                });
    }

    private void createNotification(String toUserId, String fromUserId, String type, String noteId, String noteText, String noteImage) {
        db.collection("users").document(fromUserId).get()
            .addOnSuccessListener(doc -> {
                String name = doc.getString("name");
                String pic = doc.getString("profilePic");
                
                Map<String, Object> notif = new HashMap<>();
                notif.put("type", type);
                notif.put("fromUserId", fromUserId);
                notif.put("fromUserName", name);
                notif.put("fromUserProfilePic", pic);
                notif.put("toUserId", toUserId);
                notif.put("timestamp", System.currentTimeMillis());
                notif.put("read", false);
                
                if (noteId != null) notif.put("noteId", noteId);
                if (noteText != null) notif.put("noteText", noteText);
                if (noteImage != null) notif.put("noteImage", noteImage);
                
                db.collection("notifications").add(notif);
            });
    }

    private void performDirectFollow(String targetUserId, android.widget.Button btn, String currentUserId) {
        // Get current user's name and profile pic
        db.collection("users").document(currentUserId).get()
                .addOnSuccessListener(currentUserDoc -> {
                    String myName = currentUserDoc.getString("name");
                    String myProfilePic = currentUserDoc.getString("profilePic");

                    // Add to target user's followers
                    Map<String, Object> followerData = new HashMap<>();
                    followerData.put("timestamp", System.currentTimeMillis());
                    followerData.put("followerName", myName);
                    followerData.put("followerProfilePic", myProfilePic);

                    db.collection("users").document(targetUserId)
                            .collection("followers").document(currentUserId)
                            .set(followerData);

                    // Increment target user's followers count
                    db.collection("users").document(targetUserId)
                            .update("followersCount", FieldValue.increment(1));

                    // Get target user's info
                    db.collection("users").document(targetUserId).get()
                            .addOnSuccessListener(targetUserDoc -> {
                                String targetName = targetUserDoc.getString("name");
                                String targetProfilePic = targetUserDoc.getString("profilePic");

                                // Add to current user's following
                                Map<String, Object> followingData = new HashMap<>();
                                followingData.put("timestamp", System.currentTimeMillis());
                                followingData.put("followedName", targetName);
                                followingData.put("followedProfilePic", targetProfilePic);

                                db.collection("users").document(currentUserId)
                                        .collection("following").document(targetUserId)
                                        .set(followingData);

                                // Increment current user's following count
                                db.collection("users").document(currentUserId)
                                        .update("followingCount", FieldValue.increment(1));

                                // Update button
                                btn.setText("Following");
                                btn.setBackgroundResource(R.drawable.btn_following_selector);
                                btn.setTextColor(getResources().getColor(R.color.button_text_following, null));
                                btn.setEnabled(true);

                                createNotification(targetUserId, currentUserId, "follow", null, null, (String)null);
                                if (getView() != null) com.visiboard.app.utils.UiHelper.showSuccess(getView(), "Following " + targetName);
                            });
                });
    }

    // Show unfollow confirmation
    private void showUnfollowConfirmation(String targetUserId, android.widget.Button btn) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_confirmation, null);
        TextView title = dialogView.findViewById(R.id.dialog_title);
        TextView message = dialogView.findViewById(R.id.dialog_message);
        Button btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);

        title.setText("Unfollow User");
        message.setText("Are you sure you want to unfollow this user?");
        btnConfirm.setText("Unfollow");

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnConfirm.setOnClickListener(v -> {
            unfollowUser(targetUserId, btn);
            dialog.dismiss();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    // Cancel follow request
    private void cancelFollowRequest(String targetUserId, android.widget.Button btn) {
        String currentUserId = auth.getCurrentUser().getUid();
        
        db.collection("users").document(targetUserId)
                .collection("follow_requests").document(currentUserId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    btn.setText("Follow");
                    btn.setBackgroundResource(R.drawable.btn_primary_selector);
                    btn.setTextColor(getResources().getColor(R.color.button_text_primary, null));
                    if (getView() != null) com.visiboard.app.utils.UiHelper.showSuccess(getView(), "Request canceled");
                })
                .addOnFailureListener(e -> {
                     if (getView() != null) com.visiboard.app.utils.UiHelper.showError(getView(), "Failed to cancel");
                });
    }

    // Unfollow user
    private void unfollowUser(String targetUserId, android.widget.Button btn) {
        String currentUserId = auth.getCurrentUser().getUid();

        // Remove from target user's followers
        db.collection("users").document(targetUserId)
                .collection("followers").document(currentUserId)
                .delete();

        // Decrement target user's followers count
        db.collection("users").document(targetUserId)
                .update("followersCount", FieldValue.increment(-1));

        // Remove from current user's following
        db.collection("users").document(currentUserId)
                .collection("following").document(targetUserId)
                .delete();

        // Decrement current user's following count
        db.collection("users").document(currentUserId)
                .update("followingCount", FieldValue.increment(-1));

        // Update button
        btn.setText("Follow");
        btn.setBackgroundResource(R.drawable.btn_primary_selector);
        btn.setTextColor(getResources().getColor(R.color.button_text_primary, null));

        if (getView() != null) com.visiboard.app.utils.UiHelper.showSuccess(getView(), "Unfollowed");
    }

    // Show user info dialog - now navigates to full page
    void showUserInfoDialog(String userId) {
        // Close any open note windows before navigating
        if (currentNoteDialog != null && currentNoteDialog.isShowing()) {
            currentNoteDialog.dismiss();
            currentNoteDialog = null;
        }
        
        Bundle args = new Bundle();
        args.putString("userId", userId);
        androidx.navigation.Navigation.findNavController(requireView()).navigate(R.id.userProfileFragment, args);
    }

    private void showBlockConfirmation(String userId, String userName, androidx.appcompat.app.AlertDialog parentDialog) {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Block " + userName + "?")
                .setMessage("They won't be notified. You can unblock them anytime from Settings.")
                .setPositiveButton("Block", (d, w) -> {
                    blockUser(userId);
                    parentDialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void blockUser(String targetUserId) {
        String currentUserId = auth.getCurrentUser().getUid();
        
        // 1. Add to blocked_users collection
        Map<String, Object> blockData = new HashMap<>();
        blockData.put("blockedAt", System.currentTimeMillis());

        // Check relationship before blocking to record it
        db.collection("users").document(currentUserId).collection("following").document(targetUserId).get()
            .addOnSuccessListener(followingDoc -> {
                db.collection("users").document(currentUserId).collection("followers").document(targetUserId).get()
                    .addOnSuccessListener(followerDoc -> {
                        String relationship = "none";
                        if (followingDoc.exists() && followerDoc.exists()) {
                            relationship = "mutual";
                        } else if (followingDoc.exists()) {
                            relationship = "following";
                        } else if (followerDoc.exists()) {
                            relationship = "follower";
                        }
                        blockData.put("previousRelationship", relationship);

                        // Execute block
                        com.google.firebase.firestore.WriteBatch batch = db.batch();

                        // Add to blocked_users
                        batch.set(db.collection("users").document(currentUserId)
                                .collection("blocked_users").document(targetUserId), blockData);

                        // Remove from MY following (if I was following them)
                        if (followingDoc.exists()) {
                            batch.delete(db.collection("users").document(currentUserId)
                                    .collection("following").document(targetUserId));
                            batch.update(db.collection("users").document(currentUserId), 
                                    "followingCount", com.google.firebase.firestore.FieldValue.increment(-1));
                            // Remove me from THEIR followers
                            batch.delete(db.collection("users").document(targetUserId)
                                    .collection("followers").document(currentUserId));
                            batch.update(db.collection("users").document(targetUserId), 
                                    "followersCount", com.google.firebase.firestore.FieldValue.increment(-1));
                        }

                        // Remove from MY followers (if they were following me)
                        if (followerDoc.exists()) {
                            batch.delete(db.collection("users").document(currentUserId)
                                    .collection("followers").document(targetUserId));
                            batch.update(db.collection("users").document(currentUserId), 
                                    "followersCount", com.google.firebase.firestore.FieldValue.increment(-1));
                            // Remove me from THEIR following
                            batch.delete(db.collection("users").document(targetUserId)
                                    .collection("following").document(currentUserId));
                            batch.update(db.collection("users").document(targetUserId), 
                                    "followingCount", com.google.firebase.firestore.FieldValue.increment(-1));
                        }

                        // Delete pending follow requests (both directions)
                        batch.delete(db.collection("users").document(currentUserId)
                                .collection("follow_requests").document(targetUserId));
                        batch.delete(db.collection("users").document(targetUserId)
                                .collection("follow_requests").document(currentUserId));

                        batch.commit()
                            .addOnSuccessListener(aVoid -> {
                                if (getView() != null) com.visiboard.app.utils.UiHelper.showSuccess(getView(), "User blocked");
                                // Add to local blockedUserIds set for immediate filtering
                                blockedUserIds.add(targetUserId);
                                UserCache.getInstance().addBlockedUser(targetUserId);
                                // Refresh map to remove blocked user's notes
                                loadSavedNotes();
                            })
                            .addOnFailureListener(e -> {
                                if (getView() != null) com.visiboard.app.utils.UiHelper.showError(getView(), "Failed to block user");
                            });
                    });
            });
    }

    private void createNotification(String toUserId, String fromUserId, String type,
                                    String noteId, String noteText, LatLng noteLocation) {
        db.collection("users").document(fromUserId).get()
                .addOnSuccessListener(userDoc -> {
                    String fromUserName = userDoc.getString("name");
                    String fromUserProfilePic = userDoc.getString("profilePic");

                    // For like/comment notifications, check if notification already exists for this note
                    if (noteId != null && (type.equals("like") || type.equals("comment"))) {
                        db.collection("notifications")
                                .whereEqualTo("toUserId", toUserId)
                                .whereEqualTo("type", type)
                                .whereEqualTo("noteId", noteId)
                                .get()
                                .addOnSuccessListener(querySnapshot -> {
                                    Map<String, Object> notification = new HashMap<>();
                                    notification.put("toUserId", toUserId);
                                    notification.put("fromUserId", fromUserId);
                                    notification.put("fromUserName", fromUserName);
                                    notification.put("fromUserProfilePic", fromUserProfilePic);
                                    notification.put("type", type);
                                    notification.put("timestamp", System.currentTimeMillis());
                                    notification.put("read", false);

                                    if (noteId != null) {
                                        notification.put("noteId", noteId);
                                    }
                                    if (noteText != null) {
                                        notification.put("noteText", noteText);
                                    }
                                    if (noteLocation != null) {
                                        notification.put("noteLat", noteLocation.getLatitude());
                                        notification.put("noteLng", noteLocation.getLongitude());
                                    }

                                    if (!querySnapshot.isEmpty()) {
                                        // Update existing notification
                                        String docId = querySnapshot.getDocuments().get(0).getId();
                                        db.collection("notifications").document(docId)
                                                .update(notification)
                                                .addOnSuccessListener(aVoid -> Log.d(TAG, "Notification updated"))
                                                .addOnFailureListener(e -> Log.e(TAG, "Error updating notification", e));
                                    } else {
                                        // Create new notification
                                        db.collection("notifications").add(notification)
                                                .addOnSuccessListener(docRef -> Log.d(TAG, "Notification created"))
                                                .addOnFailureListener(e -> Log.e(TAG, "Error creating notification", e));
                                    }
                                });
                    } else {
                        // For follow notifications, always create new
                        Map<String, Object> notification = new HashMap<>();
                        notification.put("toUserId", toUserId);
                        notification.put("fromUserId", fromUserId);
                        notification.put("fromUserName", fromUserName);
                        notification.put("fromUserProfilePic", fromUserProfilePic);
                        notification.put("type", type);
                        notification.put("timestamp", System.currentTimeMillis());
                        notification.put("read", false);

                        db.collection("notifications").add(notification)
                                .addOnSuccessListener(docRef -> Log.d(TAG, "Notification created"))
                                .addOnFailureListener(e -> Log.e(TAG, "Error creating notification", e));
                    }
                });
    }

    private void handleNavigationArguments() {
        if (getArguments() != null) {
            double targetLat = getArguments().getDouble("target_lat", 0);
            double targetLng = getArguments().getDouble("target_lng", 0);
            String targetNoteId = getArguments().getString("target_note_id");
            boolean openNoteWindow = getArguments().getBoolean("open_note_window", false);

            if (targetLat != 0 && targetLng != 0) {
                isNavigating = true;
                LatLng targetLocation = new LatLng(targetLat, targetLng);
                navigateAndOpen(targetLocation, targetNoteId, openNoteWindow);
            } else if (targetNoteId != null && !targetNoteId.isEmpty()) {
                // Coordinates missing, fetch them
                db.collection("notes").document(targetNoteId).get()
                        .addOnSuccessListener(documentSnapshot -> {
                            if (documentSnapshot.exists()) {
                                com.google.firebase.firestore.GeoPoint gp = documentSnapshot.getGeoPoint("location");
                                if (gp != null) {
                                    isNavigating = true;
                                    LatLng targetLocation = new LatLng(gp.getLatitude(), gp.getLongitude());
                                    navigateAndOpen(targetLocation, targetNoteId, openNoteWindow);
                                }
                            }
                        })
                        .addOnFailureListener(e -> Log.e(TAG, "Error fetching note location", e));
            }

            getArguments().clear();
        }
    }

    private void navigateAndOpen(LatLng targetLocation, String noteId, boolean openWindow) {
        if (mapLibreMap != null) {
            // Animate zoom to 18.0
            mapLibreMap.animateCamera(CameraUpdateFactory.newLatLngZoom(targetLocation, 18.0), 1000);

            if (openWindow && noteId != null) {
                // Delay opening the window to allow animation to finish
                new android.os.Handler().postDelayed(() -> {
                    openNoteWindowById(noteId, targetLocation);
                }, 1200);
            }

            // Reset navigation flag after animation
            new android.os.Handler().postDelayed(() -> {
                isNavigating = false;
            }, 2000);
        }
    }

    private void openNoteWindowById(String noteId, LatLng location) {
        db.collection("notes").document(noteId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        // Try "note" first, fallback to "text" for backward compatibility
                        String noteText = doc.getString("note");
                        if (noteText == null) noteText = doc.getString("text");

                        Long timestamp = doc.getLong("timestamp");
                        String userId = doc.getString("userId");
                        String imageBase64 = doc.getString("imageBase64");

                        if (noteText != null && timestamp != null) {
                            Symbol targetSymbol = findSymbolAtLocation(location);
                            boolean hasImage = imageBase64 != null && !imageBase64.isEmpty();
                            boolean isVirtual = doc.getBoolean("isVirtual") != null && doc.getBoolean("isVirtual");
                            showCustomInfoWindow(noteText, timestamp, location, targetSymbol, noteId, userId, imageBase64, hasImage, isVirtual);
                        }
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error loading note", e));
    }

    private Symbol findSymbolAtLocation(LatLng location) {
        if (symbolManager == null) return null;

        androidx.collection.LongSparseArray<Symbol> annotations = symbolManager.getAnnotations();
        for (int i = 0; i < annotations.size(); i++) {
            Symbol symbol = annotations.valueAt(i);
            LatLng symbolLatLng = symbol.getLatLng();
            if (symbolLatLng != null &&
                    Math.abs(symbolLatLng.getLatitude() - location.getLatitude()) < 0.0001 &&
                    Math.abs(symbolLatLng.getLongitude() - location.getLongitude()) < 0.0001) {
                return symbol;
            }
        }
        return null;
    }

    // Lifecycle
    @Override public void onStart() { super.onStart(); mapView.onStart(); }
    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
        // Restart location updates for accuracy
        startLocationUpdates();
        
        // Re-register fragment result listener in case it was cleared
        getParentFragmentManager().setFragmentResultListener("close_note_window", this, (requestKey, bundle) -> {
            if (bundle.getBoolean("close_note_window", false)) {
                if (currentNoteDialog != null && currentNoteDialog.isShowing()) {
                    currentNoteDialog.dismiss();
                    currentNoteDialog = null;
                }
            }
        });
    }
    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
        // Stop location updates to save battery
        stopLocationUpdates();
    }
    @Override public void onStop() { super.onStop(); mapView.onStop(); }
    @Override public void onLowMemory() { super.onLowMemory(); mapView.onLowMemory(); }
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Clean up resources to prevent crashes on return
        if (notesListener != null) {
            notesListener.remove();
            notesListener = null;
        }
        if (symbolManager != null) {
            symbolManager.onDestroy();
            symbolManager = null;
        }
        userLocationSymbol = null;

        // Nullify map reference to prevent access to destroyed map
        mapLibreMap = null;

        // Destroy mapView here as the view is being destroyed
        if (mapView != null) {
            mapView.onDestroy();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Additional cleanup if needed, but critical stuff is now in onDestroyView
        if (notesListener != null) notesListener.remove();
        if (mapView != null) mapView.onDestroy();
    }
    @Override public void onSaveInstanceState(@NonNull Bundle outState) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState);     }

    // ==========================================
    // FAB & Feature Logic
    // ==========================================

    private long timeFilterDuration = -1; // -1 means All Time

    private void setupTimeTravelButton() {
        btnTimeTravel.setOnClickListener(v -> {
            // Options
            final String[] options = {"All Time", "24 Hours", "1 Week", "1 Month", "6 Months", "1 Year"};
            final long[] durations = {-1, 24 * 3600000L, 7 * 24 * 3600000L, 30 * 24 * 3600000L, 180 * 24 * 3600000L, 365 * 24 * 3600000L};
            int selectedIndex = getTimeFilterIndex();

            // Create List View
            android.widget.ListView listView = new android.widget.ListView(requireContext());
            listView.setDivider(null);
            listView.setPadding(0, 8, 0, 8); // Reduced padding, transparent bg

            // Adapter
            android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<String>(requireContext(), R.layout.item_time_tree, R.id.tv_filter_option, options) {
                @NonNull
                @Override
                public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                    View view = super.getView(position, convertView, parent);
                    
                    View lineTop = view.findViewById(R.id.line_top);
                    View lineBottom = view.findViewById(R.id.line_bottom);
                    ImageView ivNode = view.findViewById(R.id.iv_node);
                    TextView tvOption = view.findViewById(R.id.tv_filter_option);

                    // Line Visibility Logic
                    lineTop.setVisibility(position == 0 ? View.INVISIBLE : View.VISIBLE);
                    lineBottom.setVisibility(position == getCount() - 1 ? View.INVISIBLE : View.VISIBLE);

                    // Colors
                    int colorPrimary = ContextCompat.getColor(requireContext(), R.color.primary);
                    int colorSecondary = ContextCompat.getColor(requireContext(), R.color.text_secondary);
                    int colorTextPrimary = ContextCompat.getColor(requireContext(), R.color.text_primary);

                    // Selection Logic
                    if (position == selectedIndex) {
                        ivNode.setColorFilter(colorPrimary);
                        tvOption.setTextColor(colorTextPrimary);
                        tvOption.setTypeface(null, android.graphics.Typeface.BOLD);
                        
                        // Highlight lines connected to selection (optional, keeping it simple for now)
                        lineTop.setBackgroundColor(colorPrimary);
                        lineBottom.setBackgroundColor(colorSecondary); // Default for next
                    } else {
                        ivNode.setColorFilter(colorSecondary);
                        tvOption.setTextColor(colorSecondary);
                        tvOption.setTypeface(null, android.graphics.Typeface.NORMAL);
                        
                        lineTop.setBackgroundColor(colorSecondary);
                        lineBottom.setBackgroundColor(colorSecondary);
                    }
                    
                    // Special Case: Fill lines up to selection
                    if (position < selectedIndex) {
                        lineTop.setBackgroundColor(colorPrimary);
                        lineBottom.setBackgroundColor(colorPrimary);
                        ivNode.setColorFilter(colorPrimary); // Filled path
                    } else if (position == selectedIndex) {
                        lineTop.setBackgroundColor(colorPrimary); // Line coming in is colored
                    }

                    return view;
                }
            };
            listView.setAdapter(adapter);

            // Popup Window
            final android.widget.PopupWindow popupWindow = new android.widget.PopupWindow(requireContext());
            popupWindow.setContentView(listView);
            popupWindow.setWidth((int) (220 * getResources().getDisplayMetrics().density)); // 220dp width
            popupWindow.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
            popupWindow.setFocusable(true);
            popupWindow.setBackgroundDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.bg_popup_tree));
            popupWindow.setElevation(24 * getResources().getDisplayMetrics().density); // High elevation for depth
            popupWindow.setAnimationStyle(R.style.PopupAnimation); // Animation style

            // Item Click
            listView.setOnItemClickListener((parent, view, position, id) -> {
                timeFilterDuration = durations[position];
                btnTimeTravel.setText(options[position]);
                
                popupWindow.dismiss();
                
                // Reload notes
                loadSavedNotes();
            });

            // Show Popup
            popupWindow.showAsDropDown(btnTimeTravel, 0, 8);
        });
    }

    // Toggle Satellite Mode
    // Toggle Satellite Mode
    private void toggleSatelliteMode(boolean enable) {
        if (mapLibreMap == null) return;
        if (isSatelliteEnabled == enable) return;
        isSatelliteEnabled = enable;

        // Use helper for consistent styling
        updateFabState(fabSatellite, isSatelliteEnabled);

        if (isSatelliteEnabled) {
             if (getView() != null) com.visiboard.app.utils.UiHelper.showInfo(getView(), "Satellite Mode On");
             // Mutual exclusivity removed: Heatmap stays ON if it was ON.
        } else {
             if (getView() != null) com.visiboard.app.utils.UiHelper.showInfo(getView(), "Satellite Mode Off");
        }

        // Save current camera position to restore
        if (mapLibreMap != null) {
            savedCameraPosition = mapLibreMap.getCameraPosition();
            isStyleSwitching = true;
        }

        // Switch Style
        currentMapStyle = isSatelliteEnabled ? GEOAPIFY_SATELLITE_STYLE_URL : (isNightMode() ? GEOAPIFY_DARK_STYLE_URL : GEOAPIFY_LIGHT_STYLE_URL);
        
        mapLibreMap.setStyle(new Style.Builder().fromUri(currentMapStyle), style -> {
            setupMapStyle(style, false);
        });

        if (isFabMenuOpen) toggleFabMenu();
    }

    // Reusable Map Setup (used in onMapReady and toggleSatelliteMode)
    private void setupMapStyle(@NonNull Style style, boolean moveCameraToUser) {
        // Add Images
        style.addImage(MARKER_ICON_ID_USER_LOCATION, getBitmapFromVectorDrawable(R.drawable.ic_user_location));
        
        // Register Remote Drop Icon
        Drawable drawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_remote_drop);
        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.setTint(getResources().getColor(R.color.accent)); // Tint it accent color
        drawable.draw(canvas);
        style.addImage("remote_drop_pin", bitmap);

        // Re-init SymbolManager for new style
        symbolManager = new SymbolManager(mapView, mapLibreMap, style);
        symbolManager.setIconAllowOverlap(true);
        symbolManager.setTextAllowOverlap(true);
        // Reset user marker reference as it belongs to the old manager/style
        userLocationSymbol = null;
        
        // Initialize Layers/Sources FIRST to ensure they exist for data rendering
        if (isHeatmapEnabled) initializeHeatmapSource(style);
        initializeNavigationLayer(style);

        // Check for navigation arguments (Deep Linking) on initial load
        if (moveCameraToUser) {
            handleNavigationArguments();
        }

        // Click Listener for Symbols
        symbolManager.addClickListener(symbol -> {
            if (symbol.getData() != null) {
                try {
                    JSONObject data = new JSONObject(symbol.getData().toString());
                    String noteText = data.getString("note");
                    long timestamp = data.getLong("timestamp");
                    String docId = data.has("docId") ? data.getString("docId") : null;
                    String ownerId = data.has("userId") ? data.getString("userId") : null;
                    String imageBase64 = data.has("imageBase64") ? data.getString("imageBase64") : null;
                    boolean hasImage = data.has("hasImage") && data.getBoolean("hasImage");

                    boolean isVirtual = data.optBoolean("isVirtual", false);
                    showCustomInfoWindow(noteText, timestamp, symbol.getLatLng(), symbol, docId, ownerId, imageBase64, hasImage, isVirtual);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return true;
        });

        // Long Click Listener for Quick Actions Popup
        symbolManager.addLongClickListener(symbol -> {
            if (symbol.getData() != null) {
                try {
                    JSONObject data = new JSONObject(symbol.getData().toString());
                    String docId = data.has("docId") ? data.getString("docId") : null;
                    String noteText = data.getString("note");
                    LatLng position = symbol.getLatLng();
                    
                    if (docId != null) {
                        showQuickActionsPopup(symbol, docId, noteText, position);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return true;
        });

        // Restore Features
        enableUserLocation(moveCameraToUser); 

        // Load Notes immediately from cache if available
        if (cachedNotesSnapshot != null && !cachedNotesSnapshot.isEmpty()) {
             renderNotesFromCache();
        } else {
             loadSavedNotes(); // Fallback to fetching
        }
    
        // Restore Navigation Route if active
        if (isNavigatingToNote && navigationDestination != null) {
             // We might need to re-draw route.
        }

        if (savedCameraPosition != null && !moveCameraToUser) {
            mapLibreMap.setCameraPosition(savedCameraPosition); // Force restore
        }

        // Camera Listener
        mapLibreMap.addOnCameraIdleListener(() -> {
            currentZoom = mapLibreMap.getCameraPosition().zoom;
            updateUserLocationMarkerSize();
        });
        
        // Ensure visibility state is correct after style load
        updateHeatmapVisibility();
    }

    // Helper to check for Night Mode
    private boolean isNightMode() {
        int nightModeFlags = requireContext().getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private int getTimeFilterIndex() {
        if (timeFilterDuration == -1) return 0;
        if (timeFilterDuration == 24 * 3600000L) return 1;
        if (timeFilterDuration == 7 * 24 * 3600000L) return 2;
        if (timeFilterDuration == 30 * 24 * 3600000L) return 3;
        if (timeFilterDuration == 180 * 24 * 3600000L) return 4;
        if (timeFilterDuration == 365 * 24 * 3600000L) return 5;
        return 0;
    }

    private boolean isFriendsRadarEnabled = false;
    private boolean isHeatmapEnabled = false;

    private void updateFabState(MaterialButton btn, boolean isActive) {
        int bgColor = isActive ? ContextCompat.getColor(requireContext(), R.color.primary) : ContextCompat.getColor(requireContext(), R.color.card_background);
        int textColor = isActive ? ContextCompat.getColor(requireContext(), android.R.color.white) : ContextCompat.getColor(requireContext(), R.color.text_primary);

        btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(bgColor));
        btn.setTextColor(textColor);
        btn.setIconTint(android.content.res.ColorStateList.valueOf(textColor));
    }

    private void toggleFriendsRadar(boolean isChecked) {
        if (isFriendsRadarEnabled == isChecked) return;
        isFriendsRadarEnabled = isChecked;

        updateFabState(fabFriends, isChecked);

        if (isFriendsRadarEnabled) {
            if (getView() != null) com.visiboard.app.utils.UiHelper.showInfo(getView(), "Friends Radar ON");
            // Mutual exclusivity removed per user request: "heatmap, hide my note, friends radar all could be turned on at once"
        } else {
            if (getView() != null) com.visiboard.app.utils.UiHelper.showInfo(getView(), "Friends Radar OFF");
        }

        updateMapVisualization();
        if (isFabMenuOpen) toggleFabMenu();
    }

    private void toggleHeatmap(boolean isChecked) {
        if (isHeatmapEnabled == isChecked) return;
        isHeatmapEnabled = isChecked;

        updateFabState(fabHeatmap, isChecked);

        if (isHeatmapEnabled) {
            if (getView() != null) com.visiboard.app.utils.UiHelper.showInfo(getView(), "Heatmap ON");
            // Mutual exclusivity removed
        } else {
            if (getView() != null) com.visiboard.app.utils.UiHelper.showInfo(getView(), "Heatmap OFF");
        }

        updateHeatmapVisibility();
        if (isFabMenuOpen) toggleFabMenu();
    }
    
    
    private void toggleHideMyNotes(boolean isChecked) {
        if (isHideMyNotesEnabled == isChecked) return;
        isHideMyNotesEnabled = isChecked;

        updateFabState(fabHideMyNotes, isChecked);
        
        // Update Icon specifically for this one if needed (Open/Closed Eye)
        fabHideMyNotes.setIconResource(isHideMyNotesEnabled ? R.drawable.ic_visibility_off : R.drawable.ic_visibility);
        
        if (getView() != null) com.visiboard.app.utils.UiHelper.showInfo(getView(), isHideMyNotesEnabled ? "Hidden your notes" : "Showing your notes");
        
        loadSavedNotes(); // Reload
        if (isFabMenuOpen) toggleFabMenu();
    }

    private void toggleFabMenu() {
        performHapticClick(fabMenu);
        isFabMenuOpen = !isFabMenuOpen;

        if (isFabMenuOpen) {
            // Show and animate up with bounce
            fabRecenter.setVisibility(View.VISIBLE);
            fabFriends.setVisibility(View.VISIBLE);
            fabHeatmap.setVisibility(View.VISIBLE);
            fabSatellite.setVisibility(View.VISIBLE);
            fabHideMyNotes.setVisibility(View.VISIBLE);
            fabRefresh.setVisibility(View.VISIBLE);

            // Set initial state for animation
            fabRecenter.setAlpha(0f); fabRecenter.setTranslationY(50);
            fabFriends.setAlpha(0f); fabFriends.setTranslationY(100);
            fabHeatmap.setAlpha(0f); fabHeatmap.setTranslationY(150);
            fabSatellite.setAlpha(0f); fabSatellite.setTranslationY(200);
            fabHideMyNotes.setAlpha(0f); fabHideMyNotes.setTranslationY(225);
            fabRefresh.setAlpha(0f); fabRefresh.setTranslationY(250);

            OvershootInterpolator interpolator = new OvershootInterpolator(1.2f);

            fabRecenter.animate().alpha(1f).translationY(0).setInterpolator(interpolator).setDuration(300).start();
            fabFriends.animate().alpha(1f).translationY(0).setInterpolator(interpolator).setDuration(350).start();
            fabHeatmap.animate().alpha(1f).translationY(0).setInterpolator(interpolator).setDuration(400).start();
            fabSatellite.animate().alpha(1f).translationY(0).setInterpolator(interpolator).setDuration(450).start();
            fabHideMyNotes.animate().alpha(1f).translationY(0).setInterpolator(interpolator).setDuration(475).start();
            fabRefresh.animate().alpha(1f).translationY(0).setInterpolator(interpolator).setDuration(500).start();

            fabMenu.animate().rotation(45f).setInterpolator(interpolator).setDuration(300).start();
        } else {
            // Animate down and hide
            fabRecenter.animate().alpha(0f).translationY(50).setInterpolator(null).setDuration(200).start();
            fabFriends.animate().alpha(0f).translationY(100).setInterpolator(null).setDuration(200).start();
            fabHeatmap.animate().alpha(0f).translationY(150).setInterpolator(null).setDuration(200).start();
            fabSatellite.animate().alpha(0f).translationY(200).setInterpolator(null).setDuration(200).start();
            fabRefresh.animate().alpha(0f).translationY(250).setInterpolator(null).setDuration(200).withEndAction(() -> {
                fabRecenter.setVisibility(View.GONE);
                fabFriends.setVisibility(View.GONE);
                fabHeatmap.setVisibility(View.GONE);
                fabSatellite.setVisibility(View.GONE);
                fabHideMyNotes.setVisibility(View.GONE);
                fabRefresh.setVisibility(View.GONE);
            }).start();
            fabHideMyNotes.animate().alpha(0f).translationY(225).setInterpolator(null).setDuration(200).start();

            fabMenu.animate().rotation(0f).setInterpolator(null).setDuration(200).start();
        }
    }

    private void updateMapVisualization() {
        // Refresh notes. Ideally we filter client side but loadSavedNotes is efficient enough
        // Preserve userLocationSymbol when refreshing
        deleteAllSymbolsExceptUserLocation();
        // Ensure userLocationSymbol is recreated if it was accidentally removed
        if (userLocationSymbol == null && fusedLocationClient != null) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                    updateUserLocationMarker(latLng);
                }
            });
        }
        loadSavedNotes();
    }

    private void updateHeatmapVisibility() {
        if (mapLibreMap == null || mapLibreMap.getStyle() == null) return;
        Style style = mapLibreMap.getStyle();

        // Lazy Init: Ensure Source and Layer exist before trying to show
        if (isHeatmapEnabled) {
            if (style.getSource(HEATMAP_SOURCE_ID) == null || style.getLayer(HEATMAP_LAYER_ID) == null) {
                initializeHeatmapSource(style);
                // Re-populate data since we just created the source
                 if (cachedNotesSnapshot != null) {
                     renderNotesFromCache();
                 } else {
                     loadSavedNotes();
                 }
            }
        }

        HeatmapLayer heatmapLayer = style.getLayerAs(HEATMAP_LAYER_ID);
        if (heatmapLayer != null) {
            heatmapLayer.setProperties(org.maplibre.android.style.layers.PropertyFactory.visibility(
                    isHeatmapEnabled ? org.maplibre.android.style.layers.Property.VISIBLE : org.maplibre.android.style.layers.Property.NONE
            ));
        }

        if (symbolManager != null) {
            // If heatmap ON, hide symbols (optional, but requested). Or just overlay?
            // "Pins disappear, Heatmap appears" was in plan.
            // SymbolManager doesn't reference a layer directly easily for visibility toggle,
            // but we can clear them or set opacity.
            // Better: updateMapVisualization handles reloading, we should modify addNoteMarker logic to SKIP if heatmap enabled?
            // Or just simple visibility toggle:
            // symbolManager uses a layer named "mapbox-android-symbol-layer-1" (auto generated).
            // Let's just abide by user plan: "Toggle ON -> Pins disappear".
            if (isHeatmapEnabled) {
                // To hide symbols, we can just clear them, but preserve userLocationSymbol
                deleteAllSymbolsExceptUserLocation();
            } else {
                // Restore symbols
                updateMapVisualization();
            }
        }
    }

    // ==========================================
    // Legend & Data Logic
    // ==========================================

    private void loadBlockedUsers() {
        if (auth.getCurrentUser() == null) return;
        db.collection("users").document(auth.getCurrentUser().getUid())
                .collection("blocked_users").get()
                .addOnSuccessListener(querySnapshot -> {
                    blockedUserIds.clear();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        blockedUserIds.add(doc.getId());
                        com.visiboard.app.utils.UserCache.getInstance().addBlockedUser(doc.getId());
                    }
                    updateMapVisualization();
                });
    }

    private void loadFollowingList() {
        if (auth.getCurrentUser() == null) return;
        db.collection("users").document(auth.getCurrentUser().getUid())
                .collection("following").get()
                .addOnSuccessListener(querySnapshot -> {
                    followingIds.clear();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        followingIds.add(doc.getId());
                    }
                    updateMapVisualization();
                });
    }
    private java.util.List<String> followingIds = new java.util.ArrayList<>();

    private void loadLegends() {
        // Show widget with loading state immediately but don't block interaction
        if (cvLegendWidget != null) cvLegendWidget.setVisibility(View.VISIBLE);
        if (pbLegendLoading != null) pbLegendLoading.setVisibility(View.VISIBLE);
        if (llLegendContent != null) llLegendContent.setVisibility(View.INVISIBLE);

        if (db == null) return;

        db.collection("users")
                .orderBy("totalLikes", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!isAdded() || getContext() == null) return;
                    if (!querySnapshot.isEmpty()) {
                        DocumentSnapshot doc = querySnapshot.getDocuments().get(0);
                        UserInfo user = doc.toObject(UserInfo.class);
                        if (user != null) {
                            if (tvLegendName != null) tvLegendName.setText(user.getName() != null ? user.getName() : "Anonymous");
                            if (ivLegendAvatar != null && user.getProfilePic() != null) {
                                try {
                                    byte[] bytes = android.util.Base64.decode(user.getProfilePic(), android.util.Base64.DEFAULT);
                                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                                    ivLegendAvatar.setImageBitmap(bitmap);
                                } catch (Exception e) { e.printStackTrace(); }
                            }
                        }
                    }
                    // Always show content and hide loading, even if empty/error, to unblock UI perception
                    if (pbLegendLoading != null) pbLegendLoading.setVisibility(View.GONE);
                    if (llLegendContent != null) llLegendContent.setVisibility(View.VISIBLE);
                })
                .addOnFailureListener(e -> {
                    if (pbLegendLoading != null) pbLegendLoading.setVisibility(View.GONE);
                    // Hide widget on failure entirely
                    if (cvLegendWidget != null) cvLegendWidget.setVisibility(View.GONE);
                    e.printStackTrace();
                });
    }

    private void showLegendsDialog() {
        android.app.Dialog dialog = new android.app.Dialog(requireContext());
        dialog.setContentView(R.layout.dialog_legends);
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        com.google.android.material.tabs.TabLayout tabLayout = dialog.findViewById(R.id.tab_layout);
        androidx.recyclerview.widget.RecyclerView rvLegends = dialog.findViewById(R.id.rv_legends);
        android.widget.ProgressBar pbLoading = dialog.findViewById(R.id.pb_loading);
        android.view.View btnClose = dialog.findViewById(R.id.btn_close);

        rvLegends.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(requireContext()));
        LegendAdapter adapter = new LegendAdapter(user -> {
            if (user != null && user.getUserId() != null) {
                showUserInfoDialog(user.getUserId());
            }
        });
        rvLegends.setAdapter(adapter);

        loadLegendData(adapter, pbLoading, false); // Default Global

        tabLayout.addOnTabSelectedListener(new com.google.android.material.tabs.TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(com.google.android.material.tabs.TabLayout.Tab tab) {
                // Tab 0: Local, Tab 1: Global
                // Wait, XML has Local first?
                // <TabItem text="Local"/> at 0
                // <TabItem text="Global"/> at 1
                loadLegendData(adapter, pbLoading, tab.getPosition() == 0);
            }
            @Override public void onTabUnselected(com.google.android.material.tabs.TabLayout.Tab tab) {}
            @Override public void onTabReselected(com.google.android.material.tabs.TabLayout.Tab tab) {}
        });
        
        // Rename text for Tab 0 to "Nearby" (User feedback fix)
        // We do this after setup to override XML text if needed
        if (tabLayout.getTabCount() > 0 && tabLayout.getTabAt(0) != null) {
            tabLayout.getTabAt(0).setText("Nearby");
        }

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void loadLegendData(LegendAdapter adapter, android.widget.ProgressBar pbLoading, boolean isLocal) {
        if (!isAdded() || getContext() == null) return;

        // Clear existing data to prevent width issues (User Fix)
        adapter.clearUsers();

        pbLoading.setVisibility(View.VISIBLE);

        // Fetch top users globally (100 should be enough to find local ones too for now)
        com.google.firebase.firestore.Query query = db.collection("users")
                .orderBy("totalLikes", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(100);

        query.get().addOnSuccessListener(querySnapshot -> {
            if (!isAdded() || getContext() == null) return;
            pbLoading.setVisibility(View.GONE);

            java.util.List<UserInfo> users = new java.util.ArrayList<>();
            
            // Get Current Location for filtering
            android.location.Location myLoc = null;
            if (fusedLocationClient != null && ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                 // We will rely on adapter.setCurrentLocation logic or pass it. 
                 // Best to use last known location from cache if sync is slow? 
                 // Actually fusedLocationClient is async.
                 // Let's assume we pass what we have.
            }
            // For now, let's just use what we have in cache or last update
            // Actually, we should get location first if "isLocal" is true?
            // To keep it simple/fast: use recent location if available.
            // But we need location for distance calculation.
            
            // Let's grab location synchronously from a cached variable if we had one?
            // We set currentLat/Lng in onLocationResult usually.
            // Let's check permissions and get straightforward location if needed.
            
            // Actually, we can just process the list.
             for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                UserInfo user = doc.toObject(UserInfo.class);
                if (user != null) {
                    if (user.getUserId() == null) user.setUserId(doc.getId());
                    users.add(user);
                }
            }
            
            if (isLocal) {
                 if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                        if (location != null) {
                             adapter.setCurrentLocation(location);
                             
                             // FILTER by 50km
                             List<UserInfo> nearbyUsers = new ArrayList<>();
                             float[] results = new float[1];
                             
                             for (UserInfo u : users) {
                                 if (u.getLat() != 0 && u.getLng() != 0) {
                                     android.location.Location.distanceBetween(
                                            location.getLatitude(), location.getLongitude(),
                                            u.getLat(), u.getLng(), results);
                                     float distanceKm = results[0] / 1000f;
                                     if (distanceKm <= 50) { // 50km Radius
                                         nearbyUsers.add(u);
                                     }
                                 }
                             }
                             
                             if (nearbyUsers.isEmpty()) {
                                 if (getView() != null) com.visiboard.app.utils.UiHelper.showInfo(getView(), "No legends within 50km");
                             }
                             adapter.setUsers(nearbyUsers);
                        } else {
                            if (getView() != null) com.visiboard.app.utils.UiHelper.showWarning(getView(), "Location not available for Nearby");
                            adapter.setUsers(new ArrayList<>()); // Empty if no loc
                        }
                    });
                 } else {
                     if (getView() != null) com.visiboard.app.utils.UiHelper.showWarning(getView(), "Permission required for Nearby");
                 }
            } else {
                adapter.setUsers(users);
                adapter.setCurrentLocation(null); // Clear loc for global to avoid distance view? Or show it too? User didn't specify. Show if available is nice.
            }

            if (users.isEmpty() && !isLocal) {
                if (getView() != null) com.visiboard.app.utils.UiHelper.showInfo(getView(), "No legends found");
            }
        }).addOnFailureListener(e -> {
            pbLoading.setVisibility(View.GONE);
            if (getView() != null) com.visiboard.app.utils.UiHelper.showError(getView(), "Failed to load legends");
        });
    }

    private void updateUserLocationInFirestore(LatLng latLng) {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        // 1. Check Cache for Ghost Mode
        UserInfo cachedUser = UserCache.getInstance().get(uid);
        if (cachedUser != null) {
            if (cachedUser.isGhostMode()) {
                // Ghost Mode Enabled: Do NOT update location
                return;
            } else {
                performLocationUpdate(uid, latLng);
            }
        } else {
            // 2. Fallback: Fetch from Firestore if not in cache (Safety check)
            db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    Boolean isGhostRaw = doc.getBoolean("isGhostMode");
                    boolean isGhost = isGhostRaw != null && isGhostRaw;
                    
                    if (isGhost) {
                        return; // Do nothing
                    } else {
                        performLocationUpdate(uid, latLng);
                    }
                }
            });
        }
    }

    private void performLocationUpdate(String uid, LatLng latLng) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("lastLat", latLng.getLatitude());
        updates.put("lastLng", latLng.getLongitude());
        
        try {
            Geocoder geocoder = new Geocoder(requireContext(), java.util.Locale.getDefault());
             List<android.location.Address> addresses = geocoder.getFromLocation(latLng.getLatitude(), latLng.getLongitude(), 1);
             if (addresses != null && !addresses.isEmpty()) {
                 String locality = addresses.get(0).getLocality();
                 if (locality == null) locality = addresses.get(0).getSubAdminArea();
                 if (locality == null) locality = "Unknown Location";
                 updates.put("lastKnownLocation", locality);
             }
        } catch (Exception e) {
            // Ignore geocoder errors
        }

        db.collection("users").document(uid).update(updates);
    }

    // Heatmap Config
    private static final String HEATMAP_SOURCE_ID = "HEATMAP_SOURCE";
    private static final String HEATMAP_LAYER_ID = "HEATMAP_LAYER";

    private void initializeHeatmapSource(Style style) {
        // Create empty GeoJson source for heatmap
        if (style.getSource(HEATMAP_SOURCE_ID) == null) {
            style.addSource(new GeoJsonSource(HEATMAP_SOURCE_ID));
        }

        // Create Heatmap Layer
        if (style.getLayer(HEATMAP_LAYER_ID) == null) {
            HeatmapLayer heatmapLayer = new HeatmapLayer(HEATMAP_LAYER_ID, HEATMAP_SOURCE_ID);
            heatmapLayer.setProperties(
                    org.maplibre.android.style.layers.PropertyFactory.visibility(org.maplibre.android.style.layers.Property.NONE)
                    // Add heatmap styling properties (colors, radius, intensity)
                    // ... omitted for brevity / default style
            );
            style.addLayer(heatmapLayer);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) enableUserLocation(true);
            else if (getView() != null) com.visiboard.app.utils.UiHelper.showError(getView(), "Permission denied.");
        }
    }

    // --- Share Logic ---

    private void showFollowingDialog(@Nullable NearbyNote noteToShare) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_following, null);

        RecyclerView rvDialog = dialogView.findViewById(R.id.rv_following_dialog);
        android.widget.ProgressBar pbLoading = dialogView.findViewById(R.id.pb_loading_following);
        TextView tvNoData = dialogView.findViewById(R.id.tv_no_following_dialog);
        ImageButton btnCloseHeader = dialogView.findViewById(R.id.btn_close_header);
        TextInputEditText etSearch = dialogView.findViewById(R.id.et_search_following);
        TextView tvTitle = dialogView.findViewById(R.id.dialog_title);

        // Update Title if sharing
        if (noteToShare != null && tvTitle != null) {
            tvTitle.setText("Share Note");
        }

        // List to hold all users for filtering
        final List<UserInfo> allFollowingList = new ArrayList<>();

        rvDialog.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(getContext()));

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();

        FollowingAdapter dialogAdapter = new FollowingAdapter(
                user -> showUserInfoDialog(user.getUserId()),
                user -> {
                    if (noteToShare != null) {
                        sendSharedNote(user, noteToShare);
                        dialog.dismiss();
                    } else {
                        showSendMessageDialog(user);
                    }
                }
        );
        rvDialog.setAdapter(dialogAdapter);

        btnCloseHeader.setOnClickListener(v -> dialog.dismiss());

        // Social Share Buttons
        ImageButton btnShareFacebook = dialogView.findViewById(R.id.btn_share_facebook);
        ImageButton btnShareInstagram = dialogView.findViewById(R.id.btn_share_instagram);
        ImageButton btnShareWhatsapp = dialogView.findViewById(R.id.btn_share_whatsapp);
        ImageButton btnShareEmail = dialogView.findViewById(R.id.btn_share_email);

        View.OnClickListener socialShareListener = v -> {
            String shareText = "";
            if (noteToShare != null) {
                shareText = "Check out this note on VisiBoard!\n\n";
                if (noteToShare.getText() != null) {
                    shareText += "\"" + noteToShare.getText() + "\"\n\n";
                }
                shareText += "📍 Location: https://maps.google.com/?q=" + noteToShare.getLat() + "," + noteToShare.getLng();
            } else {
                shareText = "Check out VisiBoard - an AR note-taking app!";
            }

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);

            String packageName = null;
            int viewId = v.getId();
            if (viewId == R.id.btn_share_facebook) {
                packageName = "com.facebook.katana";
            } else if (viewId == R.id.btn_share_instagram) {
                packageName = "com.instagram.android";
            } else if (viewId == R.id.btn_share_whatsapp) {
                packageName = "com.whatsapp";
            } else if (viewId == R.id.btn_share_email) {
                shareIntent = new Intent(Intent.ACTION_SENDTO);
                shareIntent.setData(android.net.Uri.parse("mailto:"));
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Check out this VisiBoard Note!");
                shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
            }

            if (packageName != null) {
                shareIntent.setPackage(packageName);
            }

            try {
                startActivity(shareIntent);
                dialog.dismiss();
            } catch (Exception e) {
                // App not installed, fallback to chooser
                if (packageName != null) {
                    Intent chooser = Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText), "Share via");
                    startActivity(chooser);
                } else {
                    if (getView() != null) com.visiboard.app.utils.UiHelper.showError(getView(), "No email app found");
                }
            }
        };

        if (btnShareFacebook != null) btnShareFacebook.setOnClickListener(socialShareListener);
        if (btnShareInstagram != null) btnShareInstagram.setOnClickListener(socialShareListener);
        if (btnShareWhatsapp != null) btnShareWhatsapp.setOnClickListener(socialShareListener);
        if (btnShareEmail != null) btnShareEmail.setOnClickListener(socialShareListener);
        
        // Messenger Button
        ImageButton btnShareMessenger = dialogView.findViewById(R.id.btn_share_messenger);
        if (btnShareMessenger != null) {
            btnShareMessenger.setOnClickListener(v -> {
                String shareText = "";
                if (noteToShare != null) {
                    shareText = "Check out this note on VisiBoard!\n\n";
                    if (noteToShare.getText() != null) {
                        shareText += "\"" + noteToShare.getText() + "\"\n\n";
                    }
                    shareText += "📍 Location: https://maps.google.com/?q=" + noteToShare.getLat() + "," + noteToShare.getLng();
                } else {
                    shareText = "Check out VisiBoard - an AR note-taking app!";
                }
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
                shareIntent.setPackage("com.facebook.orca");
                try {
                    startActivity(shareIntent);
                    dialog.dismiss();
                } catch (Exception e) {
                    Intent chooser = Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText), "Share via");
                    startActivity(chooser);
                }
            });
        }

        // Search Filter
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().toLowerCase().trim();
                if (allFollowingList.isEmpty()) return;

                List<UserInfo> filtered = new ArrayList<>();
                for (UserInfo user : allFollowingList) {
                    if (user.getName() != null && user.getName().toLowerCase().contains(query)) {
                        filtered.add(user);
                    }
                }
                dialogAdapter.setUsers(filtered);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        loadFollowingUsers(dialogAdapter, pbLoading, tvNoData, rvDialog, allFollowingList);
    }

    private void loadFollowingUsers(FollowingAdapter adapter, View pbLoading, View tvNoData, View rvContent, List<UserInfo> allFollowingList) {
        pbLoading.setVisibility(View.VISIBLE);
        tvNoData.setVisibility(View.GONE);
        rvContent.setVisibility(View.GONE);

        String userId = auth.getCurrentUser().getUid();

        db.collection("users").document(userId)
                .collection("following")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    int totalFollowing = querySnapshot.size();

                    if (totalFollowing == 0) {
                        pbLoading.setVisibility(View.GONE);
                        tvNoData.setVisibility(View.VISIBLE);
                        return;
                    }

                    allFollowingList.clear();
                    pbLoading.setVisibility(View.GONE);
                    rvContent.setVisibility(View.VISIBLE);

                    for (DocumentSnapshot doc : querySnapshot) {
                        String followedId = doc.getId();
                        db.collection("users").document(followedId).get()
                                .addOnSuccessListener(userDoc -> {
                                    if (userDoc.exists()) {
                                        UserInfo user = new UserInfo();
                                        user.setUserId(followedId);
                                        user.setName(userDoc.getString("name"));
                                        user.setProfilePic(userDoc.getString("profilePic"));
                                        user.setLastKnownLocation(userDoc.getString("lastKnownLocation"));

                                        allFollowingList.add(user);
                                        java.util.Collections.sort(allFollowingList, (u1, u2) -> {
                                            String n1 = u1.getName() != null ? u1.getName() : "";
                                            String n2 = u2.getName() != null ? u2.getName() : "";
                                            return n1.compareToIgnoreCase(n2);
                                        });
                                        adapter.setUsers(new ArrayList<>(allFollowingList));
                                    }
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    pbLoading.setVisibility(View.GONE);
                    tvNoData.setVisibility(View.VISIBLE);
                });
    }

    private void sendSharedNote(UserInfo recipient, NearbyNote note) {
        String fromUserId = auth.getCurrentUser().getUid();

        db.collection("users").document(fromUserId).get()
                .addOnSuccessListener(doc -> {
                    String fromUserName = doc.getString("name");
                    String fromUserProfilePic = doc.getString("profilePic");

                    String messageText = "Shared a note: " + (note.getText() != null ? note.getText() : "Image Note");

                    Map<String, Object> message = new HashMap<>();
                    message.put("fromUserId", fromUserId);
                    message.put("fromUserName", fromUserName);
                    message.put("fromUserProfilePic", fromUserProfilePic);
                    message.put("toUserId", recipient.getUserId());
                    message.put("messageText", messageText);
                    message.put("timestamp", System.currentTimeMillis());
                    message.put("anonymous", false);
                    message.put("read", false);
                    message.put("type", "shared_note");

                    // Note Data
                    message.put("noteId", note.getId());
                    message.put("noteText", note.getText());
                    message.put("noteImage", note.getImageBase64());
                    message.put("noteLat", note.getLat());
                    message.put("noteLng", note.getLng());
                    message.put("noteLikes", note.getLikesCount());
                    message.put("noteComments", note.getCommentsCount());

                    db.collection("messages").add(message)
                            .addOnSuccessListener(docRef -> {
                                // Create notification
                                Map<String, Object> notification = new HashMap<>();
                                notification.put("toUserId", recipient.getUserId());
                                notification.put("fromUserId", fromUserId);
                                notification.put("fromUserName", fromUserName);
                                notification.put("fromUserProfilePic", fromUserProfilePic);
                                notification.put("type", "shared_note");
                                notification.put("messageId", docRef.getId());
                                notification.put("messageText", "Shared a note with you");
                                notification.put("timestamp", System.currentTimeMillis());
                                notification.put("read", false);

                                notification.put("noteId", note.getId());

                                db.collection("notifications").add(notification);

                                if (getView() != null) com.visiboard.app.utils.UiHelper.showSuccess(getView(), "Note shared!");
                            })
                            .addOnFailureListener(e -> {
                                if (getView() != null) com.visiboard.app.utils.UiHelper.showError(getView(), "Failed to share note");
                            });
                });
    }

    private void showSendMessageDialog(UserInfo recipient) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_send_message, null);
        TextView tvRecipient = dialogView.findViewById(R.id.tv_recipient_name);
        TextInputEditText etMessage = dialogView.findViewById(R.id.et_message);
        android.widget.CheckBox cbAnonymous = dialogView.findViewById(R.id.cb_anonymous);
        Button btnSend = dialogView.findViewById(R.id.btn_send);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);

        tvRecipient.setText("To: " + recipient.getName());

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setView(dialogView).create();

        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        btnSend.setOnClickListener(v -> {
            String messageText = etMessage.getText().toString().trim();
            if (!messageText.isEmpty()) {
                sendMessage(recipient.getUserId(), messageText, cbAnonymous.isChecked());
                dialog.dismiss();
            } else {
                if (getView() != null) com.visiboard.app.utils.UiHelper.showWarning(getView(), "Please enter a message");
            }
        });
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void sendMessage(String toUserId, String messageText, boolean anonymous) {
        String fromUserId = auth.getCurrentUser().getUid();
        db.collection("users").document(fromUserId).get().addOnSuccessListener(doc -> {
            String fromUserName = doc.getString("name");
            String fromUserProfilePic = doc.getString("profilePic");

            Map<String, Object> message = new HashMap<>();
            message.put("fromUserId", fromUserId);
            message.put("fromUserName", anonymous ? "Anonymous" : fromUserName);
            message.put("fromUserProfilePic", anonymous ? null : fromUserProfilePic);
            message.put("toUserId", toUserId);
            message.put("messageText", messageText);
            message.put("timestamp", System.currentTimeMillis());
            message.put("anonymous", anonymous);
            message.put("read", false);

            db.collection("messages").add(message).addOnSuccessListener(docRef -> {
                createMessageNotification(toUserId, fromUserId, anonymous ? "Anonymous" : fromUserName, anonymous ? null : fromUserProfilePic, messageText, docRef.getId());
                if (getView() != null) com.visiboard.app.utils.UiHelper.showSuccess(getView(), "Message sent!");
            });
        });
    }

    private void createMessageNotification(String toUserId, String fromUserId, String fromUserName, String fromUserProfilePic, String messageText, String messageId) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("toUserId", toUserId);
        notification.put("fromUserId", fromUserId);
        notification.put("fromUserName", fromUserName);
        notification.put("fromUserProfilePic", fromUserProfilePic);
        notification.put("type", "message");
        notification.put("messageId", messageId);
        notification.put("messageText", messageText);
        notification.put("timestamp", System.currentTimeMillis());
        notification.put("read", false);
        db.collection("notifications").add(notification);
    }


    // --- Navigation Feature Methods ---

    private void startNavigation(LatLng destination) {
        isNavigatingToNote = true;
        navigationDestination = destination;
        cvNavigationOverlay.setVisibility(View.VISIBLE);
        tvNavDistance.setText("Calculating...");
        tvNavTime.setText("");

        // Hide legend widget to prevent overlap
        if (cvLegendWidget != null) {
            cvLegendWidget.setVisibility(View.GONE);
        }
        
        // Hide Time Travel button
        if (btnTimeTravel != null) {
            btnTimeTravel.setVisibility(View.GONE);
        }

        // Fetch initial route
        if (lastRouteFetchLocation != null) {
            fetchRoute(new LatLng(lastRouteFetchLocation.getLatitude(), lastRouteFetchLocation.getLongitude()), destination);
        } else {
            // Try to get current location
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                    if (location != null) {
                        lastRouteFetchLocation = location;
                        fetchRoute(new LatLng(location.getLatitude(), location.getLongitude()), destination);
                    } else {
                        if (getView() != null) com.visiboard.app.utils.UiHelper.showInfo(getView(), "Waiting for location...");
                    }
                });
            }
        }
    }

    private void stopNavigation() {
        isNavigatingToNote = false;
        navigationDestination = null;
        cvNavigationOverlay.setVisibility(View.GONE);

        // Restore legend widget
        if (cvLegendWidget != null) {
            cvLegendWidget.setVisibility(View.VISIBLE);
        }
        
        // Restore Time Travel button
        if (btnTimeTravel != null) {
            btnTimeTravel.setVisibility(View.VISIBLE);
        }

        // Clear route from map
        if (navigationRouteSource != null) {
            navigationRouteSource.setGeoJson(FeatureCollection.fromFeatures(new Feature[]{}));
        }
    }

    private void fetchRoute(LatLng start, LatLng end) {
        if (!isNavigatingToNote) return;

        String apiKey = "4034ef4942f146d6b43fd4a9871cfdc3"; // Using existing key from style URL
        String url = "https://api.geoapify.com/v1/routing?waypoints=" +
                start.getLatitude() + "," + start.getLongitude() + "|" +
                end.getLatitude() + "," + end.getLongitude() +
                "&mode=walk&apiKey=" + apiKey;

        Request request = new Request.Builder()
                .url(url)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() ->
                            com.visiboard.app.utils.UiHelper.showError(getActivity().findViewById(android.R.id.content), "Failed to fetch route")
                    );
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String jsonResponse = response.body().string();
                        JSONObject json = new JSONObject(jsonResponse);
                        JSONArray features = json.getJSONArray("features");

                        if (features.length() > 0) {
                            JSONObject feature = features.getJSONObject(0);
                            JSONObject properties = feature.getJSONObject("properties");

                            // Get distance and time
                            int distanceMeters = properties.getInt("distance");
                            int timeSeconds = properties.getInt("time"); // Note: Geoapify returns time in seconds usually
                            // Actually documentation says 'time' is in seconds.

                            // Get Geometry
                            JSONObject geometry = feature.getJSONObject("geometry");
                            final Feature routeFeature = Feature.fromJson(feature.toString());

                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> {
                                    if (!isNavigatingToNote) return;

                                    // Update Overlay
                                    if (distanceMeters < 1000) {
                                        tvNavDistance.setText(distanceMeters + " m");
                                    } else {
                                        tvNavDistance.setText(String.format(java.util.Locale.US, "%.1f km", distanceMeters / 1000.0));
                                    }

                                    int minutes = timeSeconds / 60;
                                    if (minutes < 1) tvNavTime.setText("< 1 min");
                                    else if (minutes > 60) {
                                        int hours = minutes / 60;
                                        int mins = minutes % 60;
                                        tvNavTime.setText(hours + " hr " + mins + " min");
                                    } else {
                                        tvNavTime.setText(minutes + " min");
                                    }

                                    // Update Map Layer
                                    if (navigationRouteSource != null) {
                                        navigationRouteSource.setGeoJson(routeFeature);
                                    } else {
                                        // Initialize source if somehow null (should be done in onMapReady)
                                        Log.e(TAG, "Navigation source is null");
                                    }
                                });
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void updateNavigation(Location location) {
        if (!isNavigatingToNote || navigationDestination == null) return;

        // Check distance threshold to strictly limit API calls
        if (lastRouteFetchLocation == null || location.distanceTo(lastRouteFetchLocation) > MIN_DISTANCE_FOR_RECALCULATION) {
            lastRouteFetchLocation = location;
            // Also check if we are very close to destination to stop?
            // Optional: Auto-stop if < 10 meters.
            float distToDest = location.distanceTo(new Location("dest") {{
                setLatitude(navigationDestination.getLatitude());
                setLongitude(navigationDestination.getLongitude());
            }});

            if (distToDest < 15) { // 15 meters arrival threshold
                if (getView() != null) com.visiboard.app.utils.UiHelper.showSuccess(getView(), "You have arrived!");
                stopNavigation();
                return;
            }

            fetchRoute(new LatLng(location.getLatitude(), location.getLongitude()), navigationDestination);
        }
    }

    private void initializeNavigationLayer(@NonNull Style style) {
        // Source
        if (style.getSource(NAVIGATION_SOURCE_ID) == null) {
            navigationRouteSource = new GeoJsonSource(NAVIGATION_SOURCE_ID);
            style.addSource(navigationRouteSource);
        }

        // Layer
        if (style.getLayer(NAVIGATION_LAYER_ID) == null) {
            LineLayer lineLayer = new LineLayer(NAVIGATION_LAYER_ID, NAVIGATION_SOURCE_ID);
            lineLayer.setProperties(
                    lineColor(android.graphics.Color.parseColor("#4A90E2")), // Blue path
                    lineWidth(5f),
                    lineCap(Property.LINE_CAP_ROUND),
                    lineJoin(Property.LINE_JOIN_ROUND)
            );
            style.addLayer(lineLayer);
        }
    }

    // --- Note Option Handlers ---

    private void handleSaveNote(String noteId, boolean currentlySaved, String noteText, long timestamp, String ownerId, LatLng position, String imageBase64) {
        String currentUserId = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
        if (currentUserId == null) return;

        if (currentlySaved) {
            // Unsave
            db.collection("users").document(currentUserId).collection("saved_notes").document(noteId).delete()
                    .addOnSuccessListener(aVoid -> {
                        if (getView() != null) com.visiboard.app.utils.UiHelper.showSuccess(getView(), "Note removed from saved");
                    });
        } else {
            // Save
            Map<String, Object> savedData = new HashMap<>();
            savedData.put("noteId", noteId);
            savedData.put("timestamp", timestamp);
            savedData.put("savedAt", System.currentTimeMillis());

            // Denormalized data
            savedData.put("noteText", noteText);
            savedData.put("ownerId", ownerId);
            savedData.put("latitude", position.getLatitude());
            savedData.put("longitude", position.getLongitude());
            if (imageBase64 != null) {
                savedData.put("imageBase64", imageBase64);
            }

            db.collection("users").document(currentUserId).collection("saved_notes").document(noteId).set(savedData)
                    .addOnSuccessListener(aVoid -> {
                        if (getView() != null) com.visiboard.app.utils.UiHelper.showSuccess(getView(), "Note saved");
                    });
        }
    }

    private void handleHideNote(String noteId, boolean isOwner) {
        String currentUserId = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
        if (currentUserId == null) return;

        if (isOwner) {
            // Owner hiding from map (Visibility toggle)
             db.collection("notes").document(noteId).get().addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    boolean isHidden = doc.getBoolean("isHidden") != null && doc.getBoolean("isHidden");
                    boolean newHiddenState = !isHidden;
                    db.collection("notes").document(noteId).update("isHidden", newHiddenState)
                        .addOnSuccessListener(a -> {
                            if (getView() != null) com.visiboard.app.utils.UiHelper.showInfo(getView(), newHiddenState ? "Note Hidden from Map" : "Note Visible on Map");
                        });
                }
             });
        } else {
            // Non-owner hiding for themselves
             Map<String, Object> hiddenData = new HashMap<>();
             hiddenData.put("noteId", noteId);
             hiddenData.put("hiddenAt", System.currentTimeMillis());

             db.collection("users").document(currentUserId).collection("hidden_notes_others").document(noteId).set(hiddenData)
                 .addOnSuccessListener(aVoid -> {
                     if (getView() != null) com.visiboard.app.utils.UiHelper.showSuccess(getView(), "Note hidden from your view");
                     // Preserve userLocationSymbol when hiding notes
                     deleteAllSymbolsExceptUserLocation();
                     loadSavedNotes();
                 });
        }
    }

    private void handleEditNote(String noteId, String content, String imageBase64, Dialog dialog) {
        android.content.Intent intent = new android.content.Intent(requireContext(), com.visiboard.app.ui.create.CreateNoteActivity.class);
        intent.putExtra("edit_note_id", noteId);
        intent.putExtra("edit_content", content);
        intent.putExtra("edit_image_base64", imageBase64);
        startActivity(intent);
        if (dialog != null) dialog.dismiss();
    }

    private void handleDeleteNote(String noteId, String ownerId, LatLng position, Symbol symbol, Dialog dialog) {
        if (useCloudMode && noteId != null) {
            deleteNoteFirestore(noteId, ownerId);
        } else {
            deleteNoteLocally(position);
        }
        if (symbolManager != null && symbol != null) {
            symbolManager.delete(symbol);
        }
        if (dialog != null) dialog.dismiss();
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST);
        } else {
            enableUserLocation(true);
        }
    }

    private void handleToggleComments(String noteId, boolean currentlyDisabled) {
         db.collection("notes").document(noteId)
             .update("commentsDisabled", !currentlyDisabled)
             .addOnSuccessListener(a -> {
                 if (getView() != null) com.visiboard.app.utils.UiHelper.showInfo(getView(), !currentlyDisabled ? "Comments Turned Off" : "Comments Turned On");
             });
    }
    
    // Helper for Gradient Glow (Simulated Blur)
    private void applyGradientGlow(View view) {
        if (view == null) return;
        
        // Define clean vibrant gradient colors
        int[] colors = {
            0xFF8A2BE2, // Blue Violet
            0xFF00FFFF, // Cyan
            0xFFFF1493  // Deep Pink
        };
        
        // Layer 1: Outer Faint Glow (Most Transparent, Full Size)
        android.graphics.drawable.GradientDrawable l1 = new android.graphics.drawable.GradientDrawable();
        l1.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        l1.setCornerRadius(dpToPx(18));
        l1.setOrientation(android.graphics.drawable.GradientDrawable.Orientation.TL_BR);
        l1.setColors(colors);
        l1.setAlpha(40); // Very faint
        
        // Layer 2: Middle Glow (Medium Transparent, Slightly Smaller)
        android.graphics.drawable.GradientDrawable l2 = new android.graphics.drawable.GradientDrawable();
        l2.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        l2.setCornerRadius(dpToPx(18));
        l2.setOrientation(android.graphics.drawable.GradientDrawable.Orientation.TL_BR);
        l2.setColors(colors);
        l2.setAlpha(100); 
        
        // Layer 3: Inner Core (Solid, Smallest)
        android.graphics.drawable.GradientDrawable l3 = new android.graphics.drawable.GradientDrawable();
        l3.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        l3.setCornerRadius(dpToPx(18));
        l3.setOrientation(android.graphics.drawable.GradientDrawable.Orientation.TL_BR);
        l3.setColors(colors);
        l3.setAlpha(255); // Solid
        
        // Combine into LayerDrawable
        android.graphics.drawable.LayerDrawable layerDrawable = new android.graphics.drawable.LayerDrawable(
            new android.graphics.drawable.Drawable[]{l1, l2, l3}
        );
        
        // Settings insets to simulate "Fading Out"
        int step = dpToPx(1); // Thinner steps for 4dp border
        layerDrawable.setLayerInset(0, 0, 0, 0, 0);       // Outer
        layerDrawable.setLayerInset(1, step, step, step, step);   // Middle
        layerDrawable.setLayerInset(2, step*3, step*3, step*3, step*3); // Inner (3dp inset)
        
        view.setBackground(layerDrawable);
        
        // Enforce padding to control border thickness (4dp)
        int borderThickness = dpToPx(4);
        view.setPadding(borderThickness, borderThickness, borderThickness, borderThickness);
    }
    
    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}