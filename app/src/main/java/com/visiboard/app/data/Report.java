package com.visiboard.app.data;

import java.util.UUID;

public class Report {
    private String id;
    private String reporterId;
    private String targetId;
    private String targetDetails; // e.g., Note content or User name
    private String type; // "NOTE" or "USER"
    private String category; // "SPAM", "HATE_SPEECH", "VIOLENCE", "NUDITY", "OTHER"
    private String description;
    private double lat;
    private double lng;
    private long timestamp;
    private String status; // "PENDING", "REVIEWED", "RESOLVED"

    public Report() {
        // Required for Firestore
    }

    public Report(String reporterId, String targetId, String targetDetails, String type, 
                  String category, String description, double lat, double lng) {
        this.id = UUID.randomUUID().toString();
        this.reporterId = reporterId;
        this.targetId = targetId;
        this.targetDetails = targetDetails;
        this.type = type;
        this.category = category;
        this.description = description;
        this.lat = lat;
        this.lng = lng;
        this.timestamp = System.currentTimeMillis();
        this.status = "PENDING";
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getReporterId() { return reporterId; }
    public void setReporterId(String reporterId) { this.reporterId = reporterId; }

    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }

    public String getTargetDetails() { return targetDetails; }
    public void setTargetDetails(String targetDetails) { this.targetDetails = targetDetails; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }

    public double getLng() { return lng; }
    public void setLng(double lng) { this.lng = lng; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
