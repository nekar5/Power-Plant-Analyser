package com.masters.ppa;

import android.app.Application;

import com.masters.ppa.data.database.AppDatabase;
import com.masters.ppa.utils.FileUtils;

/**
 * Main application class for Power Plant Analyser
 */
public class PowerPlantApplication extends Application {

    private static PowerPlantApplication instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        
        AppDatabase.getInstance(this);
        FileUtils.createRequiredDirectories(this);
    }

    /**
     * Get the application instance
     * @return Application instance
     */
    public static PowerPlantApplication getInstance() {
        return instance;
    }
}
