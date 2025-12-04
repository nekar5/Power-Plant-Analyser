package com.masters.ppa.ui.weather;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.masters.ppa.R;
import com.masters.ppa.data.model.WeatherData;
import com.masters.ppa.databinding.FragmentWeatherBinding;
import com.masters.ppa.utils.ChartUtils;
import com.masters.ppa.utils.FileUtils;
import com.masters.ppa.utils.NetworkUtils;
import com.masters.ppa.utils.UiUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fragment for the Weather screen
 */
public class WeatherFragment extends Fragment implements NetworkUtils.NetworkStatusListener {

    private FragmentWeatherBinding binding;
    private WeatherViewModel viewModel;
    private WeatherAdapter adapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentWeatherBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(WeatherViewModel.class);
        
        setupRecyclerView();
        setupChart();
        setupObservers();
        setupListeners();
        NetworkUtils.registerNetworkCallback(requireContext(), this);
        viewModel.loadWeatherData();
    }
    
    /**
     * Setup RecyclerView
     */
    private void setupRecyclerView() {
        adapter = new WeatherAdapter();
        binding.recyclerWeather.setAdapter(adapter);
        binding.recyclerWeather.setLayoutManager(new LinearLayoutManager(requireContext()));
    }
    
    /**
     * Setup temperature chart
     */
    private void setupChart() {
        ChartUtils.configureLineChart(binding.chartTemperature, requireContext(), true);
        binding.chartTemperature.getDescription().setEnabled(false);
        // Disable touch interactions to allow scrolling
        binding.chartTemperature.setTouchEnabled(false);
        binding.chartTemperature.setDragEnabled(false);
        binding.chartTemperature.setScaleEnabled(false);
        binding.chartTemperature.setPinchZoom(false);
        binding.chartTemperature.getAxisLeft().setTextColor(Color.WHITE);
        binding.chartTemperature.getXAxis().setTextColor(Color.WHITE);
        binding.chartTemperature.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        binding.chartTemperature.getLegend().setTextColor(Color.WHITE);
    }
    
    /**
     * Setup observers for LiveData
     */
    private void setupObservers() {
        // Weather data
        viewModel.getAllWeatherData().observe(getViewLifecycleOwner(), weatherDataList -> {
            if (weatherDataList != null && !weatherDataList.isEmpty()) {
                updateWeatherUI(weatherDataList);
            } else {
                showNoDataView(true);
            }
        });
        
        // Last updated date
        viewModel.getLastUpdatedDate().observe(getViewLifecycleOwner(), lastUpdated -> {
            if (lastUpdated != null) {
                binding.textLastUpdated.setText(getString(R.string.last_updated, FileUtils.formatDate(lastUpdated)));
                binding.textLastUpdated.setVisibility(View.VISIBLE);
            } else {
                binding.textLastUpdated.setVisibility(View.GONE);
            }
        });
        
        // Forecast range
        viewModel.getForecastRange().observe(getViewLifecycleOwner(), range -> {
            if (range != null) {
                binding.textForecastRange.setText(range);
                binding.textForecastRange.setVisibility(View.VISIBLE);
            } else {
                binding.textForecastRange.setVisibility(View.GONE);
            }
        });
        
        // Loading state
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            binding.btnRefresh.setEnabled(!isLoading);
            binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        });
        
        // Operation results
        viewModel.getOperationSuccess().observe(getViewLifecycleOwner(), success -> {
            String message = viewModel.getOperationMessage().getValue();
            if (message != null && success != null) {
                // Use UiUtils for consistent snackbar handling
                UiUtils.showSnackbar(
                    binding.getRoot(), 
                    message, 
                    !success
                );
            }
        });
    }
    
    /**
     * Setup button click listeners
     */
    private void setupListeners() {
        binding.btnRefresh.setOnClickListener(v -> viewModel.fetchWeatherData());
    }
    
    /**
     * Update weather UI with data
     * @param weatherDataList List of weather data
     */
    private void updateWeatherUI(List<WeatherData> weatherDataList) {
        showNoDataView(false);
        
        // Update RecyclerView
        adapter.submitList(weatherDataList);
        
        // Update chart
        updateTemperatureChart(weatherDataList);
    }
    
    /**
     * Update temperature chart with data
     * @param weatherDataList List of weather data
     */
    private void updateTemperatureChart(List<WeatherData> weatherDataList) {
        if (weatherDataList.size() > 7) {
            // Limit to 7 days (today + next 6 days)
            weatherDataList = weatherDataList.subList(0, 7);
        }
        
        // Create entries for chart - using average temperature
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < weatherDataList.size(); i++) {
            WeatherData data = weatherDataList.get(i);
            // Round to 1 decimal place
            float avgTemp = (float) (Math.round(data.getTemperatureAvg() * 10) / 10.0);
            entries.add(new Entry(i, avgTemp));
        }
        
        // Create dataset with styling
        LineDataSet dataSet = new LineDataSet(entries, "Average Temperature (°C)");
        dataSet.setColor(getResources().getColor(R.color.chart_blue, null));
        dataSet.setCircleColor(getResources().getColor(R.color.chart_blue, null));
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(4f);
        dataSet.setDrawCircleHole(true);
        dataSet.setCircleHoleRadius(2f);
        dataSet.setValueTextSize(10f);
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setDrawValues(true);
        dataSet.setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.format(Locale.getDefault(), "%.1f°C", value);
            }
        });
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(getResources().getColor(R.color.chart_blue, null));
        dataSet.setFillAlpha(50);
        
        // Create labels for X-axis - using date format "d MMM"
        List<String> labels = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("d MMM", Locale.getDefault());
        for (WeatherData data : weatherDataList) {
            labels.add(sdf.format(data.getDate()));
        }
        
        // Apply labels to X-axis
        binding.chartTemperature.getXAxis().setValueFormatter(
            new com.github.mikephil.charting.formatter.IndexAxisValueFormatter(labels)
        );
        
        // Set data to chart
        LineData lineData = new LineData(dataSet);
        binding.chartTemperature.setData(lineData);
        binding.chartTemperature.invalidate();
    }
    
    /**
     * Show or hide no data view
     * @param show Whether to show no data view
     */
    private void showNoDataView(boolean show) {
        binding.textNoData.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.chartTemperature.setVisibility(show ? View.GONE : View.VISIBLE);
        binding.recyclerWeather.setVisibility(show ? View.GONE : View.VISIBLE);
        binding.progressBar.setVisibility(View.GONE);
    }

    @Override
    public void onDestroyView() {
        // Clear any existing snackbar to prevent memory leaks
        UiUtils.clearSnackbar();
        
        NetworkUtils.unregisterNetworkCallback(requireContext(), this);
        super.onDestroyView();
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
}