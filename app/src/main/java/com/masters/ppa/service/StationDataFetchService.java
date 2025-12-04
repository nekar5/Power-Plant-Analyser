package com.masters.ppa.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.masters.ppa.MainActivity;
import com.masters.ppa.R;
import com.masters.ppa.data.api.SolarmanStationDataService;

import java.util.ArrayList;
import java.util.List;

/**
 * Foreground service for fetching station data in the background
 */
public class StationDataFetchService extends Service {
    
    private static final String TAG = "StationDataFetchService";
    private static final String CHANNEL_ID = "station_data_fetch_channel";
    private static final int NOTIFICATION_ID = 1;
    
    private final IBinder binder = new LocalBinder();
    private SolarmanStationDataService stationDataService;
    private List<String> progressMessages = new ArrayList<>();
    private static final int MAX_MESSAGES = 10;
    
    public interface ProgressListener {
        void onProgress(String message);
        void onComplete(String filePath, int rowCount);
        void onError(String error);
    }
    
    private ProgressListener progressListener;
    private Handler mainHandler;
    
    public class LocalBinder extends Binder {
        public StationDataFetchService getService() {
            return StationDataFetchService.this;
        }
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
        stationDataService = new SolarmanStationDataService(this);
        mainHandler = new Handler(Looper.getMainLooper());
        
        // Set progress callback
        stationDataService.setProgressCallback(message -> {
            addProgressMessage(message);
            if (progressListener != null) {
                mainHandler.post(() -> progressListener.onProgress(message));
            }
            updateNotification();
        });
        
        createNotificationChannel();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");
        
        // Try to start as foreground service (only if app is in foreground)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, createNotification("Fetching station data...", "Initializing..."), 
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NOTIFICATION_ID, createNotification("Fetching station data...", "Initializing..."));
            }
            Log.d(TAG, "Started as foreground service");
        } catch (SecurityException e) {
            Log.w(TAG, "Cannot start as foreground service (app may be in background), continuing as regular service", e);
            // Continue as regular service - will still work in background
        } catch (Exception e) {
            Log.e(TAG, "Failed to start foreground service", e);
            // Continue anyway - service will run in background
        }
        
        // Start fetching
        stationDataService.fetchStationDataForWeatherRange(
            new SolarmanStationDataService.FetchCallback() {
                @Override
                public void onSuccess(String filePath, int rowCount, String firstTimestamp, String lastTimestamp) {
                    Log.d(TAG, "Fetch completed: " + rowCount + " rows");
                    addProgressMessage("Data saved successfully");
                    updateNotification("Fetch completed", "Saved " + rowCount + " rows");
                    
                    if (progressListener != null) {
                        mainHandler.post(() -> progressListener.onComplete(filePath, rowCount));
                    }
                    
                    // Stop service after a delay
                    mainHandler.postDelayed(() -> stopSelf(), 3000);
                }

                @Override
                public void onError(String errorMessage) {
                    Log.e(TAG, "Fetch failed: " + errorMessage);
                    addProgressMessage("Error: " + errorMessage);
                    updateNotification("Fetch failed", errorMessage);
                    
                    if (progressListener != null) {
                        mainHandler.post(() -> progressListener.onError(errorMessage));
                    }
                    
                    // Stop service after a delay
                    mainHandler.postDelayed(() -> stopSelf(), 3000);
                }
            }
        );
        
        return START_NOT_STICKY; // Don't restart if killed
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed");
        if (stationDataService != null) {
            stationDataService.setProgressCallback(null);
        }
    }
    
    /**
     * Set progress listener
     */
    public void setProgressListener(ProgressListener listener) {
        this.progressListener = listener;
    }
    
    /**
     * Add progress message
     */
    private void addProgressMessage(String message) {
        synchronized (progressMessages) {
            progressMessages.add(message);
            if (progressMessages.size() > MAX_MESSAGES) {
                progressMessages.remove(0);
            }
        }
    }
    
    /**
     * Get progress messages
     */
    public List<String> getProgressMessages() {
        synchronized (progressMessages) {
            return new ArrayList<>(progressMessages);
        }
    }
    
    /**
     * Create notification channel
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Station Data Fetch",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows progress of station data fetch");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
    
    /**
     * Create notification
     */
    private Notification createNotification(String title, String content) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setProgress(0, 0, true);
        
        return builder.build();
    }
    
    /**
     * Update notification
     */
    private void updateNotification() {
        updateNotification("Fetching station data...", getLatestMessage());
    }
    
    /**
     * Update notification with specific text
     */
    private void updateNotification(String title, String content) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            Notification notification = createNotification(title, content);
            manager.notify(NOTIFICATION_ID, notification);
        }
    }
    
    /**
     * Get latest message
     */
    private String getLatestMessage() {
        synchronized (progressMessages) {
            if (progressMessages.isEmpty()) {
                return "Processing...";
            }
            return progressMessages.get(progressMessages.size() - 1);
        }
    }
}

