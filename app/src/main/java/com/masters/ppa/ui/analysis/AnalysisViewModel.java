package com.masters.ppa.ui.analysis;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.masters.ppa.R;
import com.masters.ppa.data.api.InverterApiService;
import com.masters.ppa.data.model.GenerationData;
import com.masters.ppa.data.model.InverterDataGroups;
import com.masters.ppa.data.model.SolarmanApiConfig;
import com.masters.ppa.data.model.StationData;
import com.masters.ppa.data.parser.InverterDataParser;
import com.masters.ppa.data.repository.GenerationDataRepository;
import com.masters.ppa.data.repository.SolarmanApiConfigRepository;
import com.masters.ppa.data.repository.StationConfigRepository;
import com.masters.ppa.data.repository.StationDataRepository;
import com.masters.ppa.utils.FileUtils;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * ViewModel for the Analysis screen
 */
public class AnalysisViewModel extends AndroidViewModel {

    private static final String TAG = "AnalysisViewModel";
    
    private final StationConfigRepository stationConfigRepository;
    private final StationDataRepository stationDataRepository;
    private final GenerationDataRepository generationDataRepository;
    private final SolarmanApiConfigRepository solarmanApiConfigRepository;
    private final InverterApiService inverterApiService;
    
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> operationSuccess = new MutableLiveData<>();
    private final MutableLiveData<String> operationMessage = new MutableLiveData<>();
    private final MutableLiveData<InverterDataGroups> inverterData = new MutableLiveData<>();
    private final MutableLiveData<Date> inverterLastUpdated = new MutableLiveData<>();
    
    private final Executor executor = Executors.newSingleThreadExecutor();

    public AnalysisViewModel(@NonNull Application application) {
        super(application);
        stationConfigRepository = new StationConfigRepository(application);
        stationDataRepository = new StationDataRepository(application);
        generationDataRepository = new GenerationDataRepository(application);
        solarmanApiConfigRepository = new SolarmanApiConfigRepository(application);
        inverterApiService = new InverterApiService(application);
        initStationDataCheck();
    }
    
    private final MutableLiveData<Boolean> hasStationData = new MutableLiveData<>();
    
    /**
     * Get whether station data exists
     * @return LiveData<Boolean> true if station data exists
     */
    public LiveData<Boolean> hasStationData() {
        return hasStationData;
    }
    
    /**
     * Get station configuration
     * @return LiveData<StationConfig>
     */
    public LiveData<com.masters.ppa.data.model.StationConfig> getStationConfig() {
        return stationConfigRepository.getStationConfig();
    }
    
    /**
     * Initialize station data check
     */
    private void initStationDataCheck() {
        stationConfigRepository.exists().observeForever(exists -> {
            hasStationData.postValue(exists);
        });
    }
    
    /**
     * Get latest station data
     * @return LiveData<StationData>
     */
    public LiveData<StationData> getLatestStationData() {
        return stationDataRepository.getLatestStationData();
    }
    
    /**
     * Get all station data
     * @return LiveData<List<StationData>>
     */
    public LiveData<List<StationData>> getAllStationData() {
        return stationDataRepository.getAllStationData();
    }
    
    /**
     * Get last 14 days actual generation
     * @return LiveData<List<GenerationData>>
     */
    public LiveData<List<GenerationData>> getLast14DaysActualGeneration() {
        return generationDataRepository.getLast14DaysActualGeneration();
    }
    
    /**
     * Get next 7 days predicted generation
     * @return LiveData<List<GenerationData>>
     */
    public LiveData<List<GenerationData>> getNext7DaysPredictedGeneration() {
        return generationDataRepository.getNext7DaysPredictedGeneration();
    }
    
    /**
     * Get all generation data
     * @return LiveData<List<GenerationData>>
     */
    public LiveData<List<GenerationData>> getAllGenerationData() {
        return generationDataRepository.getAllGenerationData();
    }
    
    /**
     * Get generation data last updated date
     * @return LiveData<Date>
     */
    public LiveData<Date> getGenerationLastUpdatedDate() {
        return generationDataRepository.getLastUpdatedDate();
    }
    
