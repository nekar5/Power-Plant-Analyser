package com.masters.ppa.data.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Entity class for performance configuration
 */
@Entity(tableName = "performance_config")
public class PerformanceConfig {
    
    @PrimaryKey
    private int id;
    
    private double performanceRatioAvg;
    private double temperatureCoeff;
    private int referenceTemp;
    
    public PerformanceConfig() {
        this.id = 1; // Only one performance config
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public double getPerformanceRatioAvg() {
        return performanceRatioAvg;
    }

    public void setPerformanceRatioAvg(double performanceRatioAvg) {
        this.performanceRatioAvg = performanceRatioAvg;
    }

    public double getTemperatureCoeff() {
        return temperatureCoeff;
    }

    public void setTemperatureCoeff(double temperatureCoeff) {
        this.temperatureCoeff = temperatureCoeff;
    }

    public int getReferenceTemp() {
        return referenceTemp;
    }

    public void setReferenceTemp(int referenceTemp) {
        this.referenceTemp = referenceTemp;
    }
}
