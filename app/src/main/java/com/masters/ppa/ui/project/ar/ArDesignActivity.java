package com.masters.ppa.ui.project.ar;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.media.CamcorderProfile;
import android.view.Surface;
import com.masters.ppa.utils.VideoRecorderUtils;
import com.masters.ppa.utils.VideoSaveUtils;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.CameraConfig;
import com.google.ar.core.CameraConfigFilter;
import com.google.ar.core.Session;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.FrameTime;
import com.masters.ppa.R;
import com.masters.ppa.databinding.ActivityArDesignBinding;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ArDesignActivity extends AppCompatActivity {
    
    private static final String TAG = "ArDesignActivity";
    private static final int PERMISSION_REQUEST_CODE = 1;
    
    private ActivityArDesignBinding binding;
    private ArSceneView arSceneView;
    private Session arSession;
    private boolean isArCoreSupported = false;
    
    private enum RecordingState {
        IDLE, RECORDING
    }
    
    private RecordingState recordingState = RecordingState.IDLE;
    private File videoFile;
    private VideoRecorderUtils videoRecorderUtils;
    private Surface recordingSurface;
    
    private boolean isRulerMode = false;
    
    private int configId;
    private String configName;
    
    // Ruler mode manager
    private ArRulerManager rulerManager;
    
    // AR object manager
    private ArObjectManager objectManager;
    
    // Repositories for loading config data
    private com.masters.ppa.data.repository.ConfigInverterRepository configInverterRepository;
    private com.masters.ppa.data.repository.ConfigTowerRepository configTowerRepository;
    private com.masters.ppa.data.repository.ConfigBmsRepository configBmsRepository;
    private com.masters.ppa.data.repository.InverterItemRepository inverterItemRepository;
    private com.masters.ppa.data.repository.BatteryItemRepository batteryItemRepository;
    private com.masters.ppa.data.repository.BmsItemRepository bmsItemRepository;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Get config data from intent
        configId = getIntent().getIntExtra("config_id", -1);
        configName = getIntent().getStringExtra("config_name");
        
        // Keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        binding = ActivityArDesignBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        setupUI();
        
        // Initialize video recorder utils
        videoRecorderUtils = new VideoRecorderUtils();
        
        // Check permissions
        if (!checkPermissions()) {
            requestPermissions();
        } else {
            initializeArCore();
        }
    }
    
    private void setupUI() {
        binding.btnRecordStartStop.setOnClickListener(v -> toggleRecording());
        binding.btnRecordDiscard.setOnClickListener(v -> discardRecording());
        binding.btnRecordDiscard.setVisibility(View.GONE);
        
        binding.btnRuler.setOnClickListener(v -> {
            isRulerMode = !isRulerMode;
            updateRulerModeUI();
        });
        
        binding.btnRulerControl.setOnClickListener(v -> {
            if (isRulerMode) {
                handleRulerControlClick();
            } else {
                // In object mode, this button confirms placement
                if (objectManager != null && objectManager.hasPreview()) {
                    objectManager.placeCurrentObject();
                }
            }
        });
        
        binding.btnRulerOk.setOnClickListener(v -> {
            if (isRulerMode) {
                handleRulerOkClick();
            }
        });
        
        binding.btnRulerUndo.setOnClickListener(v -> {
            if (isRulerMode && rulerManager != null) {
                try {
                    rulerManager.removeLastPoint();
                } catch (Exception e) {
                    Log.e(TAG, "Error removing last point", e);
                }
            } else {
                // In object mode, undo last placement and allow re-placing
                if (objectManager != null) {
                    objectManager.undoLastPlacement();
                }
            }
        });
        
        binding.btnRulerRestart.setOnClickListener(v -> {
            if (isRulerMode && rulerManager != null) {
                try {
                    rulerManager.clearAllMeasurements();
                } catch (Exception e) {
                    Log.e(TAG, "Error clearing all measurements", e);
                }
            } else {
                // In object mode, reset all objects
                if (objectManager != null) {
                    objectManager.clearAll();
                }
            }
        });
        
        binding.btnFocus.setOnClickListener(v -> {
            triggerAutoFocus();
        });
        
        binding.btnAdd.setOnClickListener(v -> {
            // Automatically disable ruler mode when opening object selection dialog
            if (isRulerMode) {
                isRulerMode = false;
                updateRulerModeUI();
            }
            showObjectSelectionDialog();
        });
        
        // Update button visibility when object mode is active
        if (!isRulerMode && objectManager != null) {
            objectManager.updateButtonVisibility();
        }
    }
    
    
    private void updateRulerModeUI() {
        if (isRulerMode) {
            // Set yellow background when ruler mode is active
            binding.btnRuler.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.accent_yellow));
            binding.btnRulerControl.setVisibility(View.VISIBLE);
            binding.btnRulerRestart.setVisibility(View.VISIBLE);
            binding.crosshair.setVisibility(View.VISIBLE);
            if (rulerManager != null) {
                rulerManager.updateOkButtonVisibility();
            }
            // Hide object preview if any
            if (objectManager != null) {
                objectManager.removePreview();
            }
        } else {
            // Set transparent background when ruler mode is off (keep outline)
            binding.btnRuler.setBackgroundTintList(ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
            // In object mode, show object control buttons
            binding.btnRulerControl.setVisibility(View.VISIBLE);
            binding.btnRulerControl.setText("📍");
            binding.btnRulerUndo.setVisibility(View.VISIBLE);
            binding.btnRulerRestart.setVisibility(View.VISIBLE);
            binding.btnRulerOk.setVisibility(View.GONE);
            binding.crosshair.setVisibility(View.GONE);
            // Update button visibility based on object manager state
            if (objectManager != null) {
                objectManager.updateButtonVisibility();
            }
        }
    }
    
    private void handleRulerOkClick() {
        // Break the measurement chain
        if (rulerManager != null) {
            rulerManager.breakMeasurementChain();
        }
    }
    
    private void triggerAutoFocus() {
        if (arSession == null) {
            return;
        }
        
        try {
            // Trigger auto focus by reconfiguring AR session
            // This forces the camera to refocus
            com.google.ar.core.Config config = arSession.getConfig();
            com.google.ar.core.Config.FocusMode currentMode = config.getFocusMode();
            
            // Toggle focus mode to trigger refocus
            if (currentMode == com.google.ar.core.Config.FocusMode.AUTO) {
                config.setFocusMode(com.google.ar.core.Config.FocusMode.FIXED);
                arSession.configure(config);
                // Immediately switch back to AUTO to trigger refocus
                new Handler().postDelayed(() -> {
                    try {
                        config.setFocusMode(com.google.ar.core.Config.FocusMode.AUTO);
                        arSession.configure(config);
                        Log.d(TAG, "Auto focus triggered");
                    } catch (Exception e) {
                        Log.e(TAG, "Error setting focus mode back to AUTO", e);
                    }
                }, 50);
            } else {
                config.setFocusMode(com.google.ar.core.Config.FocusMode.AUTO);
                arSession.configure(config);
                Log.d(TAG, "Auto focus triggered");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error triggering auto focus", e);
        }
    }
    
    private void handleRulerControlClick() {
        if (rulerManager != null) {
            rulerManager.handlePointPlacement();
        }
    }
    
    
    private boolean checkPermissions() {
        boolean cameraPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
                == PackageManager.PERMISSION_GRANTED;
        boolean writePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                == PackageManager.PERMISSION_GRANTED;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return cameraPermission;
        }
        return cameraPermission && writePermission;
    }
    
    private void requestPermissions() {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.CAMERA);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        ActivityCompat.requestPermissions(this, 
                permissions.toArray(new String[0]), 
                PERMISSION_REQUEST_CODE);
    }
    
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                initializeArCore();
            } else {
                Toast.makeText(this, R.string.error_camera_permission, Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
    
    @SuppressLint("ClickableViewAccessibility")
    private void initializeArCore() {
        ArCoreApk.Availability availability = ArCoreApk.getInstance().checkAvailability(this);
        if (availability.isTransient()) {
            new Handler().postDelayed(this::initializeArCore, 200);
            return;
        }
        
        if (availability.isSupported()) {
            isArCoreSupported = true;
            arSceneView = binding.arSceneView;
            
            // Setup AR session
            setupArSession();
        } else {
            Toast.makeText(this, R.string.error_arcore_not_available, Toast.LENGTH_LONG).show();
            finish();
        }
    }
    
    private void setupArSession() {
        try {
            ArCoreApk.InstallStatus installStatus = ArCoreApk.getInstance().requestInstall(this, true);
            if (installStatus == ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
                return;
            }
            
            arSession = new Session(this);
            
            // Select best camera configuration
            CameraConfigFilter filter = new CameraConfigFilter(arSession);
            filter.setTargetFps(EnumSet.of(CameraConfig.TargetFps.TARGET_FPS_60));
            List<CameraConfig> configs = arSession.getSupportedCameraConfigs(filter);
            
            if (configs.isEmpty()) {
                filter.setTargetFps(EnumSet.of(CameraConfig.TargetFps.TARGET_FPS_30));
                configs = arSession.getSupportedCameraConfigs(filter);
            }
            
            CameraConfig bestConfig = null;
            int maxResolution = 0;
            
            for (CameraConfig cameraConfig : configs) {
                int width = cameraConfig.getImageSize().getWidth();
                int height = cameraConfig.getImageSize().getHeight();
                int resolution = width * height;
                
                if (resolution > maxResolution) {
                    maxResolution = resolution;
                    bestConfig = cameraConfig;
                }
            }
            
            if (bestConfig != null) {
                arSession.setCameraConfig(bestConfig);
            }
            
            com.google.ar.core.Config config = new com.google.ar.core.Config(arSession);
            config.setFocusMode(com.google.ar.core.Config.FocusMode.AUTO);
            config.setPlaneFindingMode(com.google.ar.core.Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL);
            config.setUpdateMode(com.google.ar.core.Config.UpdateMode.LATEST_CAMERA_IMAGE);
            
            arSession.configure(config);
            arSceneView.setupSession(arSession);
            
            // Initialize ruler manager
            rulerManager = new ArRulerManager(this, arSceneView, arSession, binding.textDistance);
            rulerManager.setStateListener(new ArRulerManager.RulerStateListener() {
                @Override
                public void onOkButtonVisibilityChanged(boolean visible) {
                    binding.btnRulerOk.setVisibility(visible ? View.VISIBLE : View.GONE);
                }
                
                @Override
                public void onUndoButtonVisibilityChanged(boolean visible) {
                    binding.btnRulerUndo.setVisibility(visible ? View.VISIBLE : View.GONE);
                }
                
                @Override
                public void onPointsCountChanged(int count) {
                    // Can be used for future functionality
                }
            });
            
            // Initialize object manager
            objectManager = new ArObjectManager(this, arSceneView, arSession);
            objectManager.setErrorCallback(message -> {
                // Show error message at top of screen
                binding.textDistance.setText(message);
                binding.textDistance.setVisibility(View.VISIBLE);
                binding.textDistance.setTextColor(android.graphics.Color.RED);
                // Hide after 3 seconds
                new Handler().postDelayed(() -> {
                    binding.textDistance.setVisibility(View.GONE);
                }, 3000);
            });
            objectManager.setButtonVisibilityCallback(new ArObjectManager.ButtonVisibilityCallback() {
                @Override
                public void onControlButtonVisibilityChanged(boolean visible) {
                    binding.btnRulerControl.setVisibility(visible ? View.VISIBLE : View.GONE);
                }
                
                @Override
                public void onUndoButtonVisibilityChanged(boolean visible) {
                    binding.btnRulerUndo.setVisibility(visible ? View.VISIBLE : View.GONE);
                }
                
                @Override
                public void onResetButtonVisibilityChanged(boolean visible) {
                    binding.btnRulerRestart.setVisibility(visible ? View.VISIBLE : View.GONE);
                }
            });
            
            // Initialize repositories
            configInverterRepository = new com.masters.ppa.data.repository.ConfigInverterRepository(this);
            configTowerRepository = new com.masters.ppa.data.repository.ConfigTowerRepository(this);
            configBmsRepository = new com.masters.ppa.data.repository.ConfigBmsRepository(this);
            inverterItemRepository = new com.masters.ppa.data.repository.InverterItemRepository(this);
            batteryItemRepository = new com.masters.ppa.data.repository.BatteryItemRepository(this);
            bmsItemRepository = new com.masters.ppa.data.repository.BmsItemRepository(this);
            
            // Setup frame update listener for preview line and objects
            arSceneView.getScene().addOnUpdateListener(this::onFrameUpdate);
            
            // Setup touch listener for object manipulation only (placement is via button)
            arSceneView.setOnTouchListener((v, event) -> {
                if (isRulerMode) {
                    return false; // Let ruler mode handle touches
                }
                
                if (objectManager != null) {
                    // Handle manipulation (drag, rotate) - no tap to place
                    if (objectManager.handleObjectManipulation(event)) {
                        return true;
                    }
                }
                return false;
            });
            
        } catch (UnavailableArcoreNotInstalledException | UnavailableDeviceNotCompatibleException 
                | UnavailableApkTooOldException | UnavailableSdkTooOldException e) {
            Log.e(TAG, "ARCore not available", e);
            Toast.makeText(this, R.string.error_arcore_not_available, Toast.LENGTH_LONG).show();
            finish();
        } catch (Exception e) {
            Log.e(TAG, "Error setting up AR session", e);
        }
    }
    
    private void onFrameUpdate(FrameTime frameTime) {
        if (isRulerMode && rulerManager != null) {
            // Update preview line through ruler manager
            rulerManager.updatePreviewLine(frameTime);
        }
        
        // Update AR object preview
        if (!isRulerMode && objectManager != null) {
            objectManager.updatePreview(frameTime);
        }
    }
    
    /**
     * Show object selection dialog
     */
    private void showObjectSelectionDialog() {
        if (configId == -1) {
            Toast.makeText(this, "Config ID не встановлено", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Load config data in background thread
        new Thread(() -> {
            try {
                // Get config items
                List<com.masters.ppa.data.model.ConfigInverter> configInverters = 
                    configInverterRepository.getByConfigId(configId);
                List<com.masters.ppa.data.model.ConfigTower> configTowers = 
                    configTowerRepository.getByConfigId(configId);
                List<com.masters.ppa.data.model.ConfigBms> configBmsList = 
                    configBmsRepository.getAllByConfigId(configId);
                
                // Get all items
                List<com.masters.ppa.data.model.InverterItem> inverterItems = 
                    inverterItemRepository.getAllSync();
                List<com.masters.ppa.data.model.BatteryItem> batteryItems = 
                    batteryItemRepository.getAllSync();
                List<com.masters.ppa.data.model.BmsItem> bmsItems = 
                    bmsItemRepository.getAllSync();
                
                // Show dialog on UI thread
                runOnUiThread(() -> {
                    // Get placed counts from object manager
                    Map<Integer, Integer> placedInverterCounts = objectManager != null ? 
                        objectManager.getPlacedInverterCounts() : new HashMap<>();
                    Map<Integer, Integer> placedTowerCounts = objectManager != null ? 
                        objectManager.getPlacedTowerCounts() : new HashMap<>();
                    Map<Integer, Integer> placedBmsCounts = objectManager != null ? 
                        objectManager.getPlacedBmsCounts() : new HashMap<>();
                    
                    SelectArObjectDialog dialog = SelectArObjectDialog.newInstance(
                        configInverters,
                        inverterItems,
                        configTowers,
                        batteryItems,
                        configBmsList,
                        bmsItems,
                        placedInverterCounts,
                        placedTowerCounts,
                        placedBmsCounts
                    );
                    
                    dialog.setOnObjectSelectedListener(new SelectArObjectDialog.OnObjectSelectedListener() {
                        @Override
                        public void onInverterSelected(
                            com.masters.ppa.data.model.ConfigInverter configInverter,
                            com.masters.ppa.data.model.InverterItem inverterItem) {
                            if (objectManager != null) {
                                objectManager.startPlacingInverter(configInverter, inverterItem);
                            }
                        }
                        
                        @Override
                        public void onTowerSelected(
                            com.masters.ppa.data.model.ConfigTower configTower,
                            com.masters.ppa.data.model.BatteryItem batteryItem,
                            com.masters.ppa.data.model.ConfigBms attachedBms,
                            com.masters.ppa.data.model.BmsItem bmsItem) {
                            if (objectManager != null) {
                                objectManager.startPlacingTower(configTower, batteryItem, attachedBms, bmsItem);
                            }
                        }
                        
                        @Override
                        public void onStandaloneBmsSelected(
                            com.masters.ppa.data.model.ConfigBms configBms,
                            com.masters.ppa.data.model.BmsItem bmsItem) {
                            if (objectManager != null) {
                                objectManager.startPlacingStandaloneBms(configBms, bmsItem);
                            }
                        }
                    });
                    
                    dialog.show(getSupportFragmentManager(), "SelectArObjectDialog");
                });
            } catch (Exception e) {
                Log.e(TAG, "Error loading config data", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Помилка завантаження даних", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
    
    
    private void toggleRecording() {
        if (recordingState == RecordingState.IDLE) {
            startRecording();
        } else {
            stopRecording();
        }
    }
    
    @SuppressLint("WrongConstant")
    private void startRecording() {
        if (arSceneView == null) {
            Toast.makeText(this, "AR scene not ready", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Toast.makeText(this, "AR recording requires Android 5.0 or higher", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            // Get ArSceneView dimensions
            int width = arSceneView.getWidth();
            int height = arSceneView.getHeight();
            
            Log.d(TAG, "ArSceneView dimensions: " + width + "x" + height);
            
            if (width <= 0 || height <= 0) {
                // Wait for view to be measured
                arSceneView.post(() -> startRecording());
                return;
            }
            
            // Create video file
            videoFile = createVideoFile();
            
            // Set video size FIRST with actual ArSceneView dimensions
            videoRecorderUtils.setVideoSize(width, height);
            Log.d(TAG, "Video size set to ArSceneView dimensions: " + width + "x" + height);
            
            // Setup video quality (for codec, bitrate, framerate) - this should NOT override size
            // Only set quality parameters, not size
            int orientation = getResources().getConfiguration().orientation;
            CamcorderProfile profile = null;
            if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_HIGH)) {
                profile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
            }
            if (profile != null) {
                // Only set codec, bitrate, framerate - NOT size
                videoRecorderUtils.setVideoCodec(profile.videoCodec);
                videoRecorderUtils.setBitRate(profile.videoBitRate);
                videoRecorderUtils.setFrameRate(profile.videoFrameRate);
                Log.d(TAG, "Video quality set: codec=" + profile.videoCodec + ", bitrate=" + profile.videoBitRate + ", framerate=" + profile.videoFrameRate);
            }
            
            Log.d(TAG, "Video size set to: " + width + "x" + height);
            
            // Setup MediaRecorder
            videoRecorderUtils.setUpMediaRecorder(videoFile);
            
            // Get Surface from MediaRecorder
            recordingSurface = videoRecorderUtils.getMediaRecorder().getSurface();
            
            // Verify Surface is valid
            if (recordingSurface == null) {
                throw new IllegalStateException("Recording surface is null");
            }
            
            Log.d(TAG, "Surface obtained, starting mirroring...");
            
            // Start mirroring ArSceneView to recording Surface
            // Parameters: surface, left, bottom, width, height
            // left: distance from left edge (0 = left edge)
            // bottom: distance from bottom edge (0 = bottom edge)
            // width, height: size of the mirrored region
            arSceneView.startMirroringToSurface(
                recordingSurface,
                0, 0, width, height
            );
            
            Log.d(TAG, "Started mirroring to surface: " + width + "x" + height);
            
            recordingState = RecordingState.RECORDING;
            updateRecordingUI();
            Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting recording", e);
            Log.e(TAG, "Exception details: " + e.getClass().getName() + ": " + e.getMessage());
            if (e.getCause() != null) {
                Log.e(TAG, "Caused by: " + e.getCause().getClass().getName() + ": " + e.getCause().getMessage());
            }
            e.printStackTrace();
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            recordingState = RecordingState.IDLE;
            updateRecordingUI();
            cleanupRecording();
        }
    }
    
    
    private void stopRecording() {
        if (videoRecorderUtils == null || !videoRecorderUtils.isRecording() || recordingState != RecordingState.RECORDING) {
            return;
        }
        
        try {
            // Stop mirroring ArSceneView to Surface
            if (recordingSurface != null && arSceneView != null) {
                arSceneView.stopMirroringToSurface(recordingSurface);
                recordingSurface = null;
            }
            
            // Stop MediaRecorder
            videoRecorderUtils.stopRecording();
            videoRecorderUtils.reset();
            
            recordingState = RecordingState.IDLE;
            updateRecordingUI();
            
            Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show();
            
            // Save video to gallery
            new Handler().postDelayed(() -> {
                if (videoFile != null && videoFile.exists()) {
                    boolean saved = VideoSaveUtils.saveVideoToGallery(this, videoFile);
                    if (saved) {
                        Toast.makeText(this, R.string.video_saved, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, R.string.error_saving_video, Toast.LENGTH_SHORT).show();
                    }
                }
            }, 500);
            
        } catch (Exception e) {
            Log.e(TAG, "Error stopping recording", e);
            Toast.makeText(this, R.string.error_saving_video, Toast.LENGTH_SHORT).show();
            recordingState = RecordingState.IDLE;
            updateRecordingUI();
        } finally {
            cleanupRecording();
        }
    }
    
    private void cleanupRecording() {
        // Stop mirroring if still active
        if (recordingSurface != null && arSceneView != null) {
            try {
                arSceneView.stopMirroringToSurface(recordingSurface);
            } catch (Exception e) {
                Log.e(TAG, "Error stopping mirroring", e);
            }
            recordingSurface = null;
        }
        
        // Release video recorder utils
        if (videoRecorderUtils != null) {
            videoRecorderUtils.release();
        }
    }
    
    private void discardRecording() {
        if (videoRecorderUtils != null && videoRecorderUtils.isRecording() && recordingState == RecordingState.RECORDING) {
            try {
                // Stop mirroring
                if (recordingSurface != null && arSceneView != null) {
                    arSceneView.stopMirroringToSurface(recordingSurface);
                    recordingSurface = null;
                }
                
                // Stop and reset recorder
                videoRecorderUtils.stopRecording();
                videoRecorderUtils.reset();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping recorder for discard", e);
            }
        }
        
        cleanupRecording();
                
                if (videoFile != null && videoFile.exists()) {
            boolean deleted = videoFile.delete();
            if (!deleted) {
                Log.w(TAG, "Failed to delete video file");
            }
                }
                
                recordingState = RecordingState.IDLE;
                updateRecordingUI();
        Toast.makeText(this, "Recording discarded", Toast.LENGTH_SHORT).show();
    }
    
    private void updateRecordingUI() {
        switch (recordingState) {
            case IDLE:
                binding.btnRecordStartStop.setText("🔴\nREC");
                binding.btnRecordDiscard.setVisibility(View.GONE);
                break;
            case RECORDING:
                binding.btnRecordStartStop.setText("💾");
                binding.btnRecordDiscard.setVisibility(View.VISIBLE);
                break;
        }
    }
    
    private File createVideoFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String videoFileName = "AR_Design_" + timeStamp + ".mp4";
        
        File moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        File storageDir = new File(moviesDir, "PowerPlantAnalyserProjects");
        
        if (!storageDir.exists()) {
            boolean created = storageDir.mkdirs();
            if (!created) {
                Log.e(TAG, "Failed to create storage directory: " + storageDir.getAbsolutePath());
            }
        }
        
        return new File(storageDir, videoFileName);
    }
    
    
    @Override
    protected void onResume() {
        super.onResume();
        if (isArCoreSupported && arSceneView != null) {
            try {
                if (arSession == null) {
                    setupArSession();
                }
                if (arSession != null) {
                arSession.resume();
                }
                arSceneView.resume();
            } catch (Exception e) {
                Log.e(TAG, "Error resuming AR session", e);
            }
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        if (arSession != null) {
            arSession.pause();
        }
        if (arSceneView != null) {
            arSceneView.pause();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        if (recordingState != RecordingState.IDLE) {
            stopRecording();
        }
        
        cleanupRecording();
        
        if (arSession != null) {
            arSession.close();
            arSession = null;
        }
    }
}



