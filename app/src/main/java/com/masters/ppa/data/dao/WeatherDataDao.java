package com.masters.ppa.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.masters.ppa.data.model.WeatherData;

import java.util.Date;
import java.util.List;

/**
 * Data Access Object for WeatherData entity
 */
@Dao
public interface WeatherDataDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(WeatherData weatherData);
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<WeatherData> weatherDataList);
    
    @Update
    void update(WeatherData weatherData);
    
    @Delete
    void delete(WeatherData weatherData);
    
    @Query("DELETE FROM weather_data")
    void deleteAll();
    
    @Query("SELECT * FROM weather_data ORDER BY date ASC")
    LiveData<List<WeatherData>> getAllWeatherData();
    
    @Query("SELECT * FROM weather_data WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    LiveData<List<WeatherData>> getWeatherDataBetweenDates(Date startDate, Date endDate);
    
    @Query("SELECT * FROM weather_data WHERE date >= :startDate ORDER BY date ASC LIMIT 7")
    LiveData<List<WeatherData>> getNext7DaysWeatherData(Date startDate);
    
    @Query("SELECT MAX(lastUpdated) FROM weather_data")
    LiveData<Date> getLastUpdatedDate();
}
