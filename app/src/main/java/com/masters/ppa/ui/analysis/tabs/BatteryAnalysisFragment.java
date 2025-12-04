package com.masters.ppa.ui.analysis.tabs;

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
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.LegendEntry;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.masters.ppa.MainActivity;
import com.masters.ppa.R;
import com.masters.ppa.databinding.FragmentBatteryAnalysisBinding;
import com.masters.ppa.ml.BatteryProcessor;
import com.masters.ppa.ui.analysis.AnalysisViewModel;
import com.masters.ppa.utils.ChartUtils;
import com.masters.ppa.utils.FileUtils;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fragment for the Battery Analysis tab in Analysis screen
 */
public class BatteryAnalysisFragment extends Fragment {

    private static final String TAG = "BatteryAnalysisFragment";
    private static final int MAX_PROGRESS_MESSAGES = 10;

    private FragmentBatteryAnalysisBinding binding;
    private AnalysisViewModel viewModel;
    private BatteryProcessor batteryProcessor;
    private ExecutorService executor;
    private Handler mainHandler;
    
    // Class labels matching BatteryProcessor
    private static final String[] CLASS_LABELS = {
        "Oversized/Idle",
        "Balanced",
        "Undersized/High stress"
    };
    
    private static final int[] CLASS_COLORS = {
        Color.parseColor("#FFA500"), // Orange
        Color.parseColor("#00FF00"), // Green
        Color.parseColor("#FF0000")  // Red
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentBatteryAnalysisBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireParentFragment()).get(AnalysisViewModel.class);
        
