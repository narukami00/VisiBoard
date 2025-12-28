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

    public com.google.firebase.firestore.Query getReportsByType(String type) {
        return db.collection("reports")
                .whereEqualTo("type", type)
                .whereEqualTo("type", type);
    }

    public void dismissReport(String reportId, OnActionListener listener) {
        db.collection("reports").document(reportId).delete()
                .addOnSuccessListener(aVoid -> listener.onSuccess())
                .addOnFailureListener(e -> listener.onFailure(e.getMessage()));
    }

    public void deleteNote(String noteId, String reportId, OnActionListener listener) {
        // First get note to find owner for notification
        db.collection("notes").document(noteId).get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                String ownerId = snapshot.getString("userId"); // Assuming field is userId
                // Now delete
                db.collection("notes").document(noteId).delete()
                        .addOnSuccessListener(aVoid -> {
                            // Notify owner if found
                            if (ownerId != null) {
                                notifyUser(ownerId, "Your note was removed by Admin.", "admin");
                            }
                            dismissReport(reportId, listener);
                        })
                        .addOnFailureListener(e -> listener.onFailure(e.getMessage()));
            } else {
                // Note already gone? Just dismiss report
                dismissReport(reportId, listener);
            }
        }).addOnFailureListener(e -> listener.onFailure(e.getMessage()));
    }

    public void updateUserStatus(String userId, String field, Object value, OnActionListener listener) {
        db.collection("users").document(userId).update(field, value)
                .addOnSuccessListener(aVoid -> listener.onSuccess())
                .addOnFailureListener(e -> listener.onFailure(e.getMessage()));
    }

    public void sendNotification(Notification notification) {
        db.collection("notifications").add(notification);
    }
    
    public void notifyUser(String userId, String message, String type) {
        if (userId == null) return;
        Notification n = new Notification();
        n.setToUserId(userId);
        n.setType(type);
        n.setMessageText(message);
        n.setTimestamp(System.currentTimeMillis());
        n.setRead(false);
        n.setFromUserName("VisiBoard Admin");
        // n.setFromUserId("ADMIN"); // Optional
        sendNotification(n);
    }

    public void accessNoteOwner(String noteId, OnNoteOwnerListener listener) {
         db.collection("notes").document(noteId).get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                listener.onOwnerFound(snapshot.getString("userId"));
            } else {
                listener.onError("Note not found");
            }
         }).addOnFailureListener(e -> listener.onError(e.getMessage()));
    }

    public interface OnActionListener {
        void onSuccess();
        void onFailure(String error);
    }
    
    public interface OnNoteOwnerListener {
        void onOwnerFound(String ownerId);
        void onError(String error);
    }
}
