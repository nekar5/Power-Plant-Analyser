package com.masters.ppa.ui.analysis.tabs;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.masters.ppa.R;
import com.masters.ppa.data.model.InverterDataGroups;
import com.masters.ppa.data.model.InverterMetric;
import com.masters.ppa.data.parser.InverterDataParser;
import com.masters.ppa.databinding.FragmentCurrentDataBinding;
import com.masters.ppa.ui.analysis.AnalysisViewModel;
import com.masters.ppa.utils.FileUtils;
import com.masters.ppa.utils.NetworkUtils;
import com.masters.ppa.utils.StateUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fragment for the Current Data tab in Analysis screen
 */
public class CurrentDataFragment extends Fragment implements NetworkUtils.NetworkStatusListener {

    private FragmentCurrentDataBinding binding;
    private AnalysisViewModel viewModel;
    private InverterMetricAdapter powerFlowAdapter;
    private InverterMetricAdapter dailySummaryAdapter;
    private InverterMetricAdapter cumulativeSummaryAdapter;
    private InverterMetricAdapter advancedAdapter;
    private boolean isAdvancedExpanded = false;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentCurrentDataBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireParentFragment()).get(AnalysisViewModel.class);
        
        setupRecyclerViews();
        setupObservers();
        setupListeners();
        NetworkUtils.registerNetworkCallback(requireContext(), this);
        
        // Load saved state if available
        loadSavedState();
    }
    
    /**
     * Load saved state from SharedPreferences
     */
    private void loadSavedState() {
        InverterDataGroups savedGroups = StateUtils.loadCurrentData(requireContext());
        if (savedGroups != null && !savedGroups.getAllMetrics().isEmpty()) {
            updateUI(savedGroups);
            binding.textNoData.setVisibility(View.GONE);
            
            // Update timestamp if available
            long timestamp = StateUtils.getCurrentDataTimestamp(requireContext());
            if (timestamp > 0) {
                binding.textLastUpdated.setText(getString(R.string.last_updated, 
                    FileUtils.formatDate(new java.util.Date(timestamp))));
                binding.textLastUpdated.setVisibility(View.VISIBLE);
            }
        }
    }
    
    /**
     * Setup RecyclerViews for different groups
     */
    private void setupRecyclerViews() {
        powerFlowAdapter = new InverterMetricAdapter();
        binding.recyclerPowerFlow.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerPowerFlow.setAdapter(powerFlowAdapter);

        dailySummaryAdapter = new InverterMetricAdapter();
        binding.recyclerDailySummary.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerDailySummary.setAdapter(dailySummaryAdapter);

        cumulativeSummaryAdapter = new InverterMetricAdapter();
        binding.recyclerCumulativeSummary.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerCumulativeSummary.setAdapter(cumulativeSummaryAdapter);

        advancedAdapter = new InverterMetricAdapter();
        binding.recyclerAdvanced.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerAdvanced.setAdapter(advancedAdapter);
    }
    
    /**
     * Setup observers for LiveData
     */
    private void setupObservers() {
        // Last updated date
        viewModel.getInverterLastUpdated().observe(getViewLifecycleOwner(), lastUpdated -> {
            if (lastUpdated != null) {
                binding.textLastUpdated.setText(getString(R.string.last_updated, FileUtils.formatDate(lastUpdated)));
                binding.textLastUpdated.setVisibility(View.VISIBLE);
            } else {
                binding.textLastUpdated.setVisibility(View.GONE);
            }
        });
        
        // Inverter data
        viewModel.getInverterData().observe(getViewLifecycleOwner(), groups -> {
            if (groups != null && !groups.getAllMetrics().isEmpty()) {
                updateUI(groups);
                binding.textNoData.setVisibility(View.GONE);
                // Save state when data is updated
                StateUtils.saveCurrentData(requireContext(), groups);
            } else {
                showNoData();
            }
        });
        
        // Loading state
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            binding.btnRefresh.setEnabled(!isLoading);
        });
    }
    
    /**
     * Setup button click listeners
     */
    private void setupListeners() {
        binding.btnRefresh.setOnClickListener(v -> {
            viewModel.fetchCurrentInverterData();
        });
        
        binding.btnMore.setOnClickListener(v -> toggleAdvancedInfo());
    }
    
    /**
     * Update UI with inverter data
     */
    private void updateUI(InverterDataGroups groups) {
        // Update timestamp and system status
        InverterMetric gridStatus = groups.getMetricByName("Grid Status");
        if (gridStatus != null) {
            binding.textSystemStatus.setText("Grid Status: " + gridStatus.getValue());
        }
        
        // Update power flow diagram
        updatePowerFlowDiagram(groups);
        
        // Update status card
        updateStatusCard(groups);
        
        // Update RecyclerViews
        // Filter Power Flow - only important metrics for panels, battery, and grid
        List<InverterMetric> filteredPowerFlow = filterPowerFlowMetrics(groups);
        powerFlowAdapter.setMetrics(filteredPowerFlow);
        dailySummaryAdapter.setMetrics(new ArrayList<>(groups.getDailySummary().values()));
        cumulativeSummaryAdapter.setMetrics(new ArrayList<>(groups.getCumulativeSummary().values()));
        
        // Advanced: everything that didn't fit in previous lists
        List<InverterMetric> advancedMetrics = new ArrayList<>();
        advancedMetrics.addAll(groups.getAdvanced().values());
        // Add metrics from powerFlow that were filtered out
        for (InverterMetric metric : groups.getPowerFlow().values()) {
            if (!filteredPowerFlow.contains(metric)) {
                advancedMetrics.add(metric);
            }
        }
        advancedAdapter.setMetrics(advancedMetrics);
        
        // Show all cards
        binding.cardPowerFlow.setVisibility(View.VISIBLE);
        binding.cardStatus.setVisibility(View.VISIBLE);
        binding.cardPowerFlowDetails.setVisibility(View.VISIBLE);
        binding.cardDailySummary.setVisibility(View.VISIBLE);
        binding.cardCumulativeSummary.setVisibility(View.VISIBLE);
    }
    
    /**
     * Update power flow diagram
     */
    private void updatePowerFlowDiagram(InverterDataGroups groups) {
        // Solar Power (PV Total Power) - in kW
        InverterMetric solarPower = groups.getMetric("PVTP");
        if (solarPower == null) {
            solarPower = groups.getMetricByName("PV Total Power");
        }
        if (solarPower != null) {
            double powerKw = InverterDataParser.getPowerInKw(groups, solarPower.getKey());
            binding.textSolarPower.setText(String.format(Locale.getDefault(), "%.2f kW", powerKw));
        } else {
            binding.textSolarPower.setText("0.00 kW");
        }
        
        // Daily Production
        InverterMetric dailyProduction = groups.getMetric("Etdy_ge1");
        if (dailyProduction == null) {
            dailyProduction = groups.getMetricByName("Daily Production (Active)");
        }
        if (dailyProduction != null) {
            binding.textSolarDaily.setText("Daily: " + dailyProduction.getFormattedValue());
        } else {
            binding.textSolarDaily.setText("Daily: -");
        }
        
        // Battery SoC
        InverterMetric batterySoc = groups.getMetric("B_left_cap1");
        if (batterySoc == null) {
            batterySoc = groups.getMetricByName("SoC");
        }
        if (batterySoc != null) {
            double soc = InverterDataParser.getPercentage(groups, batterySoc.getKey());
            binding.textBatterySoc.setText(String.format(Locale.getDefault(), "%.0f%%", soc));
        } else {
            binding.textBatterySoc.setText("-");
        }
        
        // Battery Power - in kW
        InverterMetric batteryPower = groups.getMetric("B_P1");
        if (batteryPower == null) {
            batteryPower = groups.getMetricByName("Battery Power");
        }
        double batteryPowerKw = 0.0;
        if (batteryPower != null) {
            batteryPowerKw = InverterDataParser.getPowerInKw(groups, batteryPower.getKey());
            binding.textBatteryPower.setText(String.format(Locale.getDefault(), "%.2f kW", batteryPowerKw));
        } else {
            binding.textBatteryPower.setText("0.00 kW");
        }
        
        // Daily Charging Energy
        InverterMetric dailyCharge = groups.getMetric("Etdy_cg1");
        if (dailyCharge == null) {
            dailyCharge = groups.getMetricByName("Daily Charging Energy");
        }
        if (dailyCharge != null) {
            binding.textBatteryDailyCharge.setText("Chg: " + dailyCharge.getFormattedValue());
        } else {
            binding.textBatteryDailyCharge.setText("Chg: -");
        }
        
        // Daily Discharging Energy
        InverterMetric dailyDischarge = groups.getMetric("Etdy_dcg1");
        if (dailyDischarge == null) {
            dailyDischarge = groups.getMetricByName("Daily Discharging Energy");
        }
        if (dailyDischarge != null) {
            binding.textBatteryDailyDischarge.setText("Dchg: " + dailyDischarge.getFormattedValue());
        } else {
            binding.textBatteryDailyDischarge.setText("Dchg: -");
        }
        
        // Grid Power - sum of PCC_AP1 + PCC_AP2 + PCC_AP3 in kW
        double gridPowerKw = 0.0;
        InverterMetric pccAp1 = groups.getMetric("PCC_AP1");
        InverterMetric pccAp2 = groups.getMetric("PCC_AP2");
        InverterMetric pccAp3 = groups.getMetric("PCC_AP3");
        
        if (pccAp1 != null) {
            gridPowerKw += InverterDataParser.getPowerInKw(groups, pccAp1.getKey());
        }
        if (pccAp2 != null) {
            gridPowerKw += InverterDataParser.getPowerInKw(groups, pccAp2.getKey());
        }
        if (pccAp3 != null) {
            gridPowerKw += InverterDataParser.getPowerInKw(groups, pccAp3.getKey());
        }
        
        binding.textGridPower.setText(String.format(Locale.getDefault(), "%.2f kW", gridPowerKw));
        
        // Daily Grid Feed-in
        InverterMetric dailyFeedIn = groups.getMetric("t_gc_tdy1");
        if (dailyFeedIn == null) {
            dailyFeedIn = groups.getMetricByName("Daily Grid Feed-in");
        }
        if (dailyFeedIn != null) {
            binding.textGridDailyFeedin.setText("Feed-in: " + dailyFeedIn.getFormattedValue());
        } else {
            binding.textGridDailyFeedin.setText("Feed-in: -");
        }
        
        // Daily Energy Purchased (Import)
        InverterMetric dailyImport = groups.getMetric("Etdy_pu1");
        if (dailyImport == null) {
            dailyImport = groups.getMetricByName("Daily Energy Purchased");
        }
        if (dailyImport != null) {
            binding.textGridDailyImport.setText("Import: " + dailyImport.getFormattedValue());
        } else {
            binding.textGridDailyImport.setText("Import: -");
        }
        
        // Load/Consumption Power - T_AC_OP (Total AC Output Power) or AC1*AV1 + AC2*AV2 + AC3*AV3 in kW
        double loadPowerKw = 0.0;
        InverterMetric totalAcOutput = groups.getMetric("T_AC_OP");
        if (totalAcOutput == null) {
            totalAcOutput = groups.getMetricByName("Total AC Output Power");
        }
        
        if (totalAcOutput != null) {
            loadPowerKw = InverterDataParser.getPowerInKw(groups, totalAcOutput.getKey());
        } else {
            // If T_AC_OP is not available, calculate from AC currents and voltages (AC1*AV1 + AC2*AV2 + AC3*AV3)
            InverterMetric ac1 = groups.getMetric("AC1");
            InverterMetric ac2 = groups.getMetric("AC2");
            InverterMetric ac3 = groups.getMetric("AC3");
            InverterMetric av1 = groups.getMetric("AV1");
            InverterMetric av2 = groups.getMetric("AV2");
            InverterMetric av3 = groups.getMetric("AV3");
            
            if (ac1 != null && av1 != null) {
                loadPowerKw += (ac1.getNumericValue() * av1.getNumericValue()) / 1000.0;
            }
            if (ac2 != null && av2 != null) {
                loadPowerKw += (ac2.getNumericValue() * av2.getNumericValue()) / 1000.0;
            }
            if (ac3 != null && av3 != null) {
                loadPowerKw += (ac3.getNumericValue() * av3.getNumericValue()) / 1000.0;
            }
        }
        binding.textLoadPower.setText(String.format(Locale.getDefault(), "%.2f kW", loadPowerKw));
        
        // Daily Consumption
        InverterMetric dailyConsumption = groups.getMetric("Etdy_use1");
        if (dailyConsumption == null) {
            dailyConsumption = groups.getMetricByName("Daily Consumption");
        }
        if (dailyConsumption != null) {
            binding.textConsumption.setText("Daily: " + dailyConsumption.getFormattedValue());
        } else {
            binding.textConsumption.setText("Consumption");
        }
        
        // Update arrows based on power direction
        updateArrows(batteryPowerKw, gridPowerKw);
    }
    
    /**
     * Update arrow directions based on power flow
     */
    private void updateArrows(double batteryPowerKw, double gridPowerKw) {
        // Battery arrow: positive = charging (inverter to battery) → ←, negative = discharging (battery to inverter) → →
        if (batteryPowerKw > 0) {
            binding.arrowSolarLeft.setText("←"); // Inverter charging battery (power flows from inverter to battery)
        } else if (batteryPowerKw < 0) {
            binding.arrowSolarLeft.setText("→"); // Battery discharging to inverter (power flows from battery to inverter)
        } else {
            binding.arrowSolarLeft.setText("—"); // No flow - show dash
        }
        
        // Grid arrow: positive = feed-in (inverter to grid) → →, negative = import (grid to inverter) → ←, zero → ↯
        if (Math.abs(gridPowerKw) < 0.001) {
            binding.arrowSolarLeft2.setText("↯"); // Zero power - show special symbol
        } else if (gridPowerKw > 0) {
            binding.arrowSolarLeft2.setText("→"); // Inverter feeding to grid (power flows from inverter to grid)
        } else {
            binding.arrowSolarLeft2.setText("←"); // Grid feeding to inverter (power flows from grid to inverter)
        }
    }
    
    /**
     * Update status card
     */
    private void updateStatusCard(InverterDataGroups groups) {
        // Inverter Status
        InverterMetric inverterStatus = groups.getMetric("INV_ST1");
        if (inverterStatus == null) {
            inverterStatus = groups.getMetricByName("Inverter status");
        }
        if (inverterStatus != null) {
            binding.textInverterStatus.setText("Inverter Status: " + inverterStatus.getValue());
        }
        
        // Grid Status
        InverterMetric gridStatus = groups.getMetric("ST_PG1");
        if (gridStatus == null) {
            gridStatus = groups.getMetricByName("Grid Status");
        }
        if (gridStatus != null) {
            binding.textGridStatus.setText("Grid Status: " + gridStatus.getValue());
        }
    }
    
    /**
     * Show no data message
     */
    private void showNoData() {
        binding.textNoData.setVisibility(View.VISIBLE);
        binding.cardPowerFlow.setVisibility(View.GONE);
        binding.cardStatus.setVisibility(View.GONE);
        binding.cardPowerFlowDetails.setVisibility(View.GONE);
        binding.cardDailySummary.setVisibility(View.GONE);
        binding.cardCumulativeSummary.setVisibility(View.GONE);
    }
    
    /**
     * Filter Power Flow metrics - keep only important ones for panels, battery, and grid
     */
    private List<InverterMetric> filterPowerFlowMetrics(InverterDataGroups groups) {
        List<InverterMetric> filtered = new ArrayList<>();
        java.util.Set<String> addedKeys = new java.util.HashSet<>();
        
        // Important keys for panels (PV) - only current values, no daily/cumulative
        String[] panelKeys = {"PVTP", "DP1", "DP2", "DV1", "DV2", "DC1", "DC2", "DPi_t1"};
        // Important keys for battery - only current values, no daily/cumulative
        String[] batteryKeys = {"B_P1", "B_left_cap1", "B_ST1"};
        // Important keys for grid - only current values, no daily/cumulative
        String[] gridKeys = {"PCC_AP1", "PCC_AP2", "PCC_AP3", "PCC_AC1", "PCC_AC2", "PCC_AC3", "PG_Pt1", "ST_PG1", "PG_F1"};
        
        // Collect all important metrics by key
        for (String key : panelKeys) {
            InverterMetric metric = groups.getMetric(key);
            if (metric != null && metric.getKey() != null) {
                filtered.add(metric);
                addedKeys.add(metric.getKey());
            }
        }
        for (String key : batteryKeys) {
            InverterMetric metric = groups.getMetric(key);
            if (metric != null && metric.getKey() != null) {
                filtered.add(metric);
                addedKeys.add(metric.getKey());
            }
        }
        for (String key : gridKeys) {
            InverterMetric metric = groups.getMetric(key);
            if (metric != null && metric.getKey() != null) {
                filtered.add(metric);
                addedKeys.add(metric.getKey());
            }
        }
        
        // Also add by name if key not found
        String[] panelNames = {"PV Total Power", "DC Power PV1", "DC Power PV2", "DC Voltage PV1", "DC Voltage PV2", 
                               "DC Current PV1", "DC Current PV2", "Total DC Input Power"};
        String[] batteryNames = {"Battery Power", "SoC", "Battery Status"};
        String[] gridNames = {"PCC AC Power R", "PCC AC Power S", "PCC AC Power T", "PCC AC Current R", 
                             "PCC AC Current S", "PCC AC Current T", "Total Grid Power", "Grid Status", "Grid Frequency"};
        
        for (String name : panelNames) {
            InverterMetric metric = groups.getMetricByName(name);
            if (metric != null && metric.getKey() != null && !addedKeys.contains(metric.getKey())) {
                filtered.add(metric);
                addedKeys.add(metric.getKey());
            }
        }
        for (String name : batteryNames) {
            InverterMetric metric = groups.getMetricByName(name);
            if (metric != null && metric.getKey() != null && !addedKeys.contains(metric.getKey())) {
                filtered.add(metric);
                addedKeys.add(metric.getKey());
            }
        }
        for (String name : gridNames) {
            InverterMetric metric = groups.getMetricByName(name);
            if (metric != null && metric.getKey() != null && !addedKeys.contains(metric.getKey())) {
                filtered.add(metric);
                addedKeys.add(metric.getKey());
            }
        }
        
        return filtered;
    }
    
    /**
     * Toggle advanced info visibility
     */
    private void toggleAdvancedInfo() {
        isAdvancedExpanded = !isAdvancedExpanded;
        binding.cardAdvanced.setVisibility(isAdvancedExpanded ? View.VISIBLE : View.GONE);
        binding.btnMore.setText(isAdvancedExpanded ? "Less ▲" : "More ▼");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        NetworkUtils.unregisterNetworkCallback(requireContext(), this);
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
     * Adapter for displaying InverterMetric items
     */
    private static class InverterMetricAdapter extends RecyclerView.Adapter<InverterMetricAdapter.ViewHolder> {
        
        private List<InverterMetric> metrics = new ArrayList<>();
        
        public void setMetrics(List<InverterMetric> newMetrics) {
            this.metrics = newMetrics != null ? newMetrics : new ArrayList<>();
            notifyDataSetChanged();
        }
        
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_2, parent, false);
            return new ViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            InverterMetric metric = metrics.get(position);
            holder.nameText.setText(metric.getName());
            holder.valueText.setText(metric.getFormattedValue());
        }
        
        @Override
        public int getItemCount() {
            return metrics.size();
        }
        
        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView nameText;
            TextView valueText;
            
            ViewHolder(@NonNull View itemView) {
                super(itemView);
                nameText = itemView.findViewById(android.R.id.text1);
                valueText = itemView.findViewById(android.R.id.text2);
            }
        }
    }
}
