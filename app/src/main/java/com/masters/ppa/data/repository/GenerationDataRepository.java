package com.masters.ppa.data.repository;

import android.app.Application;
import android.os.AsyncTask;

import androidx.lifecycle.LiveData;

import com.masters.ppa.data.dao.GenerationDataDao;
import com.masters.ppa.data.database.AppDatabase;
import com.masters.ppa.data.model.GenerationData;
import com.masters.ppa.utils.CsvUtils;

import java.util.Date;
import java.util.List;

/**
 * Repository for GenerationData
 */
public class GenerationDataRepository {
    
    private final GenerationDataDao generationDataDao;
    private final LiveData<List<GenerationData>> allGenerationData;
    private final LiveData<Date> lastUpdatedDate;
    private final Application application;
    
    public GenerationDataRepository(Application application) {
        this.application = application;
        AppDatabase database = AppDatabase.getInstance(application);
        generationDataDao = database.generationDataDao();
        allGenerationData = generationDataDao.getAllGenerationData();
        lastUpdatedDate = generationDataDao.getLastUpdatedDate();
    }
    
    /**
     * Get all generation data as LiveData
     * @return LiveData<List<GenerationData>>
     */
    public LiveData<List<GenerationData>> getAllGenerationData() {
        return allGenerationData;
    }
    
    /**
     * Get last 14 days actual generation data
     * @return LiveData<List<GenerationData>>
     */
    public LiveData<List<GenerationData>> getLast14DaysActualGeneration() {
        return generationDataDao.getLast14DaysActualGeneration();
    }
    
    /**
     * Get next 7 days predicted generation data
     * @return LiveData<List<GenerationData>>
     */
    public LiveData<List<GenerationData>> getNext7DaysPredictedGeneration() {
        return generationDataDao.getNext7DaysPredictedGeneration();
    }
    
    /**
     * Get last updated date
     * @return LiveData<Date>
     */
    public LiveData<Date> getLastUpdatedDate() {
        return lastUpdatedDate;
    }
    
    /**
     * Insert generation data
     * @param generationData GenerationData to insert
     */
    public void insert(GenerationData generationData) {
        new InsertAsyncTask(generationDataDao).execute(generationData);
    }
    
    /**
     * Insert multiple generation data
     * @param generationDataList List of GenerationData to insert
     */
    public void insertAll(List<GenerationData> generationDataList) {
        new InsertAllAsyncTask(generationDataDao).execute(generationDataList);
    }
    
    /**
     * Delete all generation data
     */
    public void deleteAll() {
        new DeleteAllAsyncTask(generationDataDao).execute();
    }
    
    /**
     * Load generation data from CSV file
     * @param isTestMode If true, load from test file
     * @return true if successful
     */
    public boolean loadFromCsv(boolean isTestMode) {
        String filename = isTestMode ? CsvUtils.TEST_GENERATION_CSV : "generation_data.csv";
        List<GenerationData> generationDataList = CsvUtils.readGenerationData(application, filename);
        
        if (!generationDataList.isEmpty()) {
            // Clear existing data and insert new data
            deleteAll();
            insertAll(generationDataList);
            return true;
        }
        
        return false;
    }
    
    /**
     * AsyncTask for insert operation
     */
    private static class InsertAsyncTask extends AsyncTask<GenerationData, Void, Void> {
        private final GenerationDataDao asyncTaskDao;
        
        InsertAsyncTask(GenerationDataDao dao) {
            asyncTaskDao = dao;
        }
        
        @Override
        protected Void doInBackground(final GenerationData... params) {
            asyncTaskDao.insert(params[0]);
            return null;
        }
    }
    
    /**
     * AsyncTask for insert all operation
     */
    private static class InsertAllAsyncTask extends AsyncTask<List<GenerationData>, Void, Void> {
        private final GenerationDataDao asyncTaskDao;
        
        InsertAllAsyncTask(GenerationDataDao dao) {
            asyncTaskDao = dao;
        }
        
        @SafeVarargs
        @Override
        protected final Void doInBackground(final List<GenerationData>... params) {
            asyncTaskDao.insertAll(params[0]);
            return null;
        }
    }
    
    /**
     * AsyncTask for delete all operation
     */
    private static class DeleteAllAsyncTask extends AsyncTask<Void, Void, Void> {
        private final GenerationDataDao asyncTaskDao;
        
        DeleteAllAsyncTask(GenerationDataDao dao) {
            asyncTaskDao = dao;
        }
        
        @Override
        protected Void doInBackground(Void... voids) {
            asyncTaskDao.deleteAll();
            return null;
        }
    }
}
