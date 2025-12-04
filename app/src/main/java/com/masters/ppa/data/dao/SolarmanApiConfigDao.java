package com.masters.ppa.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.masters.ppa.data.model.SolarmanApiConfig;

/**
 * Data Access Object for SolarmanApiConfig entity
 */
@Dao
public interface SolarmanApiConfigDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(SolarmanApiConfig solarmanApiConfig);
    
    @Update
    void update(SolarmanApiConfig solarmanApiConfig);
    
    @Delete
    void delete(SolarmanApiConfig solarmanApiConfig);
    
    @Query("DELETE FROM solarman_api_config")
    void deleteAll();
    
    @Query("SELECT * FROM solarman_api_config WHERE id = 1")
    LiveData<SolarmanApiConfig> getSolarmanApiConfig();
    
    @Query("SELECT * FROM solarman_api_config WHERE id = 1")
    SolarmanApiConfig getSolarmanApiConfigSync();
    
    @Query("SELECT COUNT(*) FROM solarman_api_config")
    int getCount();
}
