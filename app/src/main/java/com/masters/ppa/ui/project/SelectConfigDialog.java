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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.masters.ppa.R;
import com.masters.ppa.data.model.ProjectConfig;
import com.masters.ppa.databinding.DialogSelectConfigBinding;

import java.util.List;

/**
 * Dialog for selecting a ProjectConfig for AR design
 */
public class SelectConfigDialog extends DialogFragment {
    
    private DialogSelectConfigBinding binding;
    private List<ProjectConfig> configs;
    private ConfigSelectionAdapter adapter;
    private OnConfigSelectedListener listener;
    
    public interface OnConfigSelectedListener {
        void onConfigSelected(ProjectConfig config);
    }
    
    public static SelectConfigDialog newInstance(List<ProjectConfig> configs) {
        SelectConfigDialog dialog = new SelectConfigDialog();
        dialog.configs = configs;
        return dialog;
    }
    
    public void setOnConfigSelectedListener(OnConfigSelectedListener listener) {
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
        binding = DialogSelectConfigBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Setup RecyclerView
        adapter = new ConfigSelectionAdapter(configs, config -> {
            if (listener != null) {
                listener.onConfigSelected(config);
            }
            dismiss();
        });
        
        binding.recyclerViewConfigs.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerViewConfigs.setAdapter(adapter);
        
        // Cancel button
        binding.btnCancel.setOnClickListener(v -> dismiss());
        
        // Show message if no configs
        if (configs == null || configs.isEmpty()) {
            binding.textEmpty.setVisibility(View.VISIBLE);
            binding.recyclerViewConfigs.setVisibility(View.GONE);
        } else {
            binding.textEmpty.setVisibility(View.GONE);
            binding.recyclerViewConfigs.setVisibility(View.VISIBLE);
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
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
    
    /**
     * Adapter for config selection list
     */
    private static class ConfigSelectionAdapter extends RecyclerView.Adapter<ConfigSelectionAdapter.ViewHolder> {
        
        private final List<ProjectConfig> configs;
        private final OnConfigClickListener clickListener;
        
        public interface OnConfigClickListener {
            void onConfigClick(ProjectConfig config);
        }
        
        public ConfigSelectionAdapter(List<ProjectConfig> configs, OnConfigClickListener clickListener) {
            this.configs = configs;
            this.clickListener = clickListener;
        }
        
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_config_selection, parent, false);
            return new ViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ProjectConfig config = configs.get(position);
            holder.bind(config, clickListener);
        }
        
        @Override
        public int getItemCount() {
            return configs != null ? configs.size() : 0;
        }
        
        static class ViewHolder extends RecyclerView.ViewHolder {
            private final TextView nameText;
            private final TextView dateText;
            
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                nameText = itemView.findViewById(R.id.text_config_name);
                dateText = itemView.findViewById(R.id.text_config_date);
            }
            
            public void bind(ProjectConfig config, OnConfigClickListener clickListener) {
                nameText.setText(config.getName());
                if (config.getCreatedAt() > 0) {
                    java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat(
                            "dd.MM.yyyy HH:mm", java.util.Locale.getDefault());
                    dateText.setText(dateFormat.format(new java.util.Date(config.getCreatedAt())));
                } else {
                    dateText.setText("");
                }
                
                itemView.setOnClickListener(v -> {
                    if (clickListener != null) {
                        clickListener.onConfigClick(config);
                    }
                });
            }
        }
    }
}

