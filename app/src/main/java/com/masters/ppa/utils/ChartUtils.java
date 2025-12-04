package com.masters.ppa.utils;

import android.content.Context;
import android.graphics.Color;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.masters.ppa.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Utility class for chart configuration and styling
 */
public class ChartUtils {

    /**
     * Configure a line chart with dark theme styling
     * @param chart LineChart to configure
     * @param context Application context for resources
     * @param showGridLines Whether to show grid lines
     */
    public static void configureLineChart(LineChart chart, Context context, boolean showGridLines) {
        // Basic chart styling
        chart.setDrawGridBackground(false);
        chart.getDescription().setEnabled(false);
        chart.setDrawBorders(false);
        chart.setAutoScaleMinMaxEnabled(true);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setPinchZoom(true);
        
        // Dark theme styling
        chart.setBackgroundColor(Color.TRANSPARENT);
        
        // Legend styling
        Legend legend = chart.getLegend();
        legend.setTextColor(Color.WHITE);
        legend.setForm(Legend.LegendForm.LINE);
        legend.setWordWrapEnabled(true);
        
        // X-axis styling
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(Color.WHITE);
        xAxis.setDrawGridLines(showGridLines);
        if (showGridLines) {
            xAxis.setGridColor(Color.GRAY);
            xAxis.setGridLineWidth(0.5f);
        }
        xAxis.setAxisLineColor(Color.WHITE);
        xAxis.setGranularity(1f);
        
        // Y-axis styling
        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setTextColor(Color.WHITE);
        leftAxis.setDrawGridLines(showGridLines);
        if (showGridLines) {
            leftAxis.setGridColor(Color.GRAY);
            leftAxis.setGridLineWidth(0.5f);
        }
        leftAxis.setAxisLineColor(Color.WHITE);
        
        YAxis rightAxis = chart.getAxisRight();
        rightAxis.setEnabled(false);
    }
    
    /**
     * Configure a bar chart with dark theme styling
     * @param chart BarChart to configure
     * @param context Application context for resources
     * @param showGridLines Whether to show grid lines
     */
    public static void configureBarChart(BarChart chart, Context context, boolean showGridLines) {
        // Basic chart styling
        chart.setDrawGridBackground(false);
        chart.getDescription().setEnabled(false);
        chart.setDrawBorders(false);
        chart.setAutoScaleMinMaxEnabled(true);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setPinchZoom(true);
        
        // Dark theme styling
        chart.setBackgroundColor(Color.TRANSPARENT);
        
        // Legend styling
        Legend legend = chart.getLegend();
        legend.setTextColor(Color.WHITE);
        legend.setForm(Legend.LegendForm.SQUARE);
        legend.setWordWrapEnabled(true);
        
        // X-axis styling
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(Color.WHITE);
        xAxis.setDrawGridLines(showGridLines);
        if (showGridLines) {
            xAxis.setGridColor(Color.GRAY);
            xAxis.setGridLineWidth(0.5f);
        }
        xAxis.setAxisLineColor(Color.WHITE);
        xAxis.setGranularity(1f);
        
        // Y-axis styling
        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setTextColor(Color.WHITE);
        leftAxis.setDrawGridLines(showGridLines);
        if (showGridLines) {
            leftAxis.setGridColor(Color.GRAY);
            leftAxis.setGridLineWidth(0.5f);
        }
        leftAxis.setAxisLineColor(Color.WHITE);
        
        YAxis rightAxis = chart.getAxisRight();
        rightAxis.setEnabled(false);
    }
    
    /**
     * Create a line data set with styling
     * @param entries Data entries
     * @param label Label for the data set
     * @param color Line color
     * @param fillColor Fill color (if null, no fill)
     * @return Styled LineDataSet
     */
    public static LineDataSet createLineDataSet(List<Entry> entries, String label, int color, Integer fillColor) {
        LineDataSet dataSet = new LineDataSet(entries, label);
        dataSet.setColor(color);
        dataSet.setLineWidth(2f);
        dataSet.setCircleColor(color);
        dataSet.setCircleRadius(3f);
        dataSet.setDrawCircleHole(false);
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(9f);
        dataSet.setDrawValues(false);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        
        if (fillColor != null) {
            dataSet.setDrawFilled(true);
            dataSet.setFillColor(fillColor);
            dataSet.setFillAlpha(50);
        } else {
            dataSet.setDrawFilled(false);
        }
        
        return dataSet;
    }
    
