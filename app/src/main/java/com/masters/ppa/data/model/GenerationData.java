package com.masters.ppa.data.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.Date;

/**
 * Entity class for power generation data
 */
@Entity(tableName = "generation_data")
public class GenerationData {
    
    @PrimaryKey(autoGenerate = true)
    private int id;
    
    private Date date;
    private double generationKwh;
    private double predictedGenerationKwh;
    private boolean isActual; // true for actual data, false for predicted
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

    public double getGenerationKwh() {
        return generationKwh;
    }

    public void setGenerationKwh(double generationKwh) {
        this.generationKwh = generationKwh;
    }

    public double getPredictedGenerationKwh() {
        return predictedGenerationKwh;
    }

    public void setPredictedGenerationKwh(double predictedGenerationKwh) {
        this.predictedGenerationKwh = predictedGenerationKwh;
    }

    public boolean isActual() {
        return isActual;
    }

    public void setActual(boolean actual) {
        isActual = actual;
    }

    public Date getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Date lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}
