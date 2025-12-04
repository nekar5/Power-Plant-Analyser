package com.masters.ppa.data.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.masters.ppa.data.dao.InverterItemDao;
import com.masters.ppa.data.database.AppDatabase;
import com.masters.ppa.data.model.InverterItem;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Repository for InverterItem data
 */
public class InverterItemRepository {
    
    private final InverterItemDao dao;
    private final LiveData<List<InverterItem>> allItems;
    private final Executor executor;
    
    public InverterItemRepository(Context context) {
        AppDatabase database = AppDatabase.getInstance(context);
        dao = database.inverterItemDao();
        allItems = dao.getAll();
        executor = Executors.newSingleThreadExecutor();
    }
    
    public LiveData<List<InverterItem>> getAll() {
        return allItems;
    }
    
    public void insert(InverterItem item) {
        executor.execute(() -> dao.insert(item));
    }
    
    public void delete(InverterItem item) {
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
    
    public List<InverterItem> getAllSync() {
        final List<InverterItem>[] result = new List[]{null};
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
    
    public InverterItem getById(int id) {
        final InverterItem[] result = {null};
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


