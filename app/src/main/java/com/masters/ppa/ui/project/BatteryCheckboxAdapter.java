package com.masters.ppa.ui.project;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.masters.ppa.R;
import com.masters.ppa.data.model.BatteryItem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Adapter for battery checkbox list in config dialog
 */
public class BatteryCheckboxAdapter extends RecyclerView.Adapter<BatteryCheckboxAdapter.ViewHolder> {
    
    private List<BatteryItem> batteries;
    private Set<Integer> selectedIds = new HashSet<>();
    private Set<Integer> disabledIds = new HashSet<>(); // Already selected batteries
    private OnSelectionChangedListener selectionChangedListener;
    
    public interface OnSelectionChangedListener {
        void onSelectionChanged();
    }
    
    public BatteryCheckboxAdapter(List<BatteryItem> batteries) {
        this.batteries = batteries != null ? batteries : new ArrayList<>();
    }
    
    public void setOnSelectionChangedListener(OnSelectionChangedListener listener) {
        this.selectionChangedListener = listener;
    }
    
    public void setBatteries(List<BatteryItem> batteries) {
        this.batteries = batteries != null ? batteries : new ArrayList<>();
        notifyDataSetChanged();
    }
    
    public void setDisabledIds(Set<Integer> disabledIds) {
        this.disabledIds = disabledIds != null ? disabledIds : new HashSet<>();
        notifyDataSetChanged();
    }
    
    public List<Integer> getSelectedIds() {
        return new ArrayList<>(selectedIds);
    }
    
    public boolean hasSelection() {
        return !selectedIds.isEmpty();
    }
    
    public void clearSelection() {
        selectedIds.clear();
        notifyDataSetChanged();
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_battery_checkbox, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BatteryItem battery = batteries.get(position);
        boolean isSelected = selectedIds.contains(battery.getId());
        boolean isDisabled = disabledIds.contains(battery.getId());
        
        holder.bind(battery, isSelected, isDisabled);
    }
    
    @Override
    public int getItemCount() {
        return batteries.size();
    }
    
    class ViewHolder extends RecyclerView.ViewHolder {
        private final CheckBox checkBox;
        private final TextView nameText;
        
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.checkbox_battery);
            nameText = itemView.findViewById(R.id.text_battery_name);
        }
        
        public void bind(BatteryItem battery, boolean isChecked, boolean isDisabled) {
            nameText.setText(battery.getName());
            checkBox.setChecked(isChecked);
            checkBox.setEnabled(!isDisabled);
            nameText.setAlpha(isDisabled ? 0.5f : 1.0f);
            
            if (!isDisabled) {
                checkBox.setOnCheckedChangeListener((buttonView, checked) -> {
                    if (checked) {
                        selectedIds.add(battery.getId());
                    } else {
                        selectedIds.remove(battery.getId());
                    }
                    if (selectionChangedListener != null) {
                        selectionChangedListener.onSelectionChanged();
                    }
                });
            } else {
                checkBox.setOnCheckedChangeListener(null);
            }
        }
    }
}

