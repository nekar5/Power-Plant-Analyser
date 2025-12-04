package com.masters.ppa.ui.project;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.masters.ppa.R;
import com.masters.ppa.data.model.BmsItem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Adapter for BMS checkbox list in config dialog
 */
public class BmsCheckboxAdapter extends RecyclerView.Adapter<BmsCheckboxAdapter.ViewHolder> {
    
    private List<BmsItem> bmsList;
    private Set<Integer> selectedIds = new HashSet<>();
    private OnSelectionChangedListener selectionChangedListener;
    
    public interface OnSelectionChangedListener {
        void onSelectionChanged();
    }
    
    public BmsCheckboxAdapter(List<BmsItem> bmsList) {
        this.bmsList = bmsList != null ? bmsList : new ArrayList<>();
    }
    
    public void setOnSelectionChangedListener(OnSelectionChangedListener listener) {
        this.selectionChangedListener = listener;
    }
    
    public void setBmsList(List<BmsItem> bmsList) {
        this.bmsList = bmsList != null ? bmsList : new ArrayList<>();
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
        BmsItem bms = bmsList.get(position);
        boolean isChecked = selectedIds.contains(bms.getId());
        holder.bind(bms, isChecked);
    }
    
    @Override
    public int getItemCount() {
        return bmsList.size();
    }
    
    class ViewHolder extends RecyclerView.ViewHolder {
        private final CheckBox checkBox;
        private final TextView nameText;
        
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.checkbox_battery);
            nameText = itemView.findViewById(R.id.text_battery_name);
        }
        
        public void bind(BmsItem bms, boolean isChecked) {
            nameText.setText(bms.getName());
            checkBox.setChecked(isChecked);
            
            checkBox.setOnCheckedChangeListener((buttonView, checked) -> {
                if (checked) {
                    selectedIds.add(bms.getId());
                } else {
                    selectedIds.remove(bms.getId());
                }
                if (selectionChangedListener != null) {
                    selectionChangedListener.onSelectionChanged();
                }
            });
        }
    }
}

