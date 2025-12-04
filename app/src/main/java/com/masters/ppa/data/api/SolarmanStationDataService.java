package com.masters.ppa.data.api;

import android.content.Context;
import android.util.Log;

import com.masters.ppa.data.model.SolarmanApiConfig;
import com.masters.ppa.data.model.StationConfig;
import com.masters.ppa.data.repository.SolarmanApiConfigRepository;
import com.masters.ppa.data.repository.StationConfigRepository;
import com.masters.ppa.utils.NetworkUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Service for fetching station operational data from Solarman API
 */
public class SolarmanStationDataService {
    
    private static final String TAG = "SolarmanStationDataService";
    private static final String API_BASE = "https://globalapi.solarmanpv.com";
    
    public interface ProgressCallback {
        void onProgress(String message);
    }
    
    public interface FetchCallback {
        void onSuccess(String filePath, int rowCount, String firstTimestamp, String lastTimestamp);
        void onError(String errorMessage);
    }
    
    private final Context context;
    private final ExecutorService executor;
    private final SolarmanApiConfigRepository configRepository;
    private final StationConfigRepository stationConfigRepository;
    
    private String cachedAccessToken;
    private long tokenExpiryTime = 0;
    private ProgressCallback progressCallback;
    
    private static final String METADATA_FILE = "station_data_metadata.json";
    
    public SolarmanStationDataService(Context context) {
        this.context = context.getApplicationContext();
        this.executor = Executors.newSingleThreadExecutor();
        if (context instanceof android.app.Application) {
            this.configRepository = new SolarmanApiConfigRepository((android.app.Application) context);
            this.stationConfigRepository = new StationConfigRepository(context);
        } else {
            this.configRepository = new SolarmanApiConfigRepository((android.app.Application) this.context);
            this.stationConfigRepository = new StationConfigRepository(this.context);
        }
    }
    
    /**
     * Set progress callback for status updates
     */
    public void setProgressCallback(ProgressCallback callback) {
        this.progressCallback = callback;
    }
    
