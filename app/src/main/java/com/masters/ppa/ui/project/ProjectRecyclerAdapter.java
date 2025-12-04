package com.masters.ppa.ui.project;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.masters.ppa.R;

import java.util.List;

/**
 * RecyclerAdapter for project items (not expandable)
 */
public class ProjectRecyclerAdapter extends RecyclerView.Adapter<ProjectRecyclerAdapter.ViewHolder> {
    
    private List<? extends ProjectExpandableListAdapter.ProjectItem> items;
    private boolean isDeleteMode;
    private OnDeleteClickListener deleteListener;
    private OnItemClickListener itemClickListener;
    
    public interface OnDeleteClickListener {
        void onDeleteClick(int position);
    }
    
    public interface OnItemClickListener {
        void onItemClick(int position);
    }
    
    public ProjectRecyclerAdapter(List<? extends ProjectExpandableListAdapter.ProjectItem> items) {
        this.items = items;
        this.isDeleteMode = false;
    }
    
    public void setItems(List<? extends ProjectExpandableListAdapter.ProjectItem> items) {
        this.items = items;
        notifyDataSetChanged();
    }
    
    public void setDeleteMode(boolean deleteMode) {
        this.isDeleteMode = deleteMode;
        notifyDataSetChanged();
    }
    
    public void setDeleteListener(OnDeleteClickListener listener) {
        this.deleteListener = listener;
    }
    
    public void setItemClickListener(OnItemClickListener listener) {
        this.itemClickListener = listener;
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_project_simple, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ProjectExpandableListAdapter.ProjectItem item = items != null ? items.get(position) : null;
        if (item != null) {
            holder.bind(item, isDeleteMode, deleteListener, itemClickListener, position);
        }
    }
    
    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }
    
    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView nameText;
        private final TextView dimensionsText;
        private final ImageButton deleteButton;
        
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.text_item_name);
            dimensionsText = itemView.findViewById(R.id.text_item_dimensions);
            deleteButton = itemView.findViewById(R.id.btn_delete_item);
        }
        
        public void bind(ProjectExpandableListAdapter.ProjectItem item, 
                        boolean isDeleteMode, 
                        OnDeleteClickListener deleteListener,
                        OnItemClickListener itemClickListener,
                        int position) {
            String nameLabel = itemView.getContext().getString(R.string.name_label);
            nameText.setText(nameLabel + " " + item.getName());
            
            String dimensionsFormat = itemView.getContext().getString(R.string.dimensions_format);
            String dimensions = String.format("%s %.2f x %.2f x %.2f",
                    dimensionsFormat, item.getWidth(), item.getHeight(), item.getDepth());
            dimensionsText.setText(dimensions);
            
            deleteButton.setVisibility(isDeleteMode ? View.VISIBLE : View.GONE);
            deleteButton.setOnClickListener(v -> {
                if (deleteListener != null && !isDeleteMode) return;
                if (deleteListener != null) {
                    int adapterPosition = getAdapterPosition();
                    if (adapterPosition != RecyclerView.NO_POSITION) {
                        deleteListener.onDeleteClick(adapterPosition);
                    }
                }
            });
            
            // Click listener for item view (only when not in delete mode)
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

