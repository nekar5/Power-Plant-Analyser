package com.masters.ppa.ml;

import android.content.Context;
import android.util.Log;

import com.masters.ppa.data.model.BatteryConfig;
import com.masters.ppa.data.model.StationConfig;
import com.masters.ppa.data.repository.BatteryConfigRepository;
import com.masters.ppa.data.repository.StationConfigRepository;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Processor for battery analysis using TensorFlow Lite model
 * Implements the same logic as Python battery inference code
 */
public class BatteryProcessor {
    
    private static final String TAG = "BatteryProcessor";
    
    private final Context context;
    private final ModelLoader modelLoader;
    private final StationConfigRepository stationConfigRepository;
    private final BatteryConfigRepository batteryConfigRepository;
    
    // Class labels matching Python
    private static final String[] CLASS_LABELS = {
        "Oversized/Idle",
        "Balanced",
        "Undersized/High stress"
    };

    public interface ProgressCallback {
        void onProgress(String message);
    }
    
    private ProgressCallback progressCallback;
    
    public BatteryProcessor(Context context) {
        this.context = context.getApplicationContext();
        this.modelLoader = ModelLoader.getInstance(context);
        this.stationConfigRepository = new StationConfigRepository(context);
        this.batteryConfigRepository = new BatteryConfigRepository(context);
    }
    
    public void setProgressCallback(ProgressCallback callback) {
        this.progressCallback = callback;
    }
    
    private void reportProgress(String message) {
        if (progressCallback != null) {
            progressCallback.onProgress(message);
        }
        Log.d(TAG, message);
    }
    
    /**
     * Result class for battery analysis
     */
    public static class BatteryResult {
        public final List<LocalDate> dates;
        public final int[] classIds; // Class ID for each day (0, 1, or 2)
        public final String[] classLabels; // Class label for each day
        public final float[] stress; // Stress index [0..1] for each day
        public final float[] utilization; // Utilization index [0..1] for each day
        public final List<RawDataRow> rawData; // Raw 5-minute data
        
        public BatteryResult(List<LocalDate> dates, int[] classIds, String[] classLabels,
                           float[] stress, float[] utilization, List<RawDataRow> rawData) {
            this.dates = dates;
            this.classIds = classIds;
            this.classLabels = classLabels;
            this.stress = stress;
            this.utilization = utilization;
            this.rawData = rawData;
        }
    }
    
    /**
     * Raw data row for 5-minute intervals
     */
    public static class RawDataRow {
        public LocalDateTime time;
        public float socClean;
        public float battTempC;
        
        public RawDataRow(LocalDateTime time, float socClean, float battTempC) {
            this.time = time;
            this.socClean = socClean;
            this.battTempC = battTempC;
        }
    }
    
