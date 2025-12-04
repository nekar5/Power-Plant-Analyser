package com.masters.ppa.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.masters.ppa.data.model.ProjectConfig;

import java.util.List;

/**
 * Data Access Object for ProjectConfig entity
 */
@Dao
public interface ProjectConfigDao {
    
    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(ProjectConfig config);
    
    @Delete
    void delete(ProjectConfig config);
    
    @Query("DELETE FROM project_configs")
    void deleteAll();
    
    @Query("SELECT * FROM project_configs ORDER BY createdAt DESC")
    LiveData<List<ProjectConfig>> getAll();
    
    @Query("SELECT * FROM project_configs ORDER BY createdAt DESC")
    List<ProjectConfig> getAllSync();
    
    @Query("SELECT * FROM project_configs WHERE id = :id")
    ProjectConfig getById(int id);
    
    @Query("SELECT COUNT(*) FROM project_configs")
    int getCount();
}

