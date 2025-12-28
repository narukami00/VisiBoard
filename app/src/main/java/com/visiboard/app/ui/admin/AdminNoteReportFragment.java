package com.visiboard.app.ui.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.firebase.firestore.FirebaseFirestore;
import com.visiboard.app.R;
import com.visiboard.app.data.Report;
import com.visiboard.app.data.ReportRepository;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AdminNoteReportFragment extends Fragment {

    private static final String ARG_REPORT_ID = "report_id";
    
    private TextView tvCaseId, tvNoteContent, tvNoteAuthor, tvReportCategory, tvReporterName, tvReportDescription;
    private ImageView imgNote, btnMenu;
    
    private FirebaseFirestore db;
    private ReportRepository repository;
    private String reportId;
    private Report currentReport;
    private String noteId;
    private String noteOwnerId;

    public static AdminNoteReportFragment newInstance(String reportId) {
        AdminNoteReportFragment fragment = new AdminNoteReportFragment();
        Bundle args = new Bundle();
        args.putString(ARG_REPORT_ID, reportId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_note_report_detail, container, false);
        
        db = FirebaseFirestore.getInstance();
        repository = new ReportRepository();
        if (getArguments() != null) {
            reportId = getArguments().getString(ARG_REPORT_ID);
        }

        initializeViews(view);
        fetchReportDetails();

        return view;
    }

    private void initializeViews(View view) {
        tvCaseId = view.findViewById(R.id.tvCaseId);
        tvNoteContent = view.findViewById(R.id.tvNoteContent);
        tvNoteAuthor = view.findViewById(R.id.tvNoteAuthor);
        tvReportCategory = view.findViewById(R.id.tvReportCategory);
        tvReporterName = view.findViewById(R.id.tvReporterName);
        tvReportDescription = view.findViewById(R.id.tvReportDescription);
        imgNote = view.findViewById(R.id.imgNote);
        btnMenu = view.findViewById(R.id.btnMenu);

        btnMenu.setOnClickListener(v -> showAdminActionsBottomSheet());
        view.findViewById(R.id.btnBack).setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());
    }

    private void fetchReportDetails() {
        if (reportId == null) return;

        db.collection("reports").document(reportId).get()
            .addOnSuccessListener(documentSnapshot -> {
                currentReport = documentSnapshot.toObject(Report.class);
                if (currentReport != null) {
                    currentReport.setId(documentSnapshot.getId());
                    populateReportUI();
                    fetchNoteDetails(currentReport.getTargetId());
                    fetchReporterDetails(currentReport.getReporterId());
                }
            })
            .addOnFailureListener(e -> Toast.makeText(getContext(), "Error loading report", Toast.LENGTH_SHORT).show());
    }

    private void populateReportUI() {
        tvCaseId.setText("CASE #" + currentReport.getId().substring(0, 8).toUpperCase() + "-NOTE");
        tvReportCategory.setText(currentReport.getCategory());
        tvReportDescription.setText(currentReport.getDescription());
    }

    private void fetchNoteDetails(String nId) {
        this.noteId = nId;
        db.collection("notes").document(noteId).get()
            .addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    String content = doc.getString("text");
                    if (content == null) content = doc.getString("note");
                    String imageBase64 = doc.getString("imageBase64");
                    noteOwnerId = doc.getString("userId");

                    tvNoteContent.setText(content != null ? content : "(No text content)");
                    
                    if (imageBase64 != null && !imageBase64.isEmpty()) {
                        try {
                            byte[] decoded = android.util.Base64.decode(imageBase64, android.util.Base64.DEFAULT);
                            imgNote.setVisibility(View.VISIBLE);
                            Glide.with(this).load(decoded).into(imgNote);
                        } catch (Exception e) {
                            imgNote.setVisibility(View.GONE);
                        }
                    }

                    fetchAuthorDetails(noteOwnerId);
                } else {
                    tvNoteContent.setText("[NOTE DELETED OR NOT FOUND]");
                }
            });
    }

    private void fetchAuthorDetails(String userId) {
        if (userId == null) return;
        db.collection("users").document(userId).get()
            .addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    String name = doc.getString("name");
                    tvNoteAuthor.setText("Posted by: " + name);
                }
            });
    }

    private void fetchReporterDetails(String userId) {
        if (userId == null) return;
        db.collection("users").document(userId).get()
            .addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    String name = doc.getString("name");
                    tvReporterName.setText(name);
                }
            });
    }

    private void showAdminActionsBottomSheet() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(requireContext());
        View bottomSheetView = LayoutInflater.from(getContext()).inflate(R.layout.bottom_sheet_admin_actions, null);
        
        // WARN USER
        bottomSheetView.findViewById(R.id.action_warn).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            warnUser();
        });

        // DELETE NOTE
        bottomSheetView.findViewById(R.id.action_delete_note).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            deleteNote();
        });

        // RESTRICT USER
        bottomSheetView.findViewById(R.id.action_restrict).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            showDatePickerForAction("restrict");
        });

        // BAN USER
        bottomSheetView.findViewById(R.id.action_ban).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            showDatePickerForAction("ban");
        });

        // DISMISS REPORT
        bottomSheetView.findViewById(R.id.action_dismiss).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            dismissReport();
        });

        bottomSheetDialog.setContentView(bottomSheetView);
        bottomSheetDialog.show();
    }
    
    private void warnUser() {
        if (noteOwnerId == null) {
            Toast.makeText(getContext(), "Cannot identify note owner", Toast.LENGTH_SHORT).show();
            return;
        }
        
        repository.updateUserStatus(noteOwnerId, "warned", true, new ReportRepository.OnActionListener() {
            @Override
            public void onSuccess() {
                repository.notifyUser(noteOwnerId, "You have been warned for violating community guidelines. Further violations may result in restrictions or a ban.", "admin");
                notifyReporter("Your report has been reviewed. The user has been warned.");
                Toast.makeText(getContext(), "Warning sent to user", Toast.LENGTH_SHORT).show();
                
                // Auto-dismiss report after action
                repository.dismissReport(reportId, new ReportRepository.OnActionListener() {
                    @Override public void onSuccess() { requireActivity().getSupportFragmentManager().popBackStack(); }
                    @Override public void onFailure(String error) {}
                });
            }
            @Override
            public void onFailure(String error) {
                Toast.makeText(getContext(), "Failed: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void deleteNote() {
        if (noteId == null || reportId == null) return;
        
        repository.deleteNote(noteId, reportId, new ReportRepository.OnActionListener() {
            @Override
            public void onSuccess() {
                notifyReporter("Your report has been reviewed. The reported content has been removed.");
                Toast.makeText(getContext(), "Note deleted successfully", Toast.LENGTH_SHORT).show();
                requireActivity().getSupportFragmentManager().popBackStack();
            }
            @Override
            public void onFailure(String error) {
                Toast.makeText(getContext(), "Failed: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void showDatePickerForAction(String actionType) {
        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select " + (actionType.equals("ban") ? "Ban" : "Restriction") + " Expiry Date")
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds() + (7L * 24 * 60 * 60 * 1000))
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
        if (noteOwnerId == null) return;
        
        db.collection("users").document(noteOwnerId)
            .update("banned", true, "banExpiryDate", expiryDate)
            .addOnSuccessListener(aVoid -> {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                String dateStr = sdf.format(new Date(expiryDate));
                Toast.makeText(getContext(), "User banned until " + dateStr, Toast.LENGTH_SHORT).show();
                
                repository.notifyUser(noteOwnerId, "You have been banned until " + dateStr, "admin");
                notifyReporter("Your report has been reviewed. The user has been banned.");
                
                repository.dismissReport(reportId, new ReportRepository.OnActionListener() {
                    @Override public void onSuccess() { requireActivity().getSupportFragmentManager().popBackStack(); }
                    @Override public void onFailure(String error) {}
                });
            })
            .addOnFailureListener(e -> Toast.makeText(getContext(), "Failed to ban user", Toast.LENGTH_SHORT).show());
    }
    
    private void restrictUser(long expiryDate) {
        if (noteOwnerId == null) return;
        
        db.collection("users").document(noteOwnerId)
            .update("restricted", true, "restrictionExpiryDate", expiryDate)
            .addOnSuccessListener(aVoid -> {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                String dateStr = sdf.format(new Date(expiryDate));
                Toast.makeText(getContext(), "User restricted until " + dateStr, Toast.LENGTH_SHORT).show();
                
                repository.notifyUser(noteOwnerId, "You have been restricted from posting until " + dateStr, "admin");
                notifyReporter("Your report has been reviewed. The user has been restricted.");
                
                repository.dismissReport(reportId, new ReportRepository.OnActionListener() {
                    @Override public void onSuccess() { requireActivity().getSupportFragmentManager().popBackStack(); }
                    @Override public void onFailure(String error) {}
                });
            })
            .addOnFailureListener(e -> Toast.makeText(getContext(), "Failed to restrict user", Toast.LENGTH_SHORT).show());
    }
    
    private void dismissReport() {
        if (reportId == null) return;
        
        repository.dismissReport(reportId, new ReportRepository.OnActionListener() {
            @Override
            public void onSuccess() {
                notifyReporter("Your report has been reviewed and dismissed.");
                Toast.makeText(getContext(), "Report dismissed", Toast.LENGTH_SHORT).show();
                requireActivity().getSupportFragmentManager().popBackStack();
            }
            @Override
            public void onFailure(String error) {
                Toast.makeText(getContext(), "Error: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void notifyReporter(String message) {
        if (currentReport != null && currentReport.getReporterId() != null) {
            repository.notifyUser(currentReport.getReporterId(), message, "admin");
        }
    }
}

