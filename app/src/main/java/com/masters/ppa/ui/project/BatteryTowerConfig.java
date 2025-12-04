package com.masters.ppa.ui.project;

/**
 * Helper class for battery tower configuration in dialog
 */
public class BatteryTowerConfig {
    private int batteryModelId;
    private int batteriesCount;
    private int towerNumber;
    
    public BatteryTowerConfig(int batteryModelId, int batteriesCount, int towerNumber) {
        this.batteryModelId = batteryModelId;
        this.batteriesCount = batteriesCount;
        this.towerNumber = towerNumber;
    }
    
    public int getBatteryModelId() {
        return batteryModelId;
    }
    
    public void setBatteryModelId(int batteryModelId) {
        this.batteryModelId = batteryModelId;
    }
    
    public int getBatteriesCount() {
        return batteriesCount;
    }
    
    public void setBatteriesCount(int batteriesCount) {
        this.batteriesCount = batteriesCount;
    }
    
    public int getTowerNumber() {
        return towerNumber;
    }
    
    public void setTowerNumber(int towerNumber) {
        this.towerNumber = towerNumber;
    }
}

