package com.visiboard.app.ui.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.firebase.firestore.FirebaseFirestore;
import com.visiboard.app.R;
import com.visiboard.app.data.NearbyNote;
import com.visiboard.app.data.ReportRepository;
import com.visiboard.app.ui.profile.RecentNotesAdapter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AdminProfileFragment extends Fragment {

    private static final String ARG_TARGET_USER_ID = "target_user_id";
    private static final String ARG_REPORT_ID = "report_id";

    private String targetUserId;
    private String reportId;
    private FirebaseFirestore db;
    private ReportRepository repository;
    
    private TextView tvUserName, tvUserEmail, tvReportCategory, tvReporterName, tvCaseId, tvReportDescription;
    private ImageView imgProfile;
    private RecyclerView recyclerNotes;
    private View layoutReportInfo;
    private TextView tvEmptyNotes;

    public static AdminProfileFragment newInstance(String targetUserId, String reportId) {
        AdminProfileFragment fragment = new AdminProfileFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TARGET_USER_ID, targetUserId);
        args.putString(ARG_REPORT_ID, reportId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_profile, container, false);
        db = FirebaseFirestore.getInstance();
        repository = new ReportRepository();
        
        if (getArguments() != null) {
            targetUserId = getArguments().getString(ARG_TARGET_USER_ID);
            reportId = getArguments().getString(ARG_REPORT_ID);
        }

        initializeViews(view);
        fetchUserData();
        
        if (reportId != null) {
            fetchReportData();
        } else {
            layoutReportInfo.setVisibility(View.GONE);
            view.findViewById(R.id.lblReportInfo).setVisibility(View.GONE);
            tvCaseId.setText("USER PROFILE");
        }
        
        fetchUserNotes();

        return view;
    }

    private void initializeViews(View view) {
        tvCaseId = view.findViewById(R.id.tvCaseId);
        tvUserName = view.findViewById(R.id.tvUserName);
        tvUserEmail = view.findViewById(R.id.tvUserEmail);
        imgProfile = view.findViewById(R.id.imgProfile);
        
        layoutReportInfo = view.findViewById(R.id.layoutReportInfo);
        tvReportCategory = view.findViewById(R.id.tvReportCategory);
        tvReporterName = view.findViewById(R.id.tvReporterName);
        tvReportDescription = view.findViewById(R.id.tvReportDescription);
        
        recyclerNotes = view.findViewById(R.id.recyclerNotes);
        recyclerNotes.setLayoutManager(new LinearLayoutManager(getContext()));
        
        view.findViewById(R.id.btnBack).setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());
        view.findViewById(R.id.btnMenu).setOnClickListener(v -> showAdminActionsBottomSheet());
    }

    private void fetchUserData() {
        if (targetUserId == null) return;
        
        db.collection("users").document(targetUserId).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                tvUserName.setText(doc.getString("name"));
                tvUserEmail.setText(doc.getString("email"));
                
                String pic = doc.getString("profilePic");
                if (pic != null && !pic.isEmpty()) {
                    try {
                        byte[] decodedString;
                        if (pic.startsWith("data:image")) {
                            String base64Image = pic;
                            if (base64Image.contains(",")) {
                                base64Image = base64Image.split(",")[1];
                            }
                            decodedString = android.util.Base64.decode(base64Image, android.util.Base64.DEFAULT);
                        } else {
                            decodedString = android.util.Base64.decode(pic, android.util.Base64.DEFAULT);
                        }
                        
                        Glide.with(this)
                             .load(decodedString)
                             .circleCrop()
                             .placeholder(R.drawable.ic_admin_users)
                             .into(imgProfile);
                    } catch (Exception e) { 
                        e.printStackTrace(); 
                        Glide.with(this)
                             .load(pic)
                             .circleCrop()
                             .placeholder(R.drawable.ic_admin_users)
                             .into(imgProfile);
                    }
                } else {
                    imgProfile.setImageResource(R.drawable.ic_admin_users);
                }
            }
        });
    }

    private void fetchReportData() {
        db.collection("reports").document(reportId).get().addOnSuccessListener(doc -> {
             if (doc.exists()) {
                 tvCaseId.setText("CASE #" + doc.getId().substring(0, 8).toUpperCase());
                 tvReportCategory.setText(doc.getString("category"));
                 tvReportDescription.setText(doc.getString("description"));
                 
                 String reporterId = doc.getString("reporterId");
                 if (reporterId != null) {
                     db.collection("users").document(reporterId).get().addOnSuccessListener(rDoc -> {
                         if (rDoc.exists()) tvReporterName.setText(rDoc.getString("name"));
                     });
                 }
             }
        });
    }
    
    private void fetchUserNotes() {
        db.collection("notes").whereEqualTo("userId", targetUserId).get().addOnSuccessListener(snapshots -> {
            List<NearbyNote> notes = new ArrayList<>();
            
            for (var doc : snapshots.getDocuments()) {
                NearbyNote note = new NearbyNote();
                note.setId(doc.getId());
                
                String noteText = doc.getString("text");
                if (noteText == null) noteText = doc.getString("note");
                note.setText(noteText);
                
                String summary = doc.getString("summary");
                if (summary == null && noteText != null && noteText.length() > 100) {
                    summary = noteText.substring(0, 100) + "...";
                }
                note.setSummary(summary);
                
                note.setUserId(targetUserId);
                note.setImageBase64(doc.getString("imageBase64"));
                
                Long timestamp = doc.getLong("timestamp");
                note.setTimestamp(timestamp != null ? timestamp : 0);
                
                Long likeCount = doc.getLong("likeCount");
                if (likeCount == null) likeCount = doc.getLong("likesCount");
                note.setLikesCount(likeCount != null ? likeCount.intValue() : 0);
                
                Long commentsCount = doc.getLong("commentsCount");
                note.setCommentsCount(commentsCount != null ? commentsCount.intValue() : 0);
                
                notes.add(note);
            }
            
            notes.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
            
            if (notes.isEmpty()) {
                // Show empty state
            } else {
                // Non-clickable adapter (null listener)
                RecentNotesAdapter adapter = new RecentNotesAdapter(null);
                adapter.setNotes(notes);
                recyclerNotes.setAdapter(adapter);
            }
        }).addOnFailureListener(e -> {
             Toast.makeText(getContext(), "Error loading notes: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void showAdminActionsBottomSheet() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(requireContext());
        View bottomSheetView = LayoutInflater.from(getContext()).inflate(R.layout.bottom_sheet_admin_actions, null);
        
        // Hide Note specific action
        bottomSheetView.findViewById(R.id.action_delete_note).setVisibility(View.GONE);

        bottomSheetView.findViewById(R.id.action_restrict).setOnClickListener(v -> {
             bottomSheetDialog.dismiss();
             showDatePickerForAction("restrict");
        });

        bottomSheetView.findViewById(R.id.action_ban).setOnClickListener(v -> {
             bottomSheetDialog.dismiss();
             showDatePickerForAction("ban");
        });

        bottomSheetView.findViewById(R.id.action_dismiss).setOnClickListener(v -> {
             bottomSheetDialog.dismiss();
             dismissReport();
        });

        bottomSheetDialog.setContentView(bottomSheetView);
        bottomSheetDialog.show();
    }
    
    private void showDatePickerForAction(String actionType) {
        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select " + (actionType.equals("ban") ? "Ban" : "Restriction") + " Expiry Date")
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds() + (7L * 24 * 60 * 60 * 1000)) // Default 7 days
            .build();
        
        datePicker.addOnPositiveButtonClickListener(selection -> {
            if (actionType.equals("ban")) {
                banUser(selection);
            } else {
                restrictUser(selection);
            }
        });
        
        datePicker.show(getChildFragmentManager(), "DATE_PICKER");
    }
    
    private void banUser(long expiryDate) {
        if (targetUserId == null) return;
        
        db.collection("users").document(targetUserId)
            .update("banned", true, "banExpiryDate", expiryDate)
            .addOnSuccessListener(aVoid -> {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                String dateStr = sdf.format(new Date(expiryDate));
                Toast.makeText(getContext(), "User banned until " + dateStr, Toast.LENGTH_SHORT).show();
                
                // Notify user
                repository.notifyUser(targetUserId, "You have been banned until " + dateStr, "admin");
                
                // Mark report as resolved if exists
                if (reportId != null) {
                    repository.dismissReport(reportId, new ReportRepository.OnActionListener() {
                        @Override public void onSuccess() {}
                        @Override public void onFailure(String error) {}
                    });
                }
            })
            .addOnFailureListener(e -> Toast.makeText(getContext(), "Failed to ban user", Toast.LENGTH_SHORT).show());
    }
    
    private void restrictUser(long expiryDate) {
        if (targetUserId == null) return;
        
        db.collection("users").document(targetUserId)
            .update("restricted", true, "restrictionExpiryDate", expiryDate)
            .addOnSuccessListener(aVoid -> {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                String dateStr = sdf.format(new Date(expiryDate));
                Toast.makeText(getContext(), "User restricted until " + dateStr, Toast.LENGTH_SHORT).show();
                
                repository.notifyUser(targetUserId, "You have been restricted from posting until " + dateStr, "admin");
                
                if (reportId != null) {
                    repository.dismissReport(reportId, new ReportRepository.OnActionListener() {
                        @Override public void onSuccess() {}
                        @Override public void onFailure(String error) {}
                    });
                }
            })
            .addOnFailureListener(e -> Toast.makeText(getContext(), "Failed to restrict user", Toast.LENGTH_SHORT).show());
    }
    
    private void dismissReport() {
        if (reportId == null) {
            Toast.makeText(getContext(), "No report to dismiss", Toast.LENGTH_SHORT).show();
            return;
        }
        
        repository.dismissReport(reportId, new ReportRepository.OnActionListener() {
            @Override
            public void onSuccess() {
                Toast.makeText(getContext(), "Report dismissed", Toast.LENGTH_SHORT).show();
                requireActivity().getSupportFragmentManager().popBackStack();
            }
            @Override
            public void onFailure(String error) {
                Toast.makeText(getContext(), "Error: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }
}