    /**
     * Create a bar data set with styling
     * @param entries Data entries
     * @param label Label for the data set
     * @param color Bar color
     * @return Styled BarDataSet
     */
    public static BarDataSet createBarDataSet(List<BarEntry> entries, String label, int color) {
        BarDataSet dataSet = new BarDataSet(entries, label);
        dataSet.setColor(color);
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(9f);
        dataSet.setDrawValues(true);
        
        return dataSet;
    }
    
    /**
     * Set date formatter for X-axis
     * @param chart LineChart to configure
     * @param dates List of dates
     * @param format Date format pattern
     */
    public static void setDateFormatter(LineChart chart, final List<Date> dates, final String format) {
        final SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.getDefault());
        
        chart.getXAxis().setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int index = (int) value;
                if (index >= 0 && index < dates.size()) {
                    return sdf.format(dates.get(index));
                }
                return "";
            }
        });
    }
    
    /**
     * Set string labels for X-axis
     * @param chart LineChart or BarChart to configure
     * @param labels List of labels
     */
    public static void setXAxisLabels(LineChart chart, final List<String> labels) {
        chart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
    }
    
    /**
     * Set string labels for X-axis of a bar chart
     * @param chart BarChart to configure
     * @param labels List of labels
     */
    public static void setXAxisLabels(BarChart chart, final List<String> labels) {
        chart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
    }
    
    /**
     * Create temperature line chart entries from weather data
     * @param weatherDataList List of weather data
     * @return List of entries for chart
     */
    public static List<Entry> createTemperatureEntries(List<com.masters.ppa.data.model.WeatherData> weatherDataList) {
        List<Entry> entries = new ArrayList<>();
        
        for (int i = 0; i < weatherDataList.size(); i++) {
            com.masters.ppa.data.model.WeatherData data = weatherDataList.get(i);
            entries.add(new Entry(i, (float) data.getTemperatureAvg()));
        }
        
        return entries;
    }
    
    /**
     * Create date labels from weather data
     * @param weatherDataList List of weather data
     * @return List of date labels
     */
    public static List<String> createDateLabels(List<com.masters.ppa.data.model.WeatherData> weatherDataList) {
        List<String> labels = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("d MMM", Locale.getDefault());
        
        for (com.masters.ppa.data.model.WeatherData data : weatherDataList) {
            labels.add(sdf.format(data.getDate()));
        }
        
        return labels;
    }
    
    /**
     * Create generation chart entries from generation data
     * @param generationDataList List of generation data
     * @param useActual Whether to use actual or predicted data
     * @return List of entries for chart
     */
    public static List<Entry> createGenerationEntries(List<com.masters.ppa.data.model.GenerationData> generationDataList, boolean useActual) {
        List<Entry> entries = new ArrayList<>();
        
        for (int i = 0; i < generationDataList.size(); i++) {
            com.masters.ppa.data.model.GenerationData data = generationDataList.get(i);
            float value = (float) (useActual ? data.getGenerationKwh() : data.getPredictedGenerationKwh());
            entries.add(new Entry(i, value));
        }
        
        return entries;
    }
    
    /**
     * Create bar chart entries for generation comparison
     * @param generationDataList List of generation data
     * @return List of bar entries for chart
     */
    public static List<BarEntry> createGenerationBarEntries(List<com.masters.ppa.data.model.GenerationData> generationDataList) {
        List<BarEntry> entries = new ArrayList<>();
        
        for (int i = 0; i < generationDataList.size(); i++) {
            com.masters.ppa.data.model.GenerationData data = generationDataList.get(i);
            entries.add(new BarEntry(i, new float[]{(float) data.getGenerationKwh(), (float) data.getPredictedGenerationKwh()}));
        }
        
        return entries;
    }
}
