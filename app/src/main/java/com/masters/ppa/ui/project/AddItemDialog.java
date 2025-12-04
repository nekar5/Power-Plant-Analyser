package com.masters.ppa.ui.project;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.masters.ppa.R;
import com.masters.ppa.databinding.DialogAddProjectItemBinding;

/**
 * Dialog for adding project items (Inverter, Battery, BMS)
 */
public class AddItemDialog extends DialogFragment {
    
    public interface OnItemAddedListener {
        void onItemAdded(String name, double width, double height, double depth, Double powerKw, Double capacityKWh);
        int checkNameExists(String name);
    }
    
    public enum ItemType {
        INVERTER, BATTERY, BMS
    }
    
    private DialogAddProjectItemBinding binding;
    private OnItemAddedListener listener;
    private String dialogTitle;
    private ItemType itemType;
    
    public AddItemDialog(String dialogTitle, ItemType itemType) {
        this.dialogTitle = dialogTitle;
        this.itemType = itemType;
    }
    
    public void setOnItemAddedListener(OnItemAddedListener listener) {
        this.listener = listener;
    }
    
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
            // Set dialog width to match screen width
            Window window = dialog.getWindow();
            if (window != null) {
                window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, 
                                 android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        }
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DialogAddProjectItemBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Setup fields visibility based on item type
        if (itemType == ItemType.INVERTER) {
            binding.tilPowerKw.setVisibility(View.VISIBLE);
            binding.tilCapacityKwh.setVisibility(View.GONE);
        } else if (itemType == ItemType.BATTERY) {
            binding.tilPowerKw.setVisibility(View.GONE);
            binding.tilCapacityKwh.setVisibility(View.VISIBLE);
        } else { // BMS
            binding.tilPowerKw.setVisibility(View.GONE);
            binding.tilCapacityKwh.setVisibility(View.GONE);
        }
        
        // Setup text watchers for validation
        binding.etName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                validateName();
            }
            
            @Override
            public void afterTextChanged(Editable s) {}
        });
        
        binding.etWidth.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                validateDimensions();
            }
            
            @Override
            public void afterTextChanged(Editable s) {}
        });
        
        binding.etHeight.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                validateDimensions();
            }
            
            @Override
            public void afterTextChanged(Editable s) {}
        });
        
        binding.etDepth.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                validateDimensions();
            }
            
            @Override
            public void afterTextChanged(Editable s) {}
        });
        
        if (binding.etPowerKw != null) {
            binding.etPowerKw.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    validatePowerKw();
                }
                
                @Override
                public void afterTextChanged(Editable s) {}
            });
        }
        
        if (binding.etCapacityKwh != null) {
            binding.etCapacityKwh.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    validateCapacityKwh();
                }
                
                @Override
                public void afterTextChanged(Editable s) {}
            });
        }
        
        // Setup buttons
        binding.btnSave.setOnClickListener(v -> {
            if (validateAll()) {
                String name = binding.etName.getText().toString().trim();
                double width = parseDouble(binding.etWidth.getText().toString());
                double height = parseDouble(binding.etHeight.getText().toString());
                double depth = parseDouble(binding.etDepth.getText().toString());
                
                Double powerKw = null;
                Double capacityKWh = null;
                
                if (itemType == ItemType.INVERTER && binding.etPowerKw != null) {
                    powerKw = parseDouble(binding.etPowerKw.getText().toString());
                } else if (itemType == ItemType.BATTERY && binding.etCapacityKwh != null) {
                    capacityKWh = parseDouble(binding.etCapacityKwh.getText().toString());
                }
                
                if (listener != null) {
                    listener.onItemAdded(name, width, height, depth, powerKw, capacityKWh);
                }
                dismiss();
            }
        });
        
        binding.btnCancel.setOnClickListener(v -> dismiss());
    }
    
    private boolean validateName() {
        String name = binding.etName.getText().toString().trim();
        TextInputLayout tilName = binding.tilName;
        
        if (name.isEmpty()) {
            tilName.setError(getString(R.string.error_name_empty));
            return false;
        }
        
        if (name.length() > 30) {
            tilName.setError(getString(R.string.error_name_too_long));
            return false;
        }
        
        if (listener != null) {
            int count = listener.checkNameExists(name);
            if (count > 0) {
                tilName.setError(getString(R.string.error_name_exists));
                return false;
            }
        }
        
        tilName.setError(null);
        return true;
    }
    
    private static final double MIN_POWER_KW = 1.0;
    private static final double MAX_POWER_KW = 100.0;
    
    private boolean validatePowerKw() {
        if (itemType != ItemType.INVERTER || binding.etPowerKw == null) {
            return true;
        }
        
        double powerKw = parseDouble(binding.etPowerKw.getText().toString());
        
        if (powerKw < MIN_POWER_KW || powerKw > MAX_POWER_KW) {
            binding.tilPowerKw.setError(getString(R.string.error_power_kw_invalid));
            return false;
        }
        
        binding.tilPowerKw.setError(null);
        return true;
    }
    
    private static final double MIN_CAPACITY_KWH = 1.0;
    private static final double MAX_CAPACITY_KWH = 100.0;
    
    private boolean validateCapacityKwh() {
        if (itemType != ItemType.BATTERY || binding.etCapacityKwh == null) {
            return true;
        }
        
        double capacityKWh = parseDouble(binding.etCapacityKwh.getText().toString());
        
        if (capacityKWh < MIN_CAPACITY_KWH || capacityKWh > MAX_CAPACITY_KWH) {
            binding.tilCapacityKwh.setError(getString(R.string.error_capacity_kwh_invalid));
            return false;
        }
        
        binding.tilCapacityKwh.setError(null);
        return true;
    }
    
    private static final double MIN_DIMENSION = 100.0;
    private static final double MAX_DIMENSION = 3000.0;
    
    private boolean validateDimensions() {
        boolean valid = true;
        
        double width = parseDouble(binding.etWidth.getText().toString());
        double height = parseDouble(binding.etHeight.getText().toString());
        double depth = parseDouble(binding.etDepth.getText().toString());
        
        // Validate width
        if (width < MIN_DIMENSION) {
            binding.tilWidth.setError(getString(R.string.error_dimension_too_small));
            valid = false;
        } else if (width > MAX_DIMENSION) {
            binding.tilWidth.setError(getString(R.string.error_dimension_too_large));
            valid = false;
        } else {
            binding.tilWidth.setError(null);
        }
        
        // Validate height
        if (height < MIN_DIMENSION) {
            binding.tilHeight.setError(getString(R.string.error_dimension_too_small));
            valid = false;
        } else if (height > MAX_DIMENSION) {
            binding.tilHeight.setError(getString(R.string.error_dimension_too_large));
            valid = false;
        } else {
            binding.tilHeight.setError(null);
        }
        
        // Validate depth
        if (depth < MIN_DIMENSION) {
            binding.tilDepth.setError(getString(R.string.error_dimension_too_small));
            valid = false;
        } else if (depth > MAX_DIMENSION) {
            binding.tilDepth.setError(getString(R.string.error_dimension_too_large));
            valid = false;
        } else {
            binding.tilDepth.setError(null);
        }
        
        return valid;
    }
    
    private boolean validateAll() {
        boolean nameValid = validateName();
        boolean dimensionsValid = validateDimensions();
        boolean powerKwValid = validatePowerKw();
        boolean capacityKwhValid = validateCapacityKwh();
        return nameValid && dimensionsValid && powerKwValid && capacityKwhValid;
    }
    
    private double parseDouble(String text) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

