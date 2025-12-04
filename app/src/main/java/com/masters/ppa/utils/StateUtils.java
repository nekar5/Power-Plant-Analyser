package com.masters.ppa.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.masters.ppa.data.model.GenerationData;
import com.masters.ppa.data.model.InverterDataGroups;
import com.masters.ppa.data.model.InverterMetric;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Utility class for saving and loading fragment state using SharedPreferences
 */
public class StateUtils {
    
    private static final String TAG = "StateUtils";
    private static final String PREFS_NAME = "fragment_state_prefs";
    
    // Keys for SharedPreferences
    private static final String KEY_CURRENT_DATA = "current_data_json";
    private static final String KEY_CURRENT_DATA_TIMESTAMP = "current_data_timestamp";
    private static final String KEY_FORECAST_DATA = "forecast_data_json";
    private static final String KEY_FORECAST_DATA_TIMESTAMP = "forecast_data_timestamp";
    
    /**
     * Save InverterDataGroups to SharedPreferences
     */
    public static void saveCurrentData(Context context, InverterDataGroups groups) {
        if (context == null || groups == null) {
            return;
        }
        
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            
            // Convert InverterDataGroups to JSON
            JSONObject json = new JSONObject();
            JSONArray allMetricsArray = new JSONArray();
            
            // Save all metrics
            for (InverterMetric metric : groups.getAllMetrics().values()) {
                JSONObject metricJson = new JSONObject();
                metricJson.put("key", metric.getKey() != null ? metric.getKey() : "");
                metricJson.put("name", metric.getName() != null ? metric.getName() : "");
                metricJson.put("value", metric.getValue() != null ? metric.getValue() : "");
                metricJson.put("unit", metric.getUnit() != null ? metric.getUnit() : "");
                allMetricsArray.put(metricJson);
            }
            
            json.put("metrics", allMetricsArray);
            
            editor.putString(KEY_CURRENT_DATA, json.toString());
            editor.putLong(KEY_CURRENT_DATA_TIMESTAMP, System.currentTimeMillis());
            editor.apply();
            
            Log.d(TAG, "Current data saved: " + allMetricsArray.length() + " metrics");
        } catch (JSONException e) {
            Log.e(TAG, "Error saving current data", e);
        }
    }
    
    /**
     * Load InverterDataGroups from SharedPreferences
     */
    public static InverterDataGroups loadCurrentData(Context context) {
        if (context == null) {
            return null;
        }
        
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String jsonString = prefs.getString(KEY_CURRENT_DATA, null);
            
            if (jsonString == null || jsonString.isEmpty()) {
                return null;
            }
            
            JSONObject json = new JSONObject(jsonString);
            JSONArray metricsArray = json.getJSONArray("metrics");
            
            // Reconstruct InverterDataGroups
            InverterDataGroups groups = new InverterDataGroups();
            for (int i = 0; i < metricsArray.length(); i++) {
                JSONObject metricJson = metricsArray.getJSONObject(i);
                InverterMetric metric = new InverterMetric(
                    metricJson.optString("key", ""),
                    metricJson.optString("name", ""),
                    metricJson.optString("value", ""),
                    metricJson.optString("unit", "")
                );
                groups.addMetric(metric);
            }
            
            Log.d(TAG, "Current data loaded: " + metricsArray.length() + " metrics");
            return groups;
        } catch (JSONException e) {
            Log.e(TAG, "Error loading current data", e);
            return null;
        }
    }
    
    /**
     * Get timestamp of saved current data
     */
    public static long getCurrentDataTimestamp(Context context) {
        if (context == null) {
            return 0;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getLong(KEY_CURRENT_DATA_TIMESTAMP, 0);
    }
    
    /**
     * Save GenerationData list to SharedPreferences
     */
    public static void saveForecastData(Context context, List<GenerationData> generationDataList) {
        if (context == null || generationDataList == null) {
            return;
        }
        
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            
            // Use Gson to serialize list
            Gson gson = new Gson();
            String json = gson.toJson(generationDataList);
            
            editor.putString(KEY_FORECAST_DATA, json);
            editor.putLong(KEY_FORECAST_DATA_TIMESTAMP, System.currentTimeMillis());
            editor.apply();
            
            Log.d(TAG, "Forecast data saved: " + generationDataList.size() + " items");
        } catch (Exception e) {
            Log.e(TAG, "Error saving forecast data", e);
        }
    }
    
    /**
     * Load GenerationData list from SharedPreferences
     */
    public static List<GenerationData> loadForecastData(Context context) {
        if (context == null) {
            return null;
        }
        
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String jsonString = prefs.getString(KEY_FORECAST_DATA, null);
            
            if (jsonString == null || jsonString.isEmpty()) {
                return null;
            }
            
            // Use Gson to deserialize list
            Gson gson = new Gson();
            Type listType = new TypeToken<List<GenerationData>>(){}.getType();
            List<GenerationData> generationDataList = gson.fromJson(jsonString, listType);
            
            Log.d(TAG, "Forecast data loaded: " + (generationDataList != null ? generationDataList.size() : 0) + " items");
            return generationDataList;
        } catch (Exception e) {
            Log.e(TAG, "Error loading forecast data", e);
            return null;
        }
    }
    
    /**
     * Get timestamp of saved forecast data
     */
    public static long getForecastDataTimestamp(Context context) {
        if (context == null) {
            return 0;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getLong(KEY_FORECAST_DATA_TIMESTAMP, 0);
    }
    
    /**
     * Clear all saved state
     */
    public static void clearAllState(Context context) {
        if (context == null) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
        Log.d(TAG, "All state cleared");
    }
}

