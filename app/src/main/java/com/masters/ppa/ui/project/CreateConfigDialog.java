package com.masters.ppa.ui.project;

import android.app.Dialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.masters.ppa.R;
import com.masters.ppa.data.model.BatteryItem;
import com.masters.ppa.data.model.BmsItem;
import com.masters.ppa.data.model.ConfigBms;
import com.masters.ppa.data.model.ConfigInverter;
import com.masters.ppa.data.model.ConfigTower;
import com.masters.ppa.data.model.InverterItem;
import com.masters.ppa.data.model.ProjectConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Multi-step dialog for creating Project Config
 */
public class CreateConfigDialog extends DialogFragment {
    
    private static final int STEP_1 = 1;
    private static final int STEP_2 = 2;
    private static final int STEP_3 = 3;

    private View step1View;
    private View step2View;
    private View step3View;
    
    // Config name
    private String configName = "";
    
    // Step 1 data
    private InverterCheckboxAdapter inverterAdapter;
    private List<InverterItem> allInverters = new ArrayList<>();
    private List<Integer> selectedInverterIds = new ArrayList<>();
    
    // Step 2 data
    private List<BatteryItem> allBatteries = new ArrayList<>();
    private List<BatteryTowerConfig> batteryTowerConfigs = new ArrayList<>(); // All battery configs added so far
    private AutoCompleteTextView actBatteryModel;
    private TextInputEditText etTotalBatteries;
    private TextInputEditText etTowersCount;
    private LinearLayout layoutTowerBatteries;
    private int selectedBatteryId = -1;
    private int totalBatteries = 0;
    private int towersCount = 0;
    private List<Integer> batteriesInTowers = new ArrayList<>();
    private boolean addMoreBatteriesLater = false;
    
    // Step 3 data
    private List<BmsItem> allBms = new ArrayList<>();
    private List<BmsTowerConfig> bmsTowerConfigs = new ArrayList<>(); // All BMS configs added so far
    private AutoCompleteTextView actBmsModel;
    private RadioGroup radioBmsPlacement;
    private int selectedBmsId = -1;
    private Integer attachedTowerNumber = null; // null = standalone
    private boolean addMoreBmsLater = false;
    
