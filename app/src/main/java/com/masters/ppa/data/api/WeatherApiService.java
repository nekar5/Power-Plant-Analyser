package com.masters.ppa.data.api;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.masters.ppa.utils.FileUtils;

import java.util.Locale;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Service for fetching weather data from Open-Meteo API
 */
public class WeatherApiService {
    
    private static final String TAG = "WeatherApiService";
    private static final String WEATHER_URL = "https://api.open-meteo.com/v1/forecast";
    private static final int TIMEOUT_MS = 20000;
    private static final int MAX_HISTORY_DAYS = 90; // 3 months
    
    // CSV file paths
    private static final String WEATHER_DIR = "csv/weather";
    public static final String CSV_WEATHER_TODAY7 = "weather_next_7days.csv";
    public static final String CSV_WEATHER_3MONTHS = "weather_last_max_period.csv";
    
    // CSV header
    private static final String CSV_HEADER = "time,temperature_2m,cloud_cover,shortwave_radiation,direct_radiation,diffuse_radiation,wind_speed_10m";
    
    /**
     * Interface for fetch completion callback
     */
    public interface FetchCallback {
        void onSuccess(String filePath, int rowCount, String firstTimestamp, String lastTimestamp);
        void onError(String errorMessage);
    }
    
    private final Context context;
    private final OkHttpClient httpClient;
    
    public WeatherApiService(Context context) {
        this.context = context.getApplicationContext();
        this.httpClient = new OkHttpClient.Builder()
                .callTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .build();
    }
    
