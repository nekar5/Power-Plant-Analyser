package com.masters.ppa.ui.settings;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.gson.Gson;
import com.masters.ppa.R;
import com.masters.ppa.data.model.BatteryConfig;
import com.masters.ppa.data.model.PerformanceConfig;
import com.masters.ppa.data.model.SolarmanApiConfig;
import com.masters.ppa.data.model.StationConfig;
import com.masters.ppa.data.repository.BatteryConfigRepository;
import com.masters.ppa.data.repository.PerformanceConfigRepository;
import com.masters.ppa.data.repository.SolarmanApiConfigRepository;
import com.masters.ppa.data.repository.StationConfigRepository;
import com.masters.ppa.utils.StateUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * ViewModel for the Settings screen
 */
public class SettingsViewModel extends AndroidViewModel {

    private static final String TAG = "SettingsViewModel";
    
    private final StationConfigRepository stationConfigRepository;
    private final BatteryConfigRepository batteryConfigRepository;
    private final PerformanceConfigRepository performanceConfigRepository;
    private final SolarmanApiConfigRepository solarmanApiConfigRepository;
    
    private final LiveData<StationConfig> stationConfig;
    private final LiveData<BatteryConfig> batteryConfig;
    private final LiveData<PerformanceConfig> performanceConfig;
    private final LiveData<SolarmanApiConfig> solarmanApiConfig;
    
    private final MutableLiveData<Boolean> operationSuccess = new MutableLiveData<>();
    private final MutableLiveData<String> operationMessage = new MutableLiveData<>();
    
    private final Executor executor = Executors.newSingleThreadExecutor();

    public SettingsViewModel(@NonNull Application application) {
        super(application);
        
        stationConfigRepository = new StationConfigRepository(application);
        batteryConfigRepository = new BatteryConfigRepository(application);
        performanceConfigRepository = new PerformanceConfigRepository(application);
        solarmanApiConfigRepository = new SolarmanApiConfigRepository(application);
        
        stationConfig = stationConfigRepository.getStationConfig();
        batteryConfig = batteryConfigRepository.getBatteryConfig();
        performanceConfig = performanceConfigRepository.getPerformanceConfig();
        solarmanApiConfig = solarmanApiConfigRepository.getSolarmanApiConfig();
    }
    
    /**
     * Get station configuration
     * @return LiveData<StationConfig>
     */
    public LiveData<StationConfig> getStationConfig() {
        return stationConfig;
    }
    
    /**
     * Get battery configuration
     * @return LiveData<BatteryConfig>
     */
    public LiveData<BatteryConfig> getBatteryConfig() {
        return batteryConfig;
    }
    
    /**
     * Get performance configuration
     * @return LiveData<PerformanceConfig>
     */
    public LiveData<PerformanceConfig> getPerformanceConfig() {
        return performanceConfig;
    }
    
