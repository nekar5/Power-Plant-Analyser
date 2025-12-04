package com.masters.ppa.data.repository;

import android.content.Context;

import com.masters.ppa.data.dao.ConfigBmsDao;
import com.masters.ppa.data.database.AppDatabase;
import com.masters.ppa.data.model.ConfigBms;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Repository for ConfigBms data
 */
public class ConfigBmsRepository {
    
    private final ConfigBmsDao dao;
    private final Executor executor;
    
    public ConfigBmsRepository(Context context) {
        AppDatabase database = AppDatabase.getInstance(context);
        dao = database.configBmsDao();
        executor = Executors.newSingleThreadExecutor();
    }
    
    public void insert(ConfigBms configBms) {
        executor.execute(() -> dao.insert(configBms));
    }
    
    public ConfigBms getByConfigId(int configId) {
        final ConfigBms[] result = {null};
        CountDownLatch latch = new CountDownLatch(1);
        executor.execute(() -> {
            try {
                List<ConfigBms> list = dao.getAllByConfigId(configId);
                result[0] = list != null && !list.isEmpty() ? list.get(0) : null;
            } catch (Exception e) {
                result[0] = null;
            } finally {
                latch.countDown();
            }
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            result[0] = null;
        }
        return result[0];
    }
    
    public List<ConfigBms> getAllByConfigId(int configId) {
        final List<ConfigBms>[] result = new List[]{null};
        CountDownLatch latch = new CountDownLatch(1);
        executor.execute(() -> {
            try {
                result[0] = dao.getAllByConfigId(configId);
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
    
    public void insertAll(List<ConfigBms> configBmsList) {
        executor.execute(() -> dao.insertAll(configBmsList));
    }
}