    /**
     * Run battery analysis
     */
    public BatteryResult runAnalysis() throws Exception {
        reportProgress("Loading configuration...");
        
        // Load station config
        StationConfig stationConfig = stationConfigRepository.getStationConfigSync();
        if (stationConfig == null) {
            throw new Exception("Station configuration not found");
        }
        
        // Load battery config
        BatteryConfig batteryConfig = batteryConfigRepository.getBatteryConfigSync();
        if (batteryConfig == null) {
            throw new Exception("Battery configuration not found");
        }
        
        reportProgress("Loading operational data...");
        
        // Load station data
        List<StationDataRow> stationData = loadStationData();
        if (stationData.isEmpty()) {
            throw new Exception("Operational data not found. Please load data on Station page.");
        }
        
        reportProgress("Loading weather data...");
        
        // Load weather data
        List<WeatherDataRow> weatherData = loadWeatherData();
        if (weatherData.isEmpty()) {
            throw new Exception("Weather data not found. Please load data on Station page.");
        }
        
        reportProgress("Aligning data...");
        
        // Align data
        List<AlignedRow> alignedData = alignData(stationData, weatherData, 
            stationConfig.getLatitude(), stationConfig.getLongitude());
        
        if (alignedData.isEmpty()) {
            throw new Exception("Failed to align data");
        }
        
        reportProgress("Building sequences...");
        
        // Build sequences per day
        Map<LocalDate, List<AlignedRow>> dataByDate = groupByDate(alignedData);
        List<LocalDate> dates = new ArrayList<>(dataByDate.keySet());
        dates.sort(LocalDate::compareTo);
        
        // Load model and scaler
        reportProgress("Loading model...");
        try {
            if (!modelLoader.loadModel(ModelLoader.ModelType.BATTERY)) {
                throw new Exception("Failed to load battery model. Check if model files exist in assets/models/battery/");
            }
        } catch (Exception e) {
            throw e;
        }
        
        // Load scaler parameters
        JSONObject scalerJson = loadScalerJson();
        JSONArray featuresArray = scalerJson.getJSONArray("features");
        int maxTimesteps = scalerJson.getInt("max_timesteps");
        JSONArray meanArray = scalerJson.getJSONArray("mean");
        JSONArray scaleArray = scalerJson.getJSONArray("scale");
        
        // Build feature list
        List<String> featureNames = new ArrayList<>();
        double[] meanValues = new double[meanArray.length()];
        double[] scaleValues = new double[scaleArray.length()];
        
        for (int i = 0; i < featuresArray.length(); i++) {
            featureNames.add(featuresArray.getString(i));
            meanValues[i] = meanArray.getDouble(i);
            scaleValues[i] = scaleArray.getDouble(i);
        }
        
        reportProgress("Building sequences for inference...");
        
        // Build sequences for inference
        float[][][] sequences = buildSequences(dates, dataByDate, featureNames, maxTimesteps, 
            meanValues, scaleValues);
        
        reportProgress("Running model inference...");
        
        // Run inference
        ModelLoader.BatteryPredictionResult predictions = modelLoader.predictBattery(sequences);
        if (predictions == null) {
            throw new Exception("Failed to get predictions from model");
        }
        
        reportProgress("Processing results...");
        
        // Extract results
        int[] classIds = predictions.getPredictedClasses();
        String[] classLabels = new String[classIds.length];
        for (int i = 0; i < classIds.length; i++) {
            classLabels[i] = CLASS_LABELS[classIds[i]];
        }
        
        // Prepare raw data for charts
        List<RawDataRow> rawData = new ArrayList<>();
        for (AlignedRow row : alignedData) {
            rawData.add(new RawDataRow(row.time, row.socClean, row.battTempC));
        }
        
        return new BatteryResult(dates, classIds, classLabels, predictions.stress, 
            predictions.utilization, rawData);
    }
    
    /**
     * Load station data from CSV
     */
    private List<StationDataRow> loadStationData() throws Exception {
        List<StationDataRow> rows = new ArrayList<>();
        
        File stationFile = new File(context.getFilesDir(), "csv/station_data.csv");
        if (!stationFile.exists()) {
            return rows;
        }
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(
            new FileInputStream(stationFile)));
        
        String header = reader.readLine();
        if (header == null) {
            reader.close();
            return rows;
        }
        
        String[] headerCols = header.split(",");
        Map<String, Integer> colIndex = new HashMap<>();
        for (int i = 0; i < headerCols.length; i++) {
            colIndex.put(headerCols[i].trim().toLowerCase(), i);
        }
        