    /**
     * Fetch weather data for a specific date range
     * @param lat Latitude
     * @param lon Longitude
     * @param startDate Start date
     * @param endDate End date
     * @param outputFilename Output filename
     * @param callback Callback for fetch completion
     */
    public void fetchAndSaveWeather(double lat, double lon, 
                                   LocalDate startDate, LocalDate endDate, 
                                   String outputFilename, FetchCallback callback) {
        // Ensure start date is not too far in the past
        LocalDate apiMin = LocalDate.now(ZoneOffset.UTC).minusDays(MAX_HISTORY_DAYS - 1);
        if (startDate.isBefore(apiMin)) {
            startDate = apiMin;
            Log.w(TAG, "Start date adjusted to API limit: " + startDate);
        }
        
        // Create URL for API request
        String url = WEATHER_URL + "?latitude=" + lat + "&longitude=" + lon +
                "&hourly=temperature_2m,cloud_cover,shortwave_radiation," +
                "direct_radiation,diffuse_radiation,wind_speed_10m" +
                "&start_date=" + startDate + "&end_date=" + endDate + "&timezone=auto";
        
        Log.d(TAG, "Fetching weather data from: " + url);
        
        // Create output directory
        File weatherDir = FileUtils.ensureDirectoryExists(context, WEATHER_DIR);
        if (weatherDir == null) {
            if (callback != null) {
                callback.onError("Failed to create directory for weather data");
            }
            return;
        }
        
        // Create output file
        File outputFile = new File(weatherDir, outputFilename);
        
        // Create request
        Request request = new Request.Builder().url(url).build();
        
        // Execute request asynchronously
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Failed to fetch weather data", e);
                if (callback != null) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                            callback.onError("Network error: " + e.getMessage()));
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "API error: " + response.code());
                    if (callback != null) {
                        new Handler(Looper.getMainLooper()).post(() -> 
                                callback.onError("API error: " + response.code()));
                    }
                    return;
                }
                
                try {
                    // Parse JSON response
                    String responseBody = response.body().string();
                    JSONObject obj = new JSONObject(responseBody);
                    JSONObject hourly = obj.getJSONObject("hourly");
                    JSONArray times = hourly.getJSONArray("time");
                    JSONArray temps = hourly.getJSONArray("temperature_2m");
                    JSONArray clouds = hourly.getJSONArray("cloud_cover");
                    JSONArray swr = hourly.getJSONArray("shortwave_radiation");
                    JSONArray dr = hourly.getJSONArray("direct_radiation");
                    JSONArray dif = hourly.getJSONArray("diffuse_radiation");
                    JSONArray wind = hourly.getJSONArray("wind_speed_10m");
                    
                    // Ensure parent directory exists
                    com.masters.ppa.utils.FileUtils.ensureDirectoryExists(outputFile.getParentFile());
                    
                    // Write CSV file
                    int validRowCount = 0;
                    String firstTimestamp = null;
                    String lastTimestamp = null;
                    
                    try (FileWriter writer = new FileWriter(outputFile, false)) { // false = overwrite existing file
                        // Write header
                        writer.write(CSV_HEADER + "\n");
                        
                        // Write data rows
                        for (int i = 0; i < times.length(); i++) {
                            // Check if row has valid data (not all NaN)
                            boolean hasValidData = false;
                            
                            // Check temperature
                            double temp = temps.optDouble(i, Double.NaN);
                            if (!Double.isNaN(temp)) {
                                hasValidData = true;
                            }
                            
                            // Check cloud cover
                            double cloud = clouds.optDouble(i, Double.NaN);
                            if (!Double.isNaN(cloud)) {
                                hasValidData = true;
                            }
                            
                            // Check shortwave radiation
                            double radiation = swr.optDouble(i, Double.NaN);
                            if (!Double.isNaN(radiation)) {
                                hasValidData = true;
                            }
                            
                            // Check direct radiation
                            double direct = dr.optDouble(i, Double.NaN);
                            if (!Double.isNaN(direct)) {
                                hasValidData = true;
                            }
                            
                            // Check diffuse radiation
                            double diffuse = dif.optDouble(i, Double.NaN);
                            if (!Double.isNaN(diffuse)) {
                                hasValidData = true;
                            }
                            
                            // Check wind speed
                            double windSpeed = wind.optDouble(i, Double.NaN);
                            if (!Double.isNaN(windSpeed)) {
                                hasValidData = true;
                            }
                            
                            // Skip row if all values are NaN
                            if (!hasValidData) {
                                continue;
                            }
                            
                            // Get timestamp
                            String timestamp = times.getString(i);
                            
                            // Track first and last timestamps
                            if (firstTimestamp == null) {
                                firstTimestamp = timestamp;
                            }
                            lastTimestamp = timestamp;
                            
                            // Write row
                            writer.write(timestamp + "," +
                                    formatDouble(temp) + "," +
                                    formatDouble(cloud) + "," + // Keep original scale (0-100%)
                                    formatDouble(radiation) + "," +
                                    formatDouble(direct) + "," +
                                    formatDouble(diffuse) + "," +
                                    formatDouble(windSpeed) + "\n");
                            
                            validRowCount++;
                        }
                    }
                    
                    // Log success
                    Log.d(TAG, "Weather data saved to: " + outputFile.getAbsolutePath() +
                            " with " + validRowCount + " valid rows" +
                            (firstTimestamp != null ? ", from " + firstTimestamp + " to " + lastTimestamp : ""));
                    
                    // Call success callback
                    if (callback != null) {
                        final String firstTs = firstTimestamp;
                        final String lastTs = lastTimestamp;
                        int finalValidRowCount = validRowCount;
                        new Handler(Looper.getMainLooper()).post(() ->
                                callback.onSuccess(outputFile.getAbsolutePath(), finalValidRowCount, firstTs, lastTs));
                    }
                    
                } catch (JSONException | IOException e) {
                    Log.e(TAG, "Error processing weather data", e);
                    if (callback != null) {
                        new Handler(Looper.getMainLooper()).post(() -> 
                                callback.onError("Error processing data: " + e.getMessage()));
                    }
                }
            }
        });
    }
    
    /**
     * Fetch 7-day forecast (today + 6 days)
     * @param lat Latitude
     * @param lon Longitude
     * @param callback Callback for fetch completion
     */
    public void fetch7DaysWeather(double lat, double lon, FetchCallback callback) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate endDate = today.plusDays(6);
        
        fetchAndSaveWeather(lat, lon, today, endDate, CSV_WEATHER_TODAY7, callback);
    }
    
    /**
     * Fetch 3-month historical data (today - 90 days to today)
     * @param lat Latitude
     * @param lon Longitude
     * @param callback Callback for fetch completion
     */
    public void fetch3MonthsWeather(double lat, double lon, FetchCallback callback) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate startDate = today.minusDays(MAX_HISTORY_DAYS);
        
        fetchAndSaveWeather(lat, lon, startDate, today, CSV_WEATHER_3MONTHS, callback);
    }
    
    /**
     * Format double value for CSV output
     * @param value Double value
     * @return Formatted string
     */
    private static String formatDouble(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "";
        }
        return String.format(Locale.US, "%.1f", value);
    }
    
    /**
     * Get full path to weather file
     * @param filename Filename
     * @return Full path to file
     */
    public File getWeatherFile(String filename) {
        return FileUtils.getFile(context, filename, WEATHER_DIR);
    }
}