    /**
     * Get Solarman API configuration
     * @return LiveData<SolarmanApiConfig>
     */
    public LiveData<SolarmanApiConfig> getSolarmanApiConfig() {
        return solarmanApiConfig;
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
     * Save station configuration
     * @param stationConfig StationConfig to save
     */
    public void saveStationConfig(StationConfig stationConfig) {
        executor.execute(() -> {
            try {
                stationConfigRepository.insert(stationConfig);
                // Clear saved fragment states when station config changes
                StateUtils.clearAllState(getApplication());
                operationSuccess.postValue(true);
                operationMessage.postValue("Station configuration saved");
                Log.d(TAG, "Station configuration saved");
            } catch (Exception e) {
                operationSuccess.postValue(false);
                operationMessage.postValue("Error saving station configuration: " + e.getMessage());
                Log.e(TAG, "Error saving station configuration", e);
            }
        });
    }
    
    /**
     * Save battery configuration
     * @param batteryConfig BatteryConfig to save
     */
    public void saveBatteryConfig(BatteryConfig batteryConfig) {
        executor.execute(() -> {
            try {
                batteryConfigRepository.insert(batteryConfig);
                operationSuccess.postValue(true);
                operationMessage.postValue("Battery configuration saved");
                Log.d(TAG, "Battery configuration saved");
            } catch (Exception e) {
                operationSuccess.postValue(false);
                operationMessage.postValue("Error saving battery configuration: " + e.getMessage());
                Log.e(TAG, "Error saving battery configuration", e);
            }
        });
    }
    
    /**
     * Save performance configuration
     * @param performanceConfig PerformanceConfig to save
     */
    public void savePerformanceConfig(PerformanceConfig performanceConfig) {
        executor.execute(() -> {
            try {
                performanceConfigRepository.insert(performanceConfig);
                operationSuccess.postValue(true);
                operationMessage.postValue("Performance configuration saved");
                Log.d(TAG, "Performance configuration saved");
            } catch (Exception e) {
                operationSuccess.postValue(false);
                operationMessage.postValue("Error saving performance configuration: " + e.getMessage());
                Log.e(TAG, "Error saving performance configuration", e);
            }
        });
    }
    
    /**
     * Save Solarman API configuration
     * @param solarmanApiConfig SolarmanApiConfig to save
     */
    public void saveSolarmanApiConfig(SolarmanApiConfig solarmanApiConfig) {
        executor.execute(() -> {
            try {
                solarmanApiConfigRepository.insert(solarmanApiConfig);
                operationSuccess.postValue(true);
                operationMessage.postValue("API configuration saved");
                Log.d(TAG, "API configuration saved");
            } catch (Exception e) {
                operationSuccess.postValue(false);
                operationMessage.postValue("Error saving API configuration: " + e.getMessage());
                Log.e(TAG, "Error saving API configuration", e);
            }
        });
    }
    
    /**
     * Clear station configuration
     */
    public void clearStationConfig() {
        executor.execute(() -> {
            try {
                stationConfigRepository.delete();
                operationSuccess.postValue(true);
                operationMessage.postValue("Data cleared successfully");
                Log.d(TAG, "Station configuration cleared");
            } catch (Exception e) {
                operationSuccess.postValue(false);
                operationMessage.postValue("Error clearing station configuration: " + e.getMessage());
                Log.e(TAG, "Error clearing station configuration", e);
            }
        });
    }
    
    /**
     * Clear battery configuration
     */
    public void clearBatteryConfig() {
        executor.execute(() -> {
            try {
                batteryConfigRepository.delete();
                operationSuccess.postValue(true);
                operationMessage.postValue("Data cleared successfully");
                Log.d(TAG, "Battery configuration cleared");
            } catch (Exception e) {
                operationSuccess.postValue(false);
                operationMessage.postValue("Error clearing battery configuration: " + e.getMessage());
                Log.e(TAG, "Error clearing battery configuration", e);
            }
        });
    }
    
    /**
     * Clear performance configuration
     */
    public void clearPerformanceConfig() {
        executor.execute(() -> {
            try {
                performanceConfigRepository.delete();
                operationSuccess.postValue(true);
                operationMessage.postValue("Data cleared successfully");
                Log.d(TAG, "Performance configuration cleared");
            } catch (Exception e) {
                operationSuccess.postValue(false);
                operationMessage.postValue("Error clearing performance configuration: " + e.getMessage());
                Log.e(TAG, "Error clearing performance configuration", e);
            }
        });
    }
    
    /**
     * Clear Solarman API configuration
     */
    public void clearSolarmanApiConfig() {
        executor.execute(() -> {
            try {
                solarmanApiConfigRepository.delete();
                operationSuccess.postValue(true);
                operationMessage.postValue("Data cleared successfully");
                Log.d(TAG, "API configuration cleared");
            } catch (Exception e) {
                operationSuccess.postValue(false);
                operationMessage.postValue("Error clearing API configuration: " + e.getMessage());
                Log.e(TAG, "Error clearing API configuration", e);
            }
        });
    }
    
    /**
     * Clear all configurations
     */
    public void clearAllConfigs() {
        executor.execute(() -> {
            try {
                stationConfigRepository.delete();
                batteryConfigRepository.delete();
                performanceConfigRepository.delete();
                solarmanApiConfigRepository.delete();
                operationSuccess.postValue(true);
                operationMessage.postValue("Data cleared successfully");
                Log.d(TAG, "All configurations cleared");
            } catch (Exception e) {
                operationSuccess.postValue(false);
                operationMessage.postValue("Error clearing configurations: " + e.getMessage());
                Log.e(TAG, "Error clearing configurations", e);
            }
        });
    }
    
    /**
     * Load test configuration from JSON
     */
    public void loadTestConfig() {
        executor.execute(() -> {
            try {
                // Read JSON from raw resource
                InputStream is = getApplication().getResources().openRawResource(R.raw.test_station_config);
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder jsonString = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonString.append(line);
                }
                
                // Parse JSON
                JSONObject jsonObject = new JSONObject(jsonString.toString());
                
                // Parse system config
                JSONObject systemJson = jsonObject.getJSONObject("system");
                StationConfig stationConfig = new StationConfig();
                stationConfig.setInverterPowerKw(systemJson.getDouble("inverter_power_kw"));
                stationConfig.setPanelPowerW(systemJson.getInt("panel_power_w"));
                stationConfig.setPanelCount(systemJson.getInt("panel_count"));
                stationConfig.setPanelEfficiency(systemJson.getDouble("panel_efficiency"));
                stationConfig.setTiltDeg(systemJson.getInt("tilt_deg"));
                stationConfig.setLatitude(systemJson.getDouble("latitude"));
                stationConfig.setLongitude(systemJson.getDouble("longitude"));
                
                // Parse battery config
                JSONObject batteryJson = jsonObject.getJSONObject("battery");
                BatteryConfig batteryConfig = new BatteryConfig();
                batteryConfig.setCapacityKwh(batteryJson.getDouble("capacity_kwh"));
                batteryConfig.setCount(batteryJson.getInt("count"));
                batteryConfig.setType(batteryJson.getString("type"));
                batteryConfig.setRoundtripEfficiency(batteryJson.getDouble("roundtrip_efficiency"));
                batteryConfig.setSocMinPct(batteryJson.getInt("soc_min_pct"));
                batteryConfig.setSocMaxPct(batteryJson.getInt("soc_max_pct"));
                batteryConfig.setNightUseGrid(batteryJson.getBoolean("night_use_grid"));
                batteryConfig.setAllowGridCharging(batteryJson.getBoolean("allow_grid_charging"));
                
                // Parse performance config
                JSONObject performanceJson = jsonObject.getJSONObject("performance");
                PerformanceConfig performanceConfig = new PerformanceConfig();
                performanceConfig.setPerformanceRatioAvg(performanceJson.getDouble("performance_ratio_avg"));
                performanceConfig.setTemperatureCoeff(performanceJson.getDouble("temperature_coeff"));
                performanceConfig.setReferenceTemp(performanceJson.getInt("reference_temp"));
                
                // Parse API config
                JSONObject apiJson = jsonObject.getJSONObject("solarman_api");
                SolarmanApiConfig solarmanApiConfig = new SolarmanApiConfig();
                solarmanApiConfig.setBaseUrl(apiJson.getString("base_url"));
                solarmanApiConfig.setEmail(apiJson.getString("email"));
                solarmanApiConfig.setPassword(apiJson.getString("password"));
                solarmanApiConfig.setAppId(apiJson.getString("app_id"));
                solarmanApiConfig.setAppSecret(apiJson.getString("app_secret"));
                solarmanApiConfig.setTimeout(apiJson.getInt("timeout"));
                solarmanApiConfig.setDeviceId(apiJson.getLong("device_id"));
                solarmanApiConfig.setDeviceSn(apiJson.getString("device_sn"));
                
                // Save all configs
                stationConfigRepository.insert(stationConfig);
                batteryConfigRepository.insert(batteryConfig);
                performanceConfigRepository.insert(performanceConfig);
                solarmanApiConfigRepository.insert(solarmanApiConfig);
                
                operationSuccess.postValue(true);
                operationMessage.postValue("Test configuration loaded");
                Log.d(TAG, "Test configuration loaded");
                
            } catch (IOException | JSONException e) {
                operationSuccess.postValue(false);
                operationMessage.postValue("Error loading test configuration: " + e.getMessage());
                Log.e(TAG, "Error loading test configuration", e);
            }
        });
    }
    
