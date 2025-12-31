package com.visiboard.app.ui.settings;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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
import com.visiboard.app.R;
import com.visiboard.app.data.NearbyNote;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HiddenNotesActivity extends AppCompatActivity {

    private RecyclerView rvHiddenNotes;
    private LinearLayout layoutEmptyState;
    private ProgressBar progressLoading;
    private androidx.appcompat.widget.Toolbar toolbar;

    private HiddenNotesAdapter adapter;
    private List<NearbyNote> hiddenNotes;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hidden_notes);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        if (auth.getCurrentUser() == null) {
            finish();
            return;
        }

        // Init UI
        rvHiddenNotes = findViewById(R.id.rv_hidden_notes);
        layoutEmptyState = findViewById(R.id.layout_empty_state);
        progressLoading = findViewById(R.id.progress_loading);
        toolbar = findViewById(R.id.toolbar);

        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        // Setup RecyclerView
        hiddenNotes = new ArrayList<>();
        adapter = new HiddenNotesAdapter();
        rvHiddenNotes.setLayoutManager(new LinearLayoutManager(this));
        rvHiddenNotes.setAdapter(adapter);

        loadHiddenNotes();
    }

    private void loadHiddenNotes() {
        progressLoading.setVisibility(View.VISIBLE);
        layoutEmptyState.setVisibility(View.GONE);
        String userId = auth.getCurrentUser().getUid();

        db.collection("notes")
                .whereEqualTo("userId", userId)
                .whereEqualTo("isHidden", true)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    progressLoading.setVisibility(View.GONE);
                    hiddenNotes.clear();
                    
                    for (DocumentSnapshot doc : querySnapshot) {
                        NearbyNote note = new NearbyNote();
                        note.setId(doc.getId());
                        note.setText(doc.getString("note") != null ? doc.getString("note") : doc.getString("text"));
                        note.setTimestamp(doc.getLong("timestamp") != null ? doc.getLong("timestamp") : 0L);
                        note.setImageBase64(doc.getString("imageBase64"));
                        note.setUserId(userId);
                        note.setHidden(true);
                        hiddenNotes.add(note);
                    }

                    if (hiddenNotes.isEmpty()) {
                        layoutEmptyState.setVisibility(View.VISIBLE);
                    } else {
                        adapter.notifyDataSetChanged();
                    }
                })
                .addOnFailureListener(e -> {
                    progressLoading.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to load hidden notes", Toast.LENGTH_SHORT).show();
                    com.visiboard.app.utils.UiHelper.showError(findViewById(android.R.id.content), "Failed to load hidden notes");
                });
    }

    private void unhideNote(NearbyNote note, int position) {
        db.collection("notes").document(note.getId())
                .update("isHidden", false)
                .addOnSuccessListener(aVoid -> {
                    com.visiboard.app.utils.UiHelper.showSuccess(findViewById(android.R.id.content), "Note unhidden");
                    // Remove from list
                    hiddenNotes.remove(position);
                    adapter.notifyItemRemoved(position);
                    adapter.notifyItemRangeChanged(position, hiddenNotes.size());

                    if (hiddenNotes.isEmpty()) {
                        layoutEmptyState.setVisibility(View.VISIBLE);
                    }
                })
                .addOnFailureListener(e -> {
                    com.visiboard.app.utils.UiHelper.showError(findViewById(android.R.id.content), "Failed to unhide note");
                });
    }

    // Inner Adapter Class
    private class HiddenNotesAdapter extends RecyclerView.Adapter<HiddenNotesAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_hidden_note, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            NearbyNote note = hiddenNotes.get(position);
            holder.tvNoteText.setText(note.getText());
            holder.tvTimestamp.setText(getTimeAgo(note.getTimestamp()));

            if (note.getImageBase64() != null && !note.getImageBase64().isEmpty()) {
                holder.cvImageContainer.setVisibility(View.VISIBLE);
                try {
                    byte[] decodedString = Base64.decode(note.getImageBase64(), Base64.DEFAULT);
                    Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                    holder.ivNoteImage.setImageBitmap(decodedByte);
                } catch (Exception e) {
                    e.printStackTrace();
                    holder.cvImageContainer.setVisibility(View.GONE);
                }
            } else {
                holder.cvImageContainer.setVisibility(View.GONE);
            }

            holder.btnUnhide.setOnClickListener(v -> unhideNote(note, position));
        }

        @Override
        public int getItemCount() {
            return hiddenNotes.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvNoteText, tvTimestamp;
            ImageView ivNoteImage;
            CardView cvImageContainer;
            Button btnUnhide;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvNoteText = itemView.findViewById(R.id.tv_note_text);
                tvTimestamp = itemView.findViewById(R.id.tv_timestamp);
                ivNoteImage = itemView.findViewById(R.id.iv_note_image);
                cvImageContainer = itemView.findViewById(R.id.cv_image_container);
                btnUnhide = itemView.findViewById(R.id.btn_unhide);
            }
        }
    }

    private String getTimeAgo(long timestamp) {
        if (timestamp == 0) return "";
        long now = System.currentTimeMillis();
        long diff = now - timestamp;
        
        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) return days + "d ago";
        if (hours > 0) return hours + "h ago";
        if (minutes > 0) return minutes + "m ago";
        return "Just now";
    }
}
