package com.masters.ppa.data.model;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Entity for Config Tower
 */
@Entity(
    tableName = "config_towers",
    foreignKeys = {
        @ForeignKey(
            entity = ProjectConfig.class,
            parentColumns = "id",
            childColumns = "configId",
            onDelete = ForeignKey.CASCADE
        ),
        @ForeignKey(
            entity = BatteryItem.class,
            parentColumns = "id",
            childColumns = "batteryModelId",
            onDelete = ForeignKey.CASCADE
        )
    },
    indices = {
        @Index("configId"),
        @Index("batteryModelId")
    }
)
public class ConfigTower {
    
    @PrimaryKey(autoGenerate = true)
    private int id;
    
    private int configId;
    private int towerNumber;
    private int batteryModelId;
    private int batteriesCount;
    
    public ConfigTower() {
    }
    
    public ConfigTower(int configId, int towerNumber, int batteryModelId, int batteriesCount) {
        this.configId = configId;
        this.towerNumber = towerNumber;
        this.batteryModelId = batteryModelId;
        this.batteriesCount = batteriesCount;
    }
    
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public int getConfigId() {
        return configId;
    }
    
    public void setConfigId(int configId) {
        this.configId = configId;
    }
    
    public int getTowerNumber() {
        return towerNumber;
    }
    
    public void setTowerNumber(int towerNumber) {
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
}

