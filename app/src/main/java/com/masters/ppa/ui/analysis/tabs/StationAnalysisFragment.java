package com.masters.ppa.ui.analysis.tabs;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.masters.ppa.MainActivity;
import com.masters.ppa.R;
import com.masters.ppa.data.api.SolarmanStationDataService;
import com.masters.ppa.data.api.WeatherApiService;
import com.masters.ppa.data.model.StationConfig;
import com.masters.ppa.data.repository.StationConfigRepository;
import com.masters.ppa.databinding.FragmentStationAnalysisBinding;
import com.masters.ppa.service.StationDataFetchService;
import com.masters.ppa.ui.analysis.AnalysisViewModel;
import com.masters.ppa.utils.CsvUtils;
import com.masters.ppa.utils.FileUtils;
import com.masters.ppa.utils.NetworkUtils;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;

import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Fragment for the Station Analysis tab in Analysis screen
 */
public class StationAnalysisFragment extends Fragment implements NetworkUtils.NetworkStatusListener {

    private static final String TAG = "StationAnalysisFragment";
    private static final String WEATHER_DATA_CSV = "weather_data.csv";
    private static final String STATION_DATA_CSV = "station_data.csv";
    private static final String CSV_DIR = "csv";
    private static final int MAX_PROGRESS_MESSAGES = 10;
    
    private FragmentStationAnalysisBinding binding;
    private AnalysisViewModel viewModel;
    private StationConfigRepository stationConfigRepository;
    private WeatherApiService weatherApiService;
    private SolarmanStationDataService stationDataService;
    private StationDataFetchService fetchService;
    private boolean serviceBound = false;
    private boolean isFetching = false;
    private Executor executor;
    private Handler mainHandler;
    
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            StationDataFetchService.LocalBinder binder = (StationDataFetchService.LocalBinder) service;
            fetchService = binder.getService();
            serviceBound = true;
            
            // Set progress listener
            fetchService.setProgressListener(new StationDataFetchService.ProgressListener() {
                @Override
                public void onProgress(String message) {
                    mainHandler.post(() -> {
                        updateProgress("Fetching station data...", message);
                    });
                }

                @Override
                public void onComplete(String filePath, int rowCount) {
                    mainHandler.post(() -> {
                        isFetching = false;
                        if (binding != null && isAdded()) {
                            binding.btnFetchStation.setEnabled(true);
                            hideProgress();
                            updateLastRequestTime();
                            updateCsvDates();
                            updateConfigInfo();
                            Log.d(TAG, "Station data fetched successfully: " + rowCount + " rows");
                        }
                        unbindService();
                    });
                }

                @Override
                public void onError(String error) {
                    mainHandler.post(() -> {
                        isFetching = false;
                        if (binding != null && isAdded()) {
                            binding.btnFetchStation.setEnabled(true);
                            hideProgress();
                            // Show error message about incomplete data
                            showIncompleteDataWarning(error);
                            Log.e(TAG, "Station data fetch failed: " + error);
                        }
                        unbindService();
                    });
                }
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
            fetchService = null;
        }
    };
    
    private void unbindService() {
        if (serviceBound && getContext() != null) {
            try {
                getContext().unbindService(serviceConnection);
                serviceBound = false;
            } catch (Exception e) {
                Log.e(TAG, "Error unbinding service", e);
            }
        }
    }
    
