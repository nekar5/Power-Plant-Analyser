package com.masters.ppa.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.masters.ppa.data.model.StationConfig;

/**
 * Data Access Object for StationConfig entity
 */
@Dao
public interface StationConfigDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(StationConfig stationConfig);
    
    @Update
    void update(StationConfig stationConfig);
    
    @Delete
    void delete(StationConfig stationConfig);
    
    @Query("DELETE FROM station_config")
    void deleteAll();
    
    @Query("SELECT * FROM station_config WHERE id = 1")
    LiveData<StationConfig> getStationConfig();
    
    @Query("SELECT * FROM station_config WHERE id = 1")
    StationConfig getStationConfigSync();
    
    @Query("SELECT COUNT(*) FROM station_config")
    int getCount();
}
