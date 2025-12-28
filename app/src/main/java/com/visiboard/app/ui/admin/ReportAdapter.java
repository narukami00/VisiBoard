package com.visiboard.app.ui.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.visiboard.app.R;
import com.visiboard.app.data.Report;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ReportAdapter extends RecyclerView.Adapter<ReportAdapter.ReportViewHolder> {

    private java.util.List<Report> reports = new java.util.ArrayList<>();
    private final OnReportClickListener listener;

    public interface OnReportClickListener {
        void onReportClick(Report report);
    }

    public ReportAdapter(OnReportClickListener listener) {
        this.listener = listener;
    }

    public void setReports(java.util.List<Report> reports) {
        this.reports = reports;
        notifyDataSetChanged();
    }

    @Override
    public void onBindViewHolder(@NonNull ReportViewHolder holder, int position) {
        Report model = reports.get(position);
        
        holder.reportType.setText(model.getType() != null ? model.getType().toUpperCase() + " REPORT" : "REPORT");
        holder.reportTitle.setText(model.getCategory());
        holder.reportReason.setText(model.getDescription());
        
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        holder.reportDate.setText(sdf.format(new Date(model.getTimestamp())));
        
        // Fetch reporter name from Firestore
        String reporterId = model.getReporterId();
        if (reporterId != null && !reporterId.isEmpty()) {
            holder.reportedBy.setText("Loading...");
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").document(reporterId).get()
                .addOnSuccessListener(doc -> {
                    String name = doc.getString("name");
                    holder.reportedBy.setText("Reported by " + (name != null ? name : "Unknown"));
                })
                .addOnFailureListener(e -> holder.reportedBy.setText("Reported by Unknown"));
        } else {
            holder.reportedBy.setText("Reported by Unknown");
        }

        holder.itemView.setOnClickListener(v -> listener.onReportClick(model));
    }

    @Override
    public int getItemCount() {
        return reports.size();
    }

    @NonNull
    @Override
    public ReportViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_report_card, parent, false);
        return new ReportViewHolder(view);
    }

    static class ReportViewHolder extends RecyclerView.ViewHolder {
        TextView reportType, reportDate, reportTitle, reportReason, reportedBy;

        public ReportViewHolder(@NonNull View itemView) {
            super(itemView);
            reportType = itemView.findViewById(R.id.tvReportType);
            reportDate = itemView.findViewById(R.id.tvReportDate);
            reportTitle = itemView.findViewById(R.id.tvReportTitle);
            reportReason = itemView.findViewById(R.id.tvReportReason);
            reportedBy = itemView.findViewById(R.id.tvReportedBy);
        }
    }
}
