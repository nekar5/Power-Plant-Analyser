package com.masters.ppa.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;
import com.masters.ppa.R;
import com.masters.ppa.data.model.BatteryConfig;
import com.masters.ppa.data.model.PerformanceConfig;
import com.masters.ppa.data.model.SolarmanApiConfig;
import com.masters.ppa.data.model.StationConfig;
import com.masters.ppa.databinding.FragmentSettingsBinding;
import com.masters.ppa.utils.NetworkUtils;

/**
 * Fragment for the Settings screen
 */
public class SettingsFragment extends Fragment implements NetworkUtils.NetworkStatusListener {

    private FragmentSettingsBinding binding;
    private SettingsViewModel viewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(SettingsViewModel.class);
        
        setupDropdowns();
        setupObservers();
        setupListeners();
        NetworkUtils.registerNetworkCallback(requireContext(), this);
    }
    
    /**
     * Setup dropdown menus
     */
    private void setupDropdowns() {
        // Battery type dropdown
        String[] batteryTypes = {"High Voltage", "Low Voltage"};
        ArrayAdapter<String> batteryAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                batteryTypes
        );
        ((AutoCompleteTextView) binding.tilBatteryType.getEditText()).setAdapter(batteryAdapter);
        
        // API base URL dropdown
        String[] baseUrls = {"https://globalapi.solarmanpv.com"};
        ArrayAdapter<String> urlAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                baseUrls
        );
        ((AutoCompleteTextView) binding.tilBaseUrl.getEditText()).setAdapter(urlAdapter);
    }
    
    // Using UiUtils for consistent snackbar handling
    
    /**
     * Setup observers for LiveData
     */
    private void setupObservers() {
        // Station config
        viewModel.getStationConfig().observe(getViewLifecycleOwner(), stationConfig -> {
            if (stationConfig != null) {
                binding.etInverterPower.setText(String.valueOf(stationConfig.getInverterPowerKw()));
                binding.etPanelPower.setText(String.valueOf(stationConfig.getPanelPowerW()));
                binding.etPanelCount.setText(String.valueOf(stationConfig.getPanelCount()));
                binding.etPanelEfficiency.setText(String.valueOf(stationConfig.getPanelEfficiency()));
                binding.etTiltDeg.setText(String.valueOf(stationConfig.getTiltDeg()));
                binding.etLatitude.setText(String.valueOf(stationConfig.getLatitude()));
                binding.etLongitude.setText(String.valueOf(stationConfig.getLongitude()));
            }
        });
        
        // Battery config
        viewModel.getBatteryConfig().observe(getViewLifecycleOwner(), batteryConfig -> {
            if (batteryConfig != null) {
                binding.etCapacityKwh.setText(String.valueOf(batteryConfig.getCapacityKwh()));
                binding.etBatteryCount.setText(String.valueOf(batteryConfig.getCount()));
                ((AutoCompleteTextView) binding.tilBatteryType.getEditText()).setText(batteryConfig.getType(), false);
                binding.etRoundtripEfficiency.setText(String.valueOf(batteryConfig.getRoundtripEfficiency()));
                binding.etSocMin.setText(String.valueOf(batteryConfig.getSocMinPct()));
                binding.etSocMax.setText(String.valueOf(batteryConfig.getSocMaxPct()));
                binding.switchNightUseGrid.setChecked(batteryConfig.isNightUseGrid());
                binding.switchAllowGridCharging.setChecked(batteryConfig.isAllowGridCharging());
            }
        });
        
        // Performance config
        viewModel.getPerformanceConfig().observe(getViewLifecycleOwner(), performanceConfig -> {
            if (performanceConfig != null) {
                binding.etPerformanceRatio.setText(String.valueOf(performanceConfig.getPerformanceRatioAvg()));
                binding.etTemperatureCoeff.setText(String.valueOf(performanceConfig.getTemperatureCoeff()));
                binding.etReferenceTemp.setText(String.valueOf(performanceConfig.getReferenceTemp()));
            }
        });
        
        // API config
        viewModel.getSolarmanApiConfig().observe(getViewLifecycleOwner(), apiConfig -> {
            if (apiConfig != null) {
                ((AutoCompleteTextView) binding.tilBaseUrl.getEditText()).setText(apiConfig.getBaseUrl(), false);
                binding.etEmail.setText(apiConfig.getEmail());
                binding.etPassword.setText(apiConfig.getPassword());
                binding.etAppId.setText(apiConfig.getAppId());
                binding.etAppSecret.setText(apiConfig.getAppSecret());
                binding.etTimeout.setText(String.valueOf(apiConfig.getTimeout()));
                binding.etDeviceId.setText(String.valueOf(apiConfig.getDeviceId()));
                binding.etDeviceSn.setText(apiConfig.getDeviceSn());
            }
        });
        
        // Operation results
        viewModel.getOperationSuccess().observe(getViewLifecycleOwner(), success -> {
            String message = viewModel.getOperationMessage().getValue();
            if (message != null && success != null) {
                // Use UiUtils for consistent snackbar handling
                com.masters.ppa.utils.UiUtils.showSnackbar(
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
        // Station config buttons
        binding.btnSaveStation.setOnClickListener(v -> saveStationConfig());
        binding.btnClearStation.setOnClickListener(v -> {
            viewModel.clearStationConfig();
            // Clear UI fields immediately
            binding.etInverterPower.setText("");
            binding.etPanelPower.setText("");
            binding.etPanelCount.setText("");
            binding.etPanelEfficiency.setText("");
            binding.etTiltDeg.setText("");
            binding.etLatitude.setText("");
            binding.etLongitude.setText("");
        });
        
        // Battery config buttons
        binding.btnSaveBattery.setOnClickListener(v -> saveBatteryConfig());
        binding.btnClearBattery.setOnClickListener(v -> {
            viewModel.clearBatteryConfig();
            // Clear UI fields immediately
            binding.etCapacityKwh.setText("");
            binding.etBatteryCount.setText("");
            ((AutoCompleteTextView) binding.tilBatteryType.getEditText()).setText("", false);
            binding.etRoundtripEfficiency.setText("");
            binding.etSocMin.setText("");
            binding.etSocMax.setText("");
            binding.switchNightUseGrid.setChecked(false);
            binding.switchAllowGridCharging.setChecked(false);
        });
        
        // Performance config buttons
        binding.btnSavePerformance.setOnClickListener(v -> savePerformanceConfig());
        binding.btnClearPerformance.setOnClickListener(v -> {
            viewModel.clearPerformanceConfig();
            // Clear UI fields immediately
            binding.etPerformanceRatio.setText("");
            binding.etTemperatureCoeff.setText("");
            binding.etReferenceTemp.setText("");
        });
        
        // API config buttons
        binding.btnSaveApi.setOnClickListener(v -> saveSolarmanApiConfig());
        binding.btnClearApi.setOnClickListener(v -> {
            viewModel.clearSolarmanApiConfig();
            // Clear UI fields immediately
            ((AutoCompleteTextView) binding.tilBaseUrl.getEditText()).setText("", false);
            binding.etEmail.setText("");
            binding.etPassword.setText("");
            binding.etAppId.setText("");
            binding.etAppSecret.setText("");
            binding.etTimeout.setText("");
            binding.etDeviceId.setText("");
            binding.etDeviceSn.setText("");
        });
        
        // Clear all button
        binding.btnClearAll.setOnClickListener(v -> {
            viewModel.clearAllConfigs();
            // Clear all UI fields immediately
            
            // Clear Station fields
            binding.etInverterPower.setText("");
            binding.etPanelPower.setText("");
            binding.etPanelCount.setText("");
            binding.etPanelEfficiency.setText("");
            binding.etTiltDeg.setText("");
            binding.etLatitude.setText("");
            binding.etLongitude.setText("");
            
            // Clear Battery fields
            binding.etCapacityKwh.setText("");
            binding.etBatteryCount.setText("");
            ((AutoCompleteTextView) binding.tilBatteryType.getEditText()).setText("", false);
            binding.etRoundtripEfficiency.setText("");
            binding.etSocMin.setText("");
            binding.etSocMax.setText("");
            binding.switchNightUseGrid.setChecked(false);
            binding.switchAllowGridCharging.setChecked(false);
            
            // Clear Performance fields
            binding.etPerformanceRatio.setText("");
            binding.etTemperatureCoeff.setText("");
            binding.etReferenceTemp.setText("");
            
            // Clear API fields
            ((AutoCompleteTextView) binding.tilBaseUrl.getEditText()).setText("", false);
            binding.etEmail.setText("");
            binding.etPassword.setText("");
            binding.etAppId.setText("");
            binding.etAppSecret.setText("");
            binding.etTimeout.setText("");
            binding.etDeviceId.setText("");
            binding.etDeviceSn.setText("");
        });
        
        // Load test config button
        binding.btnClearAll.setOnLongClickListener(v -> {
            viewModel.loadTestConfig();
            Toast.makeText(requireContext(), "Loading test configuration...", Toast.LENGTH_SHORT).show();
            return true;
        });
        
        // Load test config button (explicit)
        binding.btnLoadTestConfig.setOnClickListener(v -> {
            viewModel.loadTestConfig();
            Toast.makeText(requireContext(), "Test configuration loaded successfully.", Toast.LENGTH_SHORT).show();
        });
    }
    
    /**
     * Save station configuration
     */
    private void saveStationConfig() {
        try {
            StationConfig config = new StationConfig();
            
            String inverterPower = binding.etInverterPower.getText().toString();
            String panelPower = binding.etPanelPower.getText().toString();
            String panelCount = binding.etPanelCount.getText().toString();
            String panelEfficiency = binding.etPanelEfficiency.getText().toString();
            String tiltDeg = binding.etTiltDeg.getText().toString();
            String latitude = binding.etLatitude.getText().toString();
            String longitude = binding.etLongitude.getText().toString();
            
            // Validate required fields
            if (inverterPower.isEmpty() || panelPower.isEmpty() || panelCount.isEmpty() || 
                panelEfficiency.isEmpty() || tiltDeg.isEmpty() || latitude.isEmpty() || longitude.isEmpty()) {
                Toast.makeText(requireContext(), "All fields are required", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Validate ranges
            double invPower = Double.parseDouble(inverterPower);
            int panPower = Integer.parseInt(panelPower);
            int panCount = Integer.parseInt(panelCount);
            double panEff = Double.parseDouble(panelEfficiency);
            double lat = Double.parseDouble(latitude);
            double lon = Double.parseDouble(longitude);
            
            if (invPower <= 0 || invPower > 1000) {
                Toast.makeText(requireContext(), "Inverter power must be between 0 and 1000 kW", Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (panPower <= 0 || panPower > 2000) {
                Toast.makeText(requireContext(), "Panel power must be between 0 and 2000 W", Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (panCount <= 0 || panCount > 10000) {
                Toast.makeText(requireContext(), "Panel count must be between 0 and 10000", Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (panEff <= 0 || panEff > 1) {
                Toast.makeText(requireContext(), "Panel efficiency must be between 0 and 1", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Validate tilt degrees (0-60, integer)
            int tiltDegValue;
            try {
                tiltDegValue = Integer.parseInt(tiltDeg);
                if (tiltDegValue < 0 || tiltDegValue > 60) {
                    Toast.makeText(requireContext(), "Tilt degrees must be between 0 and 60", Toast.LENGTH_SHORT).show();
                    return;
                }
            } catch (NumberFormatException e) {
                Toast.makeText(requireContext(), "Tilt degrees must be an integer between 0 and 60", Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (lat < -90 || lat > 90) {
                Toast.makeText(requireContext(), "Latitude must be between -90 and 90", Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (lon < -180 || lon > 180) {
                Toast.makeText(requireContext(), "Longitude must be between -180 and 180", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Set values
            config.setInverterPowerKw(invPower);
            config.setPanelPowerW(panPower);
            config.setPanelCount(panCount);
            config.setPanelEfficiency(panEff);
            config.setTiltDeg(tiltDegValue);
            config.setLatitude(lat);
            config.setLongitude(lon);
            
            viewModel.saveStationConfig(config);
            Toast.makeText(requireContext(), "Station data saved", Toast.LENGTH_SHORT).show();
            
        } catch (NumberFormatException e) {
            Toast.makeText(requireContext(), "Invalid number format", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Save battery configuration
     */
    private void saveBatteryConfig() {
        try {
            BatteryConfig config = new BatteryConfig();
            
            String capacity = binding.etCapacityKwh.getText().toString();
            String count = binding.etBatteryCount.getText().toString();
            String type = ((AutoCompleteTextView) binding.tilBatteryType.getEditText()).getText().toString();
            String efficiency = binding.etRoundtripEfficiency.getText().toString();
            String socMin = binding.etSocMin.getText().toString();
            String socMax = binding.etSocMax.getText().toString();
            boolean nightUseGrid = binding.switchNightUseGrid.isChecked();
            boolean allowGridCharging = binding.switchAllowGridCharging.isChecked();
            
            // Validate required fields
            if (capacity.isEmpty() || count.isEmpty() || type.isEmpty() || 
                efficiency.isEmpty() || socMin.isEmpty() || socMax.isEmpty()) {
                Toast.makeText(requireContext(), "All fields are required", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Parse values
            config.setCapacityKwh(Double.parseDouble(capacity));
            config.setCount(Integer.parseInt(count));
            config.setType(type);
            config.setRoundtripEfficiency(Double.parseDouble(efficiency));
            config.setSocMinPct(Integer.parseInt(socMin));
            config.setSocMaxPct(Integer.parseInt(socMax));
            config.setNightUseGrid(nightUseGrid);
            config.setAllowGridCharging(allowGridCharging);
            
            viewModel.saveBatteryConfig(config);
            
        } catch (NumberFormatException e) {
            Toast.makeText(requireContext(), "Invalid number format", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Save performance configuration
     */
    private void savePerformanceConfig() {
        try {
            PerformanceConfig config = new PerformanceConfig();
            
            String performanceRatio = binding.etPerformanceRatio.getText().toString();
            String temperatureCoeff = binding.etTemperatureCoeff.getText().toString();
            String referenceTemp = binding.etReferenceTemp.getText().toString();
            
            // Validate required fields
            if (performanceRatio.isEmpty() || temperatureCoeff.isEmpty() || referenceTemp.isEmpty()) {
                Toast.makeText(requireContext(), "All fields are required", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Parse values
            config.setPerformanceRatioAvg(Double.parseDouble(performanceRatio));
            config.setTemperatureCoeff(Double.parseDouble(temperatureCoeff));
            config.setReferenceTemp(Integer.parseInt(referenceTemp));
            
            viewModel.savePerformanceConfig(config);
            
        } catch (NumberFormatException e) {
            Toast.makeText(requireContext(), "Invalid number format", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Save Solarman API configuration
     */
    private void saveSolarmanApiConfig() {
        try {
            SolarmanApiConfig config = new SolarmanApiConfig();
            
            String baseUrl = ((AutoCompleteTextView) binding.tilBaseUrl.getEditText()).getText().toString();
            String email = binding.etEmail.getText().toString();
            String password = binding.etPassword.getText().toString();
            String appId = binding.etAppId.getText().toString();
            String appSecret = binding.etAppSecret.getText().toString();
            String timeout = binding.etTimeout.getText().toString();
            String deviceId = binding.etDeviceId.getText().toString();
            String deviceSn = binding.etDeviceSn.getText().toString();
            
            // Validate required fields
            if (baseUrl.isEmpty() || email.isEmpty() || password.isEmpty() || 
                appId.isEmpty() || appSecret.isEmpty() || timeout.isEmpty() || 
                deviceId.isEmpty() || deviceSn.isEmpty()) {
                Toast.makeText(requireContext(), "All fields are required", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Parse values
            config.setBaseUrl(baseUrl);
            config.setEmail(email);
            config.setPassword(password);
            config.setAppId(appId);
            config.setAppSecret(appSecret);
            config.setTimeout(Integer.parseInt(timeout));
            config.setDeviceId(Long.parseLong(deviceId));
            config.setDeviceSn(deviceSn);
            
            viewModel.saveSolarmanApiConfig(config);
            
        } catch (NumberFormatException e) {
            Toast.makeText(requireContext(), "Invalid number format", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        // Clear any existing snackbar to prevent memory leaks
        com.masters.ppa.utils.UiUtils.clearSnackbar();
        
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
