package com.visiboard.app.ui.admin;

import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.BarLineChartBase;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;

// MapLibre Imports
import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.OnMapReadyCallback;
import org.maplibre.android.maps.Style;
// Removed SymbolManager as we are using HeatmapLayer now, or can keep if mixed usage. 
// I'll keep it simple for Heatmap only as requested "density".

import org.maplibre.android.style.layers.HeatmapLayer;
import org.maplibre.android.style.sources.GeoJsonSource;
import static org.maplibre.android.style.layers.PropertyFactory.heatmapColor;
import static org.maplibre.android.style.layers.PropertyFactory.heatmapIntensity;
import static org.maplibre.android.style.layers.PropertyFactory.heatmapOpacity;
import static org.maplibre.android.style.layers.PropertyFactory.heatmapRadius;
import static org.maplibre.android.style.layers.PropertyFactory.heatmapWeight;
import static org.maplibre.android.style.expressions.Expression.get;
import static org.maplibre.android.style.expressions.Expression.heatmapDensity;
import static org.maplibre.android.style.expressions.Expression.interpolate;
import static org.maplibre.android.style.expressions.Expression.linear;
import static org.maplibre.android.style.expressions.Expression.literal;
import static org.maplibre.android.style.expressions.Expression.rgb;
import static org.maplibre.android.style.expressions.Expression.rgba;
import static org.maplibre.android.style.expressions.Expression.stop;
import static org.maplibre.android.style.expressions.Expression.zoom;
import org.maplibre.geojson.Feature;
import org.maplibre.geojson.FeatureCollection;
import org.maplibre.geojson.Point;


