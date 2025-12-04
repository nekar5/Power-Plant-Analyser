package com.masters.ppa.data.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.masters.ppa.data.dao.BatteryConfigDao;
import com.masters.ppa.data.dao.BatteryItemDao;
import com.masters.ppa.data.dao.BmsItemDao;
import com.masters.ppa.data.dao.ConfigBmsDao;
import com.masters.ppa.data.dao.ConfigInverterDao;
import com.masters.ppa.data.dao.ConfigTowerDao;
import com.masters.ppa.data.dao.GenerationDataDao;
import com.masters.ppa.data.dao.InverterItemDao;
import com.masters.ppa.data.dao.PerformanceConfigDao;
import com.masters.ppa.data.dao.ProjectConfigDao;
import com.masters.ppa.data.dao.SolarmanApiConfigDao;
import com.masters.ppa.data.dao.StationConfigDao;
import com.masters.ppa.data.dao.StationDataDao;
import com.masters.ppa.data.dao.WeatherDataDao;
import com.masters.ppa.data.model.BatteryConfig;
import com.masters.ppa.data.model.BatteryItem;
import com.masters.ppa.data.model.BmsItem;
import com.masters.ppa.data.model.ConfigBms;
import com.masters.ppa.data.model.ConfigInverter;
import com.masters.ppa.data.model.ConfigTower;
import com.masters.ppa.data.model.GenerationData;
import com.masters.ppa.data.model.InverterItem;
import com.masters.ppa.data.model.PerformanceConfig;
import com.masters.ppa.data.model.ProjectConfig;
import com.masters.ppa.data.model.SolarmanApiConfig;
import com.masters.ppa.data.model.StationConfig;
import com.masters.ppa.data.model.StationData;
import com.masters.ppa.data.model.WeatherData;

/**
 * Main database class for the application
 */
@Database(entities = {
        StationConfig.class,
        BatteryConfig.class,
        PerformanceConfig.class,
        SolarmanApiConfig.class,
        WeatherData.class,
        GenerationData.class,
        StationData.class,
        InverterItem.class,
        BatteryItem.class,
        BmsItem.class,
        ProjectConfig.class,
        ConfigInverter.class,
        ConfigTower.class,
        ConfigBms.class
}, version = 6, exportSchema = false)
@TypeConverters({DateConverter.class})
public abstract class AppDatabase extends RoomDatabase {
    
    private static final String DATABASE_NAME = "power_plant_db";
    private static volatile AppDatabase instance;
    
    // DAOs
    public abstract StationConfigDao stationConfigDao();
    public abstract BatteryConfigDao batteryConfigDao();
    public abstract PerformanceConfigDao performanceConfigDao();
    public abstract SolarmanApiConfigDao solarmanApiConfigDao();
    public abstract WeatherDataDao weatherDataDao();
    public abstract GenerationDataDao generationDataDao();
    public abstract StationDataDao stationDataDao();
    public abstract InverterItemDao inverterItemDao();
    public abstract BatteryItemDao batteryItemDao();
    public abstract BmsItemDao bmsItemDao();
    public abstract ProjectConfigDao projectConfigDao();
    public abstract ConfigInverterDao configInverterDao();
    public abstract ConfigTowerDao configTowerDao();
    public abstract ConfigBmsDao configBmsDao();
    
    // Singleton pattern
    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(
                    context.getApplicationContext(),
                    AppDatabase.class,
                    DATABASE_NAME)
                    .fallbackToDestructiveMigration()
                    .build();
        }
        return instance;
    }
}
