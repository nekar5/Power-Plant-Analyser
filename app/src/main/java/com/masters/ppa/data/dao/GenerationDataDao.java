package com.masters.ppa.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.masters.ppa.data.model.GenerationData;

import java.util.Date;
import java.util.List;

/**
 * Data Access Object for GenerationData entity
 */
@Dao
public interface GenerationDataDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(GenerationData generationData);
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<GenerationData> generationDataList);
    
    @Update
    void update(GenerationData generationData);
    
    @Delete
    void delete(GenerationData generationData);
    
    @Query("DELETE FROM generation_data")
    void deleteAll();
    
    @Query("SELECT * FROM generation_data ORDER BY date ASC")
    LiveData<List<GenerationData>> getAllGenerationData();
    
    @Query("SELECT * FROM generation_data WHERE isActual = 1 AND date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    LiveData<List<GenerationData>> getActualGenerationBetweenDates(Date startDate, Date endDate);
    
    @Query("SELECT * FROM generation_data WHERE isActual = 0 AND date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    LiveData<List<GenerationData>> getPredictedGenerationBetweenDates(Date startDate, Date endDate);
    
    @Query("SELECT * FROM generation_data WHERE isActual = 1 ORDER BY date DESC LIMIT 14")
    LiveData<List<GenerationData>> getLast14DaysActualGeneration();
    
    @Query("SELECT * FROM generation_data WHERE isActual = 0 ORDER BY date ASC LIMIT 7")
    LiveData<List<GenerationData>> getNext7DaysPredictedGeneration();
    
    @Query("SELECT MAX(lastUpdated) FROM generation_data")
    LiveData<Date> getLastUpdatedDate();
}
