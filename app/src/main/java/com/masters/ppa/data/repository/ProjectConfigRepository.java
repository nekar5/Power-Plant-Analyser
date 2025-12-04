package com.masters.ppa.data.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.masters.ppa.data.dao.ProjectConfigDao;
import com.masters.ppa.data.database.AppDatabase;
import com.masters.ppa.data.model.ProjectConfig;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Repository for ProjectConfig data
 */
public class ProjectConfigRepository {
    
    private final ProjectConfigDao dao;
    private final LiveData<List<ProjectConfig>> allConfigs;
    private final Executor executor;
    
    public ProjectConfigRepository(Context context) {
        AppDatabase database = AppDatabase.getInstance(context);
        dao = database.projectConfigDao();
        allConfigs = dao.getAll();
        executor = Executors.newSingleThreadExecutor();
    }
    
    public LiveData<List<ProjectConfig>> getAll() {
        return allConfigs;
    }
    
    public long insert(ProjectConfig config) {
        final long[] result = {0};
        CountDownLatch latch = new CountDownLatch(1);
        executor.execute(() -> {
            try {
                result[0] = dao.insert(config);
            } catch (Exception e) {
                result[0] = 0;
            } finally {
                latch.countDown();
            }
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            result[0] = 0;
        }
        return result[0];
    }
    
    public void delete(ProjectConfig config) {
        executor.execute(() -> dao.delete(config));
    }
    
    public int getCount() {
        final int[] count = {0};
        CountDownLatch latch = new CountDownLatch(1);
        executor.execute(() -> {
            try {
                count[0] = dao.getCount();
            } catch (Exception e) {
                count[0] = 0;
            } finally {
                latch.countDown();
            }
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            count[0] = 0;
        }
        return count[0];
    }
    
    public List<ProjectConfig> getAllSync() {
        final List<ProjectConfig>[] result = new List[]{null};
        CountDownLatch latch = new CountDownLatch(1);
        executor.execute(() -> {
            try {
                result[0] = dao.getAllSync();
            } catch (Exception e) {
                result[0] = new java.util.ArrayList<>();
            } finally {
                latch.countDown();
            }
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            result[0] = new java.util.ArrayList<>();
        }
        return result[0];
    }
}

