package com.masters.ppa.data.model;

/**
 * Model for a single inverter metric
 */
public class InverterMetric {
    private String key;
    private String name;
    private String value;
    private String unit;

    public InverterMetric() {
    }

    public InverterMetric(String key, String name, String value, String unit) {
        this.key = key;
        this.name = name;
        this.value = value;
        this.unit = unit;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getUnit() {
        return unit != null ? unit : "";
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    /**
     * Get formatted value with unit
     */
    public String getFormattedValue() {
        if (value == null || value.isEmpty() || "-".equals(value)) {
            return "-";
        }
        String unitStr = getUnit();
        if (unitStr == null || unitStr.isEmpty() || "None".equals(unitStr)) {
            return value;
        }
        return value + " " + unitStr;
    }

    /**
     * Get numeric value as double (for calculations)
     */
    public double getNumericValue() {
        if (value == null || value.isEmpty() || "-".equals(value)) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}


