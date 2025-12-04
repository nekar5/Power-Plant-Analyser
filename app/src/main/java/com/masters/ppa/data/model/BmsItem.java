package com.masters.ppa.data.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.masters.ppa.ui.project.ProjectExpandableListAdapter;

/**
 * Entity class for BMS items in Project
 */
@Entity(tableName = "bms_items")
public class BmsItem implements ProjectExpandableListAdapter.ProjectItem {
    
    @PrimaryKey(autoGenerate = true)
    private int id;
    
    private String name;
    private double width;
    private double height;
    private double depth;
    
    // Constructors
    public BmsItem() {
    }
    
    public BmsItem(String name, double width, double height, double depth) {
        this.name = name;
        this.width = width;
        this.height = height;
        this.depth = depth;
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
}

