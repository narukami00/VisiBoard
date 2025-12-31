package com.visiboard.app.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.visiboard.app.R;
import com.visiboard.app.utils.ImageCache;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SelectFavouritesBottomSheet extends BottomSheetDialogFragment {

    private RecyclerView rvNotes;
    private View btnSave;
    private SelectorAdapter adapter;
    private final Set<String> selectedIds = new HashSet<>();
    private final List<DocumentSnapshot> allNotes = new ArrayList<>();
    
    private FirebaseFirestore db;
    private FirebaseAuth auth;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.bottom_sheet_select_favourites, container, false);
        rvNotes = v.findViewById(R.id.rv_notes_selection);
        btnSave = v.findViewById(R.id.btn_save_favourites);
        
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        
        rvNotes.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new SelectorAdapter();
        rvNotes.setAdapter(adapter);
        
        loadNotes();
        
        btnSave.setOnClickListener(view -> saveSelection());
        
        return v;
    }

    private void loadNotes() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        // Load current favourites first
        db.collection("users").document(uid).get().addOnSuccessListener(userDoc -> {
             if (getContext() == null) return;
             List<String> favs = (List<String>) userDoc.get("favouriteNotes");
             if (favs != null) selectedIds.addAll(favs);
             
             // Then load ALL notes for this user (mimics ProfileFragment logic)
             db.collection("notes")
                .whereEqualTo("userId", uid)
                .get()
                .addOnSuccessListener(querySnap -> {
                    if (getContext() == null) return;
                    allNotes.clear();
                    
                    if (!querySnap.isEmpty()) {
                        List<DocumentSnapshot> docs = new ArrayList<>(querySnap.getDocuments());
                        // Helper to safely get long
                        docs.sort((d1, d2) -> {
                            Long t1 = d1.getLong("timestamp");
                            Long t2 = d2.getLong("timestamp");
                            if (t1 == null) t1 = 0L;
                            if (t2 == null) t2 = 0L;
                            return t2.compareTo(t1); // Descending
                        });
                        allNotes.addAll(docs);
                    }
                    adapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> {
                    com.visiboard.app.utils.UiHelper.showError(getActivity().findViewById(android.R.id.content), "Error loading notes");
                });
        }).addOnFailureListener(e -> {
             com.visiboard.app.utils.UiHelper.showError(getActivity().findViewById(android.R.id.content), "Error user data");
        });
    }
    
    private void saveSelection() {
        if (auth.getCurrentUser() == null) return;
        List<String> list = new ArrayList<>(selectedIds);
        
        db.collection("users").document(auth.getCurrentUser().getUid())
            .update("favouriteNotes", list)
            .addOnSuccessListener(aVoid -> {
                Bundle result = new Bundle();
                result.putBoolean("refresh_favs", true);
                getParentFragmentManager().setFragmentResult("fav_notes_updated", result);
                dismiss();
            })
            .addOnFailureListener(e -> com.visiboard.app.utils.UiHelper.showError(getActivity().findViewById(android.R.id.content), "Failed to save"));
    }

    private class SelectorAdapter extends RecyclerView.Adapter<SelectorAdapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_note_selection, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            DocumentSnapshot doc = allNotes.get(position);
            String id = doc.getId();
            String content = doc.getString("content");
            String imageUrl = doc.getString("imageUrl");
            java.util.Date date = doc.getDate("timestamp");

            holder.tvContent.setText(content != null && !content.isEmpty() ? content : "Image Note");
            if (date != null) holder.tvDate.setText(android.text.format.DateFormat.format("MMM dd, yyyy", date));
            
            holder.cbSelect.setChecked(selectedIds.contains(id));
            
            if (imageUrl != null && !imageUrl.isEmpty()) {
                holder.ivThumb.setVisibility(View.VISIBLE);
                holder.tvPreview.setVisibility(View.GONE);
                // Safe check or comment out to debug crash
                // try {
                //    ImageCache.getInstance().loadBase64Image("thumb_" + id, imageUrl, holder.ivThumb, R.drawable.ic_image_placeholder);
                // } catch (Exception e) { e.printStackTrace(); }
                // Use simple background for now to verify stability
                holder.ivThumb.setImageResource(R.drawable.ic_image_placeholder); 
            } else {
                holder.ivThumb.setVisibility(View.GONE);
                holder.tvPreview.setVisibility(View.VISIBLE);
                holder.tvPreview.setText(content);
            }

            holder.itemView.setOnClickListener(v -> {
                if (selectedIds.contains(id)) {
                    selectedIds.remove(id);
                } else {
                    if (selectedIds.size() >= 3) {
                        com.visiboard.app.utils.UiHelper.showWarning(getActivity().findViewById(android.R.id.content), "Max 3 notes allowed");
                        return;
                    }
                    selectedIds.add(id);
                }
                notifyItemChanged(position);
            });
        }

        @Override
        public int getItemCount() {
            return allNotes.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            CheckBox cbSelect;
            ImageView ivThumb;
            TextView tvContent, tvDate, tvPreview;

            Holder(View v) {
                super(v);
                cbSelect = v.findViewById(R.id.cb_select);
                ivThumb = v.findViewById(R.id.iv_note_thumb);
                tvContent = v.findViewById(R.id.tv_note_content);
                tvDate = v.findViewById(R.id.tv_note_date);
                tvPreview = v.findViewById(R.id.tv_text_preview_small);
            }
        }
    }
}
