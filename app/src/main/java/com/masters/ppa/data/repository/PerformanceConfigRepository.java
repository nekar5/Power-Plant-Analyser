package com.masters.ppa.data.repository;

import android.app.Application;
import android.os.AsyncTask;

import androidx.lifecycle.LiveData;

import com.masters.ppa.data.dao.PerformanceConfigDao;
import com.masters.ppa.data.database.AppDatabase;
import com.masters.ppa.data.model.PerformanceConfig;

/**
 * Repository for PerformanceConfig data
 */
public class PerformanceConfigRepository {
    
    private final PerformanceConfigDao performanceConfigDao;
    private final LiveData<PerformanceConfig> performanceConfig;
    
    public PerformanceConfigRepository(Application application) {
        AppDatabase database = AppDatabase.getInstance(application);
        performanceConfigDao = database.performanceConfigDao();
        performanceConfig = performanceConfigDao.getPerformanceConfig();
    }
    
    /**
     * Get performance configuration as LiveData
     * @return LiveData<PerformanceConfig>
     */
    public LiveData<PerformanceConfig> getPerformanceConfig() {
        return performanceConfig;
    }
    
    /**
     * Get performance configuration synchronously
     * @return PerformanceConfig or null
     */
    public PerformanceConfig getPerformanceConfigSync() {
        return performanceConfigDao.getPerformanceConfigSync();
    }
    
    /**
     * Insert or update performance configuration
     * @param performanceConfig PerformanceConfig to save
     */
    public void insert(PerformanceConfig performanceConfig) {
        new InsertAsyncTask(performanceConfigDao).execute(performanceConfig);
    }
    
    /**
     * Delete performance configuration
     */
    public void delete() {
        new DeleteAsyncTask(performanceConfigDao).execute();
    }
    
    /**
     * Check if performance configuration exists
     * @return true if exists
     */
    public boolean exists() {
        return performanceConfigDao.getCount() > 0;
    }
    
    /**
     * AsyncTask for insert operation
     */
    private static class InsertAsyncTask extends AsyncTask<PerformanceConfig, Void, Void> {
        private final PerformanceConfigDao asyncTaskDao;
        
        InsertAsyncTask(PerformanceConfigDao dao) {
            asyncTaskDao = dao;
        }
        
        @Override
        protected Void doInBackground(final PerformanceConfig... params) {
            asyncTaskDao.insert(params[0]);
            return null;
        }
    }
    
    /**
     * AsyncTask for delete operation
     */
    private static class DeleteAsyncTask extends AsyncTask<Void, Void, Void> {
        private final PerformanceConfigDao asyncTaskDao;
        
        DeleteAsyncTask(PerformanceConfigDao dao) {
            asyncTaskDao = dao;
        }
        
        @Override
        protected Void doInBackground(final Void... params) {
            asyncTaskDao.deleteAll();
            return null;
        }
    }
}