import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.visiboard.app.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class AdminAnalyticsFragment extends Fragment implements OnMapReadyCallback {

    private FirebaseFirestore db;
    private TextView totalNotesText, activeUsersText;
    private MapView mapView;
    private MapLibreMap mapLibreMap;
    private BarChart notesBarChart;
    private LineChart usersLineChart;

    // Geoapify Styles
    private final String GEOAPIFY_DARK_STYLE_URL =
            "https://maps.geoapify.com/v1/styles/dark-matter-dark-grey/style.json?apiKey=4034ef4942f146d6b43fd4a9871cfdc3";
    private final String GEOAPIFY_LIGHT_STYLE_URL =
            "https://maps.geoapify.com/v1/styles/positron/style.json?apiKey=4034ef4942f146d6b43fd4a9871cfdc3";
            
            
    private static final String HEATMAP_SOURCE_ID = "notes-heatmap-source";
    private static final String HEATMAP_LAYER_ID = "notes-heatmap-layer";
    
    // Colors from MapFragment
    private static final int[] NOTE_COLORS = {
            0xFF6C5CE7, 0xFF74B9FF, 0xFF00B894, 0xFFFF6B6B, 0xFFFDCB6E, 
            0xFFE17055, 0xFFA29BFE, 0xFF55EFC4, 0xFFFF7675, 0xFFFD79A8, 0xFF00CEC9, 0xFF81ECEC
    };
    private static final int[] NOTE_BORDER_COLORS = {
            0xFF5849C7, 0xFF5A9DE8, 0xFF00966D, 0xFFE84545, 0xFFE9B949,
            0xFFCB5A3E, 0xFF8B7EE8, 0xFF3ACF98, 0xFFE85454, 0xFFE35B89, 0xFF00A8A5, 0xFF5FD4D4
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Initialize MapLibre
        MapLibre.getInstance(requireContext());
        
        View view = inflater.inflate(R.layout.fragment_admin_analytics, container, false);
        
        db = FirebaseFirestore.getInstance();
        
        // Stats
        View notesCard = view.findViewById(R.id.statTotalNotes);
        View usersCard = view.findViewById(R.id.statActiveUsers);
        
        if (notesCard != null) {
            TextView label = notesCard.findViewById(R.id.statLabel);
            if (label != null) label.setText("Total Notes");
            totalNotesText = notesCard.findViewById(R.id.statValue);
        }
        
        if (usersCard != null) {
            TextView label = usersCard.findViewById(R.id.statLabel);
            if (label != null) label.setText("Active Users");
            activeUsersText = usersCard.findViewById(R.id.statValue);
        }
        
        // Theme Toggle
        android.widget.ImageView btnThemeToggle = view.findViewById(R.id.btnThemeToggle);
        if (btnThemeToggle != null) {
            updateThemeIcon(btnThemeToggle);
            btnThemeToggle.setOnClickListener(v -> {
                com.visiboard.app.utils.ThemeManager.getInstance(requireContext()).toggleTheme();
                if (getActivity() != null) {
                    getActivity().recreate();
                }
            });
        }

        // Map
        mapView = view.findViewById(R.id.mapView);
        if (mapView != null) {
            mapView.onCreate(savedInstanceState);
            mapView.getMapAsync(this);
            
            // Fix for scroll issue: request parent to not intercept touch events when interacting with map
            mapView.setOnTouchListener((v, event) -> {
                int action = event.getAction();
                switch (action) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                        break;
                    case android.view.MotionEvent.ACTION_UP:
                        v.getParent().requestDisallowInterceptTouchEvent(false);
                        break;
                }
                return false;
            });
        }

        // Charts
        notesBarChart = view.findViewById(R.id.notesBarChart);
        usersLineChart = view.findViewById(R.id.usersLineChart);

        setupChartStyle(notesBarChart);
        setupChartStyle(usersLineChart);

        fetchData();

        return view;
    }
    
    private void updateThemeIcon(android.widget.ImageView btnThemeToggle) {
        boolean isDark = com.visiboard.app.utils.ThemeManager.getInstance(requireContext()).isDarkMode();
        if (isDark) {
            btnThemeToggle.setImageResource(R.drawable.ic_light_mode);
        } else {
            btnThemeToggle.setImageResource(R.drawable.ic_dark_mode);
        }
    }

    private void setupChartStyle(BarLineChartBase<?> chart) {
        chart.getDescription().setEnabled(false);
        chart.getLegend().setTextColor(Color.WHITE);
        chart.getXAxis().setTextColor(Color.WHITE);
        chart.getAxisLeft().setTextColor(Color.WHITE);
        chart.getAxisRight().setEnabled(false);
        chart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        chart.setNoDataText("Loading Data...");
        chart.setNoDataTextColor(Color.WHITE);
    }

    private void fetchData() {
        // Fetch Users
        db.collection("users").get().addOnSuccessListener(snapshots -> {
            if (activeUsersText != null) {
                activeUsersText.setText(String.valueOf(snapshots.size()));
            }
            processUserGrowth(snapshots);
        }).addOnFailureListener(e -> {
             if (getContext() != null) Toast.makeText(getContext(), "Error loading users", Toast.LENGTH_SHORT).show();
        });

        // Fetch Notes
        db.collection("notes").get().addOnSuccessListener(snapshots -> {
            if (totalNotesText != null) {
                totalNotesText.setText(String.valueOf(snapshots.size()));
            }
            
            // Map Heatmap Logic
            if (mapLibreMap != null && mapLibreMap.getStyle() != null && mapLibreMap.getStyle().isFullyLoaded()) {
                 updateMapHeatmap(snapshots);
            }
            
            processNoteFrequency(snapshots);
            
        }).addOnFailureListener(e -> {
             if (getContext() != null) Toast.makeText(getContext(), "Error loading notes", Toast.LENGTH_SHORT).show();
        });
    }
    
    private void updateMapHeatmap(com.google.firebase.firestore.QuerySnapshot snapshots) {
         if (mapLibreMap == null || mapLibreMap.getStyle() == null) return;
         Style style = mapLibreMap.getStyle();

         List<Feature> features = new ArrayList<>();
         for (QueryDocumentSnapshot doc : snapshots) {
            Double lat = doc.getDouble("lat");
            Double lng = doc.getDouble("lon");
            if (lng == null) lng = doc.getDouble("lng");
            String noteText = doc.getString("note");
            Long timestamp = doc.getLong("timestamp");
            
            if (lat != null && lng != null) {
                Feature feature = Feature.fromGeometry(Point.fromLngLat(lng, lat));
                String iconId = "note_" + doc.getId();
                feature.addStringProperty("iconId", iconId);
                
                if (style.getImage(iconId) == null && noteText != null && timestamp != null) {
                    Bitmap bitmap = createNoteBitmap(noteText, timestamp, doc.getId());
                    style.addImage(iconId, bitmap);
                }
                
                features.add(feature);
            }
         }
         
         if (features.isEmpty()) return;
         
         GeoJsonSource source = style.getSourceAs(HEATMAP_SOURCE_ID);
         if (source == null) {
             source = new GeoJsonSource(HEATMAP_SOURCE_ID, FeatureCollection.fromFeatures(features));
             style.addSource(source);
             
             HeatmapLayer heatmapLayer = new HeatmapLayer(HEATMAP_LAYER_ID, HEATMAP_SOURCE_ID);
             heatmapLayer.setMaxZoom(15.5f);
             heatmapLayer.setProperties(
                heatmapColor(interpolate(
                    linear(), heatmapDensity(),
                    literal(0), rgba(33, 102, 172, 0),
                    literal(0.2), rgb(103, 169, 207),
                    literal(0.4), rgb(209, 229, 240),
                    literal(0.6), rgb(253, 219, 199),
                    literal(0.8), rgb(239, 138, 98),
                    literal(1), rgb(178, 24, 43)
                )),
                heatmapWeight(interpolate(
                    linear(), get("mag"),
                    stop(0, 0),
                    stop(6, 1)
                )),
                heatmapIntensity(interpolate(
                    linear(), zoom(),
                    stop(0, 1),
                    stop(15, 3) 
                )),
                heatmapRadius(interpolate(
                    linear(), zoom(),
                    stop(0, 2),
                    stop(15, 20)
                )),
                heatmapOpacity(interpolate(
                    linear(), zoom(),
                    stop(13, 1),
                    stop(15.5, 0) 
                ))
             );
             style.addLayer(heatmapLayer);
             
             org.maplibre.android.style.layers.SymbolLayer markerLayer = new org.maplibre.android.style.layers.SymbolLayer("notes-marker-layer", HEATMAP_SOURCE_ID);
             markerLayer.setMinZoom(13.0f);
             markerLayer.setProperties(
                 org.maplibre.android.style.layers.PropertyFactory.iconImage(get("iconId")),
                 org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap(true),
                 org.maplibre.android.style.layers.PropertyFactory.iconOpacity(interpolate(
                    linear(), zoom(),
                    stop(13, 0),
                    stop(14.5, 1)
                 ))
             );
             style.addLayer(markerLayer);
             
         } else {
             source.setGeoJson(FeatureCollection.fromFeatures(features));
         }
    }

    private Bitmap createNoteBitmap(String text, long timestamp, String docId) {
        View noteCardView = LayoutInflater.from(requireContext()).inflate(R.layout.note_card_layout, null);
        TextView noteTextView = noteCardView.findViewById(R.id.note_text_view);
        
        String shortText = text.length() > 20 ? text.substring(0, 20) + "..." : text;
        noteTextView.setText(shortText);

        int colorIndex = (docId != null ? docId.hashCode() : (int) timestamp) % NOTE_COLORS.length;
        if (colorIndex < 0) colorIndex = -colorIndex;

        int backgroundColor = NOTE_COLORS[colorIndex];
        int borderColor = NOTE_BORDER_COLORS[colorIndex];

        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(16 * getResources().getDisplayMetrics().density);
        drawable.setColor(backgroundColor);
        drawable.setStroke((int)(2 * getResources().getDisplayMetrics().density), borderColor);
        noteTextView.setBackground(drawable);
        noteTextView.setTextColor(0xFFFFFFFF);

        noteCardView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        noteCardView.layout(0, 0, noteCardView.getMeasuredWidth(), noteCardView.getMeasuredHeight());
        Bitmap bitmap = Bitmap.createBitmap(noteCardView.getMeasuredWidth(), noteCardView.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        noteCardView.draw(canvas);
        return bitmap;
    }

    private void processNoteFrequency(com.google.firebase.firestore.QuerySnapshot snapshots) {
        if (!isAdded() || getContext() == null) return;
        Map<String, Integer> counts = new TreeMap<>();
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd", Locale.getDefault());
        Calendar cal = Calendar.getInstance();

        // Initialize last 7 days with 0
        for (int i = 6; i >= 0; i--) {
            cal.setTime(new Date());
            cal.add(Calendar.DAY_OF_YEAR, -i);
            counts.put(sdf.format(cal.getTime()), 0);
        }

        for (QueryDocumentSnapshot doc : snapshots) {
            Long timestamp = doc.getLong("timestamp");
            if (timestamp != null) {
                String dateKey = sdf.format(new Date(timestamp));
                if (counts.containsKey(dateKey)) {
                    counts.put(dateKey, counts.get(dateKey) + 1);
                }
            }
        }

        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        int i = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            entries.add(new BarEntry(i++, entry.getValue()));
            labels.add(entry.getKey());
        }

        if (!isAdded() || getContext() == null) return;

        int chartTextColor = getResources().getColor(R.color.text_secondary, null);
        int accentColor = getResources().getColor(R.color.accent, null);

        BarDataSet set = new BarDataSet(entries, "Notes Created");
        set.setColor(accentColor);
        set.setValueTextColor(chartTextColor);

        BarData data = new BarData(set);
        data.setBarWidth(0.9f);
        
        notesBarChart.setData(data);
        notesBarChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        notesBarChart.getXAxis().setTextColor(chartTextColor);
        notesBarChart.getAxisLeft().setTextColor(chartTextColor);
        notesBarChart.getAxisRight().setTextColor(chartTextColor);
        notesBarChart.getLegend().setTextColor(chartTextColor);
        
        CustomMarkerView mv = new CustomMarkerView(getContext(), R.layout.custom_marker_view, labels);
        mv.setChartView(notesBarChart);
        notesBarChart.setMarker(mv);
        notesBarChart.invalidate();
    }

    private void processUserGrowth(com.google.firebase.firestore.QuerySnapshot snapshots) {
        Map<String, Integer> counts = new TreeMap<>();
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd", Locale.getDefault());
        Calendar cal = Calendar.getInstance();

        // Initialize last 7 days
        for (int i = 6; i >= 0; i--) {
            cal.setTime(new Date());
            cal.add(Calendar.DAY_OF_YEAR, -i);
            counts.put(sdf.format(cal.getTime()), 0);
        }

        for (QueryDocumentSnapshot doc : snapshots) {
            Long createdAt = doc.getLong("createdAt");
            if (createdAt != null) {
                String dateKey = sdf.format(new Date(createdAt));
                if (counts.containsKey(dateKey)) {
                    counts.put(dateKey, counts.get(dateKey) + 1);
                }
            }
        }

        List<Entry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        int i = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            entries.add(new Entry(i++, entry.getValue()));
            labels.add(entry.getKey());
        }

        if (!isAdded() || getContext() == null) return;

        int chartTextColor = getResources().getColor(R.color.text_secondary, null);
        int accentColor = getResources().getColor(R.color.accent, null);

        LineDataSet set = new LineDataSet(entries, "New Users");
        set.setColor(accentColor);
        set.setLineWidth(2f);
        set.setCircleColor(chartTextColor);
        set.setValueTextColor(chartTextColor);

        LineData data = new LineData(set);
        usersLineChart.setData(data);
        usersLineChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        usersLineChart.getXAxis().setTextColor(chartTextColor);
        usersLineChart.getAxisLeft().setTextColor(chartTextColor);
        usersLineChart.getAxisRight().setTextColor(chartTextColor);
        usersLineChart.getLegend().setTextColor(chartTextColor);
        
        CustomMarkerView mv = new CustomMarkerView(getContext(), R.layout.custom_marker_view, labels);
        mv.setChartView(usersLineChart);
        usersLineChart.setMarker(mv);
        usersLineChart.invalidate();
    }

    @Override
    public void onMapReady(MapLibreMap map) {
        mapLibreMap = map;
        
        // Select map style based on theme
        boolean isDark = com.visiboard.app.utils.ThemeManager.getInstance(requireContext()).isDarkMode();
        String styleUrl = isDark ? GEOAPIFY_DARK_STYLE_URL : GEOAPIFY_LIGHT_STYLE_URL;
        
        mapLibreMap.setStyle(new Style.Builder().fromUri(styleUrl), style -> {
            // Style loaded - re-fetch to add heatmap
            if (isAdded()) fetchData();
        });

        // Interactive settings
        mapLibreMap.getUiSettings().setAllGesturesEnabled(true);
        mapLibreMap.getUiSettings().setCompassEnabled(false);
        mapLibreMap.getUiSettings().setLogoEnabled(false);
        mapLibreMap.getUiSettings().setAttributionEnabled(false);
        
        mapLibreMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(23.8103, 90.4125), 9)); // Zoomed out slightly for heatmap
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mapView != null) mapView.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mapView != null) mapView.onPause();
    }
    
    @Override
    public void onStop() {
        super.onStop();
        if (mapView != null) mapView.onStop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mapView != null) mapView.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) mapView.onLowMemory();
    }
    
    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView != null) mapView.onSaveInstanceState(outState);
    }
}