    /**
     * Get access token from Solarman API
     */
    private String getAccessToken() throws Exception {
        // Check if we have a valid cached token
        if (cachedAccessToken != null && System.currentTimeMillis() < tokenExpiryTime) {
            Log.d(TAG, "Using cached access token");
            return cachedAccessToken;
        }

        SolarmanApiConfig config = configRepository.getSolarmanApiConfigSync();
        if (config == null) {
            throw new Exception("Solarman API configuration not found. Please configure in Settings.");
        }

        // Validate required fields
        if (config.getAppId() == null || config.getAppId().isEmpty()) {
            throw new Exception("App ID is not configured");
        }
        if (config.getAppSecret() == null || config.getAppSecret().isEmpty()) {
            throw new Exception("App Secret is not configured");
        }
        if (config.getEmail() == null || config.getEmail().isEmpty()) {
            throw new Exception("Email is not configured");
        }
        if (config.getPassword() == null || config.getPassword().isEmpty()) {
            throw new Exception("Password is not configured");
        }

        String url = API_BASE + "/account/v1.0/token?appId=" + config.getAppId() + "&language=en";
        
        // Hash password with SHA-256
        String hashedPassword = hashPassword(config.getPassword());
        
        JSONObject payload = new JSONObject();
        payload.put("appSecret", config.getAppSecret());
        payload.put("email", config.getEmail());
        payload.put("password", hashedPassword);

        notifyProgress("Requesting access token...");
        Log.d(TAG, "Requesting access token from Solarman API...");

        HttpURLConnection connection = null;
        try {
            URL requestUrl = new URL(url);
            connection = (HttpURLConnection) requestUrl.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(20000);

            // Send request
            String payloadString = payload.toString();
            try (java.io.OutputStream os = connection.getOutputStream()) {
                byte[] input = payloadString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
                os.flush();
            }

            // Read response
            int responseCode = connection.getResponseCode();
            String responseBody = readResponse(connection, responseCode);

            JSONObject response = new JSONObject(responseBody);
            
            if (responseCode != 200 || !response.optBoolean("success", false)) {
                String errorMsg = response.optString("msg", "Authentication failed");
                throw new Exception("Authentication failed: " + errorMsg);
            }

            if (!response.has("access_token") || response.isNull("access_token")) {
                throw new Exception("No access_token in response");
            }

            String accessToken = response.getString("access_token");
            if (accessToken == null || accessToken.isEmpty()) {
                throw new Exception("Access token is empty");
            }
            
            cachedAccessToken = accessToken;
            // Token typically expires in 24 hours, cache for 23 hours
            tokenExpiryTime = System.currentTimeMillis() + (23 * 60 * 60 * 1000);
            
            notifyProgress("Access token received");
            Log.d(TAG, "Access token obtained successfully");
            return accessToken;

        } catch (JSONException e) {
            Log.e(TAG, "JSON parsing error", e);
            throw new Exception("Failed to parse authentication response: " + e.getMessage(), e);
        } catch (java.io.IOException e) {
            Log.e(TAG, "Network error during authentication", e);
            throw new Exception("Network error: " + e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    /**
     * Get device history data for a date range
     */
    private List<JSONObject> getDeviceHistory(String token, Date startDate, Date endDate, int timeType, int retries, int delayMs) throws Exception {
        SolarmanApiConfig config = configRepository.getSolarmanApiConfigSync();
        if (config == null) {
            throw new Exception("Solarman API configuration not found");
        }

        String url = API_BASE + "/device/v1.0/historical";
        
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        JSONObject payload = new JSONObject();
        payload.put("deviceId", config.getDeviceId());
        payload.put("deviceSn", config.getDeviceSn());
        payload.put("startTime", dateFormat.format(startDate));
        payload.put("endTime", dateFormat.format(endDate));
        payload.put("timeType", timeType);

        for (int attempt = 1; attempt <= retries; attempt++) {
            String attemptMsg = String.format(Locale.getDefault(), 
                "Requesting device history (timeType=%d) from %s to %s... [try %d/%d]", 
                timeType, dateFormat.format(startDate), dateFormat.format(endDate), attempt, retries);
            notifyProgress(attemptMsg);
            Log.d(TAG, attemptMsg);
            
            try {
                HttpURLConnection connection = null;
                try {
                    URL requestUrl = new URL(url);
                    connection = (HttpURLConnection) requestUrl.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Authorization", "Bearer " + token);
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.setDoOutput(true);
                    connection.setConnectTimeout(20000);
                    connection.setReadTimeout(20000);

                    // Send request
                    String payloadString = payload.toString();
                    try (java.io.OutputStream os = connection.getOutputStream()) {
                        byte[] input = payloadString.getBytes(StandardCharsets.UTF_8);
                        os.write(input, 0, input.length);
                        os.flush();
                    }

                    // Read response
                    int responseCode = connection.getResponseCode();
                    String responseBody = readResponse(connection, responseCode);

                    JSONObject response = new JSONObject(responseBody);

                    if (!response.optBoolean("success", false)) {
                        String errorMsg = response.optString("msg", "Request failed");
                        Log.w(TAG, "History request failed: " + errorMsg);
                        if (attempt == retries) {
                            throw new RuntimeException("API failed " + retries + "× for " + 
                                dateFormat.format(startDate) + "–" + dateFormat.format(endDate) + ": " + errorMsg);
                        }
                        Thread.sleep(delayMs);
                        continue;
                    }

                    // Success - return data
                    JSONArray paramDataList = response.optJSONArray("paramDataList");
                    List<JSONObject> result = new ArrayList<>();
                    if (paramDataList != null) {
                        for (int i = 0; i < paramDataList.length(); i++) {
                            result.add(paramDataList.getJSONObject(i));
                        }
                    }
                    return result;

                } catch (JSONException | java.io.IOException e) {
                    Log.e(TAG, "Request exception: " + e.getMessage());
                    
                    // Check if it's a network error
                    if (e instanceof java.net.UnknownHostException || 
                        e instanceof java.net.ConnectException ||
                        e instanceof java.net.SocketTimeoutException ||
                        (e instanceof java.io.IOException && e.getMessage() != null && 
                         (e.getMessage().contains("Unable to resolve host") || 
                          e.getMessage().contains("Network is unreachable")))) {
                        
                        // Wait for network connection
                        notifyProgress("Waiting for network connection...");
                        waitForNetworkConnection();
                        notifyProgress("Network connection restored, retrying...");
                    }
                    
                    if (attempt == retries) {
                        throw new RuntimeException("Network or parsing error after " + retries + 
                            " retries for " + dateFormat.format(startDate) + "–" + dateFormat.format(endDate), e);
                    }
                    Thread.sleep(delayMs);
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Request interrupted", e);
            }
        }

        throw new RuntimeException("Unexpected exit from getDeviceHistory()");
    }
    
    /**
     * Calculate hash of station configuration
     */
    private String calculateConfigHash(StationConfig config) {
        if (config == null) return "";
        
        String configString = String.format(Locale.US, "%.2f_%d_%d_%.4f_%d_%.6f_%.6f",
            config.getInverterPowerKw(),
            config.getPanelPowerW(),
            config.getPanelCount(),
            config.getPanelEfficiency(),
            config.getTiltDeg(),
            config.getLatitude(),
            config.getLongitude());
        
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(configString.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString().substring(0, 16); // Use first 16 chars
        } catch (Exception e) {
            Log.e(TAG, "Error calculating config hash", e);
            return configString;
        }
    }
    
    /**
     * Load metadata from file
     */
    private JSONObject loadMetadata() {
        try {
            File metadataFile = new File(new File(context.getFilesDir(), "csv"), METADATA_FILE);
            if (!metadataFile.exists()) {
                return new JSONObject();
            }
            
            try (BufferedReader reader = new BufferedReader(
                    new java.io.FileReader(metadataFile))) {
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line);
                }
                return new JSONObject(content.toString());
            }
        } catch (Exception e) {
            Log.w(TAG, "Error loading metadata", e);
            return new JSONObject();
        }
    }
    
    /**
     * Save metadata to file
     */
    private void saveMetadata(String configHash, Date configLastChanged, Date fetchDate) {
        try {
            File csvDir = new File(context.getFilesDir(), "csv");
            if (!csvDir.exists()) {
                csvDir.mkdirs();
            }
            
            File metadataFile = new File(csvDir, METADATA_FILE);
            JSONObject metadata = new JSONObject();
            metadata.put("configHash", configHash);
            metadata.put("configLastChanged", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(configLastChanged));
            metadata.put("lastFetchDate", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(fetchDate));
            
            try (FileWriter writer = new FileWriter(metadataFile, false)) {
                writer.write(metadata.toString(2)); // Pretty print with 2 spaces
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving metadata", e);
        }
    }
    
    /**
     * Get metadata (for external access)
     */
    public JSONObject getMetadata() {
        return loadMetadata();
    }
    
    /**
     * Get date range from existing station CSV
     */
    private Date[] getStationCsvDateRange(File csvFile) {
        if (!csvFile.exists()) {
            return null;
        }
        
        try (BufferedReader reader = new BufferedReader(new java.io.FileReader(csvFile))) {
            reader.readLine(); // Skip header
            
            String firstLine = null;
            String lastLine = null;
            String line;
            
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                if (firstLine == null) {
                    firstLine = line;
                }
                lastLine = line;
            }
            
            if (firstLine == null || lastLine == null) {
                return null;
            }
            
            // Extract date from first column
            String[] firstParts = firstLine.split(",", 2);
            String[] lastParts = lastLine.split(",", 2);
            
            if (firstParts.length == 0 || lastParts.length == 0) {
                return null;
            }
            
            SimpleDateFormat[] formats = {
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()),
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()),
                new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            };
            
            Date startDate = null;
            Date endDate = null;
            
            for (SimpleDateFormat format : formats) {
                try {
                    startDate = format.parse(firstParts[0].trim());
                    endDate = format.parse(lastParts[0].trim());
                    break;
                } catch (Exception e) {
                    // Try next format
                }
            }
            
            if (startDate != null && endDate != null) {
                return new Date[]{startDate, endDate};
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading station CSV date range", e);
        }
        
        return null;
    }
    
    /**
     * Trim CSV file to start from specific date
     */
    private void trimCsvToDate(File csvFile, Date minDate) {
        if (!csvFile.exists()) {
            return;
        }
        
        try {
            // Read all lines
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new java.io.FileReader(csvFile))) {
                String header = reader.readLine();
                if (header == null) return;
                lines.add(header);
                
                String line;
                SimpleDateFormat[] formats = {
                    new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()),
                    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()),
                    new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                };
                
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    
                    String[] parts = line.split(",", 2);
                    if (parts.length > 0) {
                        Date lineDate = null;
                        for (SimpleDateFormat format : formats) {
                            try {
                                lineDate = format.parse(parts[0].trim());
                                break;
                            } catch (Exception e) {
                                // Try next format
                            }
                        }
                        
                        // Keep line if date is >= minDate (compare dates only, ignore time)
                        if (lineDate != null) {
                            java.util.Calendar lineCal = java.util.Calendar.getInstance();
                            lineCal.setTime(lineDate);
                            lineCal.set(java.util.Calendar.HOUR_OF_DAY, 0);
                            lineCal.set(java.util.Calendar.MINUTE, 0);
                            lineCal.set(java.util.Calendar.SECOND, 0);
                            lineCal.set(java.util.Calendar.MILLISECOND, 0);
                            
                            java.util.Calendar minCal = java.util.Calendar.getInstance();
                            minCal.setTime(minDate);
                            minCal.set(java.util.Calendar.HOUR_OF_DAY, 0);
                            minCal.set(java.util.Calendar.MINUTE, 0);
                            minCal.set(java.util.Calendar.SECOND, 0);
                            minCal.set(java.util.Calendar.MILLISECOND, 0);
                            
                            if (!lineCal.getTime().before(minCal.getTime())) {
                                lines.add(line);
                            }
                        }
                    }
                }
            }
            
            // Write back
            try (FileWriter writer = new FileWriter(csvFile, false)) {
                for (String line : lines) {
                    writer.write(line + "\n");
                }
            }
            
            Log.d(TAG, "Trimmed CSV file to start from " + new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(minDate));
        } catch (Exception e) {
            Log.e(TAG, "Error trimming CSV file", e);
        }
    }
    
    /**
     * Fetch station data for weather range
     */
    public void fetchStationDataForWeatherRange(FetchCallback callback) {
        executor.execute(() -> {
            try {
                // Get current station configuration
                StationConfig currentConfig = stationConfigRepository.getStationConfigSync();
                if (currentConfig == null) {
                    if (callback != null) {
                        callback.onError("Station configuration not found. Please configure in Settings.");
                    }
                    return;
                }
                
                String currentConfigHash = calculateConfigHash(currentConfig);
                
                // Load metadata
                JSONObject metadata = loadMetadata();
                String lastConfigHash = metadata.optString("configHash", "");
                boolean configChanged = !currentConfigHash.equals(lastConfigHash);
                
                // Get config last changed date
                Date configLastChanged = new Date(); // Default to current time
                if (!configChanged && metadata.has("configLastChanged")) {
                    // Use date from metadata if config hasn't changed
                    try {
                        String configLastChangedStr = metadata.getString("configLastChanged");
                        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                        configLastChanged = dateFormat.parse(configLastChangedStr);
                    } catch (Exception e) {
                        Log.w(TAG, "Error parsing config last changed date from metadata", e);
                    }
                }
                
                // If config changed, use database file modification time or current time
                if (configChanged) {
                    try {
                        File dbFile = context.getDatabasePath("app_database");
                        if (dbFile.exists()) {
                            configLastChanged = new Date(dbFile.lastModified());
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Error getting database modification time", e);
                    }
                }
                
                if (configChanged) {
                    notifyProgress("Configuration changed, fetching all data...");
                } else {
                    notifyProgress("Configuration unchanged, checking for missing dates...");
                }
                
                // Get weather CSV file path
                File csvDir = new File(context.getFilesDir(), "csv");
                File weatherFile = new File(csvDir, "weather_last_max_period.csv");
                
                if (!weatherFile.exists()) {
                    // Try alternative path
                    File weatherDir = new File(context.getFilesDir(), "csv/weather");
                    weatherFile = new File(weatherDir, "weather_last_max_period.csv");
                }
                
                if (!weatherFile.exists()) {
                    if (callback != null) {
                        callback.onError("Weather CSV file not found. Please fetch weather data first.");
                    }
                    return;
                }
                
                // Read date range from weather CSV
                Date[] weatherDateRange = readDateRangeFromWeatherCsv(weatherFile);
                if (weatherDateRange == null || weatherDateRange.length != 2) {
                    if (callback != null) {
                        callback.onError("Could not determine date range from weather CSV");
                    }
                    return;
                }
                
                Date weatherStartDate = weatherDateRange[0];
                Date weatherEndDate = weatherDateRange[1];
                
                // Prepare CSV file
                if (!csvDir.exists()) {
                    csvDir.mkdirs();
                }
                File csvFile = new File(csvDir, "station_data.csv");
                
                Date fetchStartDate = weatherStartDate;
                boolean appendMode = false;
                
                if (!configChanged && csvFile.exists()) {
                    // Check existing data range
                    Date[] existingRange = getStationCsvDateRange(csvFile);
                    if (existingRange != null) {
                        Date existingStart = existingRange[0];
                        Date existingEnd = existingRange[1];
                        
                        // Trim CSV if it starts before weather data
                        if (existingStart.before(weatherStartDate)) {
                            notifyProgress("Trimming CSV to match weather date range...");
                            trimCsvToDate(csvFile, weatherStartDate);
                            existingStart = weatherStartDate;
                        }
                        
                        // If we have data up to some date, start from next day
                        if (!existingEnd.before(weatherEndDate)) {
                            // We already have all data
                            notifyProgress("All data already exists, no fetch needed");
                            if (callback != null) {
                                String firstTimestamp = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(existingStart);
                                String lastTimestamp = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(existingEnd);
                                callback.onSuccess(csvFile.getAbsolutePath(), 0, firstTimestamp, lastTimestamp);
                            }
                            return;
                        }
                        
                        // Start from day after existing end
                        long oneDay = 24 * 60 * 60 * 1000L;
                        fetchStartDate = new Date(existingEnd.getTime() + oneDay);
                        appendMode = true;
                        notifyProgress("Resuming from " + new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(fetchStartDate));
                    }
                } else if (configChanged) {
                    // Config changed, start fresh
                    if (csvFile.exists()) {
                        csvFile.delete();
                    }
                }
                
                Date startDate = fetchStartDate;
                Date endDate = weatherEndDate;
                
                if (startDate.after(endDate)) {
                    notifyProgress("All data already exists, no fetch needed");
                    if (callback != null) {
                        Date[] finalRange = getStationCsvDateRange(csvFile);
                        if (finalRange != null) {
                            String firstTimestamp = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(finalRange[0]);
                            String lastTimestamp = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(finalRange[1]);
                            callback.onSuccess(csvFile.getAbsolutePath(), 0, firstTimestamp, lastTimestamp);
                        } else {
                            callback.onSuccess(csvFile.getAbsolutePath(), 0, null, null);
                        }
                    }
                    return;
                }
                
                notifyProgress("Date range: " + new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(startDate) + 
                    " to " + new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(endDate));
                
                // Get access token
                String token = getAccessToken();
                
                // Open CSV file for writing (append if resuming)
                FileWriter csvWriter = null;
                // Use LinkedHashSet to preserve insertion order (order of appearance)
                java.util.Set<String> allKeys = new java.util.LinkedHashSet<>();
                java.util.List<String> allKeysOrdered = new java.util.ArrayList<>(); // Maintain order for header
                int totalRecords = 0;
                int currentBlockSize = 0;
                int lastValidBlockSize = 0;
                boolean hasValidData = false;
                Date currentBlockStartDate = null; // Track date where current block starts
                Date lastValidBlockStartDate = null; // Track date where last valid block starts
                
                // If appending, read existing keys from header
                if (appendMode && csvFile.exists()) {
                    try (BufferedReader reader = new BufferedReader(new java.io.FileReader(csvFile))) {
                        String header = reader.readLine();
                        if (header != null) {
                            String[] keys = header.split(",");
                            for (String key : keys) {
                                String trimmedKey = key.trim();
                                if (!trimmedKey.isEmpty() && !trimmedKey.equals("collectTime")) {
                                    allKeys.add(trimmedKey);
                                    allKeysOrdered.add(trimmedKey);
                                }
                            }
                            hasValidData = true;
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Error reading existing CSV header", e);
                    }
                }
                
                try {
                    csvWriter = new FileWriter(csvFile, appendMode);
                    
                    // Don't write header yet - we'll collect keys from first few days first
                    boolean headerWritten = false;
                    int keysCollectionDays = 0;
                    final int MAX_KEYS_COLLECTION_DAYS = 3; // Collect keys from first 3 days
                    java.util.List<JSONObject> pendingData = new java.util.ArrayList<>(); // Store data while collecting keys
                    
                    // Fetch data day by day and write to CSV immediately
                    long startTime = startDate.getTime();
                    long endTime = endDate.getTime();
                    long oneDay = 24 * 60 * 60 * 1000L;
                    
                    int dayCounter = 0;
                    int consecutiveFailures = 0;
                    
                    long currentTime = startTime;
                    while (currentTime <= endTime) {
                        dayCounter++;
                        Date currentDate = new Date(currentTime);
                        boolean shouldRetry = false;
                        
                        try {
                            List<JSONObject> dayData = getDeviceHistory(token, currentDate, currentDate, 1, 3, 3000);
                            
                            if (dayData != null && !dayData.isEmpty()) {
                                // If this is start of a new block, mark the date
                                if (currentBlockSize == 0) {
                                    currentBlockStartDate = currentDate;
                                }
                                
                                // Collect ALL keys from this day's data (preserve order)
                                boolean newKeysFound = false;
                                for (JSONObject item : dayData) {
                                    JSONArray dataList = item.optJSONArray("dataList");
                                    if (dataList != null) {
                                        for (int i = 0; i < dataList.length(); i++) {
                                            JSONObject dataItem = dataList.optJSONObject(i);
                                            if (dataItem != null) {
                                                String key = dataItem.optString("key", dataItem.optString("name", ""));
                                                if (!key.isEmpty() && !allKeys.contains(key)) {
                                                    allKeys.add(key);
                                                    allKeysOrdered.add(key);
                                                    newKeysFound = true;
                                                }
                                            }
                                        }
                                    }
                                }
                                
                                // If we're still collecting keys, store data for later
                                if (!headerWritten && keysCollectionDays < MAX_KEYS_COLLECTION_DAYS) {
                                    pendingData.addAll(dayData);
                                    keysCollectionDays++;
                                    if (newKeysFound) {
                                        notifyProgress(String.format(Locale.getDefault(), 
                                            "Day %d: %s - collecting keys (%d keys so far)...", dayCounter, 
                                            new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(currentDate),
                                            allKeysOrdered.size()));
                                    }
                                } else {
                                    // Write header if this is first write
                                    if (!headerWritten) {
                                        if (!appendMode || !hasValidData) {
                                            // Write header with all collected keys in order
                                            csvWriter.write("collectTime");
                                            for (String key : allKeysOrdered) {
                                                csvWriter.write("," + key);
                                            }
                                            csvWriter.write("\n");
                                            headerWritten = true;
                                            hasValidData = true;
                                            
                                            // Write all pending data now that header is written
                                            for (JSONObject item : pendingData) {
                                                writeCsvRow(csvWriter, item, allKeys, allKeysOrdered);
                                                totalRecords++;
                                                currentBlockSize++;
                                            }
                                            pendingData.clear();
                                            
                                            notifyProgress("Header written with " + allKeysOrdered.size() + " columns");
                                        } else {
                                            // Appending mode - header already exists
                                            headerWritten = true;
                                            pendingData.clear();
                                        }
                                    }
                                    
                                    // Write current day's data rows
                                    for (JSONObject item : dayData) {
                                        writeCsvRow(csvWriter, item, allKeys, allKeysOrdered);
                                        totalRecords++;
                                        currentBlockSize++;
                                    }
                                }
                                
                                // Mark as having valid data after first successful write
                                if (!hasValidData) {
                                    hasValidData = true;
                                }
                                
                                csvWriter.flush(); // Flush to disk periodically
                                
                                consecutiveFailures = 0;
                                if (headerWritten) {
                                    notifyProgress(String.format(Locale.getDefault(), 
                                        "Day %d: %s - %d records (total: %d)", dayCounter, 
                                        new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(currentDate),
                                        dayData.size(), totalRecords));
                                }
                                
                            } else {
                                // Gap detected - if we were collecting keys, write header and pending data first
                                if (!headerWritten && !pendingData.isEmpty()) {
                                    if (!appendMode || !hasValidData) {
                                        // Write header with all collected keys in order
                                        csvWriter.write("collectTime");
                                        for (String key : allKeysOrdered) {
                                            csvWriter.write("," + key);
                                        }
                                        csvWriter.write("\n");
                                        headerWritten = true;
                                        hasValidData = true;
                                        
                                        // Write all pending data now that header is written
                                        for (JSONObject item : pendingData) {
                                            writeCsvRow(csvWriter, item, allKeys, allKeysOrdered);
                                            totalRecords++;
                                            currentBlockSize++;
                                        }
                                        pendingData.clear();
                                        
                                        notifyProgress("Header written with " + allKeysOrdered.size() + " columns before gap");
                                    } else {
                                        headerWritten = true;
                                        pendingData.clear();
                                    }
                                }
                                
                                // Save current block as last valid and reset
                                if (currentBlockSize > 0) {
                                    notifyProgress("⚠️ Gap detected at " + 
                                        new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(currentDate) + 
                                        " — saving current block");
                                    lastValidBlockSize = currentBlockSize;
                                    lastValidBlockStartDate = currentBlockStartDate; // Save start date of the block we're saving
                                    currentBlockSize = 0;
                                    currentBlockStartDate = null;
                                } else {
                                    notifyProgress("Skipping empty day: " + 
                                        new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(currentDate));
                                }
                                consecutiveFailures = 0;
                            }
                        } catch (Exception e) {
                            // Check if it's a network error
                            boolean isNetworkError = e instanceof java.net.UnknownHostException || 
                                e instanceof java.net.ConnectException ||
                                e instanceof java.net.SocketTimeoutException ||
                                (e.getMessage() != null && 
                                 (e.getMessage().contains("Unable to resolve host") || 
                                  e.getMessage().contains("Network is unreachable") ||
                                  e.getMessage().contains("Network error")));
                            
                            if (isNetworkError) {
                                // Wait for network connection before continuing
                                notifyProgress("Waiting for network connection...");
                                try {
                                    waitForNetworkConnection();
                                    notifyProgress("Network connection restored, retrying day " + dayCounter);
                                    // Retry the same day instead of moving to next
                                    dayCounter--; // Decrement counter to retry same day
                                    consecutiveFailures = 0; // Reset failures since we're retrying
                                    shouldRetry = true; // Mark for retry
                                    Thread.sleep(1000); // Brief pause before retry
                                } catch (Exception networkWaitError) {
                                    notifyProgress("Network connection timeout, stopping");
                                    throw new RuntimeException("Network connection lost and not restored", networkWaitError);
                                }
                            } else {
                                consecutiveFailures++;
                                notifyProgress(String.format(Locale.getDefault(), 
                                    "Day %d: %s - failed (%d consecutive fails): %s", 
                                    dayCounter, 
                                    new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(currentDate),
                                    consecutiveFailures, e.getMessage()));
                                
                                if (consecutiveFailures >= 3) {
                                    throw new RuntimeException("Stopping: 3 consecutive failed days (last = " + 
                                        new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(currentDate) + ")", e);
                                }
                            }
                        }
                        
                        // Move to next day only if not retrying
                        if (!shouldRetry) {
                            currentTime += oneDay;
                            Thread.sleep(300); // Delay between requests
                        }
                    }
                    
                    // If header wasn't written yet (e.g., less than 3 days of data), write it now
                    if (!headerWritten && !allKeysOrdered.isEmpty()) {
                        if (!appendMode || !hasValidData) {
                            // Write header with all collected keys in order
                            csvWriter.write("collectTime");
                            for (String key : allKeysOrdered) {
                                csvWriter.write("," + key);
                            }
                            csvWriter.write("\n");
                            headerWritten = true;
                            hasValidData = true;
                            
                            // Write all pending data now that header is written
                            for (JSONObject item : pendingData) {
                                writeCsvRow(csvWriter, item, allKeys, allKeysOrdered);
                                totalRecords++;
                                currentBlockSize++;
                            }
                            pendingData.clear();
                            
                            notifyProgress("Header written with " + allKeysOrdered.size() + " columns (from " + keysCollectionDays + " days)");
                        }
                    }
                    
                    // Keep the last continuous block
                    if (currentBlockSize > 0) {
                        lastValidBlockSize = currentBlockSize;
                        // If no gap was detected, use current block start date
                        if (lastValidBlockStartDate == null) {
                            lastValidBlockStartDate = currentBlockStartDate;
                        }
                    }
                    
                } finally {
                    if (csvWriter != null) {
                        csvWriter.close();
                    }
                }
                
                // Trim file to last continuous block if there was a gap
                if (lastValidBlockStartDate != null && lastValidBlockSize > 0 && lastValidBlockSize < totalRecords) {
                    notifyProgress("Trimming file to last continuous block starting from " + 
                        new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(lastValidBlockStartDate));
                    trimCsvToDate(csvFile, lastValidBlockStartDate);
                }
                
                // Use the last valid block size for final count
                int finalRecordCount = lastValidBlockSize > 0 ? lastValidBlockSize : totalRecords;
                
                notifyProgress("Final continuous block kept with " + finalRecordCount + " records");
                
                if (finalRecordCount == 0) {
                    if (callback != null) {
                        callback.onError("No data returned for the specified date range");
                    }
                    return;
                }
                
                String csvPath = csvFile.getAbsolutePath();
                
                // Get first and last timestamps from CSV
                String firstTimestamp = null;
                String lastTimestamp = null;
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.FileReader(csvFile))) {
                    reader.readLine(); // Skip header
                    String firstLine = reader.readLine();
                    if (firstLine != null) {
                        String[] parts = firstLine.split(",", 2);
                        if (parts.length > 0) {
                            firstTimestamp = parts[0];
                        }
                    }
                    
                    String line;
                    String lastLine = null;
                    while ((line = reader.readLine()) != null) {
                        lastLine = line;
                    }
                    if (lastLine != null) {
                        String[] parts = lastLine.split(",", 2);
                        if (parts.length > 0) {
                            lastTimestamp = parts[0];
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Error reading timestamps from CSV", e);
                }
                
                notifyProgress("Data saved successfully");
                
                // Save metadata
                saveMetadata(currentConfigHash, configLastChanged, new Date());
                
                if (callback != null) {
                    callback.onSuccess(csvPath, finalRecordCount, firstTimestamp, lastTimestamp);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error fetching station data", e);
                if (callback != null) {
                    callback.onError("Error: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * Read date range from weather CSV file
     */
    private Date[] readDateRangeFromWeatherCsv(File weatherFile) {
        try {
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.FileReader(weatherFile));
            
            String line = reader.readLine(); // Skip header
            if (line == null) {
                reader.close();
                return null;
            }
            
            String firstTime = null;
            String lastTime = null;
            
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                
                String[] parts = line.split(",");
                if (parts.length > 0) {
                    String timeStr = parts[0].trim();
                    if (!timeStr.isEmpty()) {
                        if (firstTime == null) {
                            firstTime = timeStr;
                        }
                        lastTime = timeStr;
                    }
                }
            }
            reader.close();
            
            if (firstTime == null || lastTime == null) {
                return null;
            }
            
            // Parse dates
            SimpleDateFormat[] formats = {
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault()),
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()),
                new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            };
            
            Date startDate = null;
            Date endDate = null;
            
            for (SimpleDateFormat format : formats) {
                try {
                    startDate = format.parse(firstTime);
                    endDate = format.parse(lastTime);
                    break;
                } catch (Exception e) {
                    // Try next format
                }
            }
            
            if (startDate == null || endDate == null) {
                return null;
            }
            
            // Extract just the date part (set time to 00:00:00)
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTime(startDate);
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
            cal.set(java.util.Calendar.MINUTE, 0);
            cal.set(java.util.Calendar.SECOND, 0);
            cal.set(java.util.Calendar.MILLISECOND, 0);
            startDate = cal.getTime();
            
            cal.setTime(endDate);
            cal.set(java.util.Calendar.HOUR_OF_DAY, 23);
            cal.set(java.util.Calendar.MINUTE, 59);
            cal.set(java.util.Calendar.SECOND, 59);
            cal.set(java.util.Calendar.MILLISECOND, 999);
            endDate = cal.getTime();
            
            return new Date[]{startDate, endDate};
            
        } catch (Exception e) {
            Log.e(TAG, "Error reading date range from weather CSV", e);
            return null;
        }
    }
    
    /**
     * Write a single CSV row
     */
    private void writeCsvRow(FileWriter writer, JSONObject item, java.util.Set<String> allKeys, java.util.List<String> allKeysOrdered) throws Exception {
        String collectTime = item.optString("collectTime", "");
        writer.write(collectTime);
        
        // Create map for this row's data
        java.util.Map<String, String> rowData = new java.util.HashMap<>();
        JSONArray dataList = item.optJSONArray("dataList");
        if (dataList != null) {
            for (int i = 0; i < dataList.length(); i++) {
                JSONObject dataItem = dataList.optJSONObject(i);
                if (dataItem != null) {
                    String key = dataItem.optString("key", dataItem.optString("name", ""));
                    Object value = dataItem.opt("value");
                    if (value != null) {
                        rowData.put(key, value.toString());
                    }
                }
            }
        }
        
        // Write values in the same order as headers (preserve order)
        for (String key : allKeysOrdered) {
            String value = rowData.getOrDefault(key, "");
            writer.write("," + value);
        }
        writer.write("\n");
    }
    
    /**
     * Hash password with SHA-256
     */
    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            Log.e(TAG, "Error hashing password", e);
            return password;
        }
    }
    
    /**
     * Read response from HTTP connection with size limit to prevent OOM
     */
    private String readResponse(HttpURLConnection connection, int responseCode) throws Exception {
        InputStream inputStream;
        if (responseCode >= 200 && responseCode < 300) {
            inputStream = connection.getInputStream();
        } else {
            inputStream = connection.getErrorStream();
        }

        if (inputStream == null) {
            return "{\"success\":false,\"msg\":\"HTTP " + responseCode + " - No response body\"}";
        }

        // Limit response size to prevent OOM (max 50MB for large date ranges)
        final int MAX_SIZE = 50 * 1024 * 1024; // 50MB
        StringBuilder response = new StringBuilder(8192); // Initial capacity
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8), 8192)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (response.length() + line.length() > MAX_SIZE) {
                    Log.w(TAG, "Response too large, truncating at " + MAX_SIZE + " bytes");
                    response.append(line.substring(0, Math.max(0, MAX_SIZE - response.length())));
                    break;
                }
                response.append(line);
            }
        }
        
        String result = response.toString();
        if (result.isEmpty()) {
            return "{\"success\":false,\"msg\":\"HTTP " + responseCode + " - Empty response\"}";
        }
        
        return result;
    }
    
    /**
     * Wait for network connection
     */
    private void waitForNetworkConnection() {
        int maxWaitTime = 300000; // 5 minutes max wait
        int checkInterval = 2000; // Check every 2 seconds
        int waited = 0;
        
        while (waited < maxWaitTime) {
            if (NetworkUtils.isNetworkConnected(context)) {
                return; // Network connected
            }
            
            try {
                Thread.sleep(checkInterval);
                waited += checkInterval;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Wait for network interrupted", e);
            }
        }
        
        // If we get here, network didn't connect in time
        throw new RuntimeException("Network connection timeout after " + (maxWaitTime / 1000) + " seconds");
    }
    
    /**
     * Notify progress callback
     */
    private void notifyProgress(String message) {
        if (progressCallback != null) {
            progressCallback.onProgress(message);
        }
        Log.d(TAG, message);
    }
    
    /**
     * Clear cached token
     */
    public void clearCachedToken() {
        cachedAccessToken = null;
        tokenExpiryTime = 0;
    }
    
    /**
     * Shutdown executor service
     */
    public void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}

