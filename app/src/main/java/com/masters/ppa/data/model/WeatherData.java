package com.masters.ppa.data.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.Date;

/**
 * Entity class for weather data
 */
@Entity(tableName = "weather_data")
public class WeatherData {
    
    @PrimaryKey(autoGenerate = true)
    private int id;
    
    private Date date;
    private double temperatureMin;
    private double temperatureMax;
    private double temperatureAvg;
    private double cloudCover;
    private double shortwaveRadiation;
    private double windSpeed;
    private Date lastUpdated;
    
    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public double getTemperatureMin() {
        return temperatureMin;
    }

    public void setTemperatureMin(double temperatureMin) {
        this.temperatureMin = temperatureMin;
    }
    
    public double getTemperatureMax() {
        return temperatureMax;
    }

    public void setTemperatureMax(double temperatureMax) {
        this.temperatureMax = temperatureMax;
    }
    
    public double getTemperatureAvg() {
        return temperatureAvg;
    }

    public void setTemperatureAvg(double temperatureAvg) {
        this.temperatureAvg = temperatureAvg;
    }
    
    /**
     * Legacy method for compatibility
     * @return average temperature
     */
    public double getTemperature() {
        return temperatureAvg;
    }

    /**
     * Legacy method for compatibility
     * @param temperature value to set
     */
    public void setTemperature(double temperature) {
        this.temperatureAvg = temperature;
        this.temperatureMin = temperature;
        this.temperatureMax = temperature;
    }

    public double getCloudCover() {
        return cloudCover;
    }

    public void setCloudCover(double cloudCover) {
        this.cloudCover = cloudCover;
    }

    public double getShortwaveRadiation() {
        return shortwaveRadiation;
    }

    public void setShortwaveRadiation(double shortwaveRadiation) {
        this.shortwaveRadiation = shortwaveRadiation;
    }

    public double getWindSpeed() {
        return windSpeed;
    }

    public void setWindSpeed(double windSpeed) {
        this.windSpeed = windSpeed;
    }

    public Date getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Date lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}
