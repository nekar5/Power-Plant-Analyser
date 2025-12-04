package com.masters.ppa.data.repository;

import android.app.Application;
import android.content.Context;
import android.os.AsyncTask;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.masters.ppa.data.dao.StationConfigDao;
import com.masters.ppa.data.database.AppDatabase;
import com.masters.ppa.data.model.StationConfig;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Repository for StationConfig data
 */
public class StationConfigRepository {
    
    private final StationConfigDao stationConfigDao;
    private final LiveData<StationConfig> stationConfig;
    private final Executor executor;
    
    public StationConfigRepository(Context context) {
        AppDatabase database = AppDatabase.getInstance(context);
        stationConfigDao = database.stationConfigDao();
        stationConfig = stationConfigDao.getStationConfig();
        executor = Executors.newSingleThreadExecutor();
    }
    
    /**
     * Get station configuration as LiveData
     * @return LiveData<StationConfig>
     */
    public LiveData<StationConfig> getStationConfig() {
        return stationConfig;
    }
    
    /**
     * Get station configuration synchronously (must be called from background thread)
     * @return StationConfig or null
     */
    public StationConfig getStationConfigSync() {
        return stationConfigDao.getStationConfigSync();
    }
    
    /**
     * Insert or update station configuration
     * @param stationConfig StationConfig to save
     */
    public void insert(StationConfig stationConfig) {
        new InsertAsyncTask(stationConfigDao).execute(stationConfig);
    }
    
    /**
     * Delete station configuration
     */
    public void delete() {
        new DeleteAsyncTask(stationConfigDao).execute();
    }
    
    /**
     * Check if station configuration exists (asynchronous, returns LiveData)
     * @return LiveData<Boolean> true if exists
     */
    public LiveData<Boolean> exists() {
        MutableLiveData<Boolean> result = new MutableLiveData<>();
        executor.execute(() -> {
            boolean exists = stationConfigDao.getCount() > 0;
            result.postValue(exists);
        });
        return result;
    }
    
    /**
     * AsyncTask for insert operation
     */
    private static class InsertAsyncTask extends AsyncTask<StationConfig, Void, Void> {
        private final StationConfigDao asyncTaskDao;
        
        InsertAsyncTask(StationConfigDao dao) {
            asyncTaskDao = dao;
        }
        
        @Override
        protected Void doInBackground(final StationConfig... params) {
            asyncTaskDao.insert(params[0]);
            return null;
        }
    }
    
    /**
     * AsyncTask for delete operation
     */
    private static class DeleteAsyncTask extends AsyncTask<Void, Void, Void> {
        private final StationConfigDao asyncTaskDao;
        
        DeleteAsyncTask(StationConfigDao dao) {
            asyncTaskDao = dao;
        }
        
        @Override
        protected Void doInBackground(final Void... params) {
            asyncTaskDao.deleteAll();
            return null;
        }
    }
}
