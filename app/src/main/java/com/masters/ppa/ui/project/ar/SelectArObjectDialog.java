package com.masters.ppa.ui.project.ar;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.masters.ppa.R;
import com.masters.ppa.data.model.BatteryItem;
import com.masters.ppa.data.model.BmsItem;
import com.masters.ppa.data.model.ConfigBms;
import com.masters.ppa.data.model.ConfigInverter;
import com.masters.ppa.data.model.ConfigTower;
import com.masters.ppa.data.model.InverterItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dialog for selecting AR objects (inverters, battery towers, standalone BMS)
 */
public class SelectArObjectDialog extends DialogFragment {
    
    private RecyclerView recyclerView;
    private TextView textEmpty;
    private ObjectSelectionAdapter adapter;
    private OnObjectSelectedListener listener;
    
    private List<ObjectItem> objectItems = new ArrayList<>();
    
    public interface OnObjectSelectedListener {
        void onInverterSelected(ConfigInverter configInverter, InverterItem inverterItem);
        void onTowerSelected(ConfigTower configTower, BatteryItem batteryItem, ConfigBms attachedBms, BmsItem bmsItem);
        void onStandaloneBmsSelected(ConfigBms configBms, BmsItem bmsItem);
    }
    
    public static SelectArObjectDialog newInstance(
            List<ConfigInverter> configInverters,
            List<InverterItem> inverterItems,
            List<ConfigTower> configTowers,
            List<BatteryItem> batteryItems,
            List<ConfigBms> configBmsList,
            List<BmsItem> bmsItems,
            Map<Integer, Integer> placedInverterCounts,
            Map<Integer, Integer> placedTowerCounts,
            Map<Integer, Integer> placedBmsCounts) {
        SelectArObjectDialog dialog = new SelectArObjectDialog();
        
        // Create map for quick lookup
        Map<Integer, InverterItem> inverterMap = new HashMap<>();
        for (InverterItem item : inverterItems) {
            inverterMap.put(item.getId(), item);
        }
        
        Map<Integer, BatteryItem> batteryMap = new HashMap<>();
        for (BatteryItem item : batteryItems) {
            batteryMap.put(item.getId(), item);
        }
        
        Map<Integer, BmsItem> bmsMap = new HashMap<>();
        for (BmsItem item : bmsItems) {
            bmsMap.put(item.getId(), item);
        }
        
        // Map tower numbers to BMS
        Map<Integer, ConfigBms> towerBmsMap = new HashMap<>();
        for (ConfigBms configBms : configBmsList) {
            if (configBms.getAttachedTowerNumber() != null) {
                towerBmsMap.put(configBms.getAttachedTowerNumber(), configBms);
            }
        }
        
        // Add all inverters (show all, mark placed ones as disabled)
        for (ConfigInverter configInverter : configInverters) {
            int placedCount = placedInverterCounts != null ? 
                placedInverterCounts.getOrDefault(configInverter.getId(), 0) : 0;
            boolean isPlaced = placedCount >= 1;
            InverterItem inverterItem = inverterMap.get(configInverter.getInverterId());
            if (inverterItem != null) {
                ObjectItem item = new ObjectItem(
                    ObjectItem.Type.INVERTER,
                    configInverter,
                    inverterItem,
                    null,
                    null,
                    null,
                    null
                );
                item.isPlaced = isPlaced;
                dialog.objectItems.add(item);
            }
        }
        
        // Add all towers (show all, mark placed ones as disabled)
        for (ConfigTower configTower : configTowers) {
            int placedCount = placedTowerCounts != null ? 
                placedTowerCounts.getOrDefault(configTower.getId(), 0) : 0;
            boolean isPlaced = placedCount >= 1;
            BatteryItem batteryItem = batteryMap.get(configTower.getBatteryModelId());
            if (batteryItem != null) {
                ConfigBms attachedBms = towerBmsMap.get(configTower.getTowerNumber());
                BmsItem bmsItem = attachedBms != null ? bmsMap.get(attachedBms.getBmsId()) : null;
                ObjectItem item = new ObjectItem(
                    ObjectItem.Type.TOWER,
                    null,
                    null,
                    configTower,
                    batteryItem,
                    attachedBms,
                    bmsItem
                );
                item.isPlaced = isPlaced;
                dialog.objectItems.add(item);
            }
        }
        
        // Add all standalone BMS (show all, mark placed ones as disabled)
        for (ConfigBms configBms : configBmsList) {
            if (configBms.getAttachedTowerNumber() == null) {
                int placedCount = placedBmsCounts != null ? 
                    placedBmsCounts.getOrDefault(configBms.getId(), 0) : 0;
                boolean isPlaced = placedCount >= 1;
                BmsItem bmsItem = bmsMap.get(configBms.getBmsId());
                if (bmsItem != null) {
                    ObjectItem item = new ObjectItem(
                        ObjectItem.Type.STANDALONE_BMS,
                        null,
                        null,
                        null,
                        null,
                        configBms,
                        bmsItem
                    );
                    item.isPlaced = isPlaced;
                    dialog.objectItems.add(item);
                }
            }
        }
        
        return dialog;
    }
    
