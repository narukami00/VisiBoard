package com.visiboard.app.data;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.FirebaseFirestore;

public class ReportRepository {

    private final FirebaseFirestore db;

    public ReportRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    public Task<Void> submitReport(Report report) {
        return db.collection("reports")
                .document(report.getId())
                .set(report);
    }
}