    private final MutableLiveData<Boolean> hasAnyConfig = new MutableLiveData<>();
    
    /**
     * Check if any configuration exists
     * @return LiveData<Boolean> true if any configuration exists
     */
    public LiveData<Boolean> hasAnyConfig() {
        return hasAnyConfig;
    }
    
    /**
     * Initialize config check (must be called to initialize)
     */
    public void checkAnyConfig() {
        executor.execute(() -> {
            try {
                // Since we're on background thread, we can safely use synchronous methods
                // For StationConfigRepository, use LiveData-based exists()
                stationConfigRepository.exists().observeForever(stationExists -> {
                    if (Boolean.TRUE.equals(stationExists)) {
                        hasAnyConfig.postValue(true);
                    } else {
                        // Check other configs only if station config doesn't exist
                        // Note: Other repositories still use synchronous exists() 
                        // but it's safe here since we're on background thread
                        try {
                            boolean anyExists = batteryConfigRepository.exists() || 
                                                  performanceConfigRepository.exists() || 
                                                  solarmanApiConfigRepository.exists();
                            hasAnyConfig.postValue(anyExists);
                        } catch (Exception e) {
                            Log.e(TAG, "Error checking other configs", e);
                            hasAnyConfig.postValue(false);
                        }
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error checking configs", e);
                hasAnyConfig.postValue(false);
            }
        });
    }
}
