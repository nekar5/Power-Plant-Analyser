package com.masters.ppa.ui.project;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.masters.ppa.data.model.BatteryItem;
import com.masters.ppa.data.model.BmsItem;
import com.masters.ppa.data.model.ConfigBms;
import com.masters.ppa.data.model.ConfigInverter;
import com.masters.ppa.data.model.ConfigTower;
import com.masters.ppa.data.model.InverterItem;
import com.masters.ppa.data.model.ProjectConfig;
import com.masters.ppa.data.repository.BatteryItemRepository;
import com.masters.ppa.data.repository.BmsItemRepository;
import com.masters.ppa.data.repository.ConfigBmsRepository;
import com.masters.ppa.data.repository.ConfigInverterRepository;
import com.masters.ppa.data.repository.ConfigTowerRepository;
import com.masters.ppa.data.repository.InverterItemRepository;
import com.masters.ppa.data.repository.ProjectConfigRepository;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * ViewModel for the Project screen
 */
public class ProjectViewModel extends AndroidViewModel {

    private final InverterItemRepository inverterRepository;
    private final BatteryItemRepository batteryRepository;
    private final BmsItemRepository bmsRepository;
    private final ProjectConfigRepository configRepository;
    private final ConfigInverterRepository configInverterRepository;
    private final ConfigTowerRepository configTowerRepository;
    private final ConfigBmsRepository configBmsRepository;
    private final Executor executor;
    
    public ProjectViewModel(@NonNull Application application) {
        super(application);
        Context context = application.getApplicationContext();
        inverterRepository = new InverterItemRepository(context);
        batteryRepository = new BatteryItemRepository(context);
        bmsRepository = new BmsItemRepository(context);
        configRepository = new ProjectConfigRepository(context);
        configInverterRepository = new ConfigInverterRepository(context);
        configTowerRepository = new ConfigTowerRepository(context);
        configBmsRepository = new ConfigBmsRepository(context);
        executor = Executors.newSingleThreadExecutor();
    }

    // Inverter methods
    public LiveData<List<InverterItem>> getInverterItems() {
        return inverterRepository.getAll();
    }
    
    public void insertInverter(InverterItem item) {
        inverterRepository.insert(item);
    }
    
    public void deleteInverter(InverterItem item) {
        inverterRepository.delete(item);
    }
    
    public int checkInverterNameExists(String name) {
        return inverterRepository.countByName(name);
    }
    
    // Battery methods
    public LiveData<List<BatteryItem>> getBatteryItems() {
        return batteryRepository.getAll();
    }
    
    public void insertBattery(BatteryItem item) {
        batteryRepository.insert(item);
    }
    
    public void deleteBattery(BatteryItem item) {
        batteryRepository.delete(item);
    }
    
    public int checkBatteryNameExists(String name) {
        return batteryRepository.countByName(name);
    }
    
    // BMS methods
    public LiveData<List<BmsItem>> getBmsItems() {
        return bmsRepository.getAll();
    }
    
    public void insertBms(BmsItem item) {
        bmsRepository.insert(item);
    }
    
    public void deleteBms(BmsItem item) {
        bmsRepository.delete(item);
    }
    
    public int checkBmsNameExists(String name) {
        return bmsRepository.countByName(name);
    }
    
    // Config methods
    public LiveData<List<ProjectConfig>> getConfigs() {
        return configRepository.getAll();
    }
    
    public long insertConfig(ProjectConfig config) {
        return configRepository.insert(config);
    }
    
    public void deleteConfig(ProjectConfig config) {
        configRepository.delete(config);
    }
    
    public int getConfigCount() {
        return configRepository.getCount();
    }
    
    // Check if can add config (need at least 1 of each type)
    public LiveData<Boolean> canAddConfig() {
        MutableLiveData<Boolean> result = new MutableLiveData<>();
        executor.execute(() -> {
            try {
                boolean hasInverter = inverterRepository.getAllSync().size() > 0;
                boolean hasBattery = batteryRepository.getAllSync().size() > 0;
                boolean hasBms = bmsRepository.getAllSync().size() > 0;
                result.postValue(hasInverter && hasBattery && hasBms);
            } catch (Exception e) {
                result.postValue(false);
            }
        });
        return result;
    }
    
    public boolean canAddConfigSync() {
        try {
            boolean hasInverter = inverterRepository.getAllSync().size() > 0;
            boolean hasBattery = batteryRepository.getAllSync().size() > 0;
            boolean hasBms = bmsRepository.getAllSync().size() > 0;
            return hasInverter && hasBattery && hasBms;
        } catch (Exception e) {
            return false;
        }
    }
    
    // Config detail methods
    public void saveConfigInverters(List<ConfigInverter> configInverters) {
        configInverterRepository.insertAll(configInverters);
    }
    
    public void saveConfigTowers(List<ConfigTower> configTowers) {
        configTowerRepository.insertAll(configTowers);
    }
    
    public void saveConfigBms(ConfigBms configBms) {
        configBmsRepository.insert(configBms);
    }
    
    // Get config details
    public List<ConfigInverter> getConfigInverters(int configId) {
        return configInverterRepository.getByConfigId(configId);
    }
    
    public List<ConfigTower> getConfigTowers(int configId) {
        return configTowerRepository.getByConfigId(configId);
    }
    
    public ConfigBms getConfigBms(int configId) {
        return configBmsRepository.getByConfigId(configId);
    }
    
    public List<ConfigBms> getAllConfigBms(int configId) {
        return configBmsRepository.getAllByConfigId(configId);
    }
    
    public void saveConfigBmsList(List<ConfigBms> configBmsList) {
        configBmsRepository.insertAll(configBmsList);
    }
}