    private SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
    private SimpleDateFormat shortDateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentStationAnalysisBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireParentFragment()).get(AnalysisViewModel.class);
        
        // Initialize repositories and services
        stationConfigRepository = new StationConfigRepository(requireContext());
        weatherApiService = new WeatherApiService(requireContext());
        stationDataService = new SolarmanStationDataService(requireContext());
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        
        // Setup button listeners
        binding.btnFetchWeather.setOnClickListener(v -> fetchWeatherData());
        binding.btnFetchStation.setOnClickListener(v -> fetchStationData());
        binding.btnStationAnalysis.setOnClickListener(v -> showStationAnalysis());
        binding.btnHideRecommendations.setOnClickListener(v -> hideStationAnalysis());
        
        updateLastRequestTime();
        updateCsvDates();
        updateConfigInfo();
        NetworkUtils.registerNetworkCallback(requireContext(), this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        NetworkUtils.unregisterNetworkCallback(requireContext(), this);
        if (stationDataService != null) {
            stationDataService.setProgressCallback(null);
        }
        unbindService();
        binding = null;
    }
    
    @Override
    public void onNetworkStatusChanged(boolean isConnected, NetworkUtils.NetworkType networkType) {
        if (binding == null || binding.connectionStatus == null) return;
        
        if (isConnected) {
            String networkName = networkType == NetworkUtils.NetworkType.WIFI ? "Wi-Fi" : 
                                (networkType == NetworkUtils.NetworkType.MOBILE ? "Mobile" : "Ethernet");
            binding.connectionStatus.setText("Online (" + networkName + ")");
            binding.connectionStatus.setCompoundDrawablesWithIntrinsicBounds(R.drawable.status_indicator_online, 0, 0, 0);
        } else {
            binding.connectionStatus.setText("Offline");
            binding.connectionStatus.setCompoundDrawablesWithIntrinsicBounds(R.drawable.status_indicator_offline, 0, 0, 0);
        }
    }
    
    /**
     * Fetch weather data for last 3 months
     */
    private void fetchWeatherData() {
        binding.btnFetchWeather.setEnabled(false);
        showProgress("Fetching weather data...", "Connecting to API...");
        
        // Get station config on background thread
        executor.execute(() -> {
            StationConfig config = stationConfigRepository.getStationConfigSync();
            
            if (config == null) {
                mainHandler.post(() -> {
                    if (binding == null || !isAdded() || getActivity() == null) return;
                    binding.btnFetchWeather.setEnabled(true);
                    hideProgress();
                    Log.e(TAG, "Station configuration not found");
                });
                return;
            }
            
            mainHandler.post(() -> {
                if (binding == null || !isAdded()) return;
                updateProgress("Fetching weather data...", "Requesting data from API...");
            });
            
            // Fetch weather data for last 3 months
            weatherApiService.fetch3MonthsWeather(
                config.getLatitude(), 
                config.getLongitude(),
                new WeatherApiService.FetchCallback() {
                    @Override
                    public void onSuccess(String filePath, int rowCount, String firstTimestamp, String lastTimestamp) {
                        mainHandler.post(() -> {
                            if (binding == null || !isAdded()) return;
                            updateProgress("Fetching weather data...", "Saving data to file...");
                        });
                        
                        // Copy file to weather_data.csv
                        copyToWeatherDataCsv(filePath);
                        
                        mainHandler.post(() -> {
                            if (binding == null || !isAdded() || getActivity() == null) return;
                            binding.btnFetchWeather.setEnabled(true);
                            hideProgress();
                            updateLastRequestTime();
                            updateCsvDates();
                            Log.d(TAG, "Weather data fetched successfully: " + rowCount + " rows");
                        });
                    }

                    @Override
                    public void onError(String errorMessage) {
                        mainHandler.post(() -> {
                            if (binding == null || !isAdded() || getActivity() == null) return;
                            binding.btnFetchWeather.setEnabled(true);
                            hideProgress();
                            Log.e(TAG, "Weather data fetch failed: " + errorMessage);
                        });
                    }
                }
            );
        });
    }
    
    /**
     * Show progress dialog
     */
    private void showProgress(String title, String message) {
        if (binding == null || !isAdded()) return;
        
        binding.cardProgress.setVisibility(View.VISIBLE);
        binding.textProgressTitle.setText(title);
        
        // Clear existing messages
        LinearLayout messagesLayout = binding.layoutProgressMessages;
        messagesLayout.removeAllViews();
        
        // Add initial message
        addProgressMessage(message);
        
        // Block navigation
        blockNavigation(true);
    }
    
    /**
     * Update progress message
     */
    private void updateProgress(String title, String message) {
        if (binding == null || !isAdded()) return;
        
        if (binding.cardProgress.getVisibility() == View.VISIBLE) {
            binding.textProgressTitle.setText(title);
            addProgressMessage(message);
        }
    }
    
    /**
     * Add a progress message to the scrollable list (max 10 messages)
     */
    private void addProgressMessage(String message) {
        if (binding == null || !isAdded()) return;
        
        LinearLayout messagesLayout = binding.layoutProgressMessages;
        
        // Remove oldest message if we have 10
        if (messagesLayout.getChildCount() >= MAX_PROGRESS_MESSAGES) {
            messagesLayout.removeViewAt(0);
        }
        
        // Create new TextView for message
        TextView messageView = new TextView(requireContext());
        messageView.setText(message);
        messageView.setTextSize(12);
        messageView.setPadding(4, 4, 4, 4);
        messageView.setGravity(Gravity.CENTER);
        messageView.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        
        messagesLayout.addView(messageView);
        
        // Scroll to bottom
        binding.getRoot().post(() -> {
            if (binding.cardProgress.getVisibility() == View.VISIBLE) {
                androidx.core.widget.NestedScrollView scrollView = binding.scrollProgressMessages;
                if (scrollView != null) {
                    scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
                }
            }
        });
    }
    
    /**
     * Hide progress dialog
     */
    private void hideProgress() {
        if (binding == null || !isAdded()) return;
        
        binding.cardProgress.setVisibility(View.GONE);
        
        // Unblock navigation
        blockNavigation(false);
    }
    
    /**
     * Block/unblock navigation during fetch
     */
    private void blockNavigation(boolean block) {
        if (getActivity() == null) return;
        
        MainActivity mainActivity = (MainActivity) getActivity();
        if (mainActivity != null) {
            mainActivity.setNavigationEnabled(!block);
        }
    }
    
    /**
     * Copy weather data file to weather_data.csv
     */
    private void copyToWeatherDataCsv(String sourceFilePath) {
        executor.execute(() -> {
            Context context = getContext();
            if (context == null) return;
            
            try {
                File sourceFile = new File(sourceFilePath);
                if (!sourceFile.exists()) {
                    Log.e(TAG, "Source file does not exist: " + sourceFilePath);
                    return;
                }
                
                File csvDir = new File(context.getFilesDir(), CSV_DIR);
                if (!csvDir.exists()) {
                    boolean created = csvDir.mkdirs();
                    if (!created) {
                        Log.e(TAG, "Failed to create CSV directory");
                        return;
                    }
                }
                File destFile = new File(csvDir, WEATHER_DATA_CSV);
                
                // Copy file
                java.nio.file.Files.copy(
                    sourceFile.toPath(),
                    destFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                );
                
                Log.d(TAG, "Weather data copied to: " + destFile.getAbsolutePath());
            } catch (Exception e) {
                Log.e(TAG, "Error copying weather data file", e);
            }
        });
    }
    
    /**
     * Fetch station operational data using foreground service
     */
    private void fetchStationData() {
        if (isFetching) return; // Prevent multiple simultaneous fetches
        
        isFetching = true;
        binding.btnFetchStation.setEnabled(false);
        showProgress("Fetching station data...", "Starting service...");
        
        // Start service (will try to become foreground if possible)
        Intent serviceIntent = new Intent(requireContext(), StationDataFetchService.class);
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Try to start as foreground service
                requireContext().startForegroundService(serviceIntent);
            } else {
                requireContext().startService(serviceIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start service", e);
            // Fallback: use direct service call
            fetchStationDataDirectly();
            return;
        }
        
        // Bind to service
        try {
            requireContext().bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        } catch (Exception e) {
            Log.e(TAG, "Failed to bind service", e);
            // Fallback: use direct service call
            fetchStationDataDirectly();
        }
    }
    
    /**
     * Fetch station data directly (fallback if service fails)
     */
    private void fetchStationDataDirectly() {
        if (binding == null || !isAdded()) return;
        
        binding.btnFetchStation.setEnabled(false);
        showProgress("Fetching station data...", "Initializing...");
        
        // Set progress callback
        stationDataService.setProgressCallback(message -> {
            mainHandler.post(() -> {
                if (binding != null && isAdded()) {
                    updateProgress("Fetching station data...", message);
                }
            });
        });
        
        // Start fetching
        stationDataService.fetchStationDataForWeatherRange(
            new SolarmanStationDataService.FetchCallback() {
                @Override
                public void onSuccess(String filePath, int rowCount, String firstTimestamp, String lastTimestamp) {
                    mainHandler.post(() -> {
                        isFetching = false;
                        if (binding != null && isAdded()) {
                            binding.btnFetchStation.setEnabled(true);
                            hideProgress();
                            updateLastRequestTime();
                            updateCsvDates();
                            updateConfigInfo();
                            Log.d(TAG, "Station data fetched successfully: " + rowCount + " rows");
                        }
                    });
                }

                @Override
                public void onError(String errorMessage) {
                    mainHandler.post(() -> {
                        isFetching = false;
                        if (binding != null && isAdded()) {
                            binding.btnFetchStation.setEnabled(true);
                            hideProgress();
                            // Show error message about incomplete data
                            showIncompleteDataWarning(errorMessage);
                            Log.e(TAG, "Station data fetch failed: " + errorMessage);
                        }
                    });
                }
            }
        );
    }
    
    /**
     * Show warning about incomplete data
     */
    private void showIncompleteDataWarning(String error) {
        if (binding == null || !isAdded() || getContext() == null) return;
        
        android.widget.Toast.makeText(
            getContext(),
            "Incomplete data loaded. Please make a new request. Error: " + error,
            android.widget.Toast.LENGTH_LONG
        ).show();
    }
    
    /**
     * Update last request time display
     */
    private void updateLastRequestTime() {
        Context context = getContext();
        if (context == null || binding == null || !isAdded()) return;
        
        Date weatherLastModified = FileUtils.getLastModifiedDate(context, WEATHER_DATA_CSV, CSV_DIR);
        
        if (weatherLastModified != null) {
            String timeStr = "Last request: " + shortDateTimeFormat.format(weatherLastModified);
            binding.textWeatherLastRequest.setText(timeStr);
        } else {
            binding.textWeatherLastRequest.setText("Last request: Never");
        }
        
        // Station data
        Date stationLastModified = FileUtils.getLastModifiedDate(context, STATION_DATA_CSV, CSV_DIR);
        if (stationLastModified != null) {
            String timeStr = "Last request: " + shortDateTimeFormat.format(stationLastModified);
            binding.textStationLastRequest.setText(timeStr);
        } else {
            binding.textStationLastRequest.setText("Last request: Never");
        }
    }
    
    /**
     * Update CSV date range display
     */
    private void updateCsvDates() {
        executor.execute(() -> {
            Context context = getContext();
            if (context == null) return;
            
            String weatherFilePath = FileUtils.getFilePath(context, WEATHER_DATA_CSV, CSV_DIR);
            String[] weatherDateRange = CsvUtils.readDateRangeFromCsv(weatherFilePath);
            
            String stationFilePath = FileUtils.getFilePath(context, STATION_DATA_CSV, CSV_DIR);
            String[] stationDateRange = CsvUtils.readDateRangeFromCsv(stationFilePath);
            
            mainHandler.post(() -> {
                if (binding == null || !isAdded() || getActivity() == null) return;
                
                if (weatherDateRange != null && weatherDateRange.length == 2) {
                    String datesStr = "Dates range: " + weatherDateRange[0] + " - " + weatherDateRange[1];
                    binding.textWeatherDates.setText(datesStr);
                } else {
                    binding.textWeatherDates.setText("Dates range: No data");
                }
                
                // Station data
                if (stationDateRange != null && stationDateRange.length == 2) {
                    String datesStr = "Dates range: " + stationDateRange[0] + " - " + stationDateRange[1];
                    binding.textStationDates.setText(datesStr);
                } else {
                    binding.textStationDates.setText("Dates range: No data");
                }
            });
        });
    }
    
    /**
     * Update configuration info display
     */
    private void updateConfigInfo() {
        executor.execute(() -> {
            Context context = getContext();
            if (context == null) return;
            
            // Get metadata
            JSONObject metadata = stationDataService.getMetadata();
            String configLastChangedStr = metadata.optString("configLastChanged", "");
            String lastFetchDate = metadata.optString("lastFetchDate", "");
            
            // Parse config last changed date
            Date configLastChanged = null;
            if (!configLastChangedStr.isEmpty()) {
                try {
                    configLastChanged = shortDateTimeFormat.parse(configLastChangedStr);
                } catch (Exception e) {
                    try {
                        configLastChanged = dateTimeFormat.parse(configLastChangedStr);
                    } catch (Exception e2) {
                        Log.w(TAG, "Error parsing config last changed date", e2);
                    }
                }
            }
            
            // If not in metadata, try to get from database file modification time
            if (configLastChanged == null) {
                try {
                    File dbFile = context.getDatabasePath("app_database");
                    if (dbFile.exists()) {
                        configLastChanged = new Date(dbFile.lastModified());
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Error getting config last modified from database", e);
                }
            }

            Date finalConfigLastChanged = configLastChanged;
            mainHandler.post(() -> {
                if (binding == null || !isAdded() || getActivity() == null) return;
                
                // Config info
                StringBuilder configInfo = new StringBuilder();
                if (finalConfigLastChanged != null) {
                    configInfo.append("Config last changed: ").append(shortDateTimeFormat.format(finalConfigLastChanged));
                }
                
                if (!lastFetchDate.isEmpty()) {
                    if (configInfo.length() > 0) {
                        configInfo.append("\n");
                    }
                    configInfo.append("Last fetch for config: ").append(lastFetchDate);
                }
                
                if (configInfo.length() > 0) {
                    binding.textConfigInfo.setText(configInfo.toString());
                    binding.textConfigInfo.setVisibility(View.VISIBLE);
                } else {
                    binding.textConfigInfo.setVisibility(View.GONE);
                }
            });
        });
    }

    /**
     * Show station configuration analysis
     */
    private void showStationAnalysis() {
        executor.execute(() -> {
            StationConfig config = stationConfigRepository.getStationConfigSync();
            
            mainHandler.post(() -> {
                if (binding == null || !isAdded()) return;
                
                // Generate recommendations using ViewModel (includes validation)
                List<String> recommendations = viewModel.generateStationRecommendations(config);
                
                // Check if first recommendation is an error message
                if (recommendations.size() == 1 && 
                    (recommendations.get(0).contains("not found") || 
                     recommendations.get(0).contains("Incomplete configuration"))) {
                    android.widget.Toast.makeText(getContext(), 
                        recommendations.get(0), 
                        android.widget.Toast.LENGTH_LONG).show();
                    return;
                }
                
                // Clear previous recommendations
                binding.layoutStationRecommendations.removeAllViews();
                
                // Add recommendation cards
                String[] titles = {
                    "PV vs Inverter Sizing",
                    "Battery-to-PV Ratio", 
                    "Tilt Suitability",
                    "Battery Operational Strategy"
                };
                
                for (int i = 0; i < recommendations.size() && i < titles.length; i++) {
                    MaterialCardView card = createRecommendationCard(titles[i], recommendations.get(i));
                    binding.layoutStationRecommendations.addView(card);
                }
                
                // Show recommendations and hide button
                binding.layoutStationRecommendations.setVisibility(View.VISIBLE);
                binding.btnHideRecommendations.setVisibility(View.VISIBLE);
            });
        });
    }

    /**
     * Hide station analysis recommendations
     */
    private void hideStationAnalysis() {
        if (binding == null) return;
        
        binding.layoutStationRecommendations.setVisibility(View.GONE);
        binding.btnHideRecommendations.setVisibility(View.GONE);
    }

    /**
     * Create a recommendation card
     */
    private MaterialCardView createRecommendationCard(String title, String content) {
        MaterialCardView card = new MaterialCardView(requireContext());
        
        // Card styling
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, 16);
        card.setLayoutParams(cardParams);
        card.setCardElevation(4f);
        card.setRadius(8f);
        
        // Inner layout
        LinearLayout innerLayout = new LinearLayout(requireContext());
        innerLayout.setOrientation(LinearLayout.VERTICAL);
        innerLayout.setPadding(16, 16, 16, 16);
        
        // Title
        TextView titleView = new TextView(requireContext());
        titleView.setText(title);
        titleView.setTextSize(16f);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        titleParams.setMargins(0, 0, 0, 12);
        titleView.setLayoutParams(titleParams);
        
        // Content
        TextView contentView = new TextView(requireContext());
        contentView.setText(content);
        contentView.setTextSize(14f);
        contentView.setLineSpacing(0f, 1.3f);
        contentView.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        
        innerLayout.addView(titleView);
        innerLayout.addView(contentView);
        card.addView(innerLayout);
        
        return card;
    }
}
