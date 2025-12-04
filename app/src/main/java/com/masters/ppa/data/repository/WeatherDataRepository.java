package com.masters.ppa.data.repository;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.masters.ppa.data.model.WeatherData;
import com.masters.ppa.utils.CsvUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Repository for weather data
 */
public class WeatherDataRepository {
    
    private static final String TAG = "WeatherDataRepository";
    
    private final Context context;
    private final MutableLiveData<List<WeatherData>> weatherDataList = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Date> lastUpdatedDate = new MutableLiveData<>();
    
    public WeatherDataRepository(Context context) {
        this.context = context;
    }
    
    /**
     * Get all weather data
     * @return LiveData<List<WeatherData>>
     */
    public LiveData<List<WeatherData>> getAllWeatherData() {
        return weatherDataList;
    }
    
    /**
     * Get next 7 days weather data
     * @return LiveData<List<WeatherData>>
     */
    public LiveData<List<WeatherData>> getNext7DaysWeatherData() {
        return weatherDataList; // Currently the same as getAllWeatherData
    }
    
    /**
     * Get last updated date
     * @return LiveData<Date>
     */
    public LiveData<Date> getLastUpdatedDate() {
        return lastUpdatedDate;
    }
    
    /**
     * Load weather data from CSV file
     * @param filePath Path to CSV file
     * @return true if successful
     */
    public boolean loadFromCsv(String filePath) {
        try {
            Log.d(TAG, "Loading weather data from: " + filePath);
            
            // Parse CSV file
            List<WeatherData> data = CsvUtils.readWeatherData(filePath);
            
            // Update LiveData
            if (!data.isEmpty()) {
                weatherDataList.postValue(data);
                lastUpdatedDate.postValue(new Date());
                Log.d(TAG, "Loaded " + data.size() + " weather data entries");
                return true;
            } else {
                Log.w(TAG, "No weather data found in CSV file");
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading weather data from CSV", e);
            return false;
        }
    }
    
}