package com.masters.ppa.ui.project;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.TextView;

import com.masters.ppa.R;

import java.util.List;

/**
 * Simple list adapter for project items (not expandable)
 */
public class ProjectListAdapter extends BaseAdapter {
    
    protected List<? extends ProjectExpandableListAdapter.ProjectItem> items;
    protected boolean isDeleteMode;
    protected OnDeleteClickListener deleteListener;
    
    public interface OnDeleteClickListener {
        void onDeleteClick(int position);
    }
    
    public ProjectListAdapter(List<? extends ProjectExpandableListAdapter.ProjectItem> items) {
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
    
    @Override
    public int getCount() {
        return items != null ? items.size() : 0;
    }
    
    @Override
    public Object getItem(int position) {
        return items != null ? items.get(position) : null;
    }
    
    @Override
    public long getItemId(int position) {
        ProjectExpandableListAdapter.ProjectItem item = items != null ? items.get(position) : null;
        return item != null ? item.getId() : position;
    }
    
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_project_simple, parent, false);
        }
        
        TextView nameText = convertView.findViewById(R.id.text_item_name);
        TextView dimensionsText = convertView.findViewById(R.id.text_item_dimensions);
        ImageButton deleteButton = convertView.findViewById(R.id.btn_delete_item);
        
        ProjectExpandableListAdapter.ProjectItem item = items != null ? items.get(position) : null;
        if (item != null) {
            nameText.setText(parent.getContext().getString(R.string.name_label) + " " + item.getName());
            
            String dimensions = String.format("%s %.2f x %.2f x %.2f",
                    parent.getContext().getString(R.string.dimensions_format),
                    item.getWidth(), item.getHeight(), item.getDepth());
            dimensionsText.setText(dimensions);
            
            deleteButton.setVisibility(isDeleteMode ? View.VISIBLE : View.GONE);
            deleteButton.setOnClickListener(v -> {
                if (deleteListener != null) {
                    deleteListener.onDeleteClick(position);
                }
            });
        }
        
        return convertView;
    }
}

