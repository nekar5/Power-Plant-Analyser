package com.masters.ppa.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.masters.ppa.data.model.BatteryConfig;

/**
 * Data Access Object for BatteryConfig entity
 */
@Dao
public interface BatteryConfigDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(BatteryConfig batteryConfig);
    
    @Update
    void update(BatteryConfig batteryConfig);
    
    @Delete
    void delete(BatteryConfig batteryConfig);
    
    @Query("DELETE FROM battery_config")
    void deleteAll();
    
    @Query("SELECT * FROM battery_config WHERE id = 1")
    LiveData<BatteryConfig> getBatteryConfig();
    
    @Query("SELECT * FROM battery_config WHERE id = 1")
    BatteryConfig getBatteryConfigSync();
    
    @Query("SELECT COUNT(*) FROM battery_config")
    int getCount();
}
