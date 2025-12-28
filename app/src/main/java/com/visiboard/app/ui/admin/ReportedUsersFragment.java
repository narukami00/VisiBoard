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

public class ReportedUsersFragment extends Fragment implements ReportAdapter.OnReportClickListener {

    private RecyclerView recyclerView;
    private ReportAdapter adapter;
    private ReportRepository repository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_reported_users, container, false);
        recyclerView = view.findViewById(R.id.usersReportRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        
        repository = new ReportRepository();
        
        // Initialize adapter once
        if (adapter == null) {
            adapter = new ReportAdapter(report -> {
                // Open AdminProfileFragment
                AdminProfileFragment detailFragment = AdminProfileFragment.newInstance(report.getTargetId(), report.getId());
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

        com.google.firebase.firestore.Query query = repository.getReportsByType("USER");
        
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
    public void onDestroyView() {
        super.onDestroyView();
        if (reportListener != null) {
            reportListener.remove();
        }
    }

    @Override
    public void onReportClick(Report report) {
        // Handled in adapter listener lambda now
    }

    private void showActionDialog(Report report) {
        String[] actions = {"View Profile", "View All Notes", "Warn User", "Restrict User", "Ban User", "Dismiss Report"};
        
        new AlertDialog.Builder(getContext())
                .setTitle("User Report Actions")
                .setItems(actions, (dialog, which) -> {
                     ReportRepository.OnActionListener listener = new ReportRepository.OnActionListener() {
                        @Override
                        public void onSuccess() {
                            Toast.makeText(getContext(), "Action Successful", Toast.LENGTH_SHORT).show();
                            repository.notifyUser(report.getReporterId(), "Your report has been reviewed.", "admin");
                        }
                        @Override
                        public void onFailure(String error) {
                            Toast.makeText(getContext(), "Error: " + error, Toast.LENGTH_SHORT).show();
                        }
                    };
                    String targetUserId = report.getTargetId(); // For USER reports, target IS user

                    switch (which) {
                        case 0: // View Profile
                             // Simplified
                             new AlertDialog.Builder(getContext())
                                .setTitle("User Profile")
                                .setMessage("User ID: " + targetUserId + "\nDetails: " + report.getTargetDetails())
                                .setPositiveButton("OK", null)
                                .show();
                            break;
                        case 1: // View All Notes
                            Toast.makeText(getContext(), "Feature Coming Soon", Toast.LENGTH_SHORT).show();
                            break;
                        case 2: // Warn
                            repository.updateUserStatus(targetUserId, "warned", true, listener);
                            repository.notifyUser(targetUserId, "You have been warned.", "admin");
                            break;
                        case 3: // Restrict
                            repository.updateUserStatus(targetUserId, "restricted", true, listener);
                             long expiry = System.currentTimeMillis() + (7L * 24 * 60 * 60 * 1000); 
                             repository.updateUserStatus(targetUserId, "restrictionExpiryDate", expiry, new ReportRepository.OnActionListener(){
                                 @Override public void onSuccess(){} @Override public void onFailure(String e){}
                             });
                            repository.notifyUser(targetUserId, "You have been restricted for 7 days.", "admin");
                            break;
                        case 4: // Ban
                            repository.updateUserStatus(targetUserId, "banned", true, listener);
                            repository.notifyUser(targetUserId, "You have been banned.", "admin");
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
