package com.masters.ppa.data.repository;

import android.app.Application;
import android.content.Context;
import android.os.AsyncTask;

import androidx.lifecycle.LiveData;

import com.masters.ppa.data.dao.BatteryConfigDao;
import com.masters.ppa.data.database.AppDatabase;
import com.masters.ppa.data.model.BatteryConfig;

/**
 * Repository for BatteryConfig data
 */
public class BatteryConfigRepository {
    
    private final BatteryConfigDao batteryConfigDao;
    private final LiveData<BatteryConfig> batteryConfig;
    
    public BatteryConfigRepository(Context context) {
        AppDatabase database = AppDatabase.getInstance(context);
        batteryConfigDao = database.batteryConfigDao();
        batteryConfig = batteryConfigDao.getBatteryConfig();
    }
    
    /**
     * Get battery configuration as LiveData
     * @return LiveData<BatteryConfig>
     */
    public LiveData<BatteryConfig> getBatteryConfig() {
        return batteryConfig;
    }
    
    /**
     * Get battery configuration synchronously
     * @return BatteryConfig or null
     */
    public BatteryConfig getBatteryConfigSync() {
        return batteryConfigDao.getBatteryConfigSync();
    }
    
    /**
     * Insert or update battery configuration
     * @param batteryConfig BatteryConfig to save
     */
    public void insert(BatteryConfig batteryConfig) {
        new InsertAsyncTask(batteryConfigDao).execute(batteryConfig);
    }
    
    /**
     * Delete battery configuration
     */
    public void delete() {
        new DeleteAsyncTask(batteryConfigDao).execute();
    }
    
    /**
     * Check if battery configuration exists
     * @return true if exists
     */
    public boolean exists() {
        return batteryConfigDao.getCount() > 0;
    }
    
    /**
     * AsyncTask for insert operation
     */
    private static class InsertAsyncTask extends AsyncTask<BatteryConfig, Void, Void> {
        private final BatteryConfigDao asyncTaskDao;
        
        InsertAsyncTask(BatteryConfigDao dao) {
            asyncTaskDao = dao;
        }
        
        @Override
        protected Void doInBackground(final BatteryConfig... params) {
            asyncTaskDao.insert(params[0]);
            return null;
        }
    }
    
    /**
     * AsyncTask for delete operation
     */
    private static class DeleteAsyncTask extends AsyncTask<Void, Void, Void> {
        private final BatteryConfigDao asyncTaskDao;
        
        DeleteAsyncTask(BatteryConfigDao dao) {
            asyncTaskDao = dao;
        }
        
        @Override
        protected Void doInBackground(final Void... params) {
            asyncTaskDao.deleteAll();
            return null;
        }
    }
}
