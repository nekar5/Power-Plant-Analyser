package com.masters.ppa.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.masters.ppa.data.model.ConfigInverter;

import java.util.List;

/**
 * Data Access Object for ConfigInverter entity
 */
@Dao
public interface ConfigInverterDao {
    
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(ConfigInverter configInverter);
    
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertAll(List<ConfigInverter> configInverters);
    
    @Delete
    void delete(ConfigInverter configInverter);
    
    @Query("DELETE FROM config_inverters WHERE configId = :configId")
    void deleteByConfigId(int configId);
    
    @Query("SELECT * FROM config_inverters WHERE configId = :configId ORDER BY role")
    List<ConfigInverter> getByConfigId(int configId);
}

