package com.masters.ppa.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.masters.ppa.data.model.ConfigBms;

import java.util.List;

/**
 * Data Access Object for ConfigBms entity
 */
@Dao
public interface ConfigBmsDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(ConfigBms configBms);

    @Delete
    void delete(ConfigBms configBms);

    @Query("DELETE FROM config_bms WHERE configId = :configId")
    void deleteByConfigId(int configId);

    @Query("SELECT * FROM config_bms WHERE configId = :configId")
    ConfigBms getByConfigId(int configId);

    @Query("SELECT * FROM config_bms WHERE configId = :configId")
    List<ConfigBms> getAllByConfigId(int configId);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertAll(List<ConfigBms> configBmsList);
}