    private ProjectViewModel viewModel;
    
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return dialog;
    }
    
    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null) {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, 
                                 ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        }
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.dialog_create_config_container, container, false);
        
        // Inflate step views
        step1View = inflater.inflate(R.layout.dialog_create_config_step1, null);
        step2View = inflater.inflate(R.layout.dialog_create_config_step2, null);
        step3View = inflater.inflate(R.layout.dialog_create_config_step3, null);
        
        // Add step views to container
        LinearLayout containerLayout = rootView.findViewById(R.id.container_steps);
        containerLayout.addView(step1View);
        containerLayout.addView(step2View);
        containerLayout.addView(step3View);
        
        // Show only step 1 initially
        step2View.setVisibility(View.GONE);
        step3View.setVisibility(View.GONE);
        
        return rootView;
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        viewModel = new ViewModelProvider(requireActivity()).get(ProjectViewModel.class);

        setupStep1();
        setupStep2();
        setupStep3();
    }
    
    private void setupStep1() {
        TextInputEditText etConfigName = step1View.findViewById(R.id.et_config_name);
        RecyclerView recyclerInverters = step1View.findViewById(R.id.recycler_inverters);
        MaterialButton btnNext = step1View.findViewById(R.id.btn_next_step1);
        
        // Setup config name field
        if (etConfigName != null) {
            etConfigName.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    configName = s.toString().trim();
                }
                
                @Override
                public void afterTextChanged(Editable s) {}
            });
        }
        
        // Load inverters
        viewModel.getInverterItems().observe(this, inverters -> {
            if (inverters != null) {
                allInverters.clear();
                allInverters.addAll(inverters);
                inverterAdapter = new InverterCheckboxAdapter(allInverters);
                recyclerInverters.setLayoutManager(new LinearLayoutManager(requireContext()));
                recyclerInverters.setAdapter(inverterAdapter);
            }
        });
        
        btnNext.setOnClickListener(v -> {
            if (validateStep1()) {
                // Get ordered list from adapter (order of selection)
                selectedInverterIds = inverterAdapter.getSelectedIds();
                showStep(STEP_2);
            }
        });
    }
    
    private boolean validateStep1() {
        if (configName.isEmpty() || configName.length() > 30) {
            Toast.makeText(requireContext(), R.string.error_name_too_long, Toast.LENGTH_SHORT).show();
            return false;
        }
        if (!inverterAdapter.hasSelection()) {
            Toast.makeText(requireContext(), R.string.error_select_at_least_one_inverter, Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }
    
    private void setupStep2() {
        actBatteryModel = step2View.findViewById(R.id.act_battery_model);
        etTotalBatteries = step2View.findViewById(R.id.et_total_batteries);
        etTowersCount = step2View.findViewById(R.id.et_towers_count);
        layoutTowerBatteries = step2View.findViewById(R.id.layout_tower_batteries);
        com.google.android.material.checkbox.MaterialCheckBox checkboxAddMore = step2View.findViewById(R.id.checkbox_add_more_batteries);
        MaterialButton btnCancel = step2View.findViewById(R.id.btn_cancel_step2);
        MaterialButton btnNext = step2View.findViewById(R.id.btn_next_step2);
        
        // Load batteries
        viewModel.getBatteryItems().observe(this, batteries -> {
            if (batteries != null) {
                allBatteries.clear();
                // Filter out already selected batteries
                List<BatteryItem> availableBatteries = new ArrayList<>();
                Set<Integer> usedBatteryIds = new HashSet<>();
                for (BatteryTowerConfig config : batteryTowerConfigs) {
                    usedBatteryIds.add(config.getBatteryModelId());
                }
                for (BatteryItem battery : batteries) {
                    if (!usedBatteryIds.contains(battery.getId())) {
                        availableBatteries.add(battery);
                    }
                }
                allBatteries.addAll(availableBatteries);
                
                List<String> batteryNames = new ArrayList<>();
                for (BatteryItem battery : availableBatteries) {
                    batteryNames.add(battery.getName());
                }
                
                ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                        android.R.layout.simple_dropdown_item_1line, batteryNames);
                actBatteryModel.setAdapter(adapter);
                
                actBatteryModel.setOnItemClickListener((parent, view, position, id) -> {
                    selectedBatteryId = availableBatteries.get(position).getId();
                });
            }
        });
        
        etTowersCount.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String text = s.toString();
                if (!text.isEmpty()) {
                    try {
                        int countValue = Integer.parseInt(text);
                        if (countValue > 1 && totalBatteries > 0) {
                            layoutTowerBatteries.setVisibility(View.VISIBLE);
                            setupTowerBatteriesInputs(layoutTowerBatteries, countValue);
                        } else {
                            layoutTowerBatteries.setVisibility(View.GONE);
                        }
                    } catch (NumberFormatException e) {
                        layoutTowerBatteries.setVisibility(View.GONE);
                    }
                } else {
                    layoutTowerBatteries.setVisibility(View.GONE);
                }
            }
            
            @Override
            public void afterTextChanged(Editable s) {}
        });
        
        etTotalBatteries.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String text = s.toString();
                if (!text.isEmpty()) {
                    try {
                        totalBatteries = Integer.parseInt(text);
                        if (towersCount > 1) {
                            layoutTowerBatteries.setVisibility(View.VISIBLE);
                            setupTowerBatteriesInputs(layoutTowerBatteries, towersCount);
                        }
                    } catch (NumberFormatException e) {
                        totalBatteries = 0;
                    }
                } else {
                    totalBatteries = 0;
                }
            }
            
            @Override
            public void afterTextChanged(Editable s) {}
        });
        
        etTowersCount.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String text = s.toString();
                if (!text.isEmpty()) {
                    try {
                        towersCount = Integer.parseInt(text);
                    } catch (NumberFormatException e) {
                        towersCount = 0;
                    }
                } else {
                    towersCount = 0;
                }
            }
            
            @Override
            public void afterTextChanged(Editable s) {}
        });
        
        checkboxAddMore.setOnCheckedChangeListener((buttonView, isChecked) -> {
            addMoreBatteriesLater = isChecked;
        });
        
        btnCancel.setOnClickListener(v -> dismiss());
        btnNext.setOnClickListener(v -> {
            if (validateStep2()) {
                // Save current battery config
                saveCurrentBatteryConfig();
                
                if (addMoreBatteriesLater) {
                    // Reset form for next battery type
                    resetStep2Form();
                    // Stay on step 2
                } else {
                    // Move to step 3
                    showStep(STEP_3);
                }
            }
        });
    }
    
    private void resetStep2Form() {
        selectedBatteryId = -1;
        totalBatteries = 0;
        towersCount = 0;
        batteriesInTowers.clear();
        actBatteryModel.setText("");
        etTotalBatteries.setText("");
        etTowersCount.setText("");
        layoutTowerBatteries.removeAllViews();
        layoutTowerBatteries.setVisibility(View.GONE);
        // Reload batteries to exclude already selected ones
        viewModel.getBatteryItems().observe(this, batteries -> {
            if (batteries != null) {
                List<String> batteryNames = new ArrayList<>();
                Set<Integer> usedBatteryIds = new HashSet<>();
                for (BatteryTowerConfig config : batteryTowerConfigs) {
                    usedBatteryIds.add(config.getBatteryModelId());
                }
                for (BatteryItem battery : batteries) {
                    if (!usedBatteryIds.contains(battery.getId())) {
                        batteryNames.add(battery.getName());
                    }
                }
                ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                        android.R.layout.simple_dropdown_item_1line, batteryNames);
                actBatteryModel.setAdapter(adapter);
            }
        });
    }
    
    private void saveCurrentBatteryConfig() {
        if (selectedBatteryId == -1 || totalBatteries == 0 || towersCount == 0) {
            return;
        }
        
        // Find maximum tower number from existing configs to continue global numbering
        int maxTowerNumber = 0;
        for (BatteryTowerConfig config : batteryTowerConfigs) {
            if (config.getTowerNumber() > maxTowerNumber) {
                maxTowerNumber = config.getTowerNumber();
            }
        }
        
        // Calculate batteries per tower
        List<Integer> batteriesPerTower = new ArrayList<>();
        if (towersCount == 1) {
            batteriesPerTower.add(totalBatteries);
        } else {
            for (int i = 0; i < towersCount; i++) {
                if (i < batteriesInTowers.size()) {
                    batteriesPerTower.add(batteriesInTowers.get(i));
                } else {
                    batteriesPerTower.add(0);
                }
            }
        }
        
        // Create configs for each tower with this battery type
        // Start numbering from maxTowerNumber + 1 to maintain global tower numbering
        for (int i = 0; i < towersCount; i++) {
            int towerNumber = maxTowerNumber + i + 1; // Global tower number
            int batteriesCount = batteriesPerTower.get(i);
            batteryTowerConfigs.add(new BatteryTowerConfig(selectedBatteryId, batteriesCount, towerNumber));
        }
    }
    
    private void setupTowerBatteriesInputs(LinearLayout layout, int count) {
        layout.removeAllViews();
        batteriesInTowers.clear();
        
        TextView title = new TextView(requireContext());
        title.setText(R.string.batteries_per_tower);
        title.setTextSize(16);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(requireContext().getColor(R.color.text_primary_dark));
        title.setPadding(0, 0, 0, 8);
        layout.addView(title);
        
        for (int i = 1; i <= count; i++) {
            TextInputLayout til = new TextInputLayout(requireContext(), null,
                    com.google.android.material.R.style.Widget_MaterialComponents_TextInputLayout_OutlinedBox);
            til.setHint(getString(R.string.tower) + " #" + i);
            til.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            
            TextInputEditText et = new TextInputEditText(requireContext());
            et.setInputType(InputType.TYPE_CLASS_NUMBER);
            til.addView(et);
            
            final int towerIndex = i - 1;
            et.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String text = s.toString();
                    if (!text.isEmpty()) {
                        try {
                            if (towerIndex < batteriesInTowers.size()) {
                                batteriesInTowers.set(towerIndex, Integer.parseInt(text));
                            } else {
                                while (batteriesInTowers.size() < towerIndex) {
                                    batteriesInTowers.add(0);
                                }
                                batteriesInTowers.add(Integer.parseInt(text));
                            }
                        } catch (NumberFormatException e) {
                            if (towerIndex < batteriesInTowers.size()) {
                                batteriesInTowers.set(towerIndex, 0);
                            }
                        }
                    } else {
                        if (towerIndex < batteriesInTowers.size()) {
                            batteriesInTowers.set(towerIndex, 0);
                        }
                    }
                }
                
                @Override
                public void afterTextChanged(Editable s) {}
            });
            
            layout.addView(til);
            if (towerIndex >= batteriesInTowers.size()) {
                batteriesInTowers.add(0);
            }
        }
    }
    
    private BatteryItem findBatteryById(int id) {
        for (BatteryItem battery : allBatteries) {
            if (battery.getId() == id) {
                return battery;
            }
        }
        return null;
    }
    
    private BmsItem findBmsById(int id) {
        for (BmsItem bms : allBms) {
            if (bms.getId() == id) {
                return bms;
            }
        }
        // Also check all available BMS from repository
        if (viewModel.getBmsItems().getValue() != null) {
            for (BmsItem bms : viewModel.getBmsItems().getValue()) {
                if (bms.getId() == id) {
                    return bms;
                }
            }
        }
        return null;
    }
    
    private boolean validateStep2() {
        if (selectedBatteryId == -1) {
            Toast.makeText(requireContext(), R.string.error_select_battery_model, Toast.LENGTH_SHORT).show();
            return false;
        }
        
        if (totalBatteries < 1) {
            Toast.makeText(requireContext(), R.string.error_invalid_total_batteries, Toast.LENGTH_SHORT).show();
            return false;
        }
        
        if (towersCount < 1 || towersCount > totalBatteries) {
            Toast.makeText(requireContext(), R.string.error_invalid_towers_count, Toast.LENGTH_SHORT).show();
            return false;
        }
        
        if (towersCount > 1) {
            // Validate tower batteries
            int sum = 0;
            for (int i = 0; i < towersCount; i++) {
                int count = (i < batteriesInTowers.size()) ? batteriesInTowers.get(i) : 0;
                if (count < 1) {
                    Toast.makeText(requireContext(), R.string.error_tower_batteries_sum, Toast.LENGTH_SHORT).show();
                    return false;
                }
                sum += count;
            }
            
            if (sum != totalBatteries) {
                Toast.makeText(requireContext(), R.string.error_tower_batteries_sum, Toast.LENGTH_SHORT).show();
                return false;
            }
        }
        
        return true;
    }
    
    private void setupStep3() {
        actBmsModel = step3View.findViewById(R.id.act_bms_model);
        radioBmsPlacement = step3View.findViewById(R.id.radio_bms_placement);
        com.google.android.material.checkbox.MaterialCheckBox checkboxAddMore = step3View.findViewById(R.id.checkbox_add_more_bms);
        MaterialButton btnCancel = step3View.findViewById(R.id.btn_cancel_step3);
        MaterialButton btnSave = step3View.findViewById(R.id.btn_save_config);
        
        // Load BMS - allow duplicates
        viewModel.getBmsItems().observe(this, bmsList -> {
            if (bmsList != null) {
                allBms.clear();
                allBms.addAll(bmsList);
                
                List<String> bmsNames = new ArrayList<>();
                for (BmsItem bms : bmsList) {
                    bmsNames.add(bms.getName());
                }
                
                ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                        android.R.layout.simple_dropdown_item_1line, bmsNames);
                actBmsModel.setAdapter(adapter);
                
                actBmsModel.setOnItemClickListener((parent, view, position, id) -> {
                    selectedBmsId = bmsList.get(position).getId();
                });
            }
        });
        
        // Setup radio buttons when showing step 3
        setupStep3RadioButtons();
        
        checkboxAddMore.setOnCheckedChangeListener((buttonView, isChecked) -> {
            addMoreBmsLater = isChecked;
        });
        
        btnCancel.setOnClickListener(v -> dismiss());
        btnSave.setOnClickListener(v -> {
            if (validateStep3()) {
                // Save current BMS config
                saveCurrentBmsConfig();
                
                if (addMoreBmsLater) {
                    // Reset form for next BMS
                    resetStep3Form();
                    // Stay on step 3
                } else {
                    // Save config and close
                    saveConfig();
                }
            }
        });
    }
    
    private void setupStep3RadioButtons() {
        if (radioBmsPlacement == null) return;
        
        // Clear existing radio buttons except standalone
        RadioButton standalone = radioBmsPlacement.findViewById(R.id.radio_standalone);
        radioBmsPlacement.removeAllViews();
        if (standalone != null) {
            radioBmsPlacement.addView(standalone);
        } else {
            RadioButton newStandalone = new RadioButton(requireContext());
            newStandalone.setText(R.string.without_tower_standalone);
            newStandalone.setId(R.id.radio_standalone);
            newStandalone.setChecked(true);
            radioBmsPlacement.addView(newStandalone);
        }
        
        // Calculate total number of towers from all battery configurations
        int totalTowersCount = 0;
        if (!batteryTowerConfigs.isEmpty()) {
            // Find maximum tower number from all battery configs
            for (BatteryTowerConfig config : batteryTowerConfigs) {
                if (config.getTowerNumber() > totalTowersCount) {
                    totalTowersCount = config.getTowerNumber();
                }
            }
        } else {
            // Fallback: get towers count from step 2 input if no configs yet
            if (etTowersCount != null) {
                String text = etTowersCount.getText().toString();
                if (!text.isEmpty()) {
                    try {
                        totalTowersCount = Integer.parseInt(text);
                    } catch (NumberFormatException e) {
                        totalTowersCount = 0;
                    }
                }
            }
        }
        
        // Add radio buttons for each tower
        if (totalTowersCount > 0) {
            for (int i = 1; i <= totalTowersCount; i++) {
                RadioButton radio = new RadioButton(requireContext());
                radio.setText(getString(R.string.attach_to_tower, i));
                radio.setId(View.generateViewId());
                radioBmsPlacement.addView(radio);
            }
        }
        
        radioBmsPlacement.setOnCheckedChangeListener((group, checkedId) -> {
            RadioButton checked = group.findViewById(checkedId);
            if (checked != null) {
                String text = checked.getText().toString();
                if (text.contains(getString(R.string.without_tower_standalone))) {
                    attachedTowerNumber = null;
                } else {
                    // Extract tower number from text
                    try {
                        int towerNum = Integer.parseInt(text.replaceAll("[^0-9]", ""));
                        attachedTowerNumber = towerNum;
                    } catch (NumberFormatException e) {
                        attachedTowerNumber = null;
                    }
                }
            }
        });
    }
    
    private void resetStep3Form() {
        selectedBmsId = -1;
        attachedTowerNumber = null;
        actBmsModel.setText("");
        radioBmsPlacement.check(R.id.radio_standalone);
        // Reload BMS - allow duplicates
        viewModel.getBmsItems().observe(this, bmsList -> {
            if (bmsList != null) {
                List<String> bmsNames = new ArrayList<>();
                for (BmsItem bms : bmsList) {
                    bmsNames.add(bms.getName());
                }
                ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                        android.R.layout.simple_dropdown_item_1line, bmsNames);
                actBmsModel.setAdapter(adapter);
            }
        });
    }
    
    private void saveCurrentBmsConfig() {
        if (selectedBmsId == -1) {
            return;
        }
        bmsTowerConfigs.add(new BmsTowerConfig(selectedBmsId, attachedTowerNumber));
    }
    
    private boolean validateStep3() {
        if (selectedBmsId == -1) {
            if (!addMoreBmsLater || bmsTowerConfigs.isEmpty()) {
                Toast.makeText(requireContext(), R.string.error_select_at_least_one_bms, Toast.LENGTH_SHORT).show();
                return false;
            }
            // If checkbox is checked and we already have BMS configs, can proceed to save
            return true;
        }
        
        // Calculate total number of towers from all battery configurations
        int totalTowersCount = 0;
        if (!batteryTowerConfigs.isEmpty()) {
            for (BatteryTowerConfig config : batteryTowerConfigs) {
                if (config.getTowerNumber() > totalTowersCount) {
                    totalTowersCount = config.getTowerNumber();
                }
            }
        }
        
        // Check current selection + existing configs
        List<BmsTowerConfig> allConfigs = new ArrayList<>(bmsTowerConfigs);
        BmsTowerConfig currentConfig = new BmsTowerConfig(selectedBmsId, attachedTowerNumber);
        allConfigs.add(currentConfig);
        
        // Check: maximum number of BMS = number of towers
        if (totalTowersCount > 0 && allConfigs.size() > totalTowersCount) {
            Toast.makeText(requireContext(), R.string.error_too_many_bms, Toast.LENGTH_SHORT).show();
            return false;
        }
        
        // Check: no more than one BMS per tower
        Set<Integer> usedTowers = new HashSet<>();
        for (BmsTowerConfig config : allConfigs) {
            Integer towerNum = config.getAttachedTowerNumber();
            if (towerNum != null && towerNum > 0) {
                if (usedTowers.contains(towerNum)) {
                    Toast.makeText(requireContext(), R.string.error_bms_duplicate_tower, Toast.LENGTH_SHORT).show();
                    return false;
                }
                usedTowers.add(towerNum);
            }
        }
        
        return true;
    }
    
    private void showStep(int step) {
        step1View.setVisibility(step == STEP_1 ? View.VISIBLE : View.GONE);
        step2View.setVisibility(step == STEP_2 ? View.VISIBLE : View.GONE);
        step3View.setVisibility(step == STEP_3 ? View.VISIBLE : View.GONE);
        
        // Setup step 3 radio buttons when showing step 3
        if (step == STEP_3) {
            setupStep3RadioButtons();
        }
    }
    
    private void saveConfig() {
        // Use config name from input
        if (configName.isEmpty()) {
            configName = "Config " + System.currentTimeMillis();
        }
        ProjectConfig config = new ProjectConfig(configName);
        long configId = viewModel.insertConfig(config);
        
        if (configId > 0) {
            config.setId((int) configId);
            
            // Save inverters with roles
            saveInverters(configId);
            
            // Save towers
            saveTowers(configId);
            
            // Save BMS
            saveBms(configId);
            
            dismiss();
        }
    }
    
    private void saveInverters(long configId) {
        List<ConfigInverter> configInverters = new ArrayList<>();
        
        // Get ordered list from adapter (order of selection)
        List<Integer> orderedInverterIds = inverterAdapter != null ? 
                inverterAdapter.getSelectedIds() : selectedInverterIds;
        
        // Get counts from adapter
        Map<Integer, Integer> inverterCounts = inverterAdapter != null ?
                inverterAdapter.getInverterCounts() : new HashMap<>();
        
        for (int i = 0; i < orderedInverterIds.size(); i++) {
            int inverterId = orderedInverterIds.get(i);
            int count = inverterCounts.getOrDefault(inverterId, 1);
            // First selected inverter is Master, others are Slave1, Slave2, ...
            String role = (i == 0) ? "Master" : "Slave" + i;
            configInverters.add(new ConfigInverter((int) configId, inverterId, role, count));
        }
        
        viewModel.saveConfigInverters(configInverters);
    }
    
    private void saveTowers(long configId) {
        List<ConfigTower> configTowers = new ArrayList<>();
        
        // Save battery tower configs
        for (BatteryTowerConfig batteryConfig : batteryTowerConfigs) {
            configTowers.add(new ConfigTower(
                    (int) configId,
                    batteryConfig.getTowerNumber(),
                    batteryConfig.getBatteryModelId(),
                    batteryConfig.getBatteriesCount()
            ));
        }
        
        viewModel.saveConfigTowers(configTowers);
    }
    
    private void saveBms(long configId) {
        List<ConfigBms> configBmsList = new ArrayList<>();
        
        // Save all BMS configurations
        for (BmsTowerConfig bmsConfig : bmsTowerConfigs) {
            configBmsList.add(new ConfigBms(
                    (int) configId,
                    bmsConfig.getBmsId(),
                    bmsConfig.getAttachedTowerNumber()
            ));
        }
        
        viewModel.saveConfigBmsList(configBmsList);
    }
}