        DateTimeFormatter[] formatters = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        };
        
        String line;
        while ((line = reader.readLine()) != null) {
            String[] parts = line.split(",");
            if (parts.length < 3) continue;
            
            try {
                int idxTime = colIndex.getOrDefault("collecttime", -1);
                if (idxTime < 0 || idxTime >= parts.length) continue;
                
                String timeStr = parts[idxTime].trim();
                LocalDateTime time = null;
                
                if (timeStr.matches("\\d+")) {
                    long timestamp = Long.parseLong(timeStr);
                    long millis = timestamp < 2_000_000_000L ? timestamp * 1000L : timestamp;
                    time = LocalDateTime.ofEpochSecond(millis / 1000, 0, ZoneOffset.UTC);
                } else {
                    for (DateTimeFormatter formatter : formatters) {
                        try {
                            time = LocalDateTime.parse(timeStr, formatter);
                            break;
                        } catch (Exception e) {
                            // Try next format
                        }
                    }
                }
                
                if (time == null) continue;
                
                StationDataRow row = new StationDataRow();
                row.time = time;
                row.batterySocRaw = parseFloat(parts[colIndex.getOrDefault("soc_bap2", -1)], 0f);
                row.battTempC = parseFloat(parts[colIndex.getOrDefault("t_bap1", -1)], 25.0f);
                
                // Battery power
                float battPowerRaw = parseFloat(parts[colIndex.getOrDefault("p_bap2", -1)], 0f);
                float maxAbs = Math.abs(battPowerRaw);
                row.batteryPowerKw = maxAbs > 100f ? battPowerRaw / 1000f : battPowerRaw;
                
                // PV power
                int idxPvtp = colIndex.getOrDefault("pvtp", -1);
                if (idxPvtp >= 0 && idxPvtp < parts.length) {
                    String pvtpStr = parts[idxPvtp].trim().replace(",", ".");
                    java.util.regex.Pattern numPattern = java.util.regex.Pattern.compile(
                        "([-+]?\\d*\\.?\\d+(?:[eE][-+]?\\d+)?)");
                    java.util.regex.Matcher matcher = numPattern.matcher(pvtpStr);
                    if (matcher.find()) {
                        row.pvPowerKw = parseFloat(matcher.group(1), 0f) / 1000f;
                    }
                }
                
                // Grid power
                float grid1 = parseFloat(parts[colIndex.getOrDefault("pcc_ap1", -1)], 0f);
                float grid2 = parseFloat(parts[colIndex.getOrDefault("pcc_ap2", -1)], 0f);
                float grid3 = parseFloat(parts[colIndex.getOrDefault("pcc_ap3", -1)], 0f);
                row.gridPowerKw = grid1 + grid2 + grid3;
                
                // Load power
                float load1 = parseFloat(parts[colIndex.getOrDefault("ap1", -1)], 0f);
                float load2 = parseFloat(parts[colIndex.getOrDefault("ap2", -1)], 0f);
                float load3 = parseFloat(parts[colIndex.getOrDefault("ap3", -1)], 0f);
                row.loadPowerKw = load1 + load2 + load3;
                
                rows.add(row);
            } catch (Exception e) {
                Log.w(TAG, "Error parsing station row: " + line, e);
            }
        }
        
        reader.close();
        return rows;
    }
    
    /**
     * Load weather data from CSV
     */
    private List<WeatherDataRow> loadWeatherData() throws Exception {
        List<WeatherDataRow> rows = new ArrayList<>();
        
        File weatherFile = new File(context.getFilesDir(), "csv/weather_data.csv");
        if (!weatherFile.exists()) {
            return rows;
        }
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(
            new FileInputStream(weatherFile)));
        
        String header = reader.readLine();
        if (header == null) {
            reader.close();
            return rows;
        }
        
        String[] headerCols = header.split(",");
        Map<String, Integer> colIndex = new HashMap<>();
        for (int i = 0; i < headerCols.length; i++) {
            colIndex.put(headerCols[i].trim().toLowerCase(), i);
        }
        
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        
        String line;
        while ((line = reader.readLine()) != null) {
            String[] parts = line.split(",");
            if (parts.length < 3) continue;
            
            try {
                int timeIdx = colIndex.getOrDefault("time", -1);
                if (timeIdx < 0 || timeIdx >= parts.length) continue;
                
                String timeStr = parts[timeIdx].trim();
                LocalDateTime time = null;
                try {
                    time = LocalDateTime.parse(timeStr, formatter);
                } catch (Exception e) {
                    // Try alternative formats
                    DateTimeFormatter[] altFormatters = {
                        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    };
                    for (DateTimeFormatter altFormatter : altFormatters) {
                        try {
                            time = LocalDateTime.parse(timeStr, altFormatter);
                            break;
                        } catch (Exception e2) {
                            // Try next
                        }
                    }
                }
                
                if (time == null) continue;
                
                WeatherDataRow row = new WeatherDataRow();
                row.time = time;
                row.irradianceWm2 = parseFloat(parts[colIndex.getOrDefault("irradiance_wm2", 
                    colIndex.getOrDefault("shortwave_radiation", -1))], 0f);
                row.temperature2m = parseFloat(parts[colIndex.getOrDefault("temperature_2m", -1)], 0f);
                row.cloudCover = parseFloat(parts[colIndex.getOrDefault("cloud_cover", -1)], 0f);
                
                if (row.irradianceWm2 > 0 && row.temperature2m != 0 && row.cloudCover >= 0) {
                    rows.add(row);
                }
            } catch (Exception e) {
                Log.w(TAG, "Error parsing weather row: " + line, e);
            }
        }
        
        reader.close();
        return rows;
    }
    
    /**
     * Align station and weather data
     */
    private List<AlignedRow> alignData(List<StationDataRow> stationData, 
                                       List<WeatherDataRow> weatherData,
                                       double lat, double lon) {
        List<AlignedRow> aligned = new ArrayList<>();
        
        // Sort by time
        stationData.sort((a, b) -> a.time.compareTo(b.time));
        weatherData.sort((a, b) -> a.time.compareTo(b.time));
        
        // Merge with 1 hour tolerance
        for (StationDataRow station : stationData) {
            WeatherDataRow bestWeather = null;
            long minDiff = Long.MAX_VALUE;
            
            for (WeatherDataRow weather : weatherData) {
                long diff = Math.abs(java.time.Duration.between(station.time, weather.time).toMinutes());
                if (diff <= 60 && diff < minDiff) {
                    minDiff = diff;
                    bestWeather = weather;
                }
            }
            
            if (bestWeather != null) {
                AlignedRow row = new AlignedRow();
                row.time = station.time;
                row.batterySocRaw = station.batterySocRaw;
                row.battTempC = station.battTempC;
                row.batteryPowerKw = station.batteryPowerKw;
                row.pvPowerKw = station.pvPowerKw;
                row.gridPowerKw = station.gridPowerKw;
                row.loadPowerKw = station.loadPowerKw;
                row.irradianceWm2 = bestWeather.irradianceWm2;
                row.temperature2m = bestWeather.temperature2m;
                row.cloudCover = bestWeather.cloudCover;
                
                // Calculate solar elevation
                float solarElev = calculateSolarElevation(lat, lon, row.time);
                row.solarElev = Math.max(-5f, Math.min(90f, solarElev));
                row.solarElevNorm = Math.max(0f, row.solarElev) / 90f;
                
                aligned.add(row);
            }
        }
        
        // Clean SoC
        for (int i = 0; i < aligned.size(); i++) {
            AlignedRow row = aligned.get(i);
            row.socClean = Math.max(0f, Math.min(100f, row.batterySocRaw));
        }
        
        // Forward fill and back fill SoC
        for (int i = 1; i < aligned.size(); i++) {
            if (aligned.get(i).socClean == 0 && aligned.get(i - 1).socClean > 0) {
                aligned.get(i).socClean = aligned.get(i - 1).socClean;
            }
        }
        for (int i = aligned.size() - 2; i >= 0; i--) {
            if (aligned.get(i).socClean == 0 && aligned.get(i + 1).socClean > 0) {
                aligned.get(i).socClean = aligned.get(i + 1).socClean;
            }
        }
        
        return aligned;
    }
    
    /**
     * Calculate solar elevation angle
     */
    private float calculateSolarElevation(double lat, double lon, LocalDateTime time) {
        double latRad = Math.toRadians(lat);
        int dayOfYear = time.getDayOfYear();
        double declination = 23.45 * Math.sin(Math.toRadians(360.0 * (284 + dayOfYear) / 365.0));
        double declRad = Math.toRadians(declination);
        int hour = time.getHour();
        int minute = time.getMinute();
        double solarTime = hour + minute / 60.0;
        double hourAngle = 15.0 * (solarTime - 12.0);
        double hourAngleRad = Math.toRadians(hourAngle);
        double sinElev = Math.sin(latRad) * Math.sin(declRad) + 
                         Math.cos(latRad) * Math.cos(declRad) * Math.cos(hourAngleRad);
        double elevRad = Math.asin(Math.max(-1.0, Math.min(1.0, sinElev)));
        return (float) Math.toDegrees(elevRad);
    }
    
    /**
     * Group aligned data by date
     */
    private Map<LocalDate, List<AlignedRow>> groupByDate(List<AlignedRow> alignedData) {
        Map<LocalDate, List<AlignedRow>> byDate = new HashMap<>();
        for (AlignedRow row : alignedData) {
            LocalDate date = row.time.toLocalDate();
            if (!byDate.containsKey(date)) {
                byDate.put(date, new ArrayList<>());
            }
            byDate.get(date).add(row);
        }
        return byDate;
    }
    
    /**
     * Build sequences for inference
     */
    private float[][][] buildSequences(List<LocalDate> dates, 
                                       Map<LocalDate, List<AlignedRow>> dataByDate,
                                       List<String> featureNames,
                                       int maxTimesteps,
                                       double[] meanValues,
                                       double[] scaleValues) {
        int nDays = dates.size();
        int nFeatures = featureNames.size();
        
        // Initialize sequences: [n_days, max_timesteps, n_features + 1]
        // Last channel is is_valid mask
        float[][][] sequences = new float[nDays][maxTimesteps][nFeatures + 1];
        
        for (int dayIdx = 0; dayIdx < nDays; dayIdx++) {
            LocalDate date = dates.get(dayIdx);
            List<AlignedRow> dayData = dataByDate.get(date);
            
            if (dayData == null || dayData.isEmpty()) {
                // No data for this day - all invalid
                continue;
            }
            
            // Sort by time
            dayData.sort((a, b) -> a.time.compareTo(b.time));
            
            int nSteps = Math.min(dayData.size(), maxTimesteps);
            
            for (int t = 0; t < nSteps; t++) {
                AlignedRow row = dayData.get(t);
                
                // Build features
                for (int f = 0; f < nFeatures; f++) {
                    String featureName = featureNames.get(f);
                    float value = getFeatureValue(row, featureName);
                    sequences[dayIdx][t][f] = value;
                }
                
                // Mark as valid
                sequences[dayIdx][t][nFeatures] = 1.0f;
            }
        }
        
        // Normalize features (except is_valid channel)
        for (int dayIdx = 0; dayIdx < nDays; dayIdx++) {
            for (int t = 0; t < maxTimesteps; t++) {
                if (sequences[dayIdx][t][nFeatures] > 0.5f) {
                    for (int f = 0; f < nFeatures; f++) {
                        if (scaleValues[f] != 0) {
                            sequences[dayIdx][t][f] = (float) 
                                ((sequences[dayIdx][t][f] - meanValues[f]) / scaleValues[f]);
                        }
                    }
                }
            }
        }
        
        return sequences;
    }
    
    /**
     * Get feature value from aligned row
     */
    private float getFeatureValue(AlignedRow row, String featureName) {
        switch (featureName) {
            case "soc_clean":
                return row.socClean;
            case "battery_power_kw":
                return row.batteryPowerKw;
            case "batt_temp_c":
                return row.battTempC;
            case "pv_power_kw":
                return row.pvPowerKw;
            case "grid_power_kw":
                return row.gridPowerKw;
            case "load_power_kw":
                return row.loadPowerKw;
            default:
                return 0f;
        }
    }
    
    /**
     * Load scaler JSON from assets
     */
    private JSONObject loadScalerJson() throws Exception {
        try (java.io.InputStream is = context.getAssets().open("models/battery/scaler.json");
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return new JSONObject(builder.toString());
        }
    }
    
    /**
     * Parse float from string
     */
    private float parseFloat(String str, float defaultValue) {
        if (str == null || str.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(str.trim().replace(",", "."));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    /**
     * Station data row
     */
    private static class StationDataRow {
        LocalDateTime time;
        float batterySocRaw;
        float battTempC;
        float batteryPowerKw;
        float pvPowerKw;
        float gridPowerKw;
        float loadPowerKw;
    }
    
    /**
     * Weather data row
     */
    private static class WeatherDataRow {
        LocalDateTime time;
        float irradianceWm2;
        float temperature2m;
        float cloudCover;
    }
    
    /**
     * Aligned data row
     */
    private static class AlignedRow {
        LocalDateTime time;
        float batterySocRaw;
        float socClean;
        float battTempC;
        float batteryPowerKw;
        float pvPowerKw;
        float gridPowerKw;
        float loadPowerKw;
        float irradianceWm2;
        float temperature2m;
        float cloudCover;
        float solarElev;
        float solarElevNorm;
    }
}

