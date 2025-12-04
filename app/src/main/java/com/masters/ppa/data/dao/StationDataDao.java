package com.masters.ppa.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.masters.ppa.data.model.StationData;

import java.util.Date;
import java.util.List;

/**
 * Data Access Object for StationData entity
 */
@Dao
public interface StationDataDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(StationData stationData);
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<StationData> stationDataList);
    
    @Update
    void update(StationData stationData);
    
    @Delete
    void delete(StationData stationData);
    
    @Query("DELETE FROM station_data")
    void deleteAll();
    
    @Query("SELECT * FROM station_data ORDER BY timestamp DESC")
    LiveData<List<StationData>> getAllStationData();
    
    @Query("SELECT * FROM station_data ORDER BY timestamp DESC LIMIT 1")
    LiveData<StationData> getLatestStationData();
    
    @Query("SELECT * FROM station_data WHERE timestamp BETWEEN :startDate AND :endDate ORDER BY timestamp ASC")
    LiveData<List<StationData>> getStationDataBetweenDates(Date startDate, Date endDate);
    
    @Query("SELECT MAX(lastUpdated) FROM station_data")
    LiveData<Date> getLastUpdatedDate();
}
