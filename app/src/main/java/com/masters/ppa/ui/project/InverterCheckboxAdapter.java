package com.masters.ppa.ui.project;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.masters.ppa.R;
import com.masters.ppa.data.model.InverterItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Adapter for inverter checkbox list in config dialog
 */
public class InverterCheckboxAdapter extends RecyclerView.Adapter<InverterCheckboxAdapter.ViewHolder> {
    
    private List<InverterItem> inverters;
    private List<Integer> selectedIds = new ArrayList<>(); // Use list to preserve order
    private Map<Integer, Integer> inverterCounts = new HashMap<>(); // Map inverterId -> count
    
    public InverterCheckboxAdapter(List<InverterItem> inverters) {
        this.inverters = inverters != null ? inverters : new ArrayList<>();
    }
    
    public void setInverters(List<InverterItem> inverters) {
        this.inverters = inverters != null ? inverters : new ArrayList<>();
        notifyDataSetChanged();
    }
    
    public List<Integer> getSelectedIds() {
        return new ArrayList<>(selectedIds);
    }
    
    public int getInverterCount(int inverterId) {
        return inverterCounts.getOrDefault(inverterId, 1);
    }
    
    public Map<Integer, Integer> getInverterCounts() {
        return new HashMap<>(inverterCounts);
    }
    
    public boolean isSelected(int id) {
        return selectedIds.contains(id);
    }
    
    public boolean hasSelection() {
        return !selectedIds.isEmpty();
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_inverter_checkbox, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        InverterItem inverter = inverters.get(position);
        boolean isSelected = selectedIds.contains(inverter.getId());
        int count = inverterCounts.getOrDefault(inverter.getId(), 1);
        holder.bind(inverter, isSelected, count);
    }
    
    public int getSelectionOrder(int id) {
        return selectedIds.indexOf(id);
    }
    
    @Override
    public int getItemCount() {
        return inverters.size();
    }
    
    class ViewHolder extends RecyclerView.ViewHolder {
        private final CheckBox checkBox;
        private final TextView nameText;
        private final TextInputLayout tilCount;
        private final TextInputEditText etCount;
        private TextWatcher countTextWatcher;
        
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.checkbox_inverter);
            nameText = itemView.findViewById(R.id.text_inverter_name);
            tilCount = itemView.findViewById(R.id.til_inverter_count);
            etCount = itemView.findViewById(R.id.et_inverter_count);
        }
        
        public void bind(InverterItem inverter, boolean isChecked, int count) {
            nameText.setText(inverter.getName());
            checkBox.setChecked(isChecked);
            
            // Show/hide count field based on selection
            if (tilCount != null) {
                tilCount.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            }
            
            // Set count value
            if (etCount != null) {
                // Remove previous listener if exists
                if (countTextWatcher != null) {
                    etCount.removeTextChangedListener(countTextWatcher);
                }
                
                // Set text without triggering listener
                etCount.setText(String.valueOf(count));
                
                // Create and add new text watcher for count
                countTextWatcher = new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    
                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        String text = s.toString();
                        if (!text.isEmpty()) {
                            try {
                                int countValue = Integer.parseInt(text);
                                if (countValue > 0) {
                                    inverterCounts.put(inverter.getId(), countValue);
                                }
                            } catch (NumberFormatException e) {
                                // Ignore invalid input
                            }
                        } else {
                            inverterCounts.put(inverter.getId(), 1);
                        }
                    }
                    
                    @Override
                    public void afterTextChanged(Editable s) {}
                };
                etCount.addTextChangedListener(countTextWatcher);
            }
            
            checkBox.setOnCheckedChangeListener((buttonView, checked) -> {
                if (checked) {
                    // Add to end to preserve order
                    if (!selectedIds.contains(inverter.getId())) {
                        selectedIds.add(inverter.getId());
                    }
                    // Initialize count if not set
                    if (!inverterCounts.containsKey(inverter.getId())) {
                        inverterCounts.put(inverter.getId(), 1);
                    }
                    // Show count field
                    if (tilCount != null) {
                        tilCount.setVisibility(View.VISIBLE);
                    }
                } else {
                    // Remove while preserving order
                    selectedIds.remove((Integer) inverter.getId());
                    inverterCounts.remove(inverter.getId());
                    // Hide count field
                    if (tilCount != null) {
                        tilCount.setVisibility(View.GONE);
                    }
                }
            });
        }
    }
}

