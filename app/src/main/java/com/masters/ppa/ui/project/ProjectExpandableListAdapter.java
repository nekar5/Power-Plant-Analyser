package com.masters.ppa.ui.project;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ImageButton;
import android.widget.TextView;

import com.masters.ppa.R;

import java.util.List;

/**
 * Base adapter for Project ExpandableListView
 */
public abstract class ProjectExpandableListAdapter extends BaseExpandableListAdapter {
    
    protected List<? extends ProjectItem> items;
    protected boolean isDeleteMode;
    protected OnDeleteClickListener deleteListener;
    
    public interface OnDeleteClickListener {
        void onDeleteClick(int position);
    }
    
    public ProjectExpandableListAdapter(List<? extends ProjectItem> items) {
        this.items = items;
        this.isDeleteMode = false;
    }
    
    public void setItems(List<? extends ProjectItem> items) {
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
    public int getGroupCount() {
        return items != null ? items.size() : 0;
    }
    
    @Override
    public int getChildrenCount(int groupPosition) {
        return 1; // Each item has one child with details
    }
    
    @Override
    public Object getGroup(int groupPosition) {
        return items != null ? items.get(groupPosition) : null;
    }
    
    @Override
    public Object getChild(int groupPosition, int childPosition) {
        return items != null ? items.get(groupPosition) : null;
    }
    
    @Override
    public long getGroupId(int groupPosition) {
        ProjectItem item = items != null ? items.get(groupPosition) : null;
        return item != null ? item.getId() : groupPosition;
    }
    
    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return childPosition;
    }
    
    @Override
    public boolean hasStableIds() {
        return true;
    }
    
    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_project_group, parent, false);
        }
        
        TextView nameText = convertView.findViewById(R.id.text_group_name);
        ImageButton deleteButton = convertView.findViewById(R.id.btn_delete_item);
        
        ProjectItem item = items != null ? items.get(groupPosition) : null;
        if (item != null) {
            nameText.setText(parent.getContext().getString(R.string.name_label) + " " + item.getName());
            
            deleteButton.setVisibility(isDeleteMode ? View.VISIBLE : View.GONE);
            deleteButton.setOnClickListener(v -> {
                if (deleteListener != null) {
                    deleteListener.onDeleteClick(groupPosition);
                }
            });
        }
        
        return convertView;
    }
    
    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_project_child, parent, false);
        }
        
        TextView nameText = convertView.findViewById(R.id.text_child_name);
        TextView dimensionsText = convertView.findViewById(R.id.text_child_dimensions);
        
        ProjectItem item = items != null ? items.get(groupPosition) : null;
        if (item != null) {
            nameText.setText(parent.getContext().getString(R.string.name_label) + " " + item.getName());
            
            String dimensions = String.format("%s %.2f x %.2f x %.2f",
                    parent.getContext().getString(R.string.dimensions_format),
                    item.getWidth(), item.getHeight(), item.getDepth());
            dimensionsText.setText(dimensions);
        }
        
        return convertView;
    }
    
    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return false;
    }
    
    /**
     * Interface for project items
     */
    public interface ProjectItem {
        int getId();
        String getName();
        double getWidth();
        double getHeight();
        double getDepth();
    }
}