    public void setOnObjectSelectedListener(OnObjectSelectedListener listener) {
        this.listener = listener;
    }
    
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        if (dialog.getWindow() != null) {
            dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        }
        return dialog;
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_select_ar_object, container, false);
        recyclerView = view.findViewById(R.id.recycler_view_objects);
        textEmpty = view.findViewById(R.id.text_empty);
        
        // Setup cancel button
        com.google.android.material.button.MaterialButton btnCancel = view.findViewById(R.id.btn_cancel);
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dismiss());
        }
        
        return view;
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Setup RecyclerView
        adapter = new ObjectSelectionAdapter(objectItems, (item) -> {
            if (listener != null) {
                switch (item.type) {
                    case INVERTER:
                        listener.onInverterSelected(
                            item.configInverter,
                            item.inverterItem
                        );
                        break;
                    case TOWER:
                        listener.onTowerSelected(
                            item.configTower,
                            item.batteryItem,
                            item.configBms,
                            item.bmsItem
                        );
                        break;
                    case STANDALONE_BMS:
                        listener.onStandaloneBmsSelected(
                            item.configBms,
                            item.bmsItem
                        );
                        break;
                }
            }
            dismiss();
        });
        
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);
        
        // Show message if no objects
        if (objectItems.isEmpty()) {
            textEmpty.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            textEmpty.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }
    
    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
    }
    
    /**
     * Data class for object items
     */
    static class ObjectItem {
        enum Type {
            INVERTER, TOWER, STANDALONE_BMS
        }
        
        Type type;
        ConfigInverter configInverter;
        InverterItem inverterItem;
        ConfigTower configTower;
        BatteryItem batteryItem;
        ConfigBms configBms;
        BmsItem bmsItem;
        boolean isPlaced = false; // Track if object is already placed
        
        ObjectItem(Type type, ConfigInverter configInverter, InverterItem inverterItem,
                  ConfigTower configTower, BatteryItem batteryItem,
                  ConfigBms configBms, BmsItem bmsItem) {
            this.type = type;
            this.configInverter = configInverter;
            this.inverterItem = inverterItem;
            this.configTower = configTower;
            this.batteryItem = batteryItem;
            this.configBms = configBms;
            this.bmsItem = bmsItem;
        }
    }
    
    /**
     * Adapter for object selection list
     */
    private static class ObjectSelectionAdapter extends RecyclerView.Adapter<ObjectSelectionAdapter.ViewHolder> {
        
        private final List<ObjectItem> items;
        private final OnItemClickListener clickListener;
        
        public interface OnItemClickListener {
            void onItemClick(ObjectItem item);
        }
        
        public ObjectSelectionAdapter(List<ObjectItem> items, OnItemClickListener clickListener) {
            this.items = items;
            this.clickListener = clickListener;
        }
        
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_ar_object_selection, parent, false);
            return new ViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ObjectItem item = items.get(position);
            holder.bind(item, clickListener);
        }
        
        @Override
        public int getItemCount() {
            return items != null ? items.size() : 0;
        }
        
        static class ViewHolder extends RecyclerView.ViewHolder {
            private final TextView nameText;
            private final TextView detailsText;
            private final TextView typeText;
            
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                nameText = itemView.findViewById(R.id.text_object_name);
                detailsText = itemView.findViewById(R.id.text_object_details);
                typeText = itemView.findViewById(R.id.text_object_type);
            }
            
            public void bind(ObjectItem item, OnItemClickListener clickListener) {
                // Set gray color if already placed
                int textColor = item.isPlaced ? 
                    itemView.getContext().getResources().getColor(android.R.color.darker_gray) :
                    itemView.getContext().getResources().getColor(com.masters.ppa.R.color.text_primary_dark);
                int detailsColor = item.isPlaced ?
                    itemView.getContext().getResources().getColor(android.R.color.darker_gray) :
                    itemView.getContext().getResources().getColor(com.masters.ppa.R.color.text_secondary_dark);
                
                nameText.setTextColor(textColor);
                detailsText.setTextColor(detailsColor);
                typeText.setTextColor(detailsColor);
                
                // Set style for placed items - gray text but no background overlay
                if (item.isPlaced) {
                    itemView.setAlpha(1.0f);
                    // Keep default background, just make text gray
                    itemView.setBackground(itemView.getContext().getDrawable(android.R.drawable.list_selector_background));
                } else {
                    itemView.setAlpha(1.0f);
                    itemView.setBackground(itemView.getContext().getDrawable(android.R.drawable.list_selector_background));
                }
                
                switch (item.type) {
                    case INVERTER:
                        typeText.setText("🔵 Inverter");
                        nameText.setText(item.inverterItem.getName());
                        String role = item.configInverter.getRole();
                        String powerStr = String.format(Locale.getDefault(), "%.1f kW", item.inverterItem.getPowerKw());
                        if (role != null && !role.isEmpty()) {
                            detailsText.setText(String.format(Locale.getDefault(), "%s • %s", powerStr, role));
                        } else {
                            detailsText.setText(powerStr);
                        }
                        break;
                    case TOWER:
                        typeText.setText("🟢 Battery Tower");
                        nameText.setText(item.batteryItem.getName());
                        String capacityStr = String.format(Locale.getDefault(), "%.2f kWh", item.batteryItem.getCapacityKWh());
                        String modulesStr = String.format(Locale.getDefault(), "%d modules", item.configTower.getBatteriesCount());
                        if (item.configBms != null && item.bmsItem != null) {
                            detailsText.setText(String.format(Locale.getDefault(), "%s • %s • BMS: %s", 
                                capacityStr, modulesStr, item.bmsItem.getName()));
                        } else {
                            detailsText.setText(String.format(Locale.getDefault(), "%s • %s", capacityStr, modulesStr));
                        }
                        break;
                    case STANDALONE_BMS:
                        typeText.setText("🔴 BMS");
                        nameText.setText(item.bmsItem.getName());
                        detailsText.setText("Standalone BMS");
                        break;
                }
                
                itemView.setOnClickListener(v -> {
                    if (clickListener != null) {
                        clickListener.onItemClick(item);
                    }
                });
            }
        }
    }
}

