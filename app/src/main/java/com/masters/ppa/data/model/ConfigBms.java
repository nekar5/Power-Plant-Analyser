package com.masters.ppa.data.model;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Entity for Config-BMS relationship
 */
@Entity(
    tableName = "config_bms",
    foreignKeys = {
        @ForeignKey(
            entity = ProjectConfig.class,
            parentColumns = "id",
            childColumns = "configId",
            onDelete = ForeignKey.CASCADE
        ),
        @ForeignKey(
            entity = BmsItem.class,
            parentColumns = "id",
            childColumns = "bmsId",
            onDelete = ForeignKey.CASCADE
        )
    },
    indices = {
        @Index("configId"),
        @Index("bmsId")
    }
)
public class ConfigBms {
    
    @PrimaryKey(autoGenerate = true)
    private int id;
    
    private int configId;
    private int bmsId;
    private Integer attachedTowerNumber; // null if standalone
    
    public ConfigBms() {
    }
    
    public ConfigBms(int configId, int bmsId, Integer attachedTowerNumber) {
        this.configId = configId;
        this.bmsId = bmsId;
        this.attachedTowerNumber = attachedTowerNumber;
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
    
    public int getBmsId() {
        return bmsId;
    }
    
    public void setBmsId(int bmsId) {
        this.bmsId = bmsId;
    }
    
    public Integer getAttachedTowerNumber() {
        return attachedTowerNumber;
    }
    
    public void setAttachedTowerNumber(Integer attachedTowerNumber) {
        this.attachedTowerNumber = attachedTowerNumber;
    }
}

