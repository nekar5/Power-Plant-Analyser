package com.masters.ppa.data.repository;

import android.content.Context;

import com.masters.ppa.data.dao.ConfigTowerDao;
import com.masters.ppa.data.database.AppDatabase;
import com.masters.ppa.data.model.ConfigTower;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Repository for ConfigTower data
 */
public class ConfigTowerRepository {
    
    private final ConfigTowerDao dao;
    private final Executor executor;
    
    public ConfigTowerRepository(Context context) {
        AppDatabase database = AppDatabase.getInstance(context);
        dao = database.configTowerDao();
        executor = Executors.newSingleThreadExecutor();
    }
    
    public void insertAll(List<ConfigTower> configTowers) {
        executor.execute(() -> dao.insertAll(configTowers));
    }
    
    public List<ConfigTower> getByConfigId(int configId) {
        final List<ConfigTower>[] result = new List[]{null};
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

