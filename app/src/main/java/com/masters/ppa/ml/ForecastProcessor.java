package com.masters.ppa.ml;

import android.content.Context;
import android.util.Log;

import com.masters.ppa.data.model.StationConfig;
import com.masters.ppa.data.repository.StationConfigRepository;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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
 * Processor for preparing weather data and running forecast predictions
 * Implements the same logic as Python forecast code
 */
public class ForecastProcessor {
    
    private static final String TAG = "ForecastProcessor";
    
    private final Context context;
    private final ModelLoader modelLoader;
    private final StationConfigRepository stationConfigRepository;
    
    public interface ProgressCallback {
        void onProgress(String message);
    }
    
    private ProgressCallback progressCallback;
    
    public ForecastProcessor(Context context) {
        this.context = context.getApplicationContext();
        this.modelLoader = ModelLoader.getInstance(context);
        this.stationConfigRepository = new StationConfigRepository(context);
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
     * Result class for forecast predictions
     */
    public static class ForecastResult {
        public final List<Float> predictedPowerW;
        public final List<LocalDateTime> timestamps;
        public final List<LocalDate> dates;
        public final List<Float> temperatures;
        public final List<Float> cloudCovers;
        public final List<Float> irradiances;
        public final boolean operationalDataFound;
        public final boolean calibrationPerformed;
        
        public ForecastResult(List<Float> predictedPowerW, List<LocalDateTime> timestamps, 
                             List<LocalDate> dates, List<Float> temperatures, 
                             List<Float> cloudCovers, List<Float> irradiances,
                             boolean operationalDataFound, boolean calibrationPerformed) {
            this.predictedPowerW = predictedPowerW;
            this.timestamps = timestamps;
            this.dates = dates;
            this.temperatures = temperatures;
            this.cloudCovers = cloudCovers;
            this.irradiances = irradiances;
            this.operationalDataFound = operationalDataFound;
            this.calibrationPerformed = calibrationPerformed;
        }
    }
    
    /**
     * Result class for linear calibration (y = a*x + b)
     */
    private static class CalibrationResult {
        final float a; // slope
        final float b; // intercept
        
        CalibrationResult(float a, float b) {
            this.a = a;
            this.b = b;
        }
    }
    
    /**
     * Historical data row for calibration
     */
    private static class HistoricalRow {
        LocalDateTime time;
        float powerKw;
        float batterySoc;
        float batteryPower;
        float gridPower;
        float loadPower;
        float temperature2m;
        float cloudCover;
        float irradianceWm2;
        float solarElevNorm;
        float hourSin;
        float hourCos;
        float daySin;
        float dayCos;
        float effectiveIrradiance;
        float irradianceSq;
        float tempSq;
        float hourSinIrr;
        
        // Lag features
        float powerKwLag1 = 0f;
        float batterySocLag1 = 0f;
        float batteryPowerLag1 = 0f;
        float gridPowerLag1 = 0f;
        float loadPowerLag1 = 0f;
    }
    
    /**
     * Weather data row with operational features
     */
    private static class WeatherRow {
        LocalDateTime time;
        float temperature2m;
        float cloudCover;
        float irradianceWm2;
        float windSpeed10m;
        float solarElev;
        float solarElevNorm;
        
        // Operational features
        float batterySoc = 0f;
        float batteryPower = 0f;
        float gridPower = 0f;
        float loadPower = 0f;
        float powerKw = 0f;
        
        // Lag features
        float batterySocLag1 = 0f;
        float batteryPowerLag1 = 0f;
        float gridPowerLag1 = 0f;
        float loadPowerLag1 = 0f;
        float powerKwLag1 = 0f;
        
        WeatherRow(LocalDateTime time, float temperature2m, float cloudCover, 
                   float irradianceWm2, float windSpeed10m) {
            this.time = time;
            this.temperature2m = temperature2m;
            this.cloudCover = cloudCover;
            this.irradianceWm2 = irradianceWm2;
            this.windSpeed10m = windSpeed10m;
        }
    }
    
    /**
     * Run forecast prediction (using weather data from API)
     * @return ForecastResult with predictions
     */
    public ForecastResult runForecast() throws Exception {
        reportProgress("Initializing forecast...");
        
        reportProgress("Loading model...");
        if (!modelLoader.loadModel()) {
            throw new Exception("Failed to load model");
        }
        
        reportProgress("Loading weather data...");
        List<WeatherRow> weatherRows = loadWeatherData();
        reportProgress("Loaded " + weatherRows.size() + " weather rows from CSV");
        if (weatherRows.isEmpty()) {
            reportProgress("Weather data file not found. Attempting to fetch from API...");
            StationConfig config = stationConfigRepository.getStationConfigSync();
            if (config == null) {
                throw new Exception("Station configuration not found. Cannot fetch weather data.");
            }
            
            try {
                fetchWeatherDataSync(config.getLatitude(), config.getLongitude());
                weatherRows = loadWeatherData();
                reportProgress("Loaded " + weatherRows.size() + " weather rows from CSV after fetch");
                if (weatherRows.isEmpty()) {
                    throw new Exception("Failed to fetch weather data. Please check your internet connection and try again.");
                }
            } catch (Exception e) {
                throw new Exception("No weather data found and failed to fetch from API: " + e.getMessage() + 
                    ". Please go to Weather page and click Refresh button to fetch weather forecast first.");
            }
        }
        
        reportProgress("Loading station configuration...");
        StationConfig config = stationConfigRepository.getStationConfigSync();
        if (config == null) {
            throw new Exception("Station configuration not found");
        }
        
        reportProgress("Adding solar geometry...");
        addSolarGeometry(weatherRows, config.getLatitude(), config.getLongitude());
        
        reportProgress("Loading operational data...");
        boolean operationalDataFound = loadOperationalData(weatherRows);
        reportProgress("Adding lag features...");
        addLags(weatherRows);
        
        reportProgress("Preparing features...");
        List<Float> predictionsW = new ArrayList<>();
        List<LocalDateTime> timestamps = new ArrayList<>();
        List<LocalDate> dates = new ArrayList<>();
        
        List<String> featureNames = modelLoader.getFeatures();
        Map<String, Integer> featureIndexMap = new HashMap<>();
        for (int i = 0; i < featureNames.size(); i++) {
            featureIndexMap.put(featureNames.get(i), i);
        }
        
        float capKw = getPowerCapKw(config);
        float performanceRatio = getPerformanceRatio(config);
        modelLoader.setConfig(capKw);
        
        reportProgress("Computing calibration...");
        CalibrationResult calibration = computeLinearCalibration(
            modelLoader, featureNames, config, capKw, performanceRatio);
        boolean calibrationPerformed = (calibration.a != 1.0f || calibration.b != 0.0f);
        Log.d(TAG, String.format(Locale.US, "Calibration: a=%.3f, b=%.3f, performed=%b", 
            calibration.a, calibration.b, calibrationPerformed));
        
        reportProgress("Running predictions...");
        List<Float> temperatures = new ArrayList<>();
        List<Float> cloudCovers = new ArrayList<>();
        List<Float> irradiances = new ArrayList<>();
        double[] meanValues = modelLoader.getMeanValues();
        
        int totalRows = weatherRows.size();
        int processedRows = 0;
        for (WeatherRow row : weatherRows) {
            processedRows++;
            if (processedRows % 10 == 0 || processedRows == totalRows) {
                reportProgress(String.format("Processing predictions: %d/%d", processedRows, totalRows));
            }
            float[] features = createFeatures(row, featureIndexMap, featureNames, meanValues);
            float predKw = modelLoader.getRawModelPrediction(features) / 1000f;
            predKw = Math.max(0f, predKw);
            
            float fade = Math.max(0f, Math.min(1f,
                (row.irradianceWm2 / 800f) * (1 - row.cloudCover / 300f) + 
                row.solarElevNorm * 0.3f));
            predKw *= fade;
            
            if (performanceRatio > 0) {
                predKw /= performanceRatio;
            }
            
            if (capKw > 0) {
                predKw = Math.max(0f, Math.min(predKw, capKw));
            }
            
            predKw = calibration.a * predKw;
            predKw = Math.max(0f, predKw);
            
            if (capKw > 0) {
                predKw = Math.min(predKw, capKw);
            }
            
            predictionsW.add(predKw * 1000f);
            timestamps.add(row.time);
            dates.add(row.time.toLocalDate());
            temperatures.add(row.temperature2m);
            cloudCovers.add(row.cloudCover);
            irradiances.add(row.irradianceWm2);
        }
        
        reportProgress("Forecast completed successfully");
        Log.d(TAG, "Normal forecast completed: " + predictionsW.size() + " predictions");
        return new ForecastResult(predictionsW, timestamps, dates, temperatures, cloudCovers, irradiances, operationalDataFound, calibrationPerformed);
    }
    
    /**
     * Load weather data from API file
     */
    private List<WeatherRow> loadWeatherData() throws Exception {
        List<WeatherRow> rows = new ArrayList<>();
        
        java.io.File weatherFile = new java.io.File(
            context.getFilesDir(), 
            "csv/weather/weather_next_7days.csv"
        );
        
        if (!weatherFile.exists()) {
            weatherFile = new java.io.File(
                context.getFilesDir(), 
                "csv/weather_next_7days.csv"
            );
        }
        
        if (!weatherFile.exists()) {
            Log.w(TAG, "Weather file not found. Tried:");
            Log.w(TAG, "  - " + new java.io.File(context.getFilesDir(), "csv/weather/weather_next_7days.csv").getAbsolutePath());
            Log.w(TAG, "  - " + new java.io.File(context.getFilesDir(), "csv/weather_next_7days.csv").getAbsolutePath());
            return rows;
        }
        
        Log.d(TAG, "Loading weather data from: " + weatherFile.getAbsolutePath());
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(
            new java.io.FileInputStream(weatherFile)
        ));
        
        // Read header
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
        int lineNumber = 0;
        int parsedCount = 0;
        int errorCount = 0;
        
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            String[] parts = line.split(",");
            if (parts.length < 4) {
                Log.w(TAG, "Skipping row " + lineNumber + ": too few columns (" + parts.length + ")");
                continue;
            }
            
            try {
                // Parse time
                int timeIdx = colIndex.getOrDefault("time", -1);
                if (timeIdx < 0 || timeIdx >= parts.length) {
                    Log.w(TAG, "Skipping row " + lineNumber + ": time column not found");
                    continue;
                }
                
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
                            // Try next format
                        }
                    }
                    if (time == null) {
                        Log.w(TAG, "Skipping row " + lineNumber + ": cannot parse time: " + timeStr);
                        errorCount++;
                        continue;
                    }
                }
                
                // Parse temperature_2m
                int tempIdx = colIndex.getOrDefault("temperature_2m", -1);
                float temp = (tempIdx >= 0 && tempIdx < parts.length) ? 
                    parseFloat(parts[tempIdx], 0f) : 0f;
                
                // Parse cloud_cover
                int cloudIdx = colIndex.getOrDefault("cloud_cover", -1);
                float cloud = (cloudIdx >= 0 && cloudIdx < parts.length) ? 
                    parseFloat(parts[cloudIdx], 0f) : 0f;
                
                // Parse shortwave_radiation
                int irrIdx = colIndex.getOrDefault("shortwave_radiation", -1);
                float irr = (irrIdx >= 0 && irrIdx < parts.length) ? 
                    parseFloat(parts[irrIdx], 0f) : 0f;
                
                // Parse wind_speed_10m
                int windIdx = colIndex.getOrDefault("wind_speed_10m", -1);
                float wind = (windIdx >= 0 && windIdx < parts.length) ? 
                    parseFloat(parts[windIdx], 0f) : 0f;
                
                rows.add(new WeatherRow(time, temp, cloud, irr, wind));
                parsedCount++;
            } catch (Exception e) {
                Log.w(TAG, "Error parsing row " + lineNumber + ": " + line, e);
                errorCount++;
            }
        }
        
        reader.close();
        Log.d(TAG, "Loaded " + parsedCount + " weather rows (errors: " + errorCount + ", total lines: " + lineNumber + ")");
        return rows;
    }
    
    /**
     * Add solar geometry (elevation angle) to weather rows
     */
    private void addSolarGeometry(List<WeatherRow> rows, double lat, double lon) {
        for (WeatherRow row : rows) {
            // Calculate solar elevation angle
            float solarElev = calculateSolarElevation(lat, lon, row.time);
            row.solarElev = Math.max(-5f, Math.min(90f, solarElev));
            row.solarElevNorm = Math.max(0f, row.solarElev) / 90f;
        }
    }
    
    /**
     * Calculate solar elevation angle
     */
    private float calculateSolarElevation(double lat, double lon, LocalDateTime time) {
        // Convert to radians
        double latRad = Math.toRadians(lat);
        
        // Calculate day of year
        int dayOfYear = time.getDayOfYear();
        
        // Calculate declination angle (simplified)
        double declination = 23.45 * Math.sin(Math.toRadians(360.0 * (284 + dayOfYear) / 365.0));
        double declRad = Math.toRadians(declination);
        
        // Calculate hour angle
        int hour = time.getHour();
        int minute = time.getMinute();
        double solarTime = hour + minute / 60.0;
        double hourAngle = 15.0 * (solarTime - 12.0);
        double hourAngleRad = Math.toRadians(hourAngle);
        
        // Calculate solar elevation
        double sinElev = Math.sin(latRad) * Math.sin(declRad) + 
                         Math.cos(latRad) * Math.cos(declRad) * Math.cos(hourAngleRad);
        double elevRad = Math.asin(Math.max(-1.0, Math.min(1.0, sinElev)));
        
        return (float) Math.toDegrees(elevRad);
    }
    
    /**
     * Load operational data for weather rows
     * @param weatherRows List of weather rows to populate
     * @return true if operational data was found and loaded, false otherwise
     */
    private boolean loadOperationalData(List<WeatherRow> weatherRows) {
        // Try to load from Solarman CSV file (primary source)
        try {
            loadOperationalDataFromCsv(weatherRows);
            // Check if any data was actually loaded
            boolean hasData = false;
            for (WeatherRow row : weatherRows) {
                if (row.batterySoc != 0 || row.powerKw != 0) {
                    hasData = true;
                    break;
                }
            }
            if (hasData) {
                return true;
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not load operational data from CSV, using defaults", e);
        }
        
        Log.d(TAG, "Using default operational values (0) for all rows");
        return false;
    }
    
    /**
     * Load operational data from Solarman CSV file
     */
    private void loadOperationalDataFromCsv(List<WeatherRow> weatherRows) throws Exception {
        // First, try to find station_data.csv (new format)
        java.io.File stationDataFile = new java.io.File(
            context.getFilesDir(), 
            "csv/station_data.csv"
        );
        
        if (stationDataFile.exists()) {
            Log.d(TAG, "Loading operational data from station_data.csv");
            loadOperationalDataFromStationCsv(weatherRows, stationDataFile);
            return;
        }
        
        // Fallback to old format
        java.io.File solarmanFile = new java.io.File(
            context.getFilesDir(), 
            "csv/solarman/solarman_weather_range.csv"
        );
        
        if (!solarmanFile.exists()) {
            throw new Exception("Station data CSV file not found");
        }
        
        Log.d(TAG, "Loading operational data from solarman_weather_range.csv (fallback)");
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(
            new java.io.FileInputStream(solarmanFile)
        ));
        
        // Read header
        String header = reader.readLine();
        if (header == null) {
            reader.close();
            return;
        }
        
        String[] headerCols = header.split(",");
        Map<String, Integer> colIndex = new HashMap<>();
        for (int i = 0; i < headerCols.length; i++) {
            colIndex.put(headerCols[i].trim().toLowerCase(), i);
        }
        
        // Find column indices
        int idxTime = colIndex.getOrDefault("collecttime", -1);
        int idxSoc = colIndex.getOrDefault("soc_bap2", -1);
        int idxBatteryPower = colIndex.getOrDefault("p_bap2", -1);
        int idxPcc1 = colIndex.getOrDefault("pcc_ap1", -1);
        int idxPcc2 = colIndex.getOrDefault("pcc_ap2", -1);
        int idxPcc3 = colIndex.getOrDefault("pcc_ap3", -1);
        int idxAp1 = colIndex.getOrDefault("ap1", -1);
        int idxAp2 = colIndex.getOrDefault("ap2", -1);
        int idxAp3 = colIndex.getOrDefault("ap3", -1);
        int idxPvtp = colIndex.getOrDefault("pvtp", -1);
        
        // Read all rows and store by time
        Map<LocalDateTime, float[]> opsDataMap = new HashMap<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        
        String line;
        while ((line = reader.readLine()) != null) {
            String[] parts = line.split(",");
            if (parts.length < 3) continue;
            
            try {
                if (idxTime < 0 || idxTime >= parts.length) continue;
                
                String timeStr = parts[idxTime].trim();
                LocalDateTime time = LocalDateTime.parse(timeStr, formatter);
                
                float soc = (idxSoc >= 0 && idxSoc < parts.length) ? 
                    parseFloat(parts[idxSoc], 0f) : 0f;
                float batteryPower = (idxBatteryPower >= 0 && idxBatteryPower < parts.length) ? 
                    parseFloat(parts[idxBatteryPower], 0f) : 0f;
                
                float grid1 = (idxPcc1 >= 0 && idxPcc1 < parts.length) ? 
                    parseFloat(parts[idxPcc1], 0f) : 0f;
                float grid2 = (idxPcc2 >= 0 && idxPcc2 < parts.length) ? 
                    parseFloat(parts[idxPcc2], 0f) : 0f;
                float grid3 = (idxPcc3 >= 0 && idxPcc3 < parts.length) ? 
                    parseFloat(parts[idxPcc3], 0f) : 0f;
                float gridPower = grid1 + grid2 + grid3;
                
                float load1 = (idxAp1 >= 0 && idxAp1 < parts.length) ? 
                    parseFloat(parts[idxAp1], 0f) : 0f;
                float load2 = (idxAp2 >= 0 && idxAp2 < parts.length) ? 
                    parseFloat(parts[idxAp2], 0f) : 0f;
                float load3 = (idxAp3 >= 0 && idxAp3 < parts.length) ? 
                    parseFloat(parts[idxAp3], 0f) : 0f;
                float loadPower = load1 + load2 + load3;
                
                // Extract power from PVTP (similar to extract_power_kw in Python)
                float powerKw = 0f;
                if (idxPvtp >= 0 && idxPvtp < parts.length) {
                    String pvtpStr = parts[idxPvtp].trim().replace(",", ".");
                    java.util.regex.Pattern numPattern = java.util.regex.Pattern.compile(
                        "([-+]?\\d*\\.?\\d+(?:[eE][-+]?\\d+)?)"
                    );
                    java.util.regex.Matcher matcher = numPattern.matcher(pvtpStr);
                    if (matcher.find()) {
                        powerKw = parseFloat(matcher.group(1), 0f) / 1000f; // Convert W to kW
                    }
                }
                
                opsDataMap.put(time, new float[]{soc, batteryPower, gridPower, loadPower, powerKw});
            } catch (Exception e) {
                Log.w(TAG, "Error parsing Solarman row: " + line, e);
            }
        }
        
        reader.close();
        
        // Match operational data to weather rows (backward merge, tolerance 2h)
        matchOperationalDataToWeatherRows(weatherRows, opsDataMap);
    }
    
    /**
     * Load operational data from station_data.csv (new format)
     */
    private void loadOperationalDataFromStationCsv(List<WeatherRow> weatherRows, java.io.File csvFile) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
            new java.io.FileInputStream(csvFile)
        ));
        
        // Read header
        String header = reader.readLine();
        if (header == null) {
            reader.close();
            return;
        }
        
        String[] headerCols = header.split(",");
        Map<String, Integer> colIndex = new HashMap<>();
        for (int i = 0; i < headerCols.length; i++) {
            colIndex.put(headerCols[i].trim().toLowerCase(), i);
        }
        
        // Find column indices (case-insensitive)
        int idxTime = colIndex.getOrDefault("collecttime", -1);
        int idxSoc = colIndex.getOrDefault("soc_bap2", -1);
        int idxBatteryPower = colIndex.getOrDefault("p_bap2", -1);
        int idxPcc1 = colIndex.getOrDefault("pcc_ap1", -1);
        int idxPcc2 = colIndex.getOrDefault("pcc_ap2", -1);
        int idxPcc3 = colIndex.getOrDefault("pcc_ap3", -1);
        int idxAp1 = colIndex.getOrDefault("ap1", -1);
        int idxAp2 = colIndex.getOrDefault("ap2", -1);
        int idxAp3 = colIndex.getOrDefault("ap3", -1);
        int idxPvtp = colIndex.getOrDefault("pvtp", -1);
        
        // Read all rows and store by time
        Map<LocalDateTime, float[]> opsDataMap = new HashMap<>();
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
                if (idxTime < 0 || idxTime >= parts.length) continue;
                
                String timeStr = parts[idxTime].trim();
                LocalDateTime time = null;
                for (DateTimeFormatter formatter : formatters) {
                    try {
                        time = LocalDateTime.parse(timeStr, formatter);
                        break;
                    } catch (Exception e) {
                        // Try next format
                    }
                }
                if (time == null) continue;
                
                float soc = (idxSoc >= 0 && idxSoc < parts.length) ? 
                    parseFloat(parts[idxSoc], 0f) : 0f;
                float batteryPower = (idxBatteryPower >= 0 && idxBatteryPower < parts.length) ? 
                    parseFloat(parts[idxBatteryPower], 0f) : 0f;
                
                float grid1 = (idxPcc1 >= 0 && idxPcc1 < parts.length) ? 
                    parseFloat(parts[idxPcc1], 0f) : 0f;
                float grid2 = (idxPcc2 >= 0 && idxPcc2 < parts.length) ? 
                    parseFloat(parts[idxPcc2], 0f) : 0f;
                float grid3 = (idxPcc3 >= 0 && idxPcc3 < parts.length) ? 
                    parseFloat(parts[idxPcc3], 0f) : 0f;
                float gridPower = grid1 + grid2 + grid3;
                
                float load1 = (idxAp1 >= 0 && idxAp1 < parts.length) ? 
                    parseFloat(parts[idxAp1], 0f) : 0f;
                float load2 = (idxAp2 >= 0 && idxAp2 < parts.length) ? 
                    parseFloat(parts[idxAp2], 0f) : 0f;
                float load3 = (idxAp3 >= 0 && idxAp3 < parts.length) ? 
                    parseFloat(parts[idxAp3], 0f) : 0f;
                float loadPower = load1 + load2 + load3;
                
                // Extract power from PVTP (similar to extract_power_kw in Python)
                float powerKw = 0f;
                if (idxPvtp >= 0 && idxPvtp < parts.length) {
                    String pvtpStr = parts[idxPvtp].trim().replace(",", ".");
                    java.util.regex.Pattern numPattern = java.util.regex.Pattern.compile(
                        "([-+]?\\d*\\.?\\d+(?:[eE][-+]?\\d+)?)"
                    );
                    java.util.regex.Matcher matcher = numPattern.matcher(pvtpStr);
                    if (matcher.find()) {
                        powerKw = parseFloat(matcher.group(1), 0f) / 1000f; // Convert W to kW
                    }
                }
                
                opsDataMap.put(time, new float[]{soc, batteryPower, gridPower, loadPower, powerKw});
            } catch (Exception e) {
                Log.w(TAG, "Error parsing station data row: " + line, e);
            }
        }
        
        reader.close();
        
        // Match operational data to weather rows (backward merge, tolerance 2h)
        matchOperationalDataToWeatherRows(weatherRows, opsDataMap);
    }
    
    /**
     * Match operational data to weather rows (backward merge, tolerance 2h)
     * Note: For forecast (future dates), this will typically find 0 matches since
     * station_data.csv contains historical data. This is expected and normal.
     */
    private void matchOperationalDataToWeatherRows(List<WeatherRow> weatherRows, Map<LocalDateTime, float[]> opsDataMap) {
        if (weatherRows.isEmpty() || opsDataMap.isEmpty()) {
            Log.d(TAG, "No weather rows or operational data to match");
            return;
        }
        
        // Get time range of weather rows
        LocalDateTime minWeatherTime = weatherRows.get(0).time;
        LocalDateTime maxWeatherTime = weatherRows.get(0).time;
        for (WeatherRow row : weatherRows) {
            if (row.time.isBefore(minWeatherTime)) minWeatherTime = row.time;
            if (row.time.isAfter(maxWeatherTime)) maxWeatherTime = row.time;
        }
        
        // Get time range of operational data
        LocalDateTime minOpsTime = opsDataMap.keySet().iterator().next();
        LocalDateTime maxOpsTime = opsDataMap.keySet().iterator().next();
        for (LocalDateTime opsTime : opsDataMap.keySet()) {
            if (opsTime.isBefore(minOpsTime)) minOpsTime = opsTime;
            if (opsTime.isAfter(maxOpsTime)) maxOpsTime = opsTime;
        }
        
        Log.d(TAG, "Matching operational data: weather range " + minWeatherTime + " to " + maxWeatherTime +
            ", ops range " + minOpsTime + " to " + maxOpsTime);
        
        for (WeatherRow row : weatherRows) {
            LocalDateTime bestTime = null;
            long minDiff = Long.MAX_VALUE;
            
            for (LocalDateTime csvTime : opsDataMap.keySet()) {
                long diff = Math.abs(java.time.Duration.between(row.time, csvTime).toMinutes());
                if (diff <= 120 && diff < minDiff) { // 2 hours tolerance
                    minDiff = diff;
                    bestTime = csvTime;
                }
            }
            
            if (bestTime != null) {
                float[] ops = opsDataMap.get(bestTime);
                row.batterySoc = ops[0];
                row.batteryPower = ops[1];
                row.gridPower = ops[2];
                row.loadPower = ops[3];
                row.powerKw = ops[4];
            }
        }
        
        // Count rows with operational data
        int countWithData = 0;
        for (WeatherRow row : weatherRows) {
            if (row.batterySoc != 0 || row.powerKw != 0) {
                countWithData++;
            }
        }
        Log.d(TAG, "Loaded operational data from CSV for " + countWithData + " weather rows");
        
        if (countWithData == 0 && !weatherRows.isEmpty()) {
            Log.d(TAG, "No operational data matched. This is normal for forecast (future dates) " +
                "since station_data.csv contains historical data. Operational data is used for " +
                "calibration, not for forecast predictions.");
        }
    }
    
    /**
     * Add lag features (shift by 1 step)
     */
    private void addLags(List<WeatherRow> weatherRows) {
        if (weatherRows.isEmpty()) return;
        
        // Sort by time
        weatherRows.sort((a, b) -> a.time.compareTo(b.time));
        
        // Add lags (previous row's values)
        for (int i = 1; i < weatherRows.size(); i++) {
            WeatherRow current = weatherRows.get(i);
            WeatherRow previous = weatherRows.get(i - 1);
            
            current.batterySocLag1 = previous.batterySoc;
            current.batteryPowerLag1 = previous.batteryPower;
            current.gridPowerLag1 = previous.gridPower;
            current.loadPowerLag1 = previous.loadPower;
            current.powerKwLag1 = previous.powerKw;
        }
        
        // First row has no lag, keep as 0
    }
    
    /**
     * Create feature array for a weather row
     * Fills missing features with mean values (as in Python prepare_future_features)
     */
    private float[] createFeatures(WeatherRow row, Map<String, Integer> featureIndexMap, 
                                   List<String> featureNames, double[] meanValues) {
        float[] features = new float[featureNames.size()];
        
        // Calculate derived features
        int hour = row.time.getHour();
        float hourSin = (float) Math.sin(2 * Math.PI * hour / 24.0);
        float hourCos = (float) Math.cos(2 * Math.PI * hour / 24.0);
        
        int dayOfYear = row.time.getDayOfYear();
        float daySin = (float) Math.sin(2 * Math.PI * dayOfYear / 365.0);
        float dayCos = (float) Math.cos(2 * Math.PI * dayOfYear / 365.0);
        
        float effectiveIrradiance = row.irradianceWm2 * (1 - row.cloudCover / 100f);
        float irradianceSq = row.irradianceWm2 * row.irradianceWm2;
        float tempSq = row.temperature2m * row.temperature2m;
        float hourSinIrr = hourSin * row.irradianceWm2;
        
        // Map features by name (as in Python prepare_future_features)
        for (int i = 0; i < featureNames.size(); i++) {
            String featureName = featureNames.get(i);
            float value = 0f;
            boolean found = false;
            
            switch (featureName) {
                case "hour":
                    value = hour;
                    found = true;
                    break;
                case "hour_sin":
                    value = hourSin;
                    found = true;
                    break;
                case "hour_cos":
                    value = hourCos;
                    found = true;
                    break;
                case "day_sin":
                    value = daySin;
                    found = true;
                    break;
                case "day_cos":
                    value = dayCos;
                    found = true;
                    break;
                case "temperature_2m":
                    value = row.temperature2m;
                    found = true;
                    break;
                case "cloud_cover":
                    value = row.cloudCover;
                    found = true;
                    break;
                case "irradiance_wm2":
                case "shortwave_radiation":
                    value = row.irradianceWm2;
                    found = true;
                    break;
                case "wind_speed_10m":
                    value = row.windSpeed10m;
                    found = true;
                    break;
                case "solar_elev":
                    value = row.solarElev;
                    found = true;
                    break;
                case "solar_elev_norm":
                    value = row.solarElevNorm;
                    found = true;
                    break;
                case "effective_irradiance":
                    value = effectiveIrradiance;
                    found = true;
                    break;
                case "irradiance_sq":
                    value = irradianceSq;
                    found = true;
                    break;
                case "temp_sq":
                    value = tempSq;
                    found = true;
                    break;
                case "hour_sin_irr":
                    value = hourSinIrr;
                    found = true;
                    break;
                // Operational features
                case "battery_soc":
                    value = row.batterySoc;
                    found = true;
                    break;
                case "battery_power":
                    value = row.batteryPower;
                    found = true;
                    break;
                case "grid_power":
                    value = row.gridPower;
                    found = true;
                    break;
                case "load_power":
                    value = row.loadPower;
                    found = true;
                    break;
                case "power_kw":
                    value = row.powerKw;
                    found = true;
                    break;
                // Lag features
                case "battery_soc_lag1":
                    value = row.batterySocLag1;
                    found = true;
                    break;
                case "battery_power_lag1":
                    value = row.batteryPowerLag1;
                    found = true;
                    break;
                case "grid_power_lag1":
                    value = row.gridPowerLag1;
                    found = true;
                    break;
                case "load_power_lag1":
                    value = row.loadPowerLag1;
                    found = true;
                    break;
                case "power_kw_lag1":
                    value = row.powerKwLag1;
                    found = true;
                    break;
            }
            
            // If feature not found, use mean value (as in Python: weather_df[feat] = mean[i])
            if (!found && i < meanValues.length) {
                value = (float) meanValues[i];
            }
            
            features[i] = value;
        }
        
        return features;
    }
    
    
    /**
     * Get power capacity in kW from station config
     */
    private float getPowerCapKw(StationConfig config) {
        if (config.getInverterPowerKw() > 0) {
            return (float) config.getInverterPowerKw();
        }
        return (float) (config.getPanelPowerW() * config.getPanelCount()) / 1000f;
    }
    
    /**
     * Get performance ratio from station config (default 0.8)
     */
    private float getPerformanceRatio(StationConfig config) {
        return 0.8f;
    }
    
    /**
     * Parse float from string with default value
     */
    private float parseFloat(String str, float defaultValue) {
        if (str == null || str.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(str.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    /**
     * Fetch weather data synchronously from Open-Meteo API
     * This is used when weather CSV file is not found
     */
    private void fetchWeatherDataSync(double lat, double lon) throws Exception {
        Log.d(TAG, "Fetching weather data synchronously from API...");
        
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate endDate = today.plusDays(6);
        
        String url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon +
                "&hourly=temperature_2m,cloud_cover,shortwave_radiation," +
                "direct_radiation,diffuse_radiation,wind_speed_10m" +
                "&start_date=" + today + "&end_date=" + endDate + "&timezone=auto";
        
        Log.d(TAG, "Fetching weather from: " + url);
        
        // Create HTTP connection
        HttpURLConnection connection = null;
        try {
            URL requestUrl = new URL(url);
            connection = (HttpURLConnection) requestUrl.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(20000);
            
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new Exception("HTTP error: " + responseCode);
            }
            
            // Read response
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }
            
            // Parse JSON
            JSONObject json = new JSONObject(response.toString());
            if (!json.has("hourly")) {
                throw new Exception("Invalid API response: no hourly data");
            }
            
            JSONObject hourly = json.getJSONObject("hourly");
            JSONArray times = hourly.getJSONArray("time");
            JSONArray temps = hourly.getJSONArray("temperature_2m");
            JSONArray clouds = hourly.getJSONArray("cloud_cover");
            JSONArray swr = hourly.getJSONArray("shortwave_radiation");
            JSONArray dr = hourly.getJSONArray("direct_radiation");
            JSONArray dif = hourly.getJSONArray("diffuse_radiation");
            JSONArray wind = hourly.getJSONArray("wind_speed_10m");
            
            // Create output directory
            File weatherDir = new File(context.getFilesDir(), "csv/weather");
            if (!weatherDir.exists()) {
                weatherDir.mkdirs();
            }
            
            // Create output file
            File outputFile = new File(weatherDir, "weather_next_7days.csv");
            
            // Write CSV file
            try (FileWriter writer = new FileWriter(outputFile, false)) {
                // Write header
                writer.write("time,temperature_2m,cloud_cover,shortwave_radiation,direct_radiation,diffuse_radiation,wind_speed_10m\n");
                
                // Write data rows
                for (int i = 0; i < times.length(); i++) {
                    String timestamp = times.getString(i);
                    double temp = temps.optDouble(i, Double.NaN);
                    double cloud = clouds.optDouble(i, Double.NaN);
                    double radiation = swr.optDouble(i, Double.NaN);
                    double direct = dr.optDouble(i, Double.NaN);
                    double diffuse = dif.optDouble(i, Double.NaN);
                    double windSpeed = wind.optDouble(i, Double.NaN);
                    
                    writer.write(timestamp + "," +
                            formatDouble(temp) + "," +
                            formatDouble(cloud) + "," +
                            formatDouble(radiation) + "," +
                            formatDouble(direct) + "," +
                            formatDouble(diffuse) + "," +
                            formatDouble(windSpeed) + "\n");
                }
            }
            
            Log.d(TAG, "Weather data saved to: " + outputFile.getAbsolutePath());
            
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    /**
     * Format double value for CSV output
     */
    private static String formatDouble(double value) {
        if (Double.isNaN(value)) {
            return "";
        }
        return String.format(Locale.US, "%.1f", value);
    }
    
    /**
     * Load historical Solarman data from station_data.csv
     */
    private List<HistoricalRow> loadSolarmanHist() throws Exception {
        List<HistoricalRow> rows = new ArrayList<>();
        
        java.io.File stationFile = new java.io.File(context.getFilesDir(), "csv/station_data.csv");
        if (!stationFile.exists()) {
            Log.w(TAG, "Station data CSV not found: " + stationFile.getAbsolutePath());
            return rows;
        }
        
        Log.d(TAG, "Loading historical Solarman data from: " + stationFile.getAbsolutePath());
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(
            new java.io.FileInputStream(stationFile)
        ));
        
        // Read header
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
        
        // Find column indices
        int idxTime = colIndex.getOrDefault("collecttime", -1);
        int idxPvtp = colIndex.getOrDefault("pvtp", -1);
        int idxSoc = colIndex.getOrDefault("soc_bap2", -1);
        int idxBatteryPower = colIndex.getOrDefault("p_bap2", -1);
        int idxPcc1 = colIndex.getOrDefault("pcc_ap1", -1);
        int idxPcc2 = colIndex.getOrDefault("pcc_ap2", -1);
        int idxPcc3 = colIndex.getOrDefault("pcc_ap3", -1);
        int idxAp1 = colIndex.getOrDefault("ap1", -1);
        int idxAp2 = colIndex.getOrDefault("ap2", -1);
        int idxAp3 = colIndex.getOrDefault("ap3", -1);
        
        Log.d(TAG, "Column indices - collectTime: " + idxTime + ", PVTP: " + idxPvtp + 
            ", SOC_BAP2: " + idxSoc + ", P_BAP2: " + idxBatteryPower);
        Log.d(TAG, "Available columns: " + String.join(", ", colIndex.keySet()));
        
        if (idxTime < 0 || idxPvtp < 0) {
            Log.w(TAG, "Required columns not found in station data CSV. collectTime: " + 
                idxTime + ", PVTP: " + idxPvtp);
            reader.close();
            return rows;
        }
        
        DateTimeFormatter[] formatters = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        };
        
        String line;
        int lineNumber = 0;
        int parsedCount = 0;
        int errorCount = 0;
        
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            String[] parts = line.split(",");
            if (parts.length < 3) {
                Log.w(TAG, "Skipping row " + lineNumber + ": too few columns (" + parts.length + ")");
                continue;
            }
            
            try {
                String timeStr = parts[idxTime].trim();
                if (timeStr.isEmpty()) {
                    Log.w(TAG, "Skipping row " + lineNumber + ": empty time");
                    continue;
                }
                
                LocalDateTime time = null;
                
                // Check if it's Unix timestamp (numeric string)
                if (timeStr.matches("\\d+")) {
                    try {
                        long timestamp = Long.parseLong(timeStr);
                        long millis;
                        if (timestamp < 2_000_000_000L) {
                            // Seconds
                            millis = timestamp * 1000L;
                        } else {
                            // Milliseconds
                            millis = timestamp;
                        }
                        time = LocalDateTime.ofEpochSecond(millis / 1000, 0, 
                            java.time.ZoneOffset.UTC);
                    } catch (Exception e) {
                        Log.w(TAG, "Error parsing Unix timestamp: " + timeStr, e);
                    }
                } else {
                    // Try date-time formats
                    for (DateTimeFormatter formatter : formatters) {
                        try {
                            time = LocalDateTime.parse(timeStr, formatter);
                            break;
                        } catch (Exception e) {
                            // Try next format
                        }
                    }
                }
                
                if (time == null) {
                    Log.w(TAG, "Skipping row " + lineNumber + ": cannot parse time: " + timeStr);
                    errorCount++;
                    continue;
                }
                
                HistoricalRow row = new HistoricalRow();
                row.time = time;
                
                // Extract power from PVTP
                float powerKw = 0f;
                if (idxPvtp >= 0 && idxPvtp < parts.length) {
                    String pvtpStr = parts[idxPvtp].trim().replace(",", ".");
                    java.util.regex.Pattern numPattern = java.util.regex.Pattern.compile(
                        "([-+]?\\d*\\.?\\d+(?:[eE][-+]?\\d+)?)"
                    );
                    java.util.regex.Matcher matcher = numPattern.matcher(pvtpStr);
                    if (matcher.find()) {
                        powerKw = parseFloat(matcher.group(1), 0f) / 1000f;
                    }
                }
                row.powerKw = Math.max(0f, powerKw);
                
                row.batterySoc = (idxSoc >= 0 && idxSoc < parts.length) ? 
                    parseFloat(parts[idxSoc], 0f) : 0f;
                row.batteryPower = (idxBatteryPower >= 0 && idxBatteryPower < parts.length) ? 
                    parseFloat(parts[idxBatteryPower], 0f) : 0f;
                
                float grid1 = (idxPcc1 >= 0 && idxPcc1 < parts.length) ? 
                    parseFloat(parts[idxPcc1], 0f) : 0f;
                float grid2 = (idxPcc2 >= 0 && idxPcc2 < parts.length) ? 
                    parseFloat(parts[idxPcc2], 0f) : 0f;
                float grid3 = (idxPcc3 >= 0 && idxPcc3 < parts.length) ? 
                    parseFloat(parts[idxPcc3], 0f) : 0f;
                row.gridPower = grid1 + grid2 + grid3;
                
                float load1 = (idxAp1 >= 0 && idxAp1 < parts.length) ? 
                    parseFloat(parts[idxAp1], 0f) : 0f;
                float load2 = (idxAp2 >= 0 && idxAp2 < parts.length) ? 
                    parseFloat(parts[idxAp2], 0f) : 0f;
                float load3 = (idxAp3 >= 0 && idxAp3 < parts.length) ? 
                    parseFloat(parts[idxAp3], 0f) : 0f;
                row.loadPower = load1 + load2 + load3;
                
                rows.add(row);
                parsedCount++;
            } catch (Exception e) {
                Log.w(TAG, "Error parsing historical row " + lineNumber + ": " + line, e);
                errorCount++;
            }
        }
        
        reader.close();
        Log.d(TAG, "Loaded " + rows.size() + " historical Solarman records (parsed: " + 
            parsedCount + ", errors: " + errorCount + ", total lines: " + lineNumber + ")");
        
        if (rows.isEmpty() && lineNumber > 0) {
            Log.w(TAG, "No records loaded. Checked " + lineNumber + " lines. " +
                "First few lines for debugging:");
            // Re-read first few lines for debugging
            try (BufferedReader debugReader = new BufferedReader(new InputStreamReader(
                new java.io.FileInputStream(stationFile)))) {
                debugReader.readLine(); // Skip header
                for (int i = 0; i < Math.min(5, lineNumber); i++) {
                    String debugLine = debugReader.readLine();
                    if (debugLine != null) {
                        Log.w(TAG, "  Line " + (i + 1) + ": " + 
                            (debugLine.length() > 100 ? debugLine.substring(0, 100) + "..." : debugLine));
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Error reading debug lines", e);
            }
        }
        
        return rows;
    }
    
    /**
     * Load historical weather data from weather_data.csv
     */
    private List<HistoricalRow> loadWeatherHist(StationConfig config) throws Exception {
        List<HistoricalRow> rows = new ArrayList<>();
        
        // Try multiple paths
        java.io.File weatherFile = new java.io.File(context.getFilesDir(), "csv/weather_data.csv");
        if (!weatherFile.exists()) {
            weatherFile = new java.io.File(context.getFilesDir(), "csv/weather/weather_last_max_period.csv");
        }
        
        if (!weatherFile.exists()) {
            Log.w(TAG, "Weather history CSV not found");
            return rows;
        }
        
        Log.d(TAG, "Loading historical weather data from: " + weatherFile.getAbsolutePath());
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(
            new java.io.FileInputStream(weatherFile)
        ));
        
        // Read header
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
                    DateTimeFormatter[] altFormatters = {
                        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    };
                    for (DateTimeFormatter altFormatter : altFormatters) {
                        try {
                            time = LocalDateTime.parse(timeStr, altFormatter);
                            break;
                        } catch (Exception e2) {
                            // Try next format
                        }
                    }
                    if (time == null) continue;
                }
                
                HistoricalRow row = new HistoricalRow();
                row.time = time;
                
                int tempIdx = colIndex.getOrDefault("temperature_2m", -1);
                row.temperature2m = (tempIdx >= 0 && tempIdx < parts.length) ? 
                    parseFloat(parts[tempIdx], 0f) : 0f;
                
                int cloudIdx = colIndex.getOrDefault("cloud_cover", -1);
                row.cloudCover = (cloudIdx >= 0 && cloudIdx < parts.length) ? 
                    parseFloat(parts[cloudIdx], 0f) : 0f;
                
                int irrIdx = colIndex.getOrDefault("shortwave_radiation", -1);
                if (irrIdx < 0) {
                    irrIdx = colIndex.getOrDefault("irradiance_wm2", -1);
                }
                row.irradianceWm2 = (irrIdx >= 0 && irrIdx < parts.length) ? 
                    parseFloat(parts[irrIdx], 0f) : 0f;
                
                // Add solar geometry
                float solarElev = calculateSolarElevation(
                    config.getLatitude(), config.getLongitude(), time);
                row.solarElevNorm = Math.max(0f, Math.max(-5f, Math.min(90f, solarElev))) / 90f;
                
                // Calculate derived features
                int hour = time.getHour();
                row.hourSin = (float) Math.sin(2 * Math.PI * hour / 24.0);
                row.hourCos = (float) Math.cos(2 * Math.PI * hour / 24.0);
                
                int dayOfYear = time.getDayOfYear();
                row.daySin = (float) Math.sin(2 * Math.PI * dayOfYear / 365.0);
                row.dayCos = (float) Math.cos(2 * Math.PI * dayOfYear / 365.0);
                
                row.effectiveIrradiance = row.irradianceWm2 * (1 - row.cloudCover / 100f);
                row.irradianceSq = row.irradianceWm2 * row.irradianceWm2;
                row.tempSq = row.temperature2m * row.temperature2m;
                row.hourSinIrr = row.hourSin * row.irradianceWm2;
                
                rows.add(row);
            } catch (Exception e) {
                Log.w(TAG, "Error parsing weather history row: " + line, e);
            }
        }
        
        reader.close();
        Log.d(TAG, "Loaded " + rows.size() + " historical weather records");
        return rows;
    }
    
    /**
     * Align historical Solarman and weather data
     */
    private List<HistoricalRow> alignHistData(List<HistoricalRow> solRows, List<HistoricalRow> weatherRows) {
        List<HistoricalRow> aligned = new ArrayList<>();
        
        // Sort both by time
        solRows.sort((a, b) -> a.time.compareTo(b.time));
        weatherRows.sort((a, b) -> a.time.compareTo(b.time));
        
        // Merge with 1 hour tolerance
        for (HistoricalRow solRow : solRows) {
            HistoricalRow bestWeather = null;
            long minDiff = Long.MAX_VALUE;
            
            for (HistoricalRow weatherRow : weatherRows) {
                long diff = Math.abs(java.time.Duration.between(solRow.time, weatherRow.time).toMinutes());
                if (diff <= 60 && diff < minDiff) {
                    minDiff = diff;
                    bestWeather = weatherRow;
                }
            }
            
            if (bestWeather != null && bestWeather.irradianceWm2 > 0 && 
                bestWeather.temperature2m != 0 && bestWeather.cloudCover >= 0) {
                // Merge data
                HistoricalRow merged = new HistoricalRow();
                merged.time = solRow.time;
                merged.powerKw = solRow.powerKw;
                merged.batterySoc = solRow.batterySoc;
                merged.batteryPower = solRow.batteryPower;
                merged.gridPower = solRow.gridPower;
                merged.loadPower = solRow.loadPower;
                merged.temperature2m = bestWeather.temperature2m;
                merged.cloudCover = bestWeather.cloudCover;
                merged.irradianceWm2 = bestWeather.irradianceWm2;
                merged.solarElevNorm = bestWeather.solarElevNorm;
                merged.hourSin = bestWeather.hourSin;
                merged.hourCos = bestWeather.hourCos;
                merged.daySin = bestWeather.daySin;
                merged.dayCos = bestWeather.dayCos;
                merged.effectiveIrradiance = bestWeather.effectiveIrradiance;
                merged.irradianceSq = bestWeather.irradianceSq;
                merged.tempSq = bestWeather.tempSq;
                merged.hourSinIrr = bestWeather.hourSinIrr;
                aligned.add(merged);
            }
        }
        
        // Add lags
        for (int i = 1; i < aligned.size(); i++) {
            HistoricalRow current = aligned.get(i);
            HistoricalRow previous = aligned.get(i - 1);
            current.powerKwLag1 = previous.powerKw;
            current.batterySocLag1 = previous.batterySoc;
            current.batteryPowerLag1 = previous.batteryPower;
            current.gridPowerLag1 = previous.gridPower;
            current.loadPowerLag1 = previous.loadPower;
        }
        
        Log.d(TAG, "Aligned " + aligned.size() + " historical records");
        return aligned;
    }
    
    /**
     * Compute linear calibration on historical data
     */
    private CalibrationResult computeLinearCalibration(
        ModelLoader modelLoader, List<String> features, StationConfig config,
        float capKw, float performanceRatio) {
        
        try {
            // Load historical data
            List<HistoricalRow> solRows = loadSolarmanHist();
            List<HistoricalRow> weatherRows = loadWeatherHist(config);
            
            if (solRows.isEmpty() || weatherRows.isEmpty()) {
                Log.w(TAG, "No history for calibration. Using identity (a=1, b=0).");
                return new CalibrationResult(1.0f, 0.0f);
            }
            
            // Align data
            List<HistoricalRow> aligned = alignHistData(solRows, weatherRows);
            if (aligned.isEmpty()) {
                Log.w(TAG, "No overlapping history. Using identity calibration.");
                return new CalibrationResult(1.0f, 0.0f);
            }
            
            // Create feature map
            Map<String, Integer> featureIndexMap = new HashMap<>();
            for (int i = 0; i < features.size(); i++) {
                featureIndexMap.put(features.get(i), i);
            }
            
            // Prepare features and predictions
            List<Float> yTrueList = new ArrayList<>();
            List<Float> yPredList = new ArrayList<>();
            List<Float> irrList = new ArrayList<>();
            
            // Get mean values for missing features
            double[] meanValues = modelLoader.getMeanValues();
            
            for (HistoricalRow row : aligned) {
                float[] featureArray = createHistoricalFeatures(row, featureIndexMap, features, meanValues);
                
                float predKw = modelLoader.getRawModelPrediction(featureArray) / 1000f;
                predKw = Math.max(0f, predKw);
                
                float fade = Math.max(0f, Math.min(1f, 
                    (row.irradianceWm2 / 800f) * (1 - row.cloudCover / 300f) + 
                    row.solarElevNorm * 0.3f));
                predKw *= fade;
                
                if (performanceRatio > 0) {
                    predKw /= performanceRatio;
                }
                
                if (capKw > 0) {
                    predKw = Math.min(predKw, capKw);
                }
                
                yPredList.add(predKw);
                yTrueList.add(row.powerKw);
                irrList.add(row.irradianceWm2);
            }
            
            // Filter by daylight (irr > 50 W/m²) and convert to daily energy
            Map<LocalDate, Float> dailyTrueKwh = new HashMap<>();
            Map<LocalDate, Float> dailyPredKwh = new HashMap<>();
            
            for (int i = 0; i < aligned.size(); i++) {
                if (irrList.get(i) > 50f) {
                    LocalDate date = aligned.get(i).time.toLocalDate();
                    float trueKwh = yTrueList.get(i) * (5f / 60f);
                    float predKwh = yPredList.get(i) * (5f / 60f);
                    dailyTrueKwh.put(date, dailyTrueKwh.getOrDefault(date, 0f) + trueKwh);
                    dailyPredKwh.put(date, dailyPredKwh.getOrDefault(date, 0f) + predKwh);
                }
            }
            
            // Filter days with positive true energy
            List<Float> dailyTrue = new ArrayList<>();
            List<Float> dailyPred = new ArrayList<>();
            for (LocalDate date : dailyTrueKwh.keySet()) {
                float trueKwh = dailyTrueKwh.get(date);
                if (trueKwh > 0) {
                    dailyTrue.add(trueKwh);
                    dailyPred.add(dailyPredKwh.get(date));
                }
            }
            
            if (dailyTrue.size() < 3) {
                Log.w(TAG, "Too few valid days for calibration. Using identity.");
                return new CalibrationResult(1.0f, 0.0f);
            }
            
            // Compute linear regression: y = a*x + b
            float xMean = 0f;
            float yMean = 0f;
            for (int i = 0; i < dailyPred.size(); i++) {
                xMean += dailyPred.get(i);
                yMean += dailyTrue.get(i);
            }
            xMean /= dailyPred.size();
            yMean /= dailyTrue.size();
            
            float xVar = 0f;
            float covXY = 0f;
            for (int i = 0; i < dailyPred.size(); i++) {
                float xDiff = dailyPred.get(i) - xMean;
                float yDiff = dailyTrue.get(i) - yMean;
                xVar += xDiff * xDiff;
                covXY += xDiff * yDiff;
            }
            xVar /= dailyPred.size();
            covXY /= dailyPred.size();
            
            if (xVar < 1e-8f) {
                Log.w(TAG, "Very low variance of predictions. Using identity.");
                return new CalibrationResult(1.0f, 0.0f);
            }
            
            float a = covXY / xVar;
            float b = yMean - a * xMean;
            
            // Calculate metrics
            float maeBefore = 0f;
            float rmseBefore = 0f;
            float maeAfter = 0f;
            float rmseAfter = 0f;
            
            for (int i = 0; i < dailyPred.size(); i++) {
                float pred = dailyPred.get(i);
                float trueVal = dailyTrue.get(i);
                float predCalib = a * pred + b;
                
                maeBefore += Math.abs(trueVal - pred);
                rmseBefore += (trueVal - pred) * (trueVal - pred);
                maeAfter += Math.abs(trueVal - predCalib);
                rmseAfter += (trueVal - predCalib) * (trueVal - predCalib);
            }
            
            maeBefore /= dailyPred.size();
            rmseBefore = (float) Math.sqrt(rmseBefore / dailyPred.size());
            maeAfter /= dailyPred.size();
            rmseAfter = (float) Math.sqrt(rmseAfter / dailyPred.size());
            
            Log.d(TAG, String.format(Locale.US, 
                "Calibration: a=%.3f, b=%.3f, MAE before=%.3f, after=%.3f, RMSE before=%.3f, after=%.3f",
                a, b, maeBefore, maeAfter, rmseBefore, rmseAfter));
            
            return new CalibrationResult(a, b);
            
        } catch (Exception e) {
            Log.e(TAG, "Error computing calibration", e);
            return new CalibrationResult(1.0f, 0.0f);
        }
    }
    
    /**
     * Create feature array for historical row
     * Fills missing features with mean values (as in Python)
     */
    private float[] createHistoricalFeatures(HistoricalRow row, Map<String, Integer> featureIndexMap, 
                                            List<String> features, double[] meanValues) {
        float[] featureArray = new float[features.size()];
        
        for (int i = 0; i < features.size(); i++) {
            String featureName = features.get(i);
            float value = 0f;
            boolean found = false;
            
            switch (featureName) {
                case "hour":
                    value = row.time.getHour();
                    found = true;
                    break;
                case "hour_sin":
                    value = row.hourSin;
                    found = true;
                    break;
                case "hour_cos":
                    value = row.hourCos;
                    found = true;
                    break;
                case "day_sin":
                    value = row.daySin;
                    found = true;
                    break;
                case "day_cos":
                    value = row.dayCos;
                    found = true;
                    break;
                case "temperature_2m":
                    value = row.temperature2m;
                    found = true;
                    break;
                case "cloud_cover":
                    value = row.cloudCover;
                    found = true;
                    break;
                case "irradiance_wm2":
                case "shortwave_radiation":
                    value = row.irradianceWm2;
                    found = true;
                    break;
                case "wind_speed_10m":
                    value = 0f; // Not in historical data
                    found = true;
                    break;
                case "solar_elev":
                    value = row.solarElevNorm * 90f;
                    found = true;
                    break;
                case "solar_elev_norm":
                    value = row.solarElevNorm;
                    found = true;
                    break;
                case "effective_irradiance":
                    value = row.effectiveIrradiance;
                    found = true;
                    break;
                case "irradiance_sq":
                    value = row.irradianceSq;
                    found = true;
                    break;
                case "temp_sq":
                    value = row.tempSq;
                    found = true;
                    break;
                case "hour_sin_irr":
                    value = row.hourSinIrr;
                    found = true;
                    break;
                case "battery_soc":
                    value = row.batterySoc;
                    found = true;
                    break;
                case "battery_power":
                    value = row.batteryPower;
                    found = true;
                    break;
                case "grid_power":
                    value = row.gridPower;
                    found = true;
                    break;
                case "load_power":
                    value = row.loadPower;
                    found = true;
                    break;
                case "power_kw":
                    value = row.powerKw;
                    found = true;
                    break;
                case "battery_soc_lag1":
                    value = row.batterySocLag1;
                    found = true;
                    break;
                case "battery_power_lag1":
                    value = row.batteryPowerLag1;
                    found = true;
                    break;
                case "grid_power_lag1":
                    value = row.gridPowerLag1;
                    found = true;
                    break;
                case "load_power_lag1":
                    value = row.loadPowerLag1;
                    found = true;
                    break;
                case "power_kw_lag1":
                    value = row.powerKwLag1;
                    found = true;
                    break;
            }
            
            if (!found && i < meanValues.length) {
                value = (float) meanValues[i];
            }
            
            featureArray[i] = value;
        }
        
        return featureArray;
    }
}

