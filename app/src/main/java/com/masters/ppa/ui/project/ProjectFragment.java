package com.masters.ppa.ui.project;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.masters.ppa.R;
import com.masters.ppa.data.model.BatteryItem;
import com.masters.ppa.data.model.BmsItem;
import com.masters.ppa.data.model.ConfigBms;
import com.masters.ppa.data.model.ConfigInverter;
import com.masters.ppa.data.model.ConfigTower;
import com.masters.ppa.data.model.InverterItem;
import com.masters.ppa.data.model.ProjectConfig;
import com.masters.ppa.databinding.FragmentProjectBinding;
import com.masters.ppa.ui.project.ar.ArDesignActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment for the Project screen
 */
public class ProjectFragment extends Fragment {

    private FragmentProjectBinding binding;
    private ProjectViewModel viewModel;
    
    // Adapters
    private ProjectRecyclerAdapter inverterAdapter;
    private ProjectRecyclerAdapter batteryAdapter;
    private ProjectRecyclerAdapter bmsAdapter;
    private ConfigRecyclerAdapter configAdapter;
    
    // Delete mode flags
    private boolean isInverterDeleteMode = false;
    private boolean isBatteryDeleteMode = false;
    private boolean isBmsDeleteMode = false;
    private boolean isConfigDeleteMode = false;
    
    // Expand state flags (true = expanded, false = collapsed)
    private boolean isInverterExpanded = false; // Collapsed by default
    private boolean isBatteryExpanded = false; // Collapsed by default
    private boolean isBmsExpanded = false; // Collapsed by default
    private boolean isConfigExpanded = true; // Expanded by default
    
    // Current items lists
    private List<InverterItem> inverterItems = new ArrayList<>();
    private List<BatteryItem> batteryItems = new ArrayList<>();
    private List<BmsItem> bmsItems = new ArrayList<>();
    private List<ProjectConfig> configs = new ArrayList<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentProjectBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(ProjectViewModel.class);
        
