package com.masters.ppa.data.repository;

import android.app.Application;
import android.os.AsyncTask;

import androidx.lifecycle.LiveData;

import com.masters.ppa.data.dao.SolarmanApiConfigDao;
import com.masters.ppa.data.database.AppDatabase;
import com.masters.ppa.data.model.SolarmanApiConfig;

/**
 * Repository for SolarmanApiConfig data
 */
public class SolarmanApiConfigRepository {
    
    private final SolarmanApiConfigDao solarmanApiConfigDao;
    private final LiveData<SolarmanApiConfig> solarmanApiConfig;
    
    public SolarmanApiConfigRepository(Application application) {
        AppDatabase database = AppDatabase.getInstance(application);
        solarmanApiConfigDao = database.solarmanApiConfigDao();
        solarmanApiConfig = solarmanApiConfigDao.getSolarmanApiConfig();
    }
    
    /**
     * Get API configuration as LiveData
     * @return LiveData<SolarmanApiConfig>
     */
    public LiveData<SolarmanApiConfig> getSolarmanApiConfig() {
        return solarmanApiConfig;
    }
    
    /**
     * Get API configuration synchronously
     * @return SolarmanApiConfig or null
     */
    public SolarmanApiConfig getSolarmanApiConfigSync() {
        return solarmanApiConfigDao.getSolarmanApiConfigSync();
    }
    
    /**
     * Insert or update API configuration
     * @param solarmanApiConfig SolarmanApiConfig to save
     */
    public void insert(SolarmanApiConfig solarmanApiConfig) {
        new InsertAsyncTask(solarmanApiConfigDao).execute(solarmanApiConfig);
    }
    
    /**
     * Delete API configuration
     */
    public void delete() {
        new DeleteAsyncTask(solarmanApiConfigDao).execute();
    }
    
    /**
     * Check if API configuration exists
     * @return true if exists
     */
    public boolean exists() {
        return solarmanApiConfigDao.getCount() > 0;
    }
    
    /**
     * AsyncTask for insert operation
     */
    private static class InsertAsyncTask extends AsyncTask<SolarmanApiConfig, Void, Void> {
        private final SolarmanApiConfigDao asyncTaskDao;
        
        InsertAsyncTask(SolarmanApiConfigDao dao) {
            asyncTaskDao = dao;
        }
        
        @Override
        protected Void doInBackground(final SolarmanApiConfig... params) {
            asyncTaskDao.insert(params[0]);
            return null;
        }
    }
    
    /**
     * AsyncTask for delete operation
     */
    private static class DeleteAsyncTask extends AsyncTask<Void, Void, Void> {
        private final SolarmanApiConfigDao asyncTaskDao;
        
        DeleteAsyncTask(SolarmanApiConfigDao dao) {
            asyncTaskDao = dao;
        }
        
        @Override
        protected Void doInBackground(final Void... params) {
            asyncTaskDao.deleteAll();
            return null;
        }
    }
}
