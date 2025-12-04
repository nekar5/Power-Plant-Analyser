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
import com.masters.ppa.data.model.InverterItem;

/**
 * Dialog for viewing item details
 */
public class ViewItemDetailsDialog extends DialogFragment {
    
    private InverterItem inverterItem;
    private BatteryItem batteryItem;
    private BmsItem bmsItem;
    private String itemType;
    
    public static ViewItemDetailsDialog newInstance(InverterItem item) {
        ViewItemDetailsDialog dialog = new ViewItemDetailsDialog();
        dialog.inverterItem = item;
        dialog.itemType = "Inverter";
        return dialog;
    }
    
    public static ViewItemDetailsDialog newInstance(BatteryItem item) {
        ViewItemDetailsDialog dialog = new ViewItemDetailsDialog();
        dialog.batteryItem = item;
        dialog.itemType = "Battery";
        return dialog;
    }
    
    public static ViewItemDetailsDialog newInstance(BmsItem item) {
        ViewItemDetailsDialog dialog = new ViewItemDetailsDialog();
        dialog.bmsItem = item;
        dialog.itemType = "BMS";
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
        return inflater.inflate(R.layout.dialog_view_item_details, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        TextView textItemType = view.findViewById(R.id.text_item_type);
        TextView textNameValue = view.findViewById(R.id.text_name_value);
        TextView textDimensionsValue = view.findViewById(R.id.text_dimensions_value);
        TextView textAdditionalLabel = view.findViewById(R.id.text_additional_label);
        TextView textAdditionalValue = view.findViewById(R.id.text_additional_value);
        MaterialButton btnClose = view.findViewById(R.id.btn_close);
        
        // Set item type
        textItemType.setText(itemType);
        
        // Fill data based on item type
        if (inverterItem != null) {
            textNameValue.setText(inverterItem.getName());
            textDimensionsValue.setText(String.format("%.2f x %.2f x %.2f mm", 
                    inverterItem.getWidth(), inverterItem.getHeight(), inverterItem.getDepth()));
            textAdditionalLabel.setVisibility(View.VISIBLE);
            textAdditionalValue.setVisibility(View.VISIBLE);
            textAdditionalLabel.setText(getString(R.string.power_kw));
            textAdditionalValue.setText(String.format("%.2f kW", inverterItem.getPowerKw()));
        } else if (batteryItem != null) {
            textNameValue.setText(batteryItem.getName());
            textDimensionsValue.setText(String.format("%.2f x %.2f x %.2f mm", 
                    batteryItem.getWidth(), batteryItem.getHeight(), batteryItem.getDepth()));
            textAdditionalLabel.setVisibility(View.VISIBLE);
            textAdditionalValue.setVisibility(View.VISIBLE);
            textAdditionalLabel.setText(getString(R.string.capacity_kwh));
            textAdditionalValue.setText(String.format("%.2f kWh", batteryItem.getCapacityKWh()));
        } else if (bmsItem != null) {
            textNameValue.setText(bmsItem.getName());
            textDimensionsValue.setText(String.format("%.2f x %.2f x %.2f mm", 
                    bmsItem.getWidth(), bmsItem.getHeight(), bmsItem.getDepth()));
            textAdditionalLabel.setVisibility(View.GONE);
            textAdditionalValue.setVisibility(View.GONE);
        }
        
        btnClose.setOnClickListener(v -> dismiss());
    }
}