        setupAdapters();
        setupCollapsibleSections();
        setupButtons();
        setupObservers();
    }
    
    private void setupAdapters() {
        inverterAdapter = new ProjectRecyclerAdapter(inverterItems);
        batteryAdapter = new ProjectRecyclerAdapter(batteryItems);
        bmsAdapter = new ProjectRecyclerAdapter(bmsItems);
        
        // Setup RecyclerViews with LinearLayoutManager
        binding.listInverter.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.listInverter.setAdapter(inverterAdapter);
        
        binding.listBattery.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.listBattery.setAdapter(batteryAdapter);
        
        binding.listBms.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.listBms.setAdapter(bmsAdapter);
        
        configAdapter = new ConfigRecyclerAdapter(configs);
        binding.listConfig.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.listConfig.setAdapter(configAdapter);
        
        // Setup delete listeners
        inverterAdapter.setDeleteListener(position -> {
            if (position < inverterItems.size()) {
                InverterItem item = inverterItems.get(position);
                viewModel.deleteInverter(item);
            }
        });
        
        batteryAdapter.setDeleteListener(position -> {
            if (position < batteryItems.size()) {
                BatteryItem item = batteryItems.get(position);
                viewModel.deleteBattery(item);
            }
        });
        
        bmsAdapter.setDeleteListener(position -> {
            if (position < bmsItems.size()) {
                BmsItem item = bmsItems.get(position);
                viewModel.deleteBms(item);
            }
        });
        
        // Setup item click listeners for viewing details
        inverterAdapter.setItemClickListener(position -> {
            if (position < inverterItems.size()) {
                InverterItem item = inverterItems.get(position);
                ViewItemDetailsDialog dialog = ViewItemDetailsDialog.newInstance(item);
                dialog.show(getParentFragmentManager(), "ViewInverterDetails");
            }
        });
        
        batteryAdapter.setItemClickListener(position -> {
            if (position < batteryItems.size()) {
                BatteryItem item = batteryItems.get(position);
                ViewItemDetailsDialog dialog = ViewItemDetailsDialog.newInstance(item);
                dialog.show(getParentFragmentManager(), "ViewBatteryDetails");
            }
        });
        
        bmsAdapter.setItemClickListener(position -> {
            if (position < bmsItems.size()) {
                BmsItem item = bmsItems.get(position);
                ViewItemDetailsDialog dialog = ViewItemDetailsDialog.newInstance(item);
                dialog.show(getParentFragmentManager(), "ViewBmsDetails");
            }
        });
    }
    
    private void setupCollapsibleSections() {
        // Find views using root view
        View rootView = binding.getRoot();
        
        // Inverter section - collapsed by default
        View headerInverter = rootView.findViewById(R.id.header_inverter);
        View contentInverter = rootView.findViewById(R.id.content_inverter);
        ImageView iconInverter = rootView.findViewById(R.id.icon_expand_inverter);
        
        if (contentInverter != null) {
            contentInverter.setVisibility(View.GONE); // Collapsed by default
        }
        if (iconInverter != null) {
            iconInverter.setRotation(180f); // Arrow up when collapsed
        }
        
        if (headerInverter != null) {
            headerInverter.setOnClickListener(v -> {
                isInverterExpanded = !isInverterExpanded;
                toggleSection(contentInverter, iconInverter, !isInverterExpanded, null);
            });
        }
        
        // Battery section - collapsed by default
        View headerBattery = rootView.findViewById(R.id.header_battery);
        View contentBattery = rootView.findViewById(R.id.content_battery);
        ImageView iconBattery = rootView.findViewById(R.id.icon_expand_battery);
        
        if (contentBattery != null) {
            contentBattery.setVisibility(View.GONE); // Collapsed by default
        }
        if (iconBattery != null) {
            iconBattery.setRotation(180f); // Arrow up when collapsed
        }
        
        if (headerBattery != null) {
            headerBattery.setOnClickListener(v -> {
                isBatteryExpanded = !isBatteryExpanded;
                toggleSection(contentBattery, iconBattery, !isBatteryExpanded, null);
            });
        }
        
        // BMS section - collapsed by default
        View headerBms = rootView.findViewById(R.id.header_bms);
        View contentBms = rootView.findViewById(R.id.content_bms);
        ImageView iconBms = rootView.findViewById(R.id.icon_expand_bms);
        
        if (contentBms != null) {
            contentBms.setVisibility(View.GONE); // Collapsed by default
        }
        if (iconBms != null) {
            iconBms.setRotation(180f); // Arrow up when collapsed
        }
        
        if (headerBms != null) {
            headerBms.setOnClickListener(v -> {
                isBmsExpanded = !isBmsExpanded;
                toggleSection(contentBms, iconBms, !isBmsExpanded, null);
            });
        }
        
        // Config section - expanded by default
        View headerConfig = rootView.findViewById(R.id.header_config);
        View contentConfig = rootView.findViewById(R.id.content_config);
        ImageView iconConfig = rootView.findViewById(R.id.icon_expand_config);
        
        if (contentConfig != null) {
            contentConfig.setVisibility(View.VISIBLE);
        }
        if (iconConfig != null) {
            iconConfig.setRotation(0f);
        }
        
        if (headerConfig != null) {
            headerConfig.setOnClickListener(v -> {
                isConfigExpanded = !isConfigExpanded;
                toggleSection(contentConfig, iconConfig, !isConfigExpanded, null);
            });
        }
    }
    
    private void toggleSection(View content, ImageView icon, boolean isExpanded, java.util.function.Consumer<Boolean> onStateChanged) {
        boolean newState = !isExpanded;
        
        if (content != null) {
            content.setVisibility(newState ? View.VISIBLE : View.GONE);
        }
        
        if (icon != null) {
            // Rotate icon animation
            float fromRotation = isExpanded ? 0f : 180f;
            float toRotation = newState ? 0f : 180f;
            
            RotateAnimation rotateAnimation = new RotateAnimation(
                    fromRotation, toRotation,
                    RotateAnimation.RELATIVE_TO_SELF, 0.5f,
                    RotateAnimation.RELATIVE_TO_SELF, 0.5f);
            rotateAnimation.setDuration(200);
            rotateAnimation.setFillAfter(true);
            icon.startAnimation(rotateAnimation);
        }
        
        if (onStateChanged != null) {
            onStateChanged.accept(newState);
        }
    }
    
    private void setupButtons() {
        // Inverter buttons
        binding.btnAddInverter.setOnClickListener(v -> {
            AddItemDialog dialog = new AddItemDialog(getString(R.string.inverter), AddItemDialog.ItemType.INVERTER);
            dialog.setOnItemAddedListener(new AddItemDialog.OnItemAddedListener() {
                @Override
                public void onItemAdded(String name, double width, double height, double depth, Double powerKw, Double capacityKWh) {
                    if (powerKw != null) {
                        InverterItem item = new InverterItem(name, width, height, depth, powerKw);
                        viewModel.insertInverter(item);
                    }
                }
                
                @Override
                public int checkNameExists(String name) {
                    return viewModel.checkInverterNameExists(name);
                }
            });
            dialog.show(getParentFragmentManager(), "AddInverterDialog");
        });
        
        binding.btnRemoveInverter.setOnClickListener(v -> {
            if (isInverterDeleteMode) {
                // Cancel delete mode
                isInverterDeleteMode = false;
                inverterAdapter.setDeleteMode(false);
                binding.btnRemoveInverter.setText(R.string.remove);
            } else {
                // Enter delete mode
                isInverterDeleteMode = true;
                inverterAdapter.setDeleteMode(true);
                binding.btnRemoveInverter.setText(R.string.cancel);
            }
        });
        
        // Battery buttons
        binding.btnAddBattery.setOnClickListener(v -> {
            AddItemDialog dialog = new AddItemDialog(getString(R.string.battery), AddItemDialog.ItemType.BATTERY);
            dialog.setOnItemAddedListener(new AddItemDialog.OnItemAddedListener() {
                @Override
                public void onItemAdded(String name, double width, double height, double depth, Double powerKw, Double capacityKWh) {
                    if (capacityKWh != null) {
                        BatteryItem item = new BatteryItem(name, width, height, depth, capacityKWh);
                        viewModel.insertBattery(item);
                    }
                }
                
                @Override
                public int checkNameExists(String name) {
                    return viewModel.checkBatteryNameExists(name);
                }
            });
            dialog.show(getParentFragmentManager(), "AddBatteryDialog");
        });
        
        binding.btnRemoveBattery.setOnClickListener(v -> {
            if (isBatteryDeleteMode) {
                // Cancel delete mode
                isBatteryDeleteMode = false;
                batteryAdapter.setDeleteMode(false);
                binding.btnRemoveBattery.setText(R.string.remove);
            } else {
                // Enter delete mode
                isBatteryDeleteMode = true;
                batteryAdapter.setDeleteMode(true);
                binding.btnRemoveBattery.setText(R.string.cancel);
            }
        });
        
        // BMS buttons
        binding.btnAddBms.setOnClickListener(v -> {
            AddItemDialog dialog = new AddItemDialog(getString(R.string.bms), AddItemDialog.ItemType.BMS);
            dialog.setOnItemAddedListener(new AddItemDialog.OnItemAddedListener() {
                @Override
                public void onItemAdded(String name, double width, double height, double depth, Double powerKw, Double capacityKWh) {
                    BmsItem item = new BmsItem(name, width, height, depth);
                    viewModel.insertBms(item);
                }
                
                @Override
                public int checkNameExists(String name) {
                    return viewModel.checkBmsNameExists(name);
                }
            });
            dialog.show(getParentFragmentManager(), "AddBmsDialog");
        });
        
        binding.btnRemoveBms.setOnClickListener(v -> {
            if (isBmsDeleteMode) {
                // Cancel delete mode
                isBmsDeleteMode = false;
                bmsAdapter.setDeleteMode(false);
                binding.btnRemoveBms.setText(R.string.cancel);
            } else {
                // Enter delete mode
                isBmsDeleteMode = true;
                bmsAdapter.setDeleteMode(true);
                binding.btnRemoveBms.setText(R.string.remove);
            }
        });
        
        // Config buttons
        binding.btnAddConfig.setOnClickListener(v -> {
            if (viewModel.canAddConfigSync()) {
                CreateConfigDialog dialog = new CreateConfigDialog();
                dialog.show(getParentFragmentManager(), "CreateConfigDialog");
            }
        });
        
        binding.btnRemoveConfig.setOnClickListener(v -> {
            if (isConfigDeleteMode) {
                // Cancel delete mode
                isConfigDeleteMode = false;
                configAdapter.setDeleteMode(false);
                binding.btnRemoveConfig.setText(R.string.remove);
            } else {
                // Enter delete mode
                isConfigDeleteMode = true;
                configAdapter.setDeleteMode(true);
                binding.btnRemoveConfig.setText(R.string.cancel);
            }
        });
        
        // Start button
        binding.btnStart.setOnClickListener(v -> {
            if (configs != null && !configs.isEmpty()) {
                SelectConfigDialog dialog = SelectConfigDialog.newInstance(configs);
                dialog.setOnConfigSelectedListener(config -> {
                    // Open AR Design Activity with selected config
                    android.content.Intent intent = new android.content.Intent(requireContext(), 
                            ArDesignActivity.class);
                    intent.putExtra("config_id", config.getId());
                    intent.putExtra("config_name", config.getName());
                    startActivity(intent);
                });
                dialog.show(getParentFragmentManager(), "SelectConfigDialog");
            }
        });
    }
    
    private void setupObservers() {
        viewModel.getInverterItems().observe(getViewLifecycleOwner(), items -> {
            if (items != null) {
                inverterItems.clear();
                inverterItems.addAll(items);
                inverterAdapter.setItems(items);
            }
        });
        
        viewModel.getBatteryItems().observe(getViewLifecycleOwner(), items -> {
            if (items != null) {
                batteryItems.clear();
                batteryItems.addAll(items);
                batteryAdapter.setItems(items);
            }
        });
        
        viewModel.getBmsItems().observe(getViewLifecycleOwner(), items -> {
            if (items != null) {
                bmsItems.clear();
                bmsItems.addAll(items);
                bmsAdapter.setItems(items);
                updateAddConfigButtonState();
            }
        });
        
        viewModel.getConfigs().observe(getViewLifecycleOwner(), items -> {
            if (items != null) {
                configs.clear();
                configs.addAll(items);
                configAdapter.setConfigs(items);
                updateStartButtonState();
            }
        });
        
        // Setup config click listener
        configAdapter.setItemClickListener(position -> {
            if (!isConfigDeleteMode && position < configs.size()) {
                ProjectConfig config = configs.get(position);
                
                // Get config details
                List<ConfigInverter> configInverters = viewModel.getConfigInverters(config.getId());
                List<ConfigTower> configTowers = viewModel.getConfigTowers(config.getId());
                List<ConfigBms> configBmsList = viewModel.getAllConfigBms(config.getId());
                
                ViewConfigDetailsDialog dialog = ViewConfigDetailsDialog.newInstance(
                        config,
                        configInverters,
                        configTowers,
                        configBmsList,
                        inverterItems,
                        batteryItems,
                        bmsItems
                );
                dialog.show(getParentFragmentManager(), "ViewConfigDetails");
            }
        });
        
        // Setup config delete listener
        configAdapter.setDeleteListener(position -> {
            if (position < configs.size()) {
                ProjectConfig config = configs.get(position);
                viewModel.deleteConfig(config);
            }
        });
        
        // Observe inverter, battery, bms changes to update Add Config button
        viewModel.getInverterItems().observe(getViewLifecycleOwner(), items -> updateAddConfigButtonState());
        viewModel.getBatteryItems().observe(getViewLifecycleOwner(), items -> updateAddConfigButtonState());
    }
    
    private void updateAddConfigButtonState() {
        boolean canAdd = viewModel.canAddConfigSync();
        binding.btnAddConfig.setEnabled(canAdd);
        binding.btnAddConfig.setAlpha(canAdd ? 1.0f : 0.5f);
    }
    
    private void updateStartButtonState() {
        boolean hasConfigs = configs != null && !configs.isEmpty();
        binding.btnStart.setEnabled(hasConfigs);
        binding.btnStart.setAlpha(hasConfigs ? 1.0f : 0.5f);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
