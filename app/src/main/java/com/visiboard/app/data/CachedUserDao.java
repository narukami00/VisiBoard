package com.visiboard.app.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import androidx.room.Delete;

import java.util.List;

@Dao
public interface CachedUserDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(CachedUser user);
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<CachedUser> users);
    
    @Update
    void update(CachedUser user);
    
    @Delete
    void delete(CachedUser user);
    
    @Query("SELECT * FROM cached_users WHERE userId = :userId LIMIT 1")
    CachedUser getUserById(String userId);
    
    @Query("SELECT * FROM cached_users WHERE userId = :userId LIMIT 1")
    LiveData<CachedUser> getUserByIdLive(String userId);
    
    @Query("SELECT * FROM cached_users ORDER BY lastAccessTimestamp DESC LIMIT :limit")
    List<CachedUser> getRecentUsers(int limit);
    
    @Query("DELETE FROM cached_users WHERE cachedTimestamp < :expiryTime")
    void deleteExpired(long expiryTime);
    
    @Query("DELETE FROM cached_users")
    void deleteAll();
    
    @Query("SELECT COUNT(*) FROM cached_users")
    int getCount();
    
    @Query("UPDATE cached_users SET lastAccessTimestamp = :timestamp WHERE userId = :userId")
    void updateAccessTime(String userId, long timestamp);
}
