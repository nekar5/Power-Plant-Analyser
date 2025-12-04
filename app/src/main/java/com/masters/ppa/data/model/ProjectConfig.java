package com.masters.ppa.data.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Entity class for Project Config
 */
@Entity(tableName = "project_configs")
public class ProjectConfig {
    
    @PrimaryKey(autoGenerate = true)
    private int id;
    
    private String name;
    private long createdAt;
    
    public ProjectConfig() {
    }
    
    public ProjectConfig(String name) {
        this.name = name;
        this.createdAt = System.currentTimeMillis();
    }
    
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public long getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}

