package com.masters.ppa.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.masters.ppa.data.model.ConfigTower;

import java.util.List;

/**
 * Data Access Object for ConfigTower entity
 */
@Dao
public interface ConfigTowerDao {
    
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(ConfigTower configTower);
    
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertAll(List<ConfigTower> configTowers);
    
    @Delete
    void delete(ConfigTower configTower);
    
    @Query("DELETE FROM config_towers WHERE configId = :configId")
    void deleteByConfigId(int configId);
    
    @Query("SELECT * FROM config_towers WHERE configId = :configId ORDER BY towerNumber")
    List<ConfigTower> getByConfigId(int configId);
}

