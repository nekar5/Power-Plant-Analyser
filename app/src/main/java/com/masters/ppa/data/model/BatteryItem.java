package com.masters.ppa.data.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.masters.ppa.ui.project.ProjectExpandableListAdapter;

/**
 * Entity class for Battery items in Project
 */
@Entity(tableName = "battery_items")
public class BatteryItem implements ProjectExpandableListAdapter.ProjectItem {
    
    @PrimaryKey(autoGenerate = true)
    private int id;
    
    private String name;
    private double width;
    private double height;
    private double depth;
    private double capacityKWh;
    
    // Constructors
    public BatteryItem() {
    }
    
    public BatteryItem(String name, double width, double height, double depth, double capacityKWh) {
        this.name = name;
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.capacityKWh = capacityKWh;
    }
    
    // Getters and Setters
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
    
    public double getWidth() {
        return width;
    }
    
    public void setWidth(double width) {
        this.width = width;
    }
    
    public double getHeight() {
        return height;
    }
    
    public void setHeight(double height) {
        this.height = height;
    }
    
    public double getDepth() {
        return depth;
    }
    
    public void setDepth(double depth) {
        this.depth = depth;
    }
    
    public double getCapacityKWh() {
        return capacityKWh;
    }
    
    public void setCapacityKWh(double capacityKWh) {
        this.capacityKWh = capacityKWh;
    }
}

