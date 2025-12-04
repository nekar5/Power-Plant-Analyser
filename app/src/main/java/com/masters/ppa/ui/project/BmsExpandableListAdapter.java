package com.masters.ppa.ui.project;

import com.masters.ppa.data.model.BmsItem;

import java.util.List;

/**
 * Adapter for BMS ExpandableListView
 */
public class BmsExpandableListAdapter extends ProjectExpandableListAdapter {
    
    public BmsExpandableListAdapter(List<BmsItem> items) {
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
        BmsItem item = items != null ? (BmsItem) items.get(groupPosition) : null;
        return item != null ? item.getId() : groupPosition;
    }
}

