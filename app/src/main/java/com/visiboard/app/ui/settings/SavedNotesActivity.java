package com.visiboard.app.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.visiboard.app.MainActivity;
import com.visiboard.app.R;
import com.visiboard.app.utils.ImageCache;

import java.util.ArrayList;
import java.util.List;

public class SavedNotesActivity extends AppCompatActivity {

    private RecyclerView rvSavedNotes;
    private LinearLayout layoutEmptyState;
    private ProgressBar pbLoading;
    private ImageButton btnBack;

    private SavedNotesAdapter adapter;
    private FirebaseFirestore db;
    private String currentUserId;
    private List<DocumentSnapshot> savedNoteDocs = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_saved_notes);

        db = FirebaseFirestore.getInstance();
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        rvSavedNotes = findViewById(R.id.rv_saved_notes);
        layoutEmptyState = findViewById(R.id.layout_empty_state);
        pbLoading = findViewById(R.id.progress_loading);
        btnBack = findViewById(R.id.btn_back);

        btnBack.setOnClickListener(v -> finish());

        rvSavedNotes.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SavedNotesAdapter();
        rvSavedNotes.setAdapter(adapter);

        loadSavedNotes();
    }

    private void loadSavedNotes() {
        pbLoading.setVisibility(View.VISIBLE);
        layoutEmptyState.setVisibility(View.GONE);
        rvSavedNotes.setVisibility(View.GONE);

        db.collection("users").document(currentUserId).collection("saved_notes")
                .orderBy("savedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    pbLoading.setVisibility(View.GONE);
                    savedNoteDocs.clear();
                    savedNoteDocs.addAll(queryDocumentSnapshots.getDocuments());

                    if (savedNoteDocs.isEmpty()) {
                        layoutEmptyState.setVisibility(View.VISIBLE);
                        rvSavedNotes.setVisibility(View.GONE);
                    } else {
                        rvSavedNotes.setVisibility(View.VISIBLE);
                        adapter.notifyDataSetChanged();
                    }
                })
                .addOnFailureListener(e -> {
                    pbLoading.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to load saved notes", Toast.LENGTH_SHORT).show();
                });
    }

    private void removeSavedNote(int position) {
        DocumentSnapshot doc = savedNoteDocs.get(position);
        db.collection("users").document(currentUserId).collection("saved_notes")
                .document(doc.getId())
                .delete()
                .addOnSuccessListener(aVoid -> {
                    savedNoteDocs.remove(position);
                    adapter.notifyItemRemoved(position);
                    if (savedNoteDocs.isEmpty()) {
                        layoutEmptyState.setVisibility(View.VISIBLE);
                    }
                    Toast.makeText(this, "Removed from saved", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to remove", Toast.LENGTH_SHORT).show();
                });
    }

    private class SavedNotesAdapter extends RecyclerView.Adapter<SavedNotesAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_saved_note, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            DocumentSnapshot doc = savedNoteDocs.get(position);
            
            String text = doc.getString("noteText");
            Long timestamp = doc.getLong("timestamp");
            String imageBase64 = doc.getString("imageBase64");
            // Location
            Double lat = doc.getDouble("latitude");
            Double lng = doc.getDouble("longitude");
            String noteId = doc.getString("noteId");

            holder.tvNoteText.setText(text != null ? text : "No Content");
            holder.tvTimestamp.setText(getTimeAgo(timestamp != null ? timestamp : 0));

            if (imageBase64 != null && !imageBase64.isEmpty()) {
                holder.imageContainer.setVisibility(View.VISIBLE);
                // Simple load
                try {
                    byte[] decodedString = android.util.Base64.decode(imageBase64, android.util.Base64.DEFAULT);
                    android.graphics.Bitmap decodedByte = android.graphics.BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                    holder.ivNoteImage.setImageBitmap(decodedByte);
                } catch (Exception e) {
                    holder.imageContainer.setVisibility(View.GONE);
                }
            } else {
                holder.imageContainer.setVisibility(View.GONE);
            }

            holder.btnRemove.setOnClickListener(v -> removeSavedNote(position));

            holder.btnViewMap.setOnClickListener(v -> {
                if (lat != null && lng != null && noteId != null) {
                    Intent intent = new Intent(SavedNotesActivity.this, MainActivity.class);
                    intent.putExtra("navigate_to_note_id", noteId);
                    intent.putExtra("navigate_to_lat", lat);
                    intent.putExtra("navigate_to_lng", lng);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                } else {
                    Toast.makeText(SavedNotesActivity.this, "Location data missing", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public int getItemCount() {
            return savedNoteDocs.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvNoteText, tvTimestamp;
            CardView imageContainer;
            ImageView ivNoteImage;
            Button btnRemove, btnViewMap;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvNoteText = itemView.findViewById(R.id.tv_note_text);
                tvTimestamp = itemView.findViewById(R.id.tv_timestamp);
                imageContainer = itemView.findViewById(R.id.cv_image_container);
                ivNoteImage = itemView.findViewById(R.id.iv_note_image);
                btnRemove = itemView.findViewById(R.id.btn_remove);
                btnViewMap = itemView.findViewById(R.id.btn_view_map);
            }
        }
    }

    private String getTimeAgo(long time) {
        if (time < 1000000000000L) {
            time *= 1000;
        }
        long now = System.currentTimeMillis();
        if (time > now || time <= 0) {
            return "just now";
        }
        final long diff = now - time;
        if (diff < 60 * 1000) {
            return "just now";
        } else if (diff < 2 * 60 * 1000) {
            return "a minute ago";
        } else if (diff < 50 * 60 * 1000) {
            return diff / (60 * 1000) + " minutes ago";
        } else if (diff < 90 * 60 * 1000) {
            return "an hour ago";
        } else if (diff < 24 * 60 * 60 * 1000) {
            return diff / (60 * 60 * 1000) + " hours ago";
        } else if (diff < 48 * 60 * 60 * 1000) {
            return "yesterday";
        } else {
            return diff / (24 * 60 * 60 * 1000) + " days ago";
        }
    }
}
