package com.visiboard.app.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Delete;

import java.util.List;

@Dao
public interface CachedFeedNoteDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(CachedFeedNote note);
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<CachedFeedNote> notes);
    
    @Delete
    void delete(CachedFeedNote note);
    
    @Query("SELECT * FROM cached_feed_notes WHERE noteId = :noteId LIMIT 1")
    CachedFeedNote getNoteById(String noteId);
    
    @Query("SELECT * FROM cached_feed_notes ORDER BY displayOrder ASC LIMIT :limit")
    List<CachedFeedNote> getFeedNotes(int limit);
    
    @Query("SELECT * FROM cached_feed_notes ORDER BY displayOrder ASC")
    LiveData<List<CachedFeedNote>> getFeedNotesLive();
    
    @Query("DELETE FROM cached_feed_notes WHERE cachedTimestamp < :expiryTime")
    void deleteExpired(long expiryTime);
    
    @Query("DELETE FROM cached_feed_notes")
    void deleteAll();
    
    @Query("SELECT COUNT(*) FROM cached_feed_notes")
    int getCount();
    
    @Query("DELETE FROM cached_feed_notes WHERE noteId = :noteId")
    void deleteById(String noteId);
}
