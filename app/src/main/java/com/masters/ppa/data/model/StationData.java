package com.masters.ppa.data.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.Date;

/**
 * Entity class for current station data readings
 */
@Entity(tableName = "station_data")
public class StationData {
    
    @PrimaryKey(autoGenerate = true)
    private int id;
    
    private Date timestamp;
    private double powerKw;
    private double energyTodayKwh;
    private double energyTotalKwh;
    private double batteryStateOfChargePercent;
    private double batteryPowerKw;
    private double gridPowerKw;
    private double loadPowerKw;
    private Date lastUpdated;
    
    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public double getPowerKw() {
        return powerKw;
    }

    public void setPowerKw(double powerKw) {
        this.powerKw = powerKw;
    }

    public double getEnergyTodayKwh() {
        return energyTodayKwh;
    }

    public void setEnergyTodayKwh(double energyTodayKwh) {
        this.energyTodayKwh = energyTodayKwh;
    }

    public double getEnergyTotalKwh() {
        return energyTotalKwh;
    }

    public void setEnergyTotalKwh(double energyTotalKwh) {
        this.energyTotalKwh = energyTotalKwh;
    }

    public double getBatteryStateOfChargePercent() {
        return batteryStateOfChargePercent;
    }

    public void setBatteryStateOfChargePercent(double batteryStateOfChargePercent) {
        this.batteryStateOfChargePercent = batteryStateOfChargePercent;
    }

    public double getBatteryPowerKw() {
        return batteryPowerKw;
    }

    public void setBatteryPowerKw(double batteryPowerKw) {
        this.batteryPowerKw = batteryPowerKw;
    }

    public double getGridPowerKw() {
        return gridPowerKw;
    }

    public void setGridPowerKw(double gridPowerKw) {
        this.gridPowerKw = gridPowerKw;
    }

    public double getLoadPowerKw() {
        return loadPowerKw;
    }

    public void setLoadPowerKw(double loadPowerKw) {
        this.loadPowerKw = loadPowerKw;
    }

    public Date getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Date lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}
