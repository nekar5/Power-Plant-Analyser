package com.masters.ppa.ui.project;

import com.masters.ppa.data.model.BatteryItem;

import java.util.List;

/**
 * Adapter for Battery ExpandableListView
 */
public class BatteryExpandableListAdapter extends ProjectExpandableListAdapter {
    
    public BatteryExpandableListAdapter(List<BatteryItem> items) {
        super(items);
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
        BatteryItem item = items != null ? (BatteryItem) items.get(groupPosition) : null;
        return item != null ? item.getId() : groupPosition;
    }
}

