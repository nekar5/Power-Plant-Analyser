package com.masters.ppa.data.repository;

import android.content.Context;

import com.masters.ppa.data.dao.ConfigInverterDao;
import com.masters.ppa.data.database.AppDatabase;
import com.masters.ppa.data.model.ConfigInverter;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Repository for ConfigInverter data
 */
public class ConfigInverterRepository {
    
    private final ConfigInverterDao dao;
    private final Executor executor;
    
    public ConfigInverterRepository(Context context) {
        AppDatabase database = AppDatabase.getInstance(context);
        dao = database.configInverterDao();
        executor = Executors.newSingleThreadExecutor();
    }
    
    public void insertAll(List<ConfigInverter> configInverters) {
        executor.execute(() -> dao.insertAll(configInverters));
    }
    
    public List<ConfigInverter> getByConfigId(int configId) {
        final List<ConfigInverter>[] result = new List[]{null};
        CountDownLatch latch = new CountDownLatch(1);
        executor.execute(() -> {
            try {
                result[0] = dao.getByConfigId(configId);
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

