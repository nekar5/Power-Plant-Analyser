package com.masters.ppa.ui.project;

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

import com.google.android.material.button.MaterialButton;
import com.masters.ppa.R;
import com.masters.ppa.data.model.BatteryItem;
import com.masters.ppa.data.model.BmsItem;
import com.masters.ppa.data.model.ConfigBms;
import com.masters.ppa.data.model.ConfigInverter;
import com.masters.ppa.data.model.ConfigTower;
import com.masters.ppa.data.model.InverterItem;
import com.masters.ppa.data.model.ProjectConfig;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Dialog for viewing config details
 */
public class ViewConfigDetailsDialog extends DialogFragment {
    
    private ProjectConfig config;
    private List<ConfigInverter> configInverters;
    private List<ConfigTower> configTowers;
    private List<ConfigBms> configBmsList; // Changed to list for multiple BMS
    private List<InverterItem> allInverters;
    private List<BatteryItem> allBatteries;
    private List<BmsItem> allBms;
    
    public static ViewConfigDetailsDialog newInstance(ProjectConfig config,
                                                     List<ConfigInverter> configInverters,
                                                     List<ConfigTower> configTowers,
                                                     List<ConfigBms> configBmsList,
                                                     List<InverterItem> allInverters,
                                                     List<BatteryItem> allBatteries,
                                                     List<BmsItem> allBms) {
        ViewConfigDetailsDialog dialog = new ViewConfigDetailsDialog();
        dialog.config = config;
        dialog.configInverters = configInverters;
        dialog.configTowers = configTowers;
        dialog.configBmsList = configBmsList;
        dialog.allInverters = allInverters;
        dialog.allBatteries = allBatteries;
        dialog.allBms = allBms;
        return dialog;
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
        return inflater.inflate(R.layout.dialog_view_config_details, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        TextView textConfigName = view.findViewById(R.id.text_config_name);
        TextView textConfigDate = view.findViewById(R.id.text_config_date);
        TextView textInverters = view.findViewById(R.id.text_inverters);
        TextView textBatteries = view.findViewById(R.id.text_batteries);
        TextView textBms = view.findViewById(R.id.text_bms);
        MaterialButton btnClose = view.findViewById(R.id.btn_close);
        
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
        
        // Set config name and date
        textConfigName.setText(config != null ? config.getName() : "");
        if (config != null && config.getCreatedAt() > 0) {
            textConfigDate.setText(dateFormat.format(new Date(config.getCreatedAt())));
        } else {
            textConfigDate.setText("");
        }
        
        // Build inverters info
        StringBuilder invertersInfo = new StringBuilder();
        if (configInverters != null && !configInverters.isEmpty()) {
            for (ConfigInverter ci : configInverters) {
                InverterItem inverter = findInverterById(ci.getInverterId());
                if (inverter != null) {
                    invertersInfo.append("• ").append(ci.getRole())
                            .append(": ").append(inverter.getName())
                            .append(" (").append(inverter.getPowerKw()).append(" kW)");
                    int count = ci.getCount();
                    if (count > 1) {
                        invertersInfo.append(" x").append(count);
                    }
                    invertersInfo.append("\n");
                }
            }
        }
        textInverters.setText(invertersInfo.length() > 0 ? invertersInfo.toString().trim() : getString(R.string.none));
        
        // Build batteries/towers info
        StringBuilder batteriesInfo = new StringBuilder();
        if (configTowers != null && !configTowers.isEmpty()) {
            for (ConfigTower ct : configTowers) {
                BatteryItem battery = findBatteryById(ct.getBatteryModelId());
                if (battery != null) {
                    batteriesInfo.append("• Tower #").append(ct.getTowerNumber())
                            .append(": ").append(battery.getName())
                            .append(" (").append(ct.getBatteriesCount()).append(" batteries)\n");
                }
            }
        }
        textBatteries.setText(batteriesInfo.length() > 0 ? batteriesInfo.toString().trim() : getString(R.string.none));
        
        // Build BMS info
        StringBuilder bmsInfo = new StringBuilder();
        if (configBmsList != null && !configBmsList.isEmpty()) {
            for (ConfigBms configBms : configBmsList) {
                BmsItem bms = findBmsById(configBms.getBmsId());
                if (bms != null) {
                    bmsInfo.append("• ").append(bms.getName());
                    if (configBms.getAttachedTowerNumber() != null) {
                        bmsInfo.append(" (Attached to Tower #").append(configBms.getAttachedTowerNumber()).append(")");
                    } else {
                        bmsInfo.append(" (Standalone)");
                    }
                    bmsInfo.append("\n");
                }
            }
        }
        textBms.setText(bmsInfo.length() > 0 ? bmsInfo.toString().trim() : getString(R.string.none));
        
        btnClose.setOnClickListener(v -> dismiss());
    }
    
    private InverterItem findInverterById(int id) {
        if (allInverters != null) {
            for (InverterItem item : allInverters) {
                if (item.getId() == id) {
                    return item;
                }
            }
        }
        return null;
    }
    
    private BatteryItem findBatteryById(int id) {
        if (allBatteries != null) {
            for (BatteryItem item : allBatteries) {
                if (item.getId() == id) {
                    return item;
                }
            }
        }
        return null;
    }
    
    private BmsItem findBmsById(int id) {
        if (allBms != null) {
            for (BmsItem item : allBms) {
                if (item.getId() == id) {
                    return item;
                }
            }
        }
        return null;
    }
}

