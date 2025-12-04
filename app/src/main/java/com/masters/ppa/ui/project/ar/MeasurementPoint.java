package com.masters.ppa.ui.project.ar;

import com.google.ar.core.Anchor;

/**
 * Data class for measurement point information
 */
public class MeasurementPoint {
    private String label;
    private Anchor anchor;
    private Double length;

    public MeasurementPoint(String label, Anchor anchor, Double length) {
        this.label = label;
        this.anchor = anchor;
        this.length = length;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Anchor getAnchor() {
        return anchor;
    }

    public void setAnchor(Anchor anchor) {
        this.anchor = anchor;
    }

    public Double getLength() {
        return length;
    }

    public void setLength(Double length) {
        this.length = length;
    }
}

