package com.masters.ppa.data.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Entity class for station configuration
 */
@Entity(tableName = "station_config")
public class StationConfig {
    
    @PrimaryKey
    private int id;
    
    private double inverterPowerKw;
    private int panelPowerW;
    private int panelCount;
    private double panelEfficiency;
    private int tiltDeg;
    private double latitude;
    private double longitude;
    
    public StationConfig() {
        this.id = 1; // Only one station config
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public double getInverterPowerKw() {
        return inverterPowerKw;
    }

    public void setInverterPowerKw(double inverterPowerKw) {
        this.inverterPowerKw = inverterPowerKw;
    }

    public int getPanelPowerW() {
        return panelPowerW;
    }

    public void setPanelPowerW(int panelPowerW) {
        this.panelPowerW = panelPowerW;
    }

    public int getPanelCount() {
        return panelCount;
    }

    public void setPanelCount(int panelCount) {
        this.panelCount = panelCount;
    }

    public double getPanelEfficiency() {
        return panelEfficiency;
    }

    public void setPanelEfficiency(double panelEfficiency) {
        this.panelEfficiency = panelEfficiency;
    }

    public int getTiltDeg() {
        return tiltDeg;
    }

    public void setTiltDeg(int tiltDeg) {
        this.tiltDeg = tiltDeg;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }
}
