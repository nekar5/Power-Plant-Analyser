package com.masters.ppa.data.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Groups of inverter data organized by category
 */
public class InverterDataGroups {
    
    // System Status Group
    private final Map<String, InverterMetric> systemStatus = new HashMap<>();
    
    // Power Flow Group
    private final Map<String, InverterMetric> powerFlow = new HashMap<>();
    
    // Daily Summary Group
    private final Map<String, InverterMetric> dailySummary = new HashMap<>();
    
    // Cumulative Summary Group
    private final Map<String, InverterMetric> cumulativeSummary = new HashMap<>();
    
    // Advanced Group (rest of data)
    private final Map<String, InverterMetric> advanced = new HashMap<>();
    
    // All metrics by key
    private final Map<String, InverterMetric> allMetrics = new HashMap<>();

    public Map<String, InverterMetric> getSystemStatus() {
        return systemStatus;
    }

    public Map<String, InverterMetric> getPowerFlow() {
        return powerFlow;
    }

    public Map<String, InverterMetric> getDailySummary() {
        return dailySummary;
    }

    public Map<String, InverterMetric> getCumulativeSummary() {
        return cumulativeSummary;
    }

    public Map<String, InverterMetric> getAdvanced() {
        return advanced;
    }

    public Map<String, InverterMetric> getAllMetrics() {
        return allMetrics;
    }

    /**
     * Add metric to appropriate group
     */
    public void addMetric(InverterMetric metric) {
        if (metric == null || metric.getKey() == null) {
            return;
        }
        
        allMetrics.put(metric.getKey(), metric);
        String key = metric.getKey();
        String name = metric.getName();

        // System Status Group
        if ((key != null && (key.contains("ST_") || key.contains("INV_ST") || key.contains("INV_WORK") || 
            key.contains("SYSTIM"))) ||
            (name != null && (name.contains("Grid Status") || name.contains("Inverter status") || 
            name.contains("Inverter Status") || name.contains("System Time") || 
            name.contains("Status") || (name.contains("Temperature") || name.contains("Temp"))))) {
            systemStatus.put(key, metric);
            return;
        }

        // Power Flow Group - Solar, Battery, Grid, Load, Consumption
        if ((key != null && (key.contains("PV") || key.contains("B_P") || key.contains("B_left") || 
            key.contains("PG_") || key.contains("E_Puse") || key.contains("T_AC_OP") ||
            key.contains("DP") || key.contains("PVTP"))) ||
            (name != null && (name.contains("Solar") || name.contains("PV") || name.contains("Battery") || 
            name.contains("SoC") || name.contains("Grid") || name.contains("Consumption") || 
            name.contains("Power") || name.contains("Current") || name.contains("Voltage")))) {
            powerFlow.put(key, metric);
            return;
        }

        // Daily Summary Group
        // Generation time today, daily grid feed in, daily consumption
        if ((key != null && (key.contains("Etdy") || key.equals("GE_T_TODAY") || key.equals("t_gc_tdy1"))) ||
            (name != null && (name.contains("Daily") || name.contains("Generation Time Today") || 
             name.contains("Daily Grid Feed-in") || name.contains("Daily Consumption")))) {
            dailySummary.put(key, metric);
            return;
        }

        // Cumulative Summary Group
        // Total charging energy, total discharging energy, cumulative grid feed in, generation time total
        if ((key != null && (key.contains("Et_") || key.equals("t_cg_n1") || key.equals("t_dcg_n1") || 
             key.equals("t_gc1") || key.equals("GE_T_TOTAL") || (key.contains("t_") && !key.contains("Etdy")))) ||
            (name != null && (name.contains("Cumulative") || name.contains("Total Charging Energy") || 
             name.contains("Total Discharging Energy") || name.contains("Cumulative Grid Feed-in") || 
             name.contains("Generation Time Total")))) {
            cumulativeSummary.put(key, metric);
            return;
        }

        // Advanced Group (everything else)
        advanced.put(key, metric);
    }

    /**
     * Get metric by key
     */
    public InverterMetric getMetric(String key) {
        return allMetrics.get(key);
    }

    /**
     * Get metric by name (case-insensitive search)
     */
    public InverterMetric getMetricByName(String name) {
        for (InverterMetric metric : allMetrics.values()) {
            if (metric.getName() != null && metric.getName().equalsIgnoreCase(name)) {
                return metric;
            }
        }
        return null;
    }
}

