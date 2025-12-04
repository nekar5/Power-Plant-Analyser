package com.masters.ppa.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.masters.ppa.data.model.BmsItem;

import java.util.List;

/**
 * Data Access Object for BmsItem entity
 */
@Dao
public interface BmsItemDao {
    
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(BmsItem item);
    
    @Delete
    void delete(BmsItem item);
    
    @Query("DELETE FROM bms_items")
    void deleteAll();
    
    @Query("SELECT * FROM bms_items ORDER BY name")
    LiveData<List<BmsItem>> getAll();
    
    @Query("SELECT * FROM bms_items ORDER BY name")
    List<BmsItem> getAllSync();
    
    @Query("SELECT * FROM bms_items WHERE id = :id")
    BmsItem getById(int id);
    
    @Query("SELECT * FROM bms_items WHERE name = :name")
    BmsItem getByName(String name);
    
    @Query("SELECT COUNT(*) FROM bms_items WHERE name = :name")
    int countByName(String name);
}

