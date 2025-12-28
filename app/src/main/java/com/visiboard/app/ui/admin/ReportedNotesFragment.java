package com.visiboard.app.ui.admin;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.visiboard.app.R;
import com.visiboard.app.data.Report;
import com.visiboard.app.data.ReportRepository;

public class ReportedNotesFragment extends Fragment implements ReportAdapter.OnReportClickListener {

    private RecyclerView recyclerView;
    private ReportAdapter adapter;
    private ReportRepository repository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_reported_notes, container, false);
        recyclerView = view.findViewById(R.id.notesReportRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        
        repository = new ReportRepository();
        
        // Initialize adapter once
        if (adapter == null) {
            adapter = new ReportAdapter(report -> {
                AdminNoteReportFragment detailFragment = AdminNoteReportFragment.newInstance(report.getId());
                requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.adminFragmentContainer, detailFragment)
                    .addToBackStack(null)
                    .commit();
            });
        }
        recyclerView.setAdapter(adapter);
        
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        setupDataListener();
    }

    private com.google.firebase.firestore.ListenerRegistration reportListener;

    private void setupDataListener() {
        // Remove old listener if exists
        if (reportListener != null) {
            reportListener.remove();
        }

        com.google.firebase.firestore.Query query = repository.getReportsByType("NOTE");
        
        reportListener = query.addSnapshotListener((snapshots, e) -> {
            if (e != null) {
                Toast.makeText(getContext(), "Error loading reports: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                return;
            }
            if (snapshots != null) {
                java.util.List<Report> reports = snapshots.toObjects(Report.class);
                adapter.setReports(reports);
                
                android.widget.TextView tvEmpty = getView().findViewById(R.id.tvEmptyState);
                if (tvEmpty != null) {
                    tvEmpty.setVisibility(reports.isEmpty() ? View.VISIBLE : View.GONE);
                }
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        if (reportListener != null) {
            reportListener.remove();
            reportListener = null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (reportListener != null) {
            reportListener.remove();
            reportListener = null;
        }
    }

    @Override
    public void onReportClick(Report report) {
        // Handled in adapter listener lambda now
    }

    private void showActionDialog(Report report) {
        String[] actions = {"View Note Details", "Delete Note", "Warn User", "Restrict User", "Ban User", "Dismiss Report"};
        
        new AlertDialog.Builder(getContext())
                .setTitle("Admin Actions")
                .setItems(actions, (dialog, which) -> {
                    ReportRepository.OnActionListener listener = new ReportRepository.OnActionListener() {
                        @Override
                        public void onSuccess() {
                            Toast.makeText(getContext(), "Action Successful", Toast.LENGTH_SHORT).show();
                            // Notify reporter
                            repository.notifyUser(report.getReporterId(), "Your report has been reviewed and action was taken.", "admin");
                        }

                        @Override
                        public void onFailure(String error) {
                            Toast.makeText(getContext(), "Error: " + error, Toast.LENGTH_SHORT).show();
                        }
                    };

                    switch (which) {
                        case 0: // View Note
                            // Simplified for now
                            new AlertDialog.Builder(getContext())
                                .setTitle("Note Details")
                                .setMessage(report.getTargetDetails() + "\nID: " + report.getTargetId())
                                .setPositiveButton("OK", null)
                                .show();
                            break;
                        case 1: // Delete Note
                             repository.deleteNote(report.getTargetId(), report.getId(), listener);
                            break;
                            
                        case 2: // Warn User
                            // Need user ID. For NOTE report, targetId is NoteId. Need to fetch owner.
                            repository.accessNoteOwner(report.getTargetId(), new ReportRepository.OnNoteOwnerListener() {
                                @Override
                                public void onOwnerFound(String ownerId) {
                                    repository.updateUserStatus(ownerId, "warned", true, listener);
                                    repository.notifyUser(ownerId, "You have been warned for violating community guidelines.", "admin");
                                }
                                @Override
                                public void onError(String error) { Toast.makeText(getContext(), "User not found", Toast.LENGTH_SHORT).show(); }
                            });
                            break;
                            
                        case 3: // Restrict User
                            repository.accessNoteOwner(report.getTargetId(), new ReportRepository.OnNoteOwnerListener() {
                                @Override
                                public void onOwnerFound(String ownerId) {
                                    repository.updateUserStatus(ownerId, "restricted", true, listener);
                                    long expiry = System.currentTimeMillis() + (7L * 24 * 60 * 60 * 1000); // 7 days
                                    repository.updateUserStatus(ownerId, "restrictionExpiryDate", expiry, new ReportRepository.OnActionListener() {
                                        @Override public void onSuccess() {} 
                                        @Override public void onFailure(String e) {}
                                    });
                                    repository.notifyUser(ownerId, "You have been restricted for 7 days.", "admin");
                                }
                                @Override
                                public void onError(String error) { Toast.makeText(getContext(), "User not found", Toast.LENGTH_SHORT).show(); }
                            });
                            break;
                            
                        case 4: // Ban User
                            repository.accessNoteOwner(report.getTargetId(), new ReportRepository.OnNoteOwnerListener() {
                                @Override
                                public void onOwnerFound(String ownerId) {
                                    repository.updateUserStatus(ownerId, "banned", true, listener);
                                    repository.notifyUser(ownerId, "You have been permanently banned.", "admin");
                                }
                                @Override
                                public void onError(String error) { Toast.makeText(getContext(), "User not found", Toast.LENGTH_SHORT).show(); }
                            });
                            break;
                            
                        case 5: // Dismiss
                            repository.dismissReport(report.getId(), new ReportRepository.OnActionListener() {
                                @Override
                                public void onSuccess() { Toast.makeText(getContext(), "Dismissed", Toast.LENGTH_SHORT).show(); }
                                @Override
                                public void onFailure(String e) { Toast.makeText(getContext(), "Error", Toast.LENGTH_SHORT).show(); }
                            });
                            break;
                    }
                })
                .show();
    }
}
