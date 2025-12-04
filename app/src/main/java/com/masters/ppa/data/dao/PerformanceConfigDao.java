package com.masters.ppa.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.masters.ppa.data.model.PerformanceConfig;

/**
 * Data Access Object for PerformanceConfig entity
 */
@Dao
public interface PerformanceConfigDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(PerformanceConfig performanceConfig);
    
    @Update
    void update(PerformanceConfig performanceConfig);
    
    @Delete
    void delete(PerformanceConfig performanceConfig);
    
    @Query("DELETE FROM performance_config")
    void deleteAll();
    
    @Query("SELECT * FROM performance_config WHERE id = 1")
    LiveData<PerformanceConfig> getPerformanceConfig();
    
    @Query("SELECT * FROM performance_config WHERE id = 1")
    PerformanceConfig getPerformanceConfigSync();
    
    @Query("SELECT COUNT(*) FROM performance_config")
    int getCount();
}
