package com.masters.ppa.data.model;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Entity for Config-Inverter relationship with role
 */
@Entity(
    tableName = "config_inverters",
    foreignKeys = {
        @ForeignKey(
            entity = ProjectConfig.class,
            parentColumns = "id",
            childColumns = "configId",
            onDelete = ForeignKey.CASCADE
        ),
        @ForeignKey(
            entity = InverterItem.class,
            parentColumns = "id",
            childColumns = "inverterId",
            onDelete = ForeignKey.CASCADE
        )
    },
    indices = {
        @Index("configId"),
        @Index("inverterId")
    }
)
public class ConfigInverter {
    
    @PrimaryKey(autoGenerate = true)
    private int id;
    
    private int configId;
    private int inverterId;
    private String role; // Master, Slave1, Slave2, ...
    private int count; // Number of inverters of this type
    
    public ConfigInverter() {
    }
    
    public ConfigInverter(int configId, int inverterId, String role) {
        this.configId = configId;
        this.inverterId = inverterId;
        this.role = role;
        this.count = 1; // Default is 1
    }
    
    public ConfigInverter(int configId, int inverterId, String role, int count) {
        this.configId = configId;
        this.inverterId = inverterId;
        this.role = role;
        this.count = count;
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
    
    public int getInverterId() {
        return inverterId;
    }
    
    public void setInverterId(int inverterId) {
        this.inverterId = inverterId;
    }
    
    public String getRole() {
        return role;
    }
    
    public void setRole(String role) {
        this.role = role;
    }
    
    public int getCount() {
        return count;
    }
    
    public void setCount(int count) {
        this.count = count;
    }
}

