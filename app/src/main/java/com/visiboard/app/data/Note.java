package com.visiboard.app.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "notes")
public class Note {
    @PrimaryKey @NonNull
    public String id;
    public String text;
    public String summary;
    public double lat;
    public double lng;
    public int status; // 0=pending,1=syncing,2=synced
    public long timestamp;
}
