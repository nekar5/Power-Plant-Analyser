package com.masters.ppa.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.masters.ppa.data.model.InverterItem;

import java.util.List;

/**
 * Data Access Object for InverterItem entity
 */
@Dao
public interface InverterItemDao {
    
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(InverterItem item);
    
    @Delete
    void delete(InverterItem item);
    
    @Query("DELETE FROM inverter_items")
    void deleteAll();
    
    @Query("SELECT * FROM inverter_items ORDER BY name")
    LiveData<List<InverterItem>> getAll();
    
    @Query("SELECT * FROM inverter_items ORDER BY name")
    List<InverterItem> getAllSync();
    
    @Query("SELECT * FROM inverter_items WHERE id = :id")
    InverterItem getById(int id);
    
    @Query("SELECT * FROM inverter_items WHERE name = :name")
    InverterItem getByName(String name);
    
    @Query("SELECT COUNT(*) FROM inverter_items WHERE name = :name")
    int countByName(String name);
}


