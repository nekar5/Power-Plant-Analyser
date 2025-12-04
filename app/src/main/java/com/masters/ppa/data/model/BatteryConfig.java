package com.masters.ppa.data.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Entity class for battery configuration
 */
@Entity(tableName = "battery_config")
public class BatteryConfig {
    
    @PrimaryKey
    private int id;
    
    private double capacityKwh;
    private int count;
    private String type; // High Voltage / Low Voltage
    private double roundtripEfficiency;
    private int socMinPct;
    private int socMaxPct;
    private boolean nightUseGrid;
    private boolean allowGridCharging;
    
    public BatteryConfig() {
        this.id = 1; // Only one battery config
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public double getCapacityKwh() {
        return capacityKwh;
    }

    public void setCapacityKwh(double capacityKwh) {
        this.capacityKwh = capacityKwh;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public double getRoundtripEfficiency() {
        return roundtripEfficiency;
    }

    public void setRoundtripEfficiency(double roundtripEfficiency) {
        this.roundtripEfficiency = roundtripEfficiency;
    }

    public int getSocMinPct() {
        return socMinPct;
    }

    public void setSocMinPct(int socMinPct) {
        this.socMinPct = socMinPct;
    }

    public int getSocMaxPct() {
        return socMaxPct;
    }

    public void setSocMaxPct(int socMaxPct) {
        this.socMaxPct = socMaxPct;
    }

    public boolean isNightUseGrid() {
        return nightUseGrid;
    }

    public void setNightUseGrid(boolean nightUseGrid) {
        this.nightUseGrid = nightUseGrid;
    }

    public boolean isAllowGridCharging() {
        return allowGridCharging;
    }

    public void setAllowGridCharging(boolean allowGridCharging) {
        this.allowGridCharging = allowGridCharging;
    }
}
