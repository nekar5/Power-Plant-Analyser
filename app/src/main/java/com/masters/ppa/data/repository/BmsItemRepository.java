package com.masters.ppa.data.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.masters.ppa.data.dao.BmsItemDao;
import com.masters.ppa.data.database.AppDatabase;
import com.masters.ppa.data.model.BmsItem;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Repository for BmsItem data
 */
public class BmsItemRepository {
    
    private final BmsItemDao dao;
    private final LiveData<List<BmsItem>> allItems;
    private final Executor executor;
    
    public BmsItemRepository(Context context) {
        AppDatabase database = AppDatabase.getInstance(context);
        dao = database.bmsItemDao();
        allItems = dao.getAll();
        executor = Executors.newSingleThreadExecutor();
    }
    
    public LiveData<List<BmsItem>> getAll() {
        return allItems;
    }
    
    public void insert(BmsItem item) {
        executor.execute(() -> dao.insert(item));
    }
    
    public void delete(BmsItem item) {
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
    
    public List<BmsItem> getAllSync() {
        final List<BmsItem>[] result = new List[]{null};
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
    
    public BmsItem getById(int id) {
        final BmsItem[] result = {null};
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

