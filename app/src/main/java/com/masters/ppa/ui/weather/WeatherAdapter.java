package com.masters.ppa.ui.weather;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.masters.ppa.data.model.WeatherData;
import com.masters.ppa.databinding.ItemWeatherDayBinding;
import com.masters.ppa.utils.DateUtils;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Adapter for displaying weather data in a RecyclerView
 */
public class WeatherAdapter extends ListAdapter<WeatherData, WeatherAdapter.WeatherViewHolder> {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("d MMM", Locale.getDefault());
    private static final SimpleDateFormat DATE_FORMAT_FULL = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    
    public WeatherAdapter() {
        super(new WeatherDiffCallback());
    }

    @NonNull
    @Override
    public WeatherViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemWeatherDayBinding binding = ItemWeatherDayBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new WeatherViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull WeatherViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    /**
     * ViewHolder for weather data items
     */
    static class WeatherViewHolder extends RecyclerView.ViewHolder {
        
        private final ItemWeatherDayBinding binding;
        
        public WeatherViewHolder(@NonNull ItemWeatherDayBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
        
        /**
         * Bind weather data to the view
         * @param data WeatherData to display
         */
        public void bind(WeatherData data) {
            binding.textDate.setText(DateUtils.formatShortDate(data.getDate()));
            
            // Set average temperature with proper formatting
            double tempAvg = data.getTemperatureAvg();
            binding.textTempAvg.setText("Avg: " + (Double.isNaN(tempAvg) || Double.isInfinite(tempAvg) ? "" : String.format(Locale.getDefault(), "%.1f", tempAvg)) + " °C");
            
            // Set min-max temperature range
            double tempMin = data.getTemperatureMin();
            double tempMax = data.getTemperatureMax();
            String minStr = Double.isNaN(tempMin) || Double.isInfinite(tempMin) ? "" : String.format(Locale.getDefault(), "%.1f", tempMin);
            String maxStr = Double.isNaN(tempMax) || Double.isInfinite(tempMax) ? "" : String.format(Locale.getDefault(), "%.1f", tempMax);
            binding.textTempRange.setText("min: " + minStr + " °C, max: " + maxStr + " °C");
                    
            // Ensure cloud cover is displayed as percentage (0-100%)
            double cloudCoverPercent = data.getCloudCover();
            if (cloudCoverPercent <= 1.0) {
                // If stored as 0-1 range, convert to percentage
                cloudCoverPercent *= 100;
            }
            binding.textCloudCover.setText((Double.isNaN(cloudCoverPercent) || Double.isInfinite(cloudCoverPercent) ? "" : String.format(Locale.getDefault(), "%.0f", cloudCoverPercent)) + "%");
            
            double radiation = data.getShortwaveRadiation();
            binding.textRadiation.setText((Double.isNaN(radiation) || Double.isInfinite(radiation) ? "" : String.format(Locale.getDefault(), "%.1f", radiation)) + " kW/m² (total)");
            double windSpeed = data.getWindSpeed();
            binding.textWindSpeed.setText((Double.isNaN(windSpeed) || Double.isInfinite(windSpeed) ? "" : String.format(Locale.getDefault(), "%.1f", windSpeed)) + " m/s");
        }
    }
    
    /**
     * DiffUtil callback for efficient RecyclerView updates
     */
    private static class WeatherDiffCallback extends DiffUtil.ItemCallback<WeatherData> {
        
        @Override
        public boolean areItemsTheSame(@NonNull WeatherData oldItem, @NonNull WeatherData newItem) {
            return oldItem.getId() == newItem.getId();
        }
        
        @Override
        public boolean areContentsTheSame(@NonNull WeatherData oldItem, @NonNull WeatherData newItem) {
            return oldItem.getDate().equals(newItem.getDate()) &&
                   oldItem.getTemperatureMin() == newItem.getTemperatureMin() &&
                   oldItem.getTemperatureMax() == newItem.getTemperatureMax() &&
                   oldItem.getTemperatureAvg() == newItem.getTemperatureAvg() &&
                   oldItem.getCloudCover() == newItem.getCloudCover() &&
                   oldItem.getShortwaveRadiation() == newItem.getShortwaveRadiation() &&
                   oldItem.getWindSpeed() == newItem.getWindSpeed();
        }
    }
}
