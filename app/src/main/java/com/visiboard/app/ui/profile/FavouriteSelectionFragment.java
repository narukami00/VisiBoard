package com.visiboard.app.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.visiboard.app.R;

import java.util.ArrayList;
import java.util.List;

public class FavouriteSelectionFragment extends Fragment {

    private RecyclerView rvNotes;
    private View btnSave;
    private SelectableNotesAdapter adapter;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_favourite_selection, container, false);
        
        // Setup Toolbar
        androidx.appcompat.widget.Toolbar toolbar = v.findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(view -> Navigation.findNavController(view).popBackStack());
        
        rvNotes = v.findViewById(R.id.rv_selectable_notes);
        btnSave = v.findViewById(R.id.fab_save_selection);
        
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        
        rvNotes.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new SelectableNotesAdapter();
        rvNotes.setAdapter(adapter);
        
        loadNotes();
        
        btnSave.setOnClickListener(view -> saveSelection());
        
        return v;
    }
    
    private void loadNotes() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        // 1. Get Selected Ids
        db.collection("users").document(uid).get().addOnSuccessListener(userDoc -> {
             if (getContext() == null) return;
             List<String> favs = (List<String>) userDoc.get("favouriteNotes");
             adapter.setSelectedIds(favs);
             
             // 2. Load ALl Notes
             db.collection("notes")
                .whereEqualTo("userId", uid)
                .get()
                .addOnSuccessListener(querySnap -> {
                    if (getContext() == null) return;
                    
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
                        adapter.setNotes(docs);
                    } else {
                        Toast.makeText(getContext(), "No notes found to select from.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                     if (getContext() != null) 
                         Toast.makeText(getContext(), "Error loading notes: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
        });
    }
    
    private void saveSelection() {
        if (auth.getCurrentUser() == null) return;
        
        List<String> selectedIds = adapter.getSelectedIds();
        if (selectedIds.size() > 3) {
             Toast.makeText(getContext(), "Max 3 notes allowed", Toast.LENGTH_SHORT).show();
             return;
        }

        db.collection("users").document(auth.getCurrentUser().getUid())
            .update("favouriteNotes", selectedIds)
            .addOnSuccessListener(a -> {
                if (getContext() == null || getView() == null) return;
                
                Bundle result = new Bundle();
                result.putBoolean("refresh_favs", true);
                getParentFragmentManager().setFragmentResult("fav_notes_updated", result);
                Navigation.findNavController(requireView()).popBackStack();
            })
            .addOnFailureListener(e -> Toast.makeText(getContext(), "Failed to save", Toast.LENGTH_SHORT).show());
    }
}
