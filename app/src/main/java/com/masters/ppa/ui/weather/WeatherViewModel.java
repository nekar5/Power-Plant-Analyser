package com.masters.ppa.ui.weather;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.masters.ppa.data.api.WeatherApiService;
import com.masters.ppa.data.model.StationConfig;
import com.masters.ppa.data.model.WeatherData;
import com.masters.ppa.data.repository.StationConfigRepository;
import com.masters.ppa.data.repository.WeatherDataRepository;
import com.masters.ppa.utils.DateUtils;

import java.io.File;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * ViewModel for the Weather screen
 */
public class WeatherViewModel extends AndroidViewModel {

    private static final String TAG = "WeatherViewModel";
    
    private final WeatherDataRepository weatherDataRepository;
    private final StationConfigRepository stationConfigRepository;
    private final WeatherApiService weatherApiService;
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> operationSuccess = new MutableLiveData<>();
    private final MutableLiveData<String> operationMessage = new MutableLiveData<>();
    private final MutableLiveData<String> forecastRange = new MutableLiveData<>();
    
    private final Executor executor = Executors.newSingleThreadExecutor();

    public WeatherViewModel(@NonNull Application application) {
        super(application);
        weatherDataRepository = new WeatherDataRepository(application);
        stationConfigRepository = new StationConfigRepository(application);
        weatherApiService = new WeatherApiService(application);
        updateForecastRange();
    }
    
    /**
     * Get all weather data
     * @return LiveData<List<WeatherData>>
     */
    public LiveData<List<WeatherData>> getAllWeatherData() {
        return weatherDataRepository.getAllWeatherData();
    }
    
    /**
     * Get next 7 days weather data
     * @return LiveData<List<WeatherData>>
     */
    public LiveData<List<WeatherData>> getNext7DaysWeatherData() {
        return weatherDataRepository.getNext7DaysWeatherData();
    }
    
    /**
     * Get last updated date
     * @return LiveData<Date>
     */
    public LiveData<Date> getLastUpdatedDate() {
        return weatherDataRepository.getLastUpdatedDate();
    }
    
    /**
     * Get loading status
     * @return LiveData<Boolean>
     */
    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }
    
    /**
     * Get operation success status
     * @return LiveData<Boolean>
     */
    public LiveData<Boolean> getOperationSuccess() {
        return operationSuccess;
    }
    
    /**
     * Get operation message
     * @return LiveData<String>
     */
    public LiveData<String> getOperationMessage() {
        return operationMessage;
    }
    
    /**
     * Get forecast date range
     * @return LiveData<String>
     */
    public LiveData<String> getForecastRange() {
        return forecastRange;
    }
    
    /**
     * Update forecast date range
     */
    private void updateForecastRange() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate endDate = today.plusDays(6);
        
        forecastRange.postValue("Forecast: " + DateUtils.formatDateRange(today, endDate));
    }
    
    /**
     * Load weather data from CSV file
     */
    public void loadWeatherData() {
        isLoading.setValue(true);
        executor.execute(() -> {
            try {
                File weatherFile = weatherApiService.getWeatherFile(WeatherApiService.CSV_WEATHER_TODAY7);
                if (!weatherFile.exists()) {
                    operationSuccess.postValue(false);
                    operationMessage.postValue("No weather data found. Please fetch weather data first.");
                    isLoading.postValue(false);
                    return;
                }
                
                boolean success = weatherDataRepository.loadFromCsv(weatherFile.getAbsolutePath());
                if (success) {
                    operationSuccess.postValue(true);
                    operationMessage.postValue("Weather data loaded successfully");
                    updateForecastRange();
                } else {
                    operationSuccess.postValue(false);
                    operationMessage.postValue("No weather data found");
                }
            } catch (Exception e) {
                operationSuccess.postValue(false);
                operationMessage.postValue("Error loading weather data: " + e.getMessage());
                Log.e(TAG, "Error loading weather data", e);
            } finally {
                isLoading.postValue(false);
            }
        });
    }
    
    /**
     * Get station coordinates or return null if not configured
     */
    private StationConfig getStationConfigOrNull() {
        try {
            return stationConfigRepository.getStationConfigSync();
        } catch (Exception e) {
            Log.e(TAG, "Error getting station config", e);
            return null;
        }
    }
    
    /**
     * Load weather data from file and update UI
     */
    private void loadWeatherDataFromFile(String filePath) {
        executor.execute(() -> {
            try {
                boolean success = weatherDataRepository.loadFromCsv(filePath);
                if (success) {
                    operationSuccess.postValue(true);
                    operationMessage.postValue("Weather data updated successfully (7-day forecast)");
                    updateForecastRange();
                } else {
                    operationSuccess.postValue(false);
                    operationMessage.postValue("Error loading fetched weather data");
                }
            } catch (Exception e) {
                operationSuccess.postValue(false);
                operationMessage.postValue("Error processing weather data: " + e.getMessage());
                Log.e(TAG, "Error processing weather data", e);
            } finally {
                isLoading.postValue(false);
            }
        });
    }
    
    /**
     * Fetch weather data from API
     */
    public void fetchWeatherData() {
        isLoading.setValue(true);
        executor.execute(() -> {
            StationConfig config = getStationConfigOrNull();
            if (config == null) {
                operationSuccess.postValue(false);
                operationMessage.postValue("Error: Station coordinates not set. Please fill settings first.");
                isLoading.postValue(false);
                return;
            }
            
            Log.d(TAG, "Fetching weather data for coordinates: " + config.getLatitude() + ", " + config.getLongitude());
            
            weatherApiService.fetch7DaysWeather(config.getLatitude(), config.getLongitude(), 
                new WeatherApiService.FetchCallback() {
                    @Override
                    public void onSuccess(String filePath, int rowCount, String firstTimestamp, String lastTimestamp) {
                        loadWeatherDataFromFile(filePath);
                    }

                    @Override
                    public void onError(String errorMessage) {
                        operationSuccess.postValue(false);
                        operationMessage.postValue("Weather data fetch failed: " + errorMessage);
                        Log.e(TAG, "Weather data fetch failed: " + errorMessage);
                        isLoading.postValue(false);
                    }
                });
        });
    }
    
    /**
     * Fetch historical weather data (last 3 months)
     */
    public void fetchHistoricalWeatherData() {
        isLoading.setValue(true);
        operationMessage.postValue("Fetching historical weather data (last 3 months)...");
        
        executor.execute(() -> {
            StationConfig config = getStationConfigOrNull();
            if (config == null) {
                operationSuccess.postValue(false);
                operationMessage.postValue("Error: Station coordinates not set. Please fill settings first.");
                isLoading.postValue(false);
                return;
            }
            
            Log.d(TAG, "Fetching historical weather data for coordinates: " + config.getLatitude() + ", " + config.getLongitude());
            
            weatherApiService.fetch3MonthsWeather(config.getLatitude(), config.getLongitude(), 
                new WeatherApiService.FetchCallback() {
                    @Override
                    public void onSuccess(String filePath, int rowCount, String firstTimestamp, String lastTimestamp) {
                        operationSuccess.postValue(true);
                        operationMessage.postValue("Historical weather data saved successfully (last 3 months)");
                        isLoading.postValue(false);
                    }

                    @Override
                    public void onError(String errorMessage) {
                        operationSuccess.postValue(false);
                        operationMessage.postValue("Historical weather data fetch failed: " + errorMessage);
                        Log.e(TAG, "Historical weather data fetch failed: " + errorMessage);
                        isLoading.postValue(false);
                    }
                });
        });
    }
}