package com.masters.ppa.ui.analysis.tabs;

import static android.content.ContentValues.TAG;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.masters.ppa.MainActivity;
import com.masters.ppa.R;
import com.masters.ppa.data.model.GenerationData;
import com.masters.ppa.databinding.FragmentGenerationForecastBinding;
import com.masters.ppa.ml.ForecastProcessor;
import com.masters.ppa.ui.analysis.AnalysisViewModel;
import com.masters.ppa.utils.ChartUtils;
import com.masters.ppa.utils.FileUtils;
import com.masters.ppa.utils.StateUtils;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fragment for the Generation Forecast tab in Analysis screen
 */
public class GenerationForecastFragment extends Fragment {

    private FragmentGenerationForecastBinding binding;
    private AnalysisViewModel viewModel;
    private ProgressBar progressBar;
    private TextView tvSummary;
    private LineChart chart;
    private LineChart chartDaily;
    
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    private static final int MAX_PROGRESS_MESSAGES = 10;
    

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentGenerationForecastBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireParentFragment()).get(AnalysisViewModel.class);
        
        // Initialize views
        chart = binding.chartGeneration;
        chartDaily = binding.chartDaily;
        progressBar = view.findViewById(R.id.progressBar);
        tvSummary = view.findViewById(R.id.tvSummary);
        
        // If tvSummary doesn't exist in layout, create it programmatically or use textLastUpdated
        if (tvSummary == null) {
            tvSummary = binding.textLastUpdated;
        }
        if (progressBar == null) {
            // progressBar can be null - we'll handle that
        }
        
        // Hide operational warning by default
        binding.textOperationalWarning.setVisibility(View.GONE);
        
        setupChart();
        setupObservers();
        setupListeners();
        
        // Load saved state if available
        loadSavedState();
    }
    
    /**
     * Load saved state from SharedPreferences
     */
    private void loadSavedState() {
        List<GenerationData> savedData = StateUtils.loadForecastData(requireContext());
        if (savedData != null && !savedData.isEmpty()) {
            updateGenerationChart(savedData);
            
            // Update timestamp if available
            long timestamp = StateUtils.getForecastDataTimestamp(requireContext());
            if (timestamp > 0) {
                binding.textLastUpdated.setText(getString(R.string.last_updated, 
                    FileUtils.formatDate(new java.util.Date(timestamp))));
                binding.textLastUpdated.setVisibility(View.VISIBLE);
            }
        }
    }
    
    /**
     * Setup generation charts
     */
    private void setupChart() {
        ChartUtils.configureLineChart(binding.chartGeneration, requireContext(), true);
        ChartUtils.configureLineChart(binding.chartDaily, requireContext(), true);
        
        // Disable touch interactions to allow scrolling to table
        binding.chartGeneration.setTouchEnabled(false);
        binding.chartGeneration.setDragEnabled(false);
        binding.chartGeneration.setScaleEnabled(false);
        binding.chartGeneration.setPinchZoom(false);
        
        binding.chartDaily.setTouchEnabled(false);
        binding.chartDaily.setDragEnabled(false);
        binding.chartDaily.setScaleEnabled(false);
        binding.chartDaily.setPinchZoom(false);
    }
    
    /**
     * Setup observers for LiveData
     */
    private void setupObservers() {
        // Last updated date
        viewModel.getGenerationLastUpdatedDate().observe(getViewLifecycleOwner(), lastUpdated -> {
            if (lastUpdated != null) {
                binding.textLastUpdated.setText(getString(R.string.last_updated, FileUtils.formatDate(lastUpdated)));
                binding.textLastUpdated.setVisibility(View.VISIBLE);
            } else {
                binding.textLastUpdated.setVisibility(View.GONE);
            }
        });
        
        // Generation data
        viewModel.getAllGenerationData().observe(getViewLifecycleOwner(), generationDataList -> {
            if (generationDataList != null && !generationDataList.isEmpty()) {
                updateGenerationChart(generationDataList);
                // Save state when data is updated
                StateUtils.saveForecastData(requireContext(), generationDataList);
            }
        });
        
        // Loading state
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            binding.btnForecast.setEnabled(!isLoading);
            if (progressBar != null) {
                progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            }
        });
    }
    
    /**
     * Setup button click listeners
     */
    private void setupListeners() {
        binding.btnForecast.setOnClickListener(v -> runForecast());
    }
    
    /**
     * Run normal forecast using weather data from API
     */
    private void runForecast() {
        // Hide warning at the start of forecast
        binding.textOperationalWarning.setVisibility(View.GONE);
        
        binding.btnForecast.setEnabled(false);
        showProgress("Forecast...", "Initializing...");
        
        // Block navigation during forecast
        blockNavigation(true);

        executor.execute(() -> {
            try {
                ForecastProcessor processor = new ForecastProcessor(requireContext().getApplicationContext());
                
                // Set progress callback
                processor.setProgressCallback(message -> {
                    mainHandler.post(() -> {
                        if (binding != null && isAdded()) {
                            updateProgress("Forecast...", message);
                        }
                    });
                });
                
                ForecastProcessor.ForecastResult result = processor.runForecast();

                // Calculate daily aggregation (kWh)
                Map<LocalDate, Float> dateToEnergyKwh = new HashMap<>();
                for (int i = 0; i < result.predictedPowerW.size(); i++) {
                    LocalDate date = result.dates.get(i);
                    float powerW = result.predictedPowerW.get(i);
                    // Hourly data: daily energy increment (kWh) = powerW / 1000
                    float energyKwh = powerW / 1000f;
                    dateToEnergyKwh.put(date, dateToEnergyKwh.getOrDefault(date, 0f) + energyKwh);
                }

                // Find min and max daily energy
                float minDailyKwh = Float.MAX_VALUE;
                float maxDailyKwh = Float.MIN_VALUE;
                for (Float dailyKwh : dateToEnergyKwh.values()) {
                    if (dailyKwh < minDailyKwh) minDailyKwh = dailyKwh;
                    if (dailyKwh > maxDailyKwh) maxDailyKwh = dailyKwh;
                }
                if (minDailyKwh == Float.MAX_VALUE) minDailyKwh = 0f;
                if (maxDailyKwh == Float.MIN_VALUE) maxDailyKwh = 0f;

                // Sort dates for chart
                List<LocalDate> sortedDates = new ArrayList<>(dateToEnergyKwh.keySet());
                sortedDates.sort(LocalDate::compareTo);
                List<Float> dailyEnergyKwh = new ArrayList<>();
                for (LocalDate date : sortedDates) {
                    dailyEnergyKwh.add(dateToEnergyKwh.get(date));
                }

                float finalMinDaily = minDailyKwh;
                float finalMaxDaily = maxDailyKwh;
                boolean finalOperationalDataFound = result.operationalDataFound;
                boolean finalCalibrationPerformed = result.calibrationPerformed;
                requireActivity().runOnUiThread(() -> {
                    hideProgress();
                    updateForecastCharts(result.predictedPowerW, result.dates, sortedDates, dailyEnergyKwh, "Forecast");
                    updateForecastTable(result, sortedDates, dailyEnergyKwh);
                    updateForecastRecommendations(result, sortedDates, dailyEnergyKwh);
                    updateOperationalDataWarning(finalOperationalDataFound, finalCalibrationPerformed);
                    if (tvSummary != null) {
                        tvSummary.setText(String.format(Locale.getDefault(), 
                            "Predicted daily energy: min %.2f kWh, max %.2f kWh", 
                            finalMinDaily, finalMaxDaily));
                    }
                    binding.btnForecast.setEnabled(true);
                    blockNavigation(false);
                    
                    // Save forecast result - convert to GenerationData list for saving
                    // Note: Forecast result is different from GenerationData, but we save what we can
                    // The chart data is already saved via updateGenerationChart when LiveData updates
                });

            } catch (Exception e) {
                e.printStackTrace();
                requireActivity().runOnUiThread(() -> {
                    hideProgress();
                    binding.textOperationalWarning.setVisibility(View.GONE);
                    binding.btnForecast.setEnabled(true);
                    blockNavigation(false);
                    showToast("Error during forecast: " + e.getMessage());
                });
            }
        });
    }
    
    /**
     * Show progress dialog
     */
    private void showProgress(String title, String message) {
        if (binding == null || !isAdded()) return;
        
        binding.cardProgress.setVisibility(View.VISIBLE);
        binding.textProgressTitle.setText(title);
        
        LinearLayout messagesLayout = binding.layoutProgressMessages;
        messagesLayout.removeAllViews();
        
        addProgressMessage(message);
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
     * Add a progress message
     */
    private void addProgressMessage(String message) {
        if (binding == null || !isAdded()) return;
        
        LinearLayout messagesLayout = binding.layoutProgressMessages;
        
        if (messagesLayout.getChildCount() >= MAX_PROGRESS_MESSAGES) {
            messagesLayout.removeViewAt(0);
        }
        
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
        
        binding.getRoot().post(() -> {
            if (binding.cardProgress.getVisibility() == View.VISIBLE) {
                binding.scrollProgressMessages.post(() -> 
                    binding.scrollProgressMessages.fullScroll(View.FOCUS_DOWN));
            }
        });
    }
    
    /**
     * Hide progress dialog
     */
    private void hideProgress() {
        if (binding == null || !isAdded()) return;
        
        binding.cardProgress.setVisibility(View.GONE);
    }
    
    /**
     * Update forecast recommendations
     */
    private void updateForecastRecommendations(ForecastProcessor.ForecastResult result, 
                                             List<LocalDate> sortedDates, List<Float> dailyEnergyKwh) {
        if (result == null || dailyEnergyKwh == null || dailyEnergyKwh.isEmpty()) {
            binding.cardForecastRecommendation.setVisibility(View.GONE);
            return;
        }

        try {
            // Convert Float to Double for ViewModel
            List<Double> dailyGenerationKwh = new ArrayList<>();
            for (Float dailyKwh : dailyEnergyKwh) {
                dailyGenerationKwh.add(dailyKwh.doubleValue());
            }

            // Get real configuration values - observe once and remove observer
            viewModel.getStationConfig().observe(getViewLifecycleOwner(), stationConfig -> {
                // Get real values if available, otherwise use defaults
                double inverterPowerKw = (stationConfig != null && stationConfig.getInverterPowerKw() > 0) 
                    ? stationConfig.getInverterPowerKw() : 10.0;
                double latitude = (stationConfig != null && stationConfig.getLatitude() >= -90 && stationConfig.getLatitude() <= 90) 
                    ? stationConfig.getLatitude() : 50.0; // Default to Ukraine
                
                // Get current month
                int currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1; // Calendar.MONTH is 0-based

                // Generate recommendations using ViewModel with real config values
                String forecastSummary = viewModel.generateForecastSummary(
                    dailyGenerationKwh, inverterPowerKw, latitude, currentMonth);

                // Split summary and recommendation
                String[] parts = forecastSummary.split("Recommendation:");
                if (binding != null) {
                    binding.textForecastSummary.setText(parts[0].trim());
                    
                    if (parts.length > 1) {
                        binding.textForecastRecommendation.setText(parts[1].trim());
                    } else {
                        binding.textForecastRecommendation.setText("No specific recommendations available.");
                    }

                    binding.cardForecastRecommendation.setVisibility(View.VISIBLE);
                }
                
                // Remove observer after first use to avoid repeated calls
                viewModel.getStationConfig().removeObservers(getViewLifecycleOwner());
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Error updating forecast recommendations", e);
            if (binding != null) {
                binding.cardForecastRecommendation.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Block/unblock navigation during forecast
     */
    private void blockNavigation(boolean block) {
        if (getActivity() == null) return;
        
        mainHandler.post(() -> {
            // Block main navigation (between Settings, Project, Analysis, Weather)
            MainActivity mainActivity = (MainActivity) getActivity();
            if (mainActivity != null) {
                mainActivity.setNavigationEnabled(!block);
            }
            
            // Block tab navigation (between tabs in Analysis)
            Fragment parentFragment = getParentFragment();
            if (parentFragment instanceof com.masters.ppa.ui.analysis.AnalysisFragment) {
                com.masters.ppa.ui.analysis.AnalysisFragment analysisFragment = 
                    (com.masters.ppa.ui.analysis.AnalysisFragment) parentFragment;
                analysisFragment.setTabNavigationEnabled(!block);
            }
        });
    }
    
    /**
     * Update charts with forecast predictions (detailed W and daily kWh)
     */
    private void updateForecastCharts(List<Float> predictionsW, List<LocalDate> dates, 
                                      List<LocalDate> sortedDates, List<Float> dailyEnergyKwh, String title) {
        // Chart A: Detailed chart (time steps vs W)
        List<Entry> detailedEntries = new ArrayList<>();
        for (int i = 0; i < predictionsW.size(); i++) {
            detailedEntries.add(new Entry(i, predictionsW.get(i)));
        }

        LineDataSet detailedDataSet = new LineDataSet(detailedEntries, "Predicted PV Power (W)");
        detailedDataSet.setColor(getResources().getColor(R.color.chart_blue, null));
        detailedDataSet.setCircleColor(getResources().getColor(R.color.chart_blue, null));
        detailedDataSet.setLineWidth(2f);
        detailedDataSet.setCircleRadius(4f);
        detailedDataSet.setDrawCircleHole(true);
        detailedDataSet.setCircleHoleRadius(2f);
        detailedDataSet.setValueTextSize(10f);
        detailedDataSet.setValueTextColor(Color.WHITE);
        detailedDataSet.setDrawValues(false);
        detailedDataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        detailedDataSet.setDrawFilled(true);
        detailedDataSet.setFillColor(getResources().getColor(R.color.chart_blue, null));
        detailedDataSet.setFillAlpha(50);

        LineData detailedLineData = new LineData(detailedDataSet);
        chart.setData(detailedLineData);

        // Hide X-axis labels for upper chart
        chart.getXAxis().setDrawLabels(false);

        chart.invalidate();

        // Chart B: Daily aggregated chart (date vs kWh)
        List<Entry> dailyEntries = new ArrayList<>();
        for (int i = 0; i < dailyEnergyKwh.size(); i++) {
            dailyEntries.add(new Entry(i, dailyEnergyKwh.get(i)));
        }

        LineDataSet dailyDataSet = new LineDataSet(dailyEntries, "Predicted Daily Energy (kWh)");
        dailyDataSet.setColor(getResources().getColor(R.color.chart_blue, null));
        dailyDataSet.setCircleColor(getResources().getColor(R.color.chart_blue, null));
        dailyDataSet.setLineWidth(2f);
        dailyDataSet.setCircleRadius(4f);
        dailyDataSet.setDrawCircleHole(true);
        dailyDataSet.setCircleHoleRadius(2f);
        dailyDataSet.setValueTextSize(10f);
        dailyDataSet.setValueTextColor(Color.WHITE);
        dailyDataSet.setDrawValues(true);
        dailyDataSet.setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.format(Locale.getDefault(), "%.1f", value);
            }
        });
        dailyDataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dailyDataSet.setDrawFilled(true);
        dailyDataSet.setFillColor(getResources().getColor(R.color.chart_blue, null));
        dailyDataSet.setFillAlpha(50);

        LineData dailyLineData = new LineData(dailyDataSet);
        chartDaily.setData(dailyLineData);

        // Set X-axis labels with dates
        if (!sortedDates.isEmpty()) {
            List<String> dateLabels = new ArrayList<>();
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM");
            for (LocalDate date : sortedDates) {
                dateLabels.add(date.format(dateFormatter));
            }
            ChartUtils.setXAxisLabels(chartDaily, dateLabels);
        }

        // Show daily chart
        chartDaily.setVisibility(View.VISIBLE);
        chartDaily.invalidate();
    }
    
    /**
     * Update forecast table with daily aggregated data
     */
    private void updateForecastTable(ForecastProcessor.ForecastResult result, 
                                     List<LocalDate> sortedDates, 
                                     List<Float> dailyEnergyKwh) {
        if (result == null || sortedDates.isEmpty() || dailyEnergyKwh.isEmpty()) {
            binding.scrollForecastTable.setVisibility(View.GONE);
            return;
        }
        
        TableLayout tableLayout = binding.tableForecast;
        tableLayout.removeAllViews();
        
        // Aggregate weather data by date
        Map<LocalDate, List<Float>> dateToTemps = new HashMap<>();
        Map<LocalDate, List<Float>> dateToClouds = new HashMap<>();
        Map<LocalDate, List<Float>> dateToIrrs = new HashMap<>();
        
        for (int i = 0; i < result.dates.size(); i++) {
            LocalDate date = result.dates.get(i);
            if (!dateToTemps.containsKey(date)) {
                dateToTemps.put(date, new ArrayList<>());
                dateToClouds.put(date, new ArrayList<>());
                dateToIrrs.put(date, new ArrayList<>());
            }
            dateToTemps.get(date).add(result.temperatures.get(i));
            dateToClouds.get(date).add(result.cloudCovers.get(i));
            dateToIrrs.get(date).add(result.irradiances.get(i));
        }
        
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        
        // Create header row
        TableRow headerRow = createTableRow(true);
        addTableCell(headerRow, "Date", true);
        addTableCell(headerRow, "Temp(°C)", true);
        addTableCell(headerRow, "Clouds(%)", true);
        addTableCell(headerRow, "Irr(kWh/m²)", true);
        addTableCell(headerRow, "Pred. Gen (kWh)", true);
        tableLayout.addView(headerRow);
        
        // Create data rows
        for (int i = 0; i < sortedDates.size(); i++) {
            LocalDate date = sortedDates.get(i);
            float genKwh = dailyEnergyKwh.get(i);
            
            // Calculate averages
            List<Float> temps = dateToTemps.get(date);
            List<Float> clouds = dateToClouds.get(date);
            List<Float> irrs = dateToIrrs.get(date);
            
            float avgTemp = 0f;
            float avgCloud = 0f;
            float totalIrrKwh = 0f;
            
            if (temps != null && !temps.isEmpty()) {
                float sum = 0f;
                for (Float t : temps) sum += t;
                avgTemp = sum / temps.size();
            }
            
            if (clouds != null && !clouds.isEmpty()) {
                float sum = 0f;
                for (Float c : clouds) sum += c;
                avgCloud = sum / clouds.size();
            }
            
            if (irrs != null && !irrs.isEmpty()) {
                // Sum irradiance in W/m², then convert to kWh/m²
                // Matching Python: x.sum() / 1000
                float sum = 0f;
                for (Float irr : irrs) sum += irr;
                totalIrrKwh = sum / 1000f;
            }
            
            TableRow dataRow = createTableRow(false);
            addTableCell(dataRow, date.format(dateFormatter), false);
            addTableCell(dataRow, String.format(Locale.getDefault(), "%.2f", avgTemp), false);
            addTableCell(dataRow, String.format(Locale.getDefault(), "%.1f", avgCloud), false);
            addTableCell(dataRow, String.format(Locale.getDefault(), "%.2f", totalIrrKwh), false);
            addTableCell(dataRow, String.format(Locale.getDefault(), "%.2f", genKwh), false);
            tableLayout.addView(dataRow);
        }
        
        binding.scrollForecastTable.setVisibility(View.VISIBLE);
    }
    
    /**
     * Create a table row with styling
     */
    private TableRow createTableRow(boolean isHeader) {
        TableRow row = new TableRow(requireContext());
        TableRow.LayoutParams params = new TableRow.LayoutParams(
            TableRow.LayoutParams.MATCH_PARENT,
            TableRow.LayoutParams.WRAP_CONTENT
        );
        row.setLayoutParams(params);
        if (isHeader) {
            row.setBackgroundColor(getResources().getColor(R.color.card_dark, null));
        } else {
            // Add divider between rows
            row.setPadding(0, 0, 0, 1);
            row.setBackgroundColor(getResources().getColor(R.color.surface_dark, null));
        }
        return row;
    }
    
    /**
     * Add a cell to table row
     */
    private void addTableCell(TableRow row, String text, boolean isHeader) {
        TextView cell = new TextView(requireContext());
        TableRow.LayoutParams params = new TableRow.LayoutParams(
            0,
            TableRow.LayoutParams.WRAP_CONTENT,
            1.0f
        );
        params.setMargins(12, 16, 12, 16);
        cell.setLayoutParams(params);
        cell.setText(text);
        cell.setTextSize(isHeader ? 13 : 12);
        cell.setTextColor(getResources().getColor(
            isHeader ? R.color.text_primary_dark : R.color.text_secondary_dark, null));
        cell.setGravity(android.view.Gravity.CENTER);
        if (isHeader) {
            cell.setTypeface(null, android.graphics.Typeface.BOLD);
        }
        // Add padding for better readability
        cell.setPadding(8, 8, 8, 8);
        row.addView(cell);
    }
    
    /**
     * Update operational data warning visibility
     * Shows warning only if calibration was not performed (no historical data)
     */
    private void updateOperationalDataWarning(boolean operationalDataFound, boolean calibrationPerformed) {
        // Hide warning if calibration was performed (historical data exists)
        // Show warning only if no historical data for calibration
        if (calibrationPerformed) {
            binding.textOperationalWarning.setVisibility(View.GONE);
        } else {
            binding.textOperationalWarning.setVisibility(View.VISIBLE);
            binding.textOperationalWarning.setText("⚠ Operational data not found for calibration");
        }
    }
    
    /**
     * Show toast message
     */
    private void showToast(String msg) {
        requireActivity().runOnUiThread(() ->
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        );
    }
    
    /**
     * Update generation chart with data
     * @param generationDataList List of generation data
     */
    private void updateGenerationChart(List<GenerationData> generationDataList) {
        // Split data into actual and predicted
        List<GenerationData> actualData = new ArrayList<>();
        List<GenerationData> predictedData = new ArrayList<>();
        
        for (GenerationData data : generationDataList) {
            if (data.isActual()) {
                actualData.add(data);
            } else {
                predictedData.add(data);
            }
        }
        
        // Create entries for actual data
        List<Entry> actualEntries = ChartUtils.createGenerationEntries(actualData, true);
        
        // Create entries for predicted data
        List<Entry> predictedEntries = ChartUtils.createGenerationEntries(predictedData, false);
        
        // Create datasets
        LineDataSet actualDataSet = ChartUtils.createLineDataSet(
                actualEntries, 
                "Actual Generation (kWh)", 
                getResources().getColor(R.color.chart_blue, null),
                null);
        
        LineDataSet predictedDataSet = ChartUtils.createLineDataSet(
                predictedEntries, 
                "Predicted Generation (kWh)", 
                getResources().getColor(R.color.chart_orange, null),
                null);
        
        // Hide X-axis labels for upper chart
        binding.chartGeneration.getXAxis().setDrawLabels(false);
        
        // Set data to chart
        LineData lineData = new LineData(actualDataSet, predictedDataSet);
        binding.chartGeneration.setData(lineData);
        binding.chartGeneration.invalidate();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Unblock navigation when fragment is destroyed
        blockNavigation(false);
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
        binding = null;
    }
}