        batteryProcessor = new BatteryProcessor(requireContext().getApplicationContext());
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        
        setupCharts();
        setupListeners();
    }
    
    /**
     * Setup charts configuration
     */
    private void setupCharts() {
        ChartUtils.configureLineChart(binding.chartStressUtil, requireContext(), true);
        ChartUtils.configureLineChart(binding.chartUsageClass, requireContext(), true);
        ChartUtils.configureLineChart(binding.chartSocTemp, requireContext(), true);
        
        // Disable touch interactions to allow scrolling
        binding.chartStressUtil.setTouchEnabled(false);
        binding.chartStressUtil.setDragEnabled(false);
        binding.chartStressUtil.setScaleEnabled(false);
        binding.chartStressUtil.setPinchZoom(false);
        
        binding.chartUsageClass.setTouchEnabled(false);
        binding.chartUsageClass.setDragEnabled(false);
        binding.chartUsageClass.setScaleEnabled(false);
        binding.chartUsageClass.setPinchZoom(false);
        
        binding.chartSocTemp.setTouchEnabled(false);
        binding.chartSocTemp.setDragEnabled(false);
        binding.chartSocTemp.setScaleEnabled(false);
        binding.chartSocTemp.setPinchZoom(false);
    }
    
    /**
     * Setup button click listeners
     */
    private void setupListeners() {
        binding.btnAnalyse.setOnClickListener(v -> runAnalysis());
    }
    
    /**
     * Run battery analysis
     */
    private void runAnalysis() {
        // Check data availability first
        executor.execute(() -> {
            boolean hasStationData = checkDataFile("csv/station_data.csv");
            boolean hasWeatherData = checkDataFile("csv/weather_data.csv");
            
            if (!hasStationData || !hasWeatherData) {
                String message = "";
                if (!hasStationData && !hasWeatherData) {
                    message = "Operational and weather data not found. Please load data on Station page.";
                } else if (!hasStationData) {
                    message = "Operational data not found. Please load data on Station page.";
                } else {
                    message = "Weather data not found. Please load data on Station page.";
                }

                String finalMessage = message;
                mainHandler.post(() -> {
                    showError(finalMessage);
                });
                return;
            }
            
            // Start analysis
            mainHandler.post(() -> {
                binding.btnAnalyse.setEnabled(false);
                showProgress("Battery Analysis...", "Initializing...");
                blockNavigation(true);
                
                // Set progress callback
                batteryProcessor.setProgressCallback(message -> {
                    mainHandler.post(() -> {
                        if (binding != null && isAdded()) {
                            updateProgress("Battery Analysis...", message);
                        }
                    });
                });
                
                executor.execute(() -> {
                    try {
                        BatteryProcessor.BatteryResult result = batteryProcessor.runAnalysis();
                        
                        mainHandler.post(() -> {
                            if (binding != null && isAdded()) {
                                hideProgress();
                                binding.btnAnalyse.setEnabled(true);
                                blockNavigation(false);
                                displayResults(result);
                            }
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "Error running battery analysis", e);
                        mainHandler.post(() -> {
                            if (binding != null && isAdded()) {
                                hideProgress();
                                binding.btnAnalyse.setEnabled(true);
                                blockNavigation(false);
                                showError("Analysis error: " + e.getMessage());
                            }
                        });
                    }
                });
            });
        });
    }
    
    /**
     * Check if data file exists and is not empty
     */
    private boolean checkDataFile(String relativePath) {
        try {
            java.io.File dataFile = new java.io.File(requireContext().getFilesDir(), relativePath);
            return dataFile.exists() && dataFile.length() > 0;
        } catch (Exception e) {
            Log.e(TAG, "Error checking data file: " + relativePath, e);
            return false;
        }
    }
    
    /**
     * Display analysis results
     */
    private void displayResults(BatteryProcessor.BatteryResult result) {
        if (result == null || result.dates.isEmpty()) {
            showError("No results to display");
            return;
        }
        
        // Update charts
        updateStressUtilizationChart(result);
        updateUsageClassChart(result);
        updateSocTempChart(result);
        
        // Update recommendation and table
        updateRecommendation(result);
        updateResultTable(result);
    }
    
    /**
     * Update stress and utilization chart
     */
    private void updateStressUtilizationChart(BatteryProcessor.BatteryResult result) {
        List<Entry> stressEntries = new ArrayList<>();
        List<Entry> utilEntries = new ArrayList<>();
        
        for (int i = 0; i < result.dates.size(); i++) {
            stressEntries.add(new Entry(i, result.stress[i]));
            utilEntries.add(new Entry(i, result.utilization[i]));
        }
        
        LineDataSet stressDataSet = ChartUtils.createLineDataSet(
            stressEntries, "Stress", 
            getResources().getColor(R.color.chart_red, null), null);
        stressDataSet.setDrawCircles(false); // Remove circles on line points
        
        LineDataSet utilDataSet = ChartUtils.createLineDataSet(
            utilEntries, "Utilization", 
            getResources().getColor(R.color.chart_blue, null), null);
        utilDataSet.setDrawCircles(false); // Remove circles on line points
        
        LineData lineData = new LineData(stressDataSet, utilDataSet);
        binding.chartStressUtil.setData(lineData);
        binding.chartStressUtil.getDescription().setText("");
        
        // Set date labels
        List<String> dateLabels = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM");
        for (LocalDate date : result.dates) {
            dateLabels.add(date.format(formatter));
        }
        ChartUtils.setXAxisLabels(binding.chartStressUtil, dateLabels);
        
        binding.textChart1Title.setVisibility(View.VISIBLE);
        binding.chartStressUtil.setVisibility(View.VISIBLE);
        binding.chartStressUtil.invalidate();
    }
    
    /**
     * Update usage class chart
     */
    private void updateUsageClassChart(BatteryProcessor.BatteryResult result) {
        List<Entry> classEntries = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        
        for (int i = 0; i < result.dates.size(); i++) {
            classEntries.add(new Entry(i, result.classIds[i]));
            colors.add(CLASS_COLORS[result.classIds[i]]);
        }
        
        LineDataSet classDataSet = new LineDataSet(classEntries, "Usage Class");
        classDataSet.setDrawCircles(true);
        classDataSet.setDrawValues(false);
        classDataSet.setCircleColors(colors);
        classDataSet.setCircleRadius(5f);
        classDataSet.setLineWidth(0f); // No line, only points
        classDataSet.setColor(Color.TRANSPARENT);
        
        LineData lineData = new LineData(classDataSet);
        binding.chartUsageClass.setData(lineData);
        binding.chartUsageClass.getDescription().setText("");
        
        // Configure Y axis for classes
        YAxis leftAxis = binding.chartUsageClass.getAxisLeft();
        leftAxis.setAxisMinimum(-0.5f);
        leftAxis.setAxisMaximum(2.5f);
        leftAxis.setLabelCount(3, true);
        leftAxis.setGranularity(1f);
        leftAxis.setGranularityEnabled(true);
        leftAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int idx = (int) Math.round(value);
                if (idx >= 0 && idx < CLASS_LABELS.length) {
                    // Show only colored circle for Y axis
                    return "●";
                }
                return "";
            }
        });
        
        // Force Y axis to show all 3 values (0, 1, 2)
        leftAxis.setAxisMinimum(-0.5f);
        leftAxis.setAxisMaximum(2.5f);
        leftAxis.setLabelCount(3, true);
        
        // Disable right Y axis
        binding.chartUsageClass.getAxisRight().setEnabled(false);
        
        // Configure legend to show at bottom
        Legend legend = binding.chartUsageClass.getLegend();
        legend.setEnabled(true);
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        legend.setDrawInside(false);
        legend.setForm(Legend.LegendForm.CIRCLE);
        legend.setFormSize(12f);
        legend.setTextSize(12f);
        legend.setXEntrySpace(20f);
        
        // Create custom legend entries
        List<LegendEntry> legendEntries = new ArrayList<>();
        for (int i = 0; i < CLASS_LABELS.length; i++) {
            LegendEntry entry = new LegendEntry();
            entry.label = CLASS_LABELS[i];
            entry.formColor = CLASS_COLORS[i];
            entry.form = Legend.LegendForm.CIRCLE;
            legendEntries.add(entry);
        }
        legend.setCustom(legendEntries);
        
        // Set date labels
        List<String> dateLabels = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM");
        for (LocalDate date : result.dates) {
            dateLabels.add(date.format(formatter));
        }
        ChartUtils.setXAxisLabels(binding.chartUsageClass, dateLabels);
        
        binding.textChart2Title.setVisibility(View.VISIBLE);
        binding.chartUsageClass.setVisibility(View.VISIBLE);
        binding.chartUsageClass.invalidate();
    }
    
    /**
     * Update SoC and temperature chart
     */
    private void updateSocTempChart(BatteryProcessor.BatteryResult result) {
        if (result.rawData == null || result.rawData.isEmpty()) {
            binding.chartSocTemp.setVisibility(View.GONE);
            return;
        }
        
        List<Entry> socEntries = new ArrayList<>();
        List<Entry> tempEntries = new ArrayList<>();
        
        for (int i = 0; i < result.rawData.size(); i++) {
            BatteryProcessor.RawDataRow row = result.rawData.get(i);
            socEntries.add(new Entry(i, row.socClean));
            tempEntries.add(new Entry(i, row.battTempC));
        }
        
        LineDataSet socDataSet = ChartUtils.createLineDataSet(
            socEntries, "SoC [%]", 
            getResources().getColor(R.color.chart_blue, null), null);
        socDataSet.setDrawCircles(false); // Remove circles on line points
        socDataSet.setAxisDependency(YAxis.AxisDependency.LEFT); // Left Y axis for SoC
        
        LineDataSet tempDataSet = ChartUtils.createLineDataSet(
            tempEntries, "Battery Temp [°C]", 
            getResources().getColor(R.color.chart_red, null), null);
        tempDataSet.setDrawCircles(false); // Remove circles on line points
        tempDataSet.setAxisDependency(YAxis.AxisDependency.RIGHT); // Right Y axis for temperature
        
        // Configure right Y axis for temperature (0-50°C)
        YAxis rightAxis = binding.chartSocTemp.getAxisRight();
        rightAxis.setEnabled(true);
        rightAxis.setAxisMinimum(0f);
        rightAxis.setAxisMaximum(50f);
        rightAxis.setTextColor(getResources().getColor(R.color.chart_red, null));
        rightAxis.setDrawGridLines(false);
        
        // Configure left Y axis for SoC (0-100%)
        YAxis leftAxis = binding.chartSocTemp.getAxisLeft();
        leftAxis.setAxisMinimum(0f);
        leftAxis.setAxisMaximum(100f);
        leftAxis.setTextColor(getResources().getColor(R.color.chart_blue, null));
        
        LineData lineData = new LineData(socDataSet, tempDataSet);
        binding.chartSocTemp.setData(lineData);
        binding.chartSocTemp.getDescription().setText("");
        
        // Set date labels for SoC/Temperature chart - use same approach as other charts
        List<String> dateLabels = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM");
        
        // Create labels based on data points, not raw data size
        int dataPointsCount = Math.min(socEntries.size(), result.dates.size());
        for (int i = 0; i < dataPointsCount; i++) {
            if (i < result.dates.size()) {
                dateLabels.add(result.dates.get(i).format(formatter));
            }
        }
        ChartUtils.setXAxisLabels(binding.chartSocTemp, dateLabels);
        
        binding.textChart3Title.setVisibility(View.VISIBLE);
        binding.chartSocTemp.setVisibility(View.VISIBLE);
        binding.chartSocTemp.invalidate();
    }
    
    /**
     * Update recommendation card
     */
    private void updateRecommendation(BatteryProcessor.BatteryResult result) {
        if (result.classIds == null || result.classIds.length == 0) {
            binding.cardRecommendation.setVisibility(View.GONE);
            return;
        }
        
        // Use ViewModel to generate summary and recommendations
        String summary = viewModel.generateAnalysisSummary(result.classIds);
        String recommendations = viewModel.generateBatteryRecommendations(result.classIds);
        
        binding.textAnalysisSummary.setText(summary);
        binding.textRecommendationContent.setText(recommendations);
        
        binding.cardRecommendation.setVisibility(View.VISIBLE);
    }

    /**
     * Update result table
     */
    private void updateResultTable(BatteryProcessor.BatteryResult result) {
        TableLayout tableLayout = binding.tableResults;
        tableLayout.removeAllViews();
        
        // Create header row
        TableRow headerRow = createTableRow(true);
        addTableCell(headerRow, "Date", true);
        addTableCell(headerRow, "Usage Class", true);
        addTableCell(headerRow, "Stress", true);
        addTableCell(headerRow, "Utilization", true);
        tableLayout.addView(headerRow);
        
        // Create data rows
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (int i = 0; i < result.dates.size(); i++) {
            TableRow dataRow = createTableRow(false);
            addTableCell(dataRow, result.dates.get(i).format(dateFormatter), false);
            addTableCell(dataRow, result.classLabels[i], false);
            addTableCell(dataRow, String.format(Locale.getDefault(), "%.2f", result.stress[i]), false);
            addTableCell(dataRow, String.format(Locale.getDefault(), "%.2f", result.utilization[i]), false);
            
            // Set row background color based on class
            int classId = result.classIds[i];
            dataRow.setBackgroundColor(Color.argb(30, 
                Color.red(CLASS_COLORS[classId]),
                Color.green(CLASS_COLORS[classId]),
                Color.blue(CLASS_COLORS[classId])));
            
            tableLayout.addView(dataRow);
        }
        
        binding.scrollResultTable.setVisibility(View.VISIBLE);
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
        cell.setGravity(Gravity.CENTER);
        if (isHeader) {
            cell.setTypeface(null, android.graphics.Typeface.BOLD);
        }
        cell.setPadding(8, 8, 8, 8);
        row.addView(cell);
    }
    
    /**
     * Show progress dialog
     */
    private void showProgress(String title, String message) {
        if (binding == null || !isAdded()) return;
        
        // Always clear previous messages and show progress dialog
        LinearLayout messagesLayout = binding.layoutProgressMessages;
        messagesLayout.removeAllViews();
        
        binding.cardProgress.setVisibility(View.VISIBLE);
        binding.textProgressTitle.setText(title);
        
        addProgressMessage(message);
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
        blockNavigation(false);
    }
    
    /**
     * Block/unblock navigation during analysis
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
     * Show error message
     */
    private void showError(String message) {
        if (binding == null || !isAdded() || getContext() == null) return;
        
        Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
        Log.e(TAG, message);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        blockNavigation(false);
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
        binding = null;
    }
}
