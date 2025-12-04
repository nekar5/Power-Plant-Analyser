package com.masters.ppa.ui.project;

/**
 * Helper class for BMS tower configuration in dialog
 */
public class BmsTowerConfig {
    private int bmsId;
    private Integer attachedTowerNumber; // null = standalone
    
    public BmsTowerConfig(int bmsId, Integer attachedTowerNumber) {
        this.bmsId = bmsId;
        this.attachedTowerNumber = attachedTowerNumber;
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

