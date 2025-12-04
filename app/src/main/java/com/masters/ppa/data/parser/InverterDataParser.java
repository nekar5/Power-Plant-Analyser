package com.masters.ppa.data.parser;

import android.util.Log;

import com.masters.ppa.data.model.InverterDataGroups;
import com.masters.ppa.data.model.InverterMetric;
import com.masters.ppa.utils.DateUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.Locale;

/**
 * Parser for Solarman inverter data
 */
public class InverterDataParser {
    
    private static final String TAG = "InverterDataParser";

    /**
     * Parse JSON response from Solarman API into InverterDataGroups
     */
    public static InverterDataGroups parseJsonResponse(JSONObject response) throws JSONException {
        InverterDataGroups groups = new InverterDataGroups();

        if (!response.optBoolean("success", false)) {
            throw new JSONException("API response indicates failure");
        }

        JSONArray dataList = response.optJSONArray("dataList");
        if (dataList == null) {
            Log.w(TAG, "dataList is null or empty");
            return groups;
        }

        for (int i = 0; i < dataList.length(); i++) {
            try {
                JSONObject item = dataList.getJSONObject(i);
                InverterMetric metric = new InverterMetric();
                
                metric.setKey(item.optString("key", ""));
                metric.setName(item.optString("name", ""));
                metric.setValue(item.optString("value", "-"));
                
                // Handle unit - can be null or "None"
                String unit = item.optString("unit", "");
                if (unit == null || unit.isEmpty() || "None".equals(unit) || "null".equalsIgnoreCase(unit)) {
                    unit = "";
                }
                metric.setUnit(unit);

                groups.addMetric(metric);
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing data item " + i, e);
            }
        }

        Log.d(TAG, "Parsed " + groups.getAllMetrics().size() + " metrics");
        return groups;
    }

    /**
     * Format numeric value with 2 decimal places and unit
     */
    public static String formatValue(double value, String unit) {
        if (value == 0.0 && (unit == null || unit.isEmpty())) {
            return "-";
        }
        
        String formatted = Double.isNaN(value) || Double.isInfinite(value) ? "" : String.format(Locale.getDefault(), "%.2f", value);
        // Remove trailing zeros
        formatted = formatted.replaceAll("\\.?0+$", "");
        
        if (unit != null && !unit.isEmpty() && !"None".equals(unit)) {
            return formatted + " " + unit;
        }
        return formatted;
    }

    /**
     * Get formatted timestamp
     */
    public static String formatTimestamp(Date date) {
        if (date == null) {
            date = new Date();
        }
        return DateUtils.formatDateTime(date);
    }

    /**
     * Get power value in kW
     */
    public static double getPowerInKw(InverterDataGroups groups, String metricKey) {
        InverterMetric metric = groups.getMetric(metricKey);
        if (metric == null) {
            return 0.0;
        }
        
        double value = metric.getNumericValue();
        String unit = metric.getUnit();
        
        // Convert to kW
        if (unit != null) {
            if (unit.contains("W")) {
                return value / 1000.0;
            } else if (unit.contains("kW")) {
                return value;
            }
        }
        
        return value / 1000.0; // Default assume W
    }

    /**
     * Get percentage value
     */
    public static double getPercentage(InverterDataGroups groups, String metricKey) {
        InverterMetric metric = groups.getMetric(metricKey);
        if (metric == null) {
            return 0.0;
        }
        
        double value = metric.getNumericValue();
        String unit = metric.getUnit();
        
        // Already a percentage
        if (unit != null && unit.contains("%")) {
            return value;
        }
        
        return value;
    }
}


