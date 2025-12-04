package com.masters.ppa.data.repository;

import android.app.Application;
import android.os.AsyncTask;

import androidx.lifecycle.LiveData;

import com.masters.ppa.data.dao.StationDataDao;
import com.masters.ppa.data.database.AppDatabase;
import com.masters.ppa.data.model.StationData;
import com.masters.ppa.utils.CsvUtils;

import java.util.Date;
import java.util.List;

/**
 * Repository for StationData
 */
public class StationDataRepository {
    
    private final StationDataDao stationDataDao;
    private final LiveData<List<StationData>> allStationData;
    private final LiveData<StationData> latestStationData;
    private final LiveData<Date> lastUpdatedDate;
    private final Application application;
    
    public StationDataRepository(Application application) {
        this.application = application;
        AppDatabase database = AppDatabase.getInstance(application);
        stationDataDao = database.stationDataDao();
        allStationData = stationDataDao.getAllStationData();
        latestStationData = stationDataDao.getLatestStationData();
        lastUpdatedDate = stationDataDao.getLastUpdatedDate();
    }
    
    /**
     * Get all station data as LiveData
     * @return LiveData<List<StationData>>
     */
    public LiveData<List<StationData>> getAllStationData() {
        return allStationData;
    }
    
    /**
     * Get latest station data
     * @return LiveData<StationData>
     */
    public LiveData<StationData> getLatestStationData() {
        return latestStationData;
    }
    
    /**
     * Get last updated date
     * @return LiveData<Date>
     */
    public LiveData<Date> getLastUpdatedDate() {
        return lastUpdatedDate;
    }
    
    /**
     * Insert station data
     * @param stationData StationData to insert
     */
    public void insert(StationData stationData) {
        new InsertAsyncTask(stationDataDao).execute(stationData);
    }
    
    /**
     * Insert multiple station data
     * @param stationDataList List of StationData to insert
     */
    public void insertAll(List<StationData> stationDataList) {
        new InsertAllAsyncTask(stationDataDao).execute(stationDataList);
    }
    
    /**
     * Delete all station data
     */
    public void deleteAll() {
        new DeleteAllAsyncTask(stationDataDao).execute();
    }
    
    /**
     * Load station data from CSV file
     * @param isTestMode If true, load from test file
     * @return true if successful
     */
    public boolean loadFromCsv(boolean isTestMode) {
        String filename = isTestMode ? CsvUtils.TEST_STATION_CSV : "station_data.csv";
        List<StationData> stationDataList = CsvUtils.readStationData(application, filename);
        
        if (!stationDataList.isEmpty()) {
            // Clear existing data and insert new data
            deleteAll();
            insertAll(stationDataList);
            return true;
        }
        
        return false;
    }
    
    /**
     * Fetch latest data from Solarman API (placeholder)
     * @return true if successful
     */
    public boolean fetchLatestData() {
        // This is a placeholder for the actual API call
        // In a real implementation, this would call the Solarman API
        // and parse the response into StationData objects
        
        // For now, just return false to indicate no data was fetched
        return false;
    }
    
    /**
     * AsyncTask for insert operation
     */
    private static class InsertAsyncTask extends AsyncTask<StationData, Void, Void> {
        private final StationDataDao asyncTaskDao;
        
        InsertAsyncTask(StationDataDao dao) {
            asyncTaskDao = dao;
        }
        
        @Override
        protected Void doInBackground(final StationData... params) {
            asyncTaskDao.insert(params[0]);
            return null;
        }
    }
    
    /**
     * AsyncTask for insert all operation
     */
    private static class InsertAllAsyncTask extends AsyncTask<List<StationData>, Void, Void> {
        private final StationDataDao asyncTaskDao;
        
        InsertAllAsyncTask(StationDataDao dao) {
            asyncTaskDao = dao;
        }
        
        @SafeVarargs
        @Override
        protected final Void doInBackground(final List<StationData>... params) {
            asyncTaskDao.insertAll(params[0]);
            return null;
        }
    }
    
    /**
     * AsyncTask for delete all operation
     */
    private static class DeleteAllAsyncTask extends AsyncTask<Void, Void, Void> {
        private final StationDataDao asyncTaskDao;
        
        DeleteAllAsyncTask(StationDataDao dao) {
            asyncTaskDao = dao;
        }
        
        @Override
        protected Void doInBackground(Void... voids) {
            asyncTaskDao.deleteAll();
            return null;
        }
    }
}
