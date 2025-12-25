package com.visiboard.app.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.visiboard.app.R;

import java.util.HashMap;
import java.util.Map;

public class EditDetailsBottomSheet extends BottomSheetDialogFragment {

    private TextInputEditText etWork, etEducation, etRelationship, etHometown;
    private Button btnSave;
    private FirebaseFirestore db;
    private FirebaseAuth auth;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_edit_details, container, false);

        etWork = view.findViewById(R.id.et_work);
        etEducation = view.findViewById(R.id.et_education);
        etRelationship = view.findViewById(R.id.et_relationship);
        etHometown = view.findViewById(R.id.et_hometown);
        btnSave = view.findViewById(R.id.btn_save_details);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        loadCurrentDetails();

        btnSave.setOnClickListener(v -> saveDetails());

        return view;
    }

    private void loadCurrentDetails() {
        if (auth.getCurrentUser() == null) return;

        db.collection("users").document(auth.getCurrentUser().getUid()).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        etWork.setText(documentSnapshot.getString("work"));
                        etEducation.setText(documentSnapshot.getString("education"));
                        etRelationship.setText(documentSnapshot.getString("relationship"));
                        etHometown.setText(documentSnapshot.getString("hometown"));
                    }
                });
    }

    private void saveDetails() {
        if (auth.getCurrentUser() == null) return;

        String work = etWork.getText().toString().trim();
        String education = etEducation.getText().toString().trim();
        String relationship = etRelationship.getText().toString().trim();
        String hometown = etHometown.getText().toString().trim();

        Map<String, Object> updates = new HashMap<>();
        updates.put("work", work);
        updates.put("education", education);
        updates.put("relationship", relationship);
        updates.put("hometown", hometown);

        btnSave.setEnabled(false);
        btnSave.setText("Saving...");

        db.collection("users").document(auth.getCurrentUser().getUid())
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    dismiss();
                    if (getParentFragmentManager() != null) {
                        getParentFragmentManager().setFragmentResult("details_updated", new Bundle());
                    }
                })
                .addOnFailureListener(e -> {
                    btnSave.setEnabled(true);
                    btnSave.setText("Save Details");
                    Toast.makeText(getContext(), "Failed to save: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}
