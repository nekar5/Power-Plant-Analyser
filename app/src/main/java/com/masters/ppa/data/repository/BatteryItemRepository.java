package com.masters.ppa.data.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.masters.ppa.data.dao.BatteryItemDao;
import com.masters.ppa.data.database.AppDatabase;
import com.masters.ppa.data.model.BatteryItem;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Repository for BatteryItem data
 */
public class BatteryItemRepository {
    
    private final BatteryItemDao dao;
    private final LiveData<List<BatteryItem>> allItems;
    private final Executor executor;
    
    public BatteryItemRepository(Context context) {
        AppDatabase database = AppDatabase.getInstance(context);
        dao = database.batteryItemDao();
        allItems = dao.getAll();
        executor = Executors.newSingleThreadExecutor();
    }
    
    public LiveData<List<BatteryItem>> getAll() {
        return allItems;
    }
    
    public void insert(BatteryItem item) {
        executor.execute(() -> dao.insert(item));
    }
    
    public void delete(BatteryItem item) {
        executor.execute(() -> dao.delete(item));
    }
    
    public int countByName(String name) {
        final int[] result = {0};
        CountDownLatch latch = new CountDownLatch(1);
        executor.execute(() -> {
            try {
                result[0] = dao.countByName(name);
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
    
    public List<BatteryItem> getAllSync() {
        final List<BatteryItem>[] result = new List[]{null};
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
    
    public BatteryItem getById(int id) {
        final BatteryItem[] result = {null};
        CountDownLatch latch = new CountDownLatch(1);
        executor.execute(() -> {
            try {
                result[0] = dao.getById(id);
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
}

