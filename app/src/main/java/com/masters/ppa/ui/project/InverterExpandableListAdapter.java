package com.masters.ppa.ui.project;

import com.masters.ppa.data.model.InverterItem;

import java.util.List;

/**
 * Adapter for Inverter ExpandableListView
 */
public class InverterExpandableListAdapter extends ProjectExpandableListAdapter {
    
    public InverterExpandableListAdapter(List<InverterItem> items) {
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
        InverterItem item = items != null ? (InverterItem) items.get(groupPosition) : null;
        return item != null ? item.getId() : groupPosition;
    }
}