    /**
     * Get station data last updated date
     * @return LiveData<Date>
     */
    public LiveData<Date> getStationLastUpdatedDate() {
        return stationDataRepository.getLastUpdatedDate();
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
     * Get inverter data
     * @return LiveData<InverterDataGroups>
     */
    public LiveData<InverterDataGroups> getInverterData() {
        return inverterData;
    }
    
    /**
     * Get inverter data last updated date
     * @return LiveData<Date>
     */
    public LiveData<Date> getInverterLastUpdated() {
        return inverterLastUpdated;
    }
    
    /**
     * Fetch current inverter data from Solarman API
     */
    public void fetchCurrentInverterData() {
        isLoading.setValue(true);
        executor.execute(() -> {
            try {
                // Check API configuration
                SolarmanApiConfig config = solarmanApiConfigRepository.getSolarmanApiConfigSync();
                if (config == null) {
                    operationSuccess.postValue(false);
                    operationMessage.postValue("Solarman API configuration not found. Please configure in Settings.");
                    isLoading.postValue(false);
                    return;
                }
                
                // Validate required configuration fields
                if (config.getDeviceSn() == null || config.getDeviceSn().isEmpty()) {
                    operationSuccess.postValue(false);
                    operationMessage.postValue("Device SN is not configured. Please configure in Settings.");
                    isLoading.postValue(false);
                    return;
                }
                
                if (config.getAppId() == null || config.getAppId().isEmpty() ||
                    config.getAppSecret() == null || config.getAppSecret().isEmpty() ||
                    config.getEmail() == null || config.getEmail().isEmpty() ||
                    config.getPassword() == null || config.getPassword().isEmpty()) {
                    operationSuccess.postValue(false);
                    operationMessage.postValue("Incomplete API configuration. Please check App ID, App Secret, Email, and Password in Settings.");
                    isLoading.postValue(false);
                    return;
                }
                
                operationMessage.postValue("Fetching inverter data...");
                
                // Get data from API
                Future<JSONObject> dataFuture = inverterApiService.getCurrentDataAsync(config.getDeviceSn());
                JSONObject response = dataFuture.get();
                
                // Parse response
                InverterDataGroups groups = InverterDataParser.parseJsonResponse(response);
                inverterData.postValue(groups);
                inverterLastUpdated.postValue(new Date());
                
                operationSuccess.postValue(true);
                operationMessage.postValue("Inverter data updated successfully");
                
                Log.d(TAG, "✅ Inverter data fetched and parsed: " + groups.getAllMetrics().size() + " metrics");
                
            } catch (Exception e) {
                operationSuccess.postValue(false);
                operationMessage.postValue("Error fetching inverter data: " + e.getMessage());
                Log.e(TAG, "Error fetching inverter data", e);
            } finally {
                isLoading.postValue(false);
            }
        });
    }
    
    /**
     * Refresh station data (now uses inverter API)
     */
    public void refreshStationData() {
        // For CurrentDataFragment, we use inverter API
        fetchCurrentInverterData();
    }
    
    /**
     * Load test station data
     */
    public void loadTestStationData() {
        isLoading.setValue(true);
        executor.execute(() -> {
            try {
                // Copy test data from raw resource to internal storage
                File copiedFile = null;
                
                // Verify file was copied successfully
                if (copiedFile == null || !copiedFile.exists()) {
                    Log.e(TAG, "Failed to copy test station data file");
                    operationSuccess.postValue(false);
                    operationMessage.postValue("Error: Could not copy test station data file");
                    isLoading.postValue(false);
                    return;
                }
                
                // Load the data
                boolean success = stationDataRepository.loadFromCsv(true);
                if (success) {
                    operationSuccess.postValue(true);
                    operationMessage.postValue("Test station data loaded successfully");
                } else {
                    operationSuccess.postValue(false);
                    operationMessage.postValue("Error loading test station data");
                }
            } catch (Exception e) {
                operationSuccess.postValue(false);
                operationMessage.postValue("Error loading test station data: " + e.getMessage());
                Log.e(TAG, "Error loading test station data", e);
            } finally {
                isLoading.postValue(false);
            }
        });
    }
    
    /**
     * Load test generation data
     * Note: Test data files are not included in the app
     */
    public void loadTestGenerationData() {
        isLoading.setValue(true);
        executor.execute(() -> {
            try {
                operationSuccess.postValue(false);
                operationMessage.postValue("Test data files are not available. Please load data from API or CSV files.");
                Log.w(TAG, "Test generation data loading is disabled - test files are not included in the app");
            } catch (Exception e) {
                operationSuccess.postValue(false);
                operationMessage.postValue("Error loading test generation data: " + e.getMessage());
                Log.e(TAG, "Error loading test generation data", e);
            } finally {
                isLoading.postValue(false);
            }
        });
    }

    /**
     * Generate battery analysis summary
     */
    public String generateAnalysisSummary(int[] classIds) {
        if (classIds == null || classIds.length == 0) {
            return "";
        }

        // Count class occurrences
        int[] classCounts = new int[3];
        for (int classId : classIds) {
            if (classId >= 0 && classId < 3) {
                classCounts[classId]++;
            }
        }

        int totalDays = classIds.length;
        double[] classPercentages = new double[3];
        for (int i = 0; i < 3; i++) {
            classPercentages[i] = (classCounts[i] * 100.0) / totalDays;
        }

        return String.format(Locale.getDefault(),
            "Analysis of %d days:\n" +
            "🟠 Oversized/Idle: %.1f%% (%d days)\n" +
            "🟢 Balanced: %.1f%% (%d days)\n" +
            "🔴 Undersized/High stress: %.1f%% (%d days)",
            totalDays,
            classPercentages[0], classCounts[0],
            classPercentages[1], classCounts[1],
            classPercentages[2], classCounts[2]);
    }

    /**
     * Generate battery analysis recommendations based on class percentages
     */
    public String generateBatteryRecommendations(int[] classIds) {
        if (classIds == null || classIds.length == 0) {
            return "";
        }

        // Count class occurrences
        int[] classCounts = new int[3];
        for (int classId : classIds) {
            if (classId >= 0 && classId < 3) {
                classCounts[classId]++;
            }
        }

        int totalDays = classIds.length;
        double[] classPercentages = new double[3];
        for (int i = 0; i < 3; i++) {
            classPercentages[i] = (classCounts[i] * 100.0) / totalDays;
        }

        double p0 = classPercentages[0]; // Oversized/Idle
        double p1 = classPercentages[1]; // Balanced
        double p2 = classPercentages[2]; // Undersized/High stress

        StringBuilder sb = new StringBuilder();

        // Only show recommendations for significant percentages
        if (p0 >= 25) {
            sb.append("🟠 Oversized / Idle System\n\n");
            if (p0 >= 70) {
                sb.append("Very strong signal (≥70%)\n");
                sb.append("The system is operating with substantial unused capacity. ");
                sb.append("The inverter and battery remain lightly loaded for most of the period. ");
                sb.append("Consider downsizing the battery, reducing inverter capacity, or increasing self-consumption ");
                sb.append("(EV charging, heat pump, water heating, daytime shifting). ");
                sb.append("Significant cost optimization is possible.\n\n");
            } else if (p0 >= 60) {
                sb.append("Strong signal (60–70%)\n");
                sb.append("The system is consistently under-utilized. ");
                sb.append("Most of the available generation and storage potential remains unused. ");
                sb.append("Consider whether current sizing matches your actual consumption profile.\n\n");
            } else if (p0 >= 45) {
                sb.append("Moderate signal (45–60%)\n");
                sb.append("The system shows a noticeable trend toward under-utilization. ");
                sb.append("This is not critical, but there is room for efficiency improvements.\n\n");
            } else {
                sb.append("Mild signal (25–45%)\n");
                sb.append("Occasional under-loading detected. ");
                sb.append("The system operates stably, but has more capacity than strictly needed.\n\n");
            }
        }

        if (p1 >= 35) {
            sb.append("🟢 Balanced System\n\n");
            if (p1 >= 80) {
                sb.append("Excellent match (≥80%)\n");
                sb.append("The system is optimally sized. ");
                sb.append("Battery cycling, SoC ranges, and load coverage are well balanced. ");
                sb.append("No adjustments are necessary.\n\n");
            } else if (p1 >= 65) {
                sb.append("Very good match (65–80%)\n");
                sb.append("The system performs efficiently with only minor deviations. ");
                sb.append("Generation and consumption are well aligned.\n\n");
            } else if (p1 >= 50) {
                sb.append("Acceptable match (50–65%)\n");
                sb.append("The system is generally well-matched, with occasional deviations. ");
                sb.append("No action required, but periodic monitoring is recommended.\n\n");
            } else {
                sb.append("Slight imbalance (35–50%)\n");
                sb.append("The system is close to balanced but shows intermittent over- or under-loading. ");
                sb.append("Seasonal effects or unusual consumption patterns may be the cause.\n\n");
            }
        }

        if (p2 >= 15) {
            sb.append("🔴 Undersized / High Stress System\n\n");
            if (p2 >= 50) {
                sb.append("Critical signal (≥50%)\n");
                sb.append("The system operates under continuous high stress. ");
                sb.append("The battery frequently reaches deep cycles, operates at higher temperatures, or experiences high C-rates. ");
                sb.append("Battery lifespan may be significantly reduced. ");
                sb.append("Consider adding battery capacity, improving thermal conditions, or reducing peak loads.\n\n");
            } else if (p2 >= 40) {
                sb.append("Severe signal (40–50%)\n");
                sb.append("Frequent overload patterns detected. ");
                sb.append("The current battery and inverter configuration is insufficient for your consumption profile. ");
                sb.append("Recommended actions: add storage, reduce peak consumption, or shift loads more effectively.\n\n");
            } else if (p2 >= 25) {
                sb.append("Moderate signal (25–40%)\n");
                sb.append("Noticeable periods of high stress occur. ");
                sb.append("This may be caused by seasonal peaks, insufficient generation on cloudy days, or large evening loads.\n\n");
            } else {
                sb.append("Mild signal (15–25%)\n");
                sb.append("Some stressful days are present but not dominant. ");
                sb.append("The system is generally stable, though peak loads occasionally exceed capacity.\n\n");
            }
        }

        if (sb.length() == 0) {
            sb.append("No significant patterns detected. The system shows mixed behavior across all categories.");
        }

        return sb.toString().trim();
    }

    /**
     * Generate forecast analysis summary and recommendations
     */
    public String generateForecastSummary(List<Double> dailyGenerationKwh, double inverterPowerKw, 
                                         double latitude, int currentMonth) {
        if (dailyGenerationKwh == null || dailyGenerationKwh.isEmpty()) {
            return "No forecast data available.";
        }

        // Determine season based on month
        String season = getSeason(currentMonth);
        double seasonalReference = getSeasonalReference(season);
        
        // Calculate daily potential
        double dailyPotentialKwh = inverterPowerKw * seasonalReference;
        
        // Calculate solar level percentages for each day
        List<Double> dailySolarLevels = new ArrayList<>();
        for (double dailyGen : dailyGenerationKwh) {
            double solarLevel = (dailyPotentialKwh > 0) ? (dailyGen / dailyPotentialKwh) * 100.0 : 0.0;
            dailySolarLevels.add(solarLevel);
        }
        
        // Calculate weekly average
        double weeklyAverage = dailySolarLevels.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        
        StringBuilder sb = new StringBuilder();
        
        // Weekly rating and recommendation
        String weeklyRating = getWeeklyRating(weeklyAverage);
        String weeklyRecommendation = getWeeklyRecommendation(weeklyAverage);
        
        sb.append(String.format(Locale.getDefault(), "Weekly solar rating: %.0f%% (%s)\n\n", 
                weeklyAverage, weeklyRating));
        sb.append("Recommendation:\n");
        sb.append(weeklyRecommendation);
        
        // Find best days for flexible loads
        List<String> bestDays = getBestDaysForLoads(dailySolarLevels);
        if (!bestDays.isEmpty()) {
            sb.append("\n\nBest days for flexible loads: ");
            sb.append(String.join(", ", bestDays));
        }
        
        return sb.toString();
    }

    /**
     * Generate daily forecast recommendations
     */
    public List<String> generateDailyForecastRecommendations(List<Double> dailyGenerationKwh, 
                                                           double inverterPowerKw, int currentMonth) {
        if (dailyGenerationKwh == null || dailyGenerationKwh.isEmpty()) {
            return new ArrayList<>();
        }

        String season = getSeason(currentMonth);
        double seasonalReference = getSeasonalReference(season);
        double dailyPotentialKwh = inverterPowerKw * seasonalReference;
        
        List<String> recommendations = new ArrayList<>();
        
        for (double dailyGen : dailyGenerationKwh) {
            double solarLevel = (dailyPotentialKwh > 0) ? (dailyGen / dailyPotentialKwh) * 100.0 : 0.0;
            recommendations.add(getDailyRecommendation(solarLevel));
        }
        
        return recommendations;
    }

    private String getSeason(int month) {
        if (month == 12 || month == 1 || month == 2) return "winter";
        if (month >= 3 && month <= 5) return "spring";
        if (month >= 6 && month <= 8) return "summer";
        return "autumn"; // 9-11
    }

    private double getSeasonalReference(String season) {
        switch (season) {
            case "winter": return 1.3; // Average of 0.8-1.8
            case "spring": return 3.0; // Average of 2.5-3.5
            case "summer": return 5.5; // Average of 4.5-6.5
            case "autumn": return 2.5; // Average of 2.0-3.0
            default: return 3.0;
        }
    }

    private String getWeeklyRating(double weeklyAverage) {
        if (weeklyAverage < 10) return "Extremely low";
        if (weeklyAverage < 25) return "Very low";
        if (weeklyAverage < 40) return "Low";
        if (weeklyAverage < 60) return "Moderate";
        if (weeklyAverage < 80) return "High";
        return "Excellent";
    }

    private String getWeeklyRecommendation(double weeklyAverage) {
        if (weeklyAverage < 10) {
            return "The expected sunlight at your coordinates is extremely low for the coming days. " +
                   "Your PV system will produce only a small fraction of its seasonal potential. " +
                   "Expect almost full dependence on the grid. Keep the battery at a safe state of charge " +
                   "and avoid scheduling any heavy loads based on solar generation.";
        } else if (weeklyAverage < 25) {
            return "Forecasted production is significantly below seasonal norms for your location. " +
                   "Your system will not fully recharge the battery most days. Use the grid for critical tasks " +
                   "and avoid daytime loads unless necessary.";
        } else if (weeklyAverage < 40) {
            return "A weak solar week ahead, slightly below typical levels for this season. " +
                   "Solar will cover only part of daily consumption; battery cycling will be limited. " +
                   "Schedule only light flexible loads during midday.";
        } else if (weeklyAverage < 60) {
            return "A balanced week with moderate sunlight for your region. " +
                   "Your PV system will noticeably reduce grid usage. " +
                   "Daytime loads such as dishwasher or washing machine can be shifted to sunny hours.";
        } else if (weeklyAverage < 80) {
            return "A productive solar week expected, well above seasonal average. " +
                   "This is a good time to maximize self-consumption: run flexible loads during the day " +
                   "and rely less on the grid.";
        } else {
            return "Forecast indicates near-ideal solar output for your coordinates. " +
                   "Expect high generation and frequent full battery charges. " +
                   "Ideal period for energy-intensive daytime tasks — EV charging, heat pump operation, " +
                   "water heating — to minimize grid imports.";
        }
    }

    private String getDailyRecommendation(double solarLevel) {
        if (solarLevel < 15) {
            return "Very poor solar day — expect almost full grid usage.";
        } else if (solarLevel < 35) {
            return "Weak solar day — battery may not fully charge.";
        } else if (solarLevel < 55) {
            return "Moderate solar day — some loads can be shifted to midday.";
        } else if (solarLevel < 75) {
            return "Good solar day — schedule flexible loads during daytime.";
        } else {
            return "Excellent solar day — ideal for high-consumption tasks in daylight.";
        }
    }

    private List<String> getBestDaysForLoads(List<Double> dailySolarLevels) {
        List<String> dayNames = List.of("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun");
        List<String> bestDays = new ArrayList<>();
        
        for (int i = 0; i < Math.min(dailySolarLevels.size(), dayNames.size()); i++) {
            if (dailySolarLevels.get(i) >= 55) { // Good or excellent days
                bestDays.add(dayNames.get(i));
            }
        }
        
        return bestDays;
    }

    /**
     * Generate station configuration analysis recommendations
     */
    public List<String> generateStationRecommendations(com.masters.ppa.data.model.StationConfig config) {
        List<String> recommendations = new ArrayList<>();
        
        if (config == null) {
            recommendations.add("Station configuration not found. Please configure your station in Settings first.");
            return recommendations;
        }

        // Validate that all required fields are properly filled
        String validationError = validateStationConfig(config);
        if (validationError != null) {
            recommendations.add(validationError);
            return recommendations;
        }

        try {
            // Calculate basic parameters
            double pvDcKwp = (config.getPanelPowerW() * config.getPanelCount()) / 1000.0;
            double dcAcRatio = (config.getInverterPowerKw() > 0) ? pvDcKwp / config.getInverterPowerKw() : 0;
            
            // 1. PV vs Inverter (DC/AC Ratio)
            String pvInverterRec;
            if (dcAcRatio < 0.9) {
                pvInverterRec = String.format(Locale.getDefault(),
                    "Your %.1f kW inverter capacity exceeds your %.1f kWp PV array size (ratio %.2f). This provides room for future panel expansion but may result in underutilized inverter potential during peak generation periods.",
                    config.getInverterPowerKw(), pvDcKwp, dcAcRatio);
            } else if (dcAcRatio <= 1.2) {
                pvInverterRec = String.format(Locale.getDefault(),
                    "Your PV-to-inverter ratio is well balanced (%.1f kWp / %.1f kW = %.2f). This configuration minimizes power clipping while maximizing energy harvest throughout most of the year.",
                    pvDcKwp, config.getInverterPowerKw(), dcAcRatio);
            } else {
                pvInverterRec = String.format(Locale.getDefault(),
                    "Your %.1f kWp PV array is oversized relative to the %.1f kW inverter capacity (ratio %.2f). While this may cause some power clipping during peak sun hours, it can improve overall annual energy production.",
                    pvDcKwp, config.getInverterPowerKw(), dcAcRatio);
            }
            recommendations.add(pvInverterRec);

            // 2. Battery Size vs PV Size (need battery config from JSON structure)
            // For now, use default values if battery config is not available in StationConfig
            double batteryTotalKwh = 15.36; // Default: 2.56 * 6 from test config
            double socMinPct = 20.0;
            double socMaxPct = 100.0;
            double usableKwh = batteryTotalKwh * (socMaxPct - socMinPct) / 100.0;
            double batteryPerKwp = (pvDcKwp > 0) ? usableKwh / pvDcKwp : 0;
            
            String batteryPvRec;
            if (batteryPerKwp < 0.5) {
                batteryPvRec = String.format(Locale.getDefault(),
                    "Your %.1f kWh usable battery capacity is relatively small compared to your %.1f kWp PV system (%.2f kWh/kWp). This setup prioritizes immediate consumption over energy storage, with limited backup during outages.",
                    usableKwh, pvDcKwp, batteryPerKwp);
            } else if (batteryPerKwp <= 1.5) {
                batteryPvRec = String.format(Locale.getDefault(),
                    "Your battery-to-PV ratio provides good balance between energy storage and system cost (%.1f kWh / %.1f kWp = %.2f kWh/kWp). This configuration offers reasonable autonomy during cloudy periods and grid outages.",
                    usableKwh, pvDcKwp, batteryPerKwp);
            } else {
                batteryPvRec = String.format(Locale.getDefault(),
                    "Your %.1f kWh battery system is quite large relative to your %.1f kWp PV capacity (%.2f kWh/kWp). This provides excellent backup power and energy independence, though the battery may not fully charge during winter months.",
                    usableKwh, pvDcKwp, batteryPerKwp);
            }
            recommendations.add(batteryPvRec);

            // 3. Tilt vs Latitude
            double latDeg = Math.abs(config.getLatitude());
            int tiltDeg = config.getTiltDeg();
            
            String tiltRec;
            if (tiltDeg < latDeg - 15) {
                tiltRec = String.format(Locale.getDefault(),
                    "Your %d° panel tilt angle is quite low for your %.1f° latitude. This configuration favors summer energy production but may reduce winter generation when sun angles are lower.",
                    tiltDeg, config.getLatitude());
            } else if (tiltDeg <= latDeg + 15) {
                tiltRec = String.format(Locale.getDefault(),
                    "Your %d° panel tilt angle is well-suited for your %.1f° latitude. This provides a balanced energy production profile throughout the year with good overall efficiency.",
                    tiltDeg, config.getLatitude());
            } else {
                tiltRec = String.format(Locale.getDefault(),
                    "Your %d° panel tilt angle is steeper than typically optimal for your %.1f° latitude. This may boost winter production but could slightly reduce peak summer generation.",
                    tiltDeg, config.getLatitude());
            }
            recommendations.add(tiltRec);

            // 4. Battery usage strategy (using defaults from test config)
            boolean nightUseGrid = true;
            boolean allowGridCharging = false;
            
            String strategyRec;
            if (socMinPct < 15) {
                strategyRec = String.format(Locale.getDefault(),
                    "Your battery strategy uses aggressive discharge levels (%.0f%%-%.0f%% SoC range, %.1f kWh usable), maximizing usable capacity but potentially increasing battery wear. Consider monitoring battery health over time.",
                    socMinPct, socMaxPct, usableKwh);
            } else if (socMinPct <= 30) {
                strategyRec = String.format(Locale.getDefault(),
                    "Your battery management strikes a good balance between usable capacity and battery longevity (%.0f%%-%.0f%% SoC range, %.1f kWh usable). The conservative discharge limits help preserve battery life while providing adequate backup power.",
                    socMinPct, socMaxPct, usableKwh);
            } else {
                strategyRec = String.format(Locale.getDefault(),
                    "Your battery strategy is very conservative (%.0f%%-%.0f%% SoC range, %.1f kWh usable), prioritizing battery longevity over maximum capacity utilization. This approach minimizes wear but reduces available backup energy.",
                    socMinPct, socMaxPct, usableKwh);
            }
            recommendations.add(strategyRec);

        } catch (Exception e) {
            recommendations.clear();
            recommendations.add("Unable to analyze configuration due to invalid or missing data. Please verify your station settings.");
        }

        return recommendations;
    }

    /**
     * Validate station configuration completeness
     */
    private String validateStationConfig(com.masters.ppa.data.model.StationConfig config) {
        // Check inverter power
        if (config.getInverterPowerKw() <= 0) {
            return "Incomplete configuration: Inverter power is not set. Please complete your station configuration in Settings.";
        }
        
        // Check panel specifications
        if (config.getPanelPowerW() <= 0) {
            return "Incomplete configuration: Panel power is not set. Please complete your station configuration in Settings.";
        }
        
        if (config.getPanelCount() <= 0) {
            return "Incomplete configuration: Panel count is not set. Please complete your station configuration in Settings.";
        }
        
        // Check panel efficiency (should be between 0 and 1)
        if (config.getPanelEfficiency() <= 0 || config.getPanelEfficiency() > 1) {
            return "Incomplete configuration: Panel efficiency is invalid. Please set a value between 0.1 and 1.0 in Settings.";
        }
        
        // Check tilt angle (should be between 0 and 90 degrees)
        if (config.getTiltDeg() < 0 || config.getTiltDeg() > 90) {
            return "Incomplete configuration: Panel tilt angle is invalid. Please set a value between 0 and 90 degrees in Settings.";
        }
        
        // Check coordinates
        if (config.getLatitude() < -90 || config.getLatitude() > 90) {
            return "Incomplete configuration: Latitude is invalid. Please set a value between -90 and 90 degrees in Settings.";
        }
        
        if (config.getLongitude() < -180 || config.getLongitude() > 180) {
            return "Incomplete configuration: Longitude is invalid. Please set a value between -180 and 180 degrees in Settings.";
        }
        
        // Check for zero coordinates (likely not set)
        if (config.getLatitude() == 0.0 && config.getLongitude() == 0.0) {
            return "Incomplete configuration: Station coordinates appear to be not set. Please enter your actual latitude and longitude in Settings.";
        }
        
        return null; // Configuration is valid
    }
    
}
