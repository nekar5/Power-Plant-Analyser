package com.masters.ppa.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.masters.ppa.data.model.BatteryItem;

import java.util.List;

/**
 * Data Access Object for BatteryItem entity
 */
@Dao
public interface BatteryItemDao {
    
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(BatteryItem item);
    
    @Delete
    void delete(BatteryItem item);
    
    @Query("DELETE FROM battery_items")
    void deleteAll();
    
    @Query("SELECT * FROM battery_items ORDER BY name")
    LiveData<List<BatteryItem>> getAll();
    
    @Query("SELECT * FROM battery_items ORDER BY name")
    List<BatteryItem> getAllSync();
    
    @Query("SELECT * FROM battery_items WHERE id = :id")
    BatteryItem getById(int id);
    
    @Query("SELECT * FROM battery_items WHERE name = :name")
    BatteryItem getByName(String name);
    
    @Query("SELECT COUNT(*) FROM battery_items WHERE name = :name")
    int countByName(String name);
}

