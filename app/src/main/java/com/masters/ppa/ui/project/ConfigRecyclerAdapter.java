package com.masters.ppa.ui.project;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.masters.ppa.R;
import com.masters.ppa.data.model.ProjectConfig;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * RecyclerAdapter for Config list
 */
public class ConfigRecyclerAdapter extends RecyclerView.Adapter<ConfigRecyclerAdapter.ViewHolder> {
    
    private List<ProjectConfig> configs;
    private boolean isDeleteMode;
    private OnItemClickListener itemClickListener;
    private OnDeleteClickListener deleteListener;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
    
    public interface OnItemClickListener {
        void onItemClick(int position);
    }
    
    public interface OnDeleteClickListener {
        void onDeleteClick(int position);
    }
    
    public ConfigRecyclerAdapter(List<ProjectConfig> configs) {
        this.configs = configs;
        this.isDeleteMode = false;
    }
    
    public void setConfigs(List<ProjectConfig> configs) {
        this.configs = configs;
        notifyDataSetChanged();
    }
    
    public void setDeleteMode(boolean deleteMode) {
        this.isDeleteMode = deleteMode;
        notifyDataSetChanged();
    }
    
    public void setItemClickListener(OnItemClickListener listener) {
        this.itemClickListener = listener;
    }
    
    public void setDeleteListener(OnDeleteClickListener listener) {
        this.deleteListener = listener;
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_config_simple, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ProjectConfig config = configs != null ? configs.get(position) : null;
        if (config != null) {
            holder.bind(config, isDeleteMode, deleteListener, itemClickListener, position);
        }
    }
    
    @Override
    public int getItemCount() {
        return configs != null ? configs.size() : 0;
    }
    
    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView nameText;
        private final TextView dateText;
        private final ImageButton deleteButton;
        
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.text_config_name);
            dateText = itemView.findViewById(R.id.text_config_date);
            deleteButton = itemView.findViewById(R.id.btn_delete_config);
        }
        
        public void bind(ProjectConfig config, 
                        boolean isDeleteMode, 
                        OnDeleteClickListener deleteListener,
                        OnItemClickListener itemClickListener,
                        int position) {
            nameText.setText(config.getName());
            if (config.getCreatedAt() > 0) {
                dateText.setText(DATE_FORMAT.format(new Date(config.getCreatedAt())));
            } else {
                dateText.setText("");
            }
            
            // Show/hide delete button based on delete mode
            deleteButton.setVisibility(isDeleteMode ? View.VISIBLE : View.GONE);
            
            // Set delete button click listener
            deleteButton.setOnClickListener(v -> {
                if (deleteListener != null) {
                    int adapterPosition = getAdapterPosition();
                    if (adapterPosition != RecyclerView.NO_POSITION) {
                        deleteListener.onDeleteClick(adapterPosition);
                    }
                }
            });
            
            // Set item click listener (only if not in delete mode)
            itemView.setOnClickListener(v -> {
                if (!isDeleteMode && itemClickListener != null) {
                    int adapterPosition = getAdapterPosition();
                    if (adapterPosition != RecyclerView.NO_POSITION) {
                        itemClickListener.onItemClick(adapterPosition);
                    }
                }
            });
        }
    }
}

