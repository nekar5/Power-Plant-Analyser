package com.masters.ppa.data.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Entity class for Solarman API configuration
 */
@Entity(tableName = "solarman_api_config")
public class SolarmanApiConfig {
    
    @PrimaryKey
    private int id;
    
    private String baseUrl;
    private String email;
    private String password;
    private String appId;
    private String appSecret;
    private int timeout;
    private long deviceId;
    private String deviceSn;
    
    public SolarmanApiConfig() {
        this.id = 1; // Only one API config
        this.baseUrl = "https://globalapi.solarmanpv.com"; // Default value
        this.timeout = 20; // Default value
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(long deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceSn() {
        return deviceSn;
    }

    public void setDeviceSn(String deviceSn) {
        this.deviceSn = deviceSn;
    }
}
