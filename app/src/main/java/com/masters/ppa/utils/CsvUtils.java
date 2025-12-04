package com.masters.ppa.utils;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import com.masters.ppa.data.model.GenerationData;
import com.masters.ppa.data.model.StationData;
import com.masters.ppa.data.model.WeatherData;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvException;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Utility class for CSV operations
 */
public class CsvUtils {

    private static final String TAG = "CsvUtils";
    
    // Date format for CSV files
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private static final SimpleDateFormat DATE_TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    private static final SimpleDateFormat ISO_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault());
    
    // CSV file names
    public static final String WEATHER_CSV = "weather_next_7days.csv";
    public static final String WEATHER_HISTORICAL_CSV = "weather_last_3months.csv";
    public static final String TEST_GENERATION_CSV = "solarman_weather_range_test.csv";
    public static final String GENERATION_CSV = "generation_data.csv";
    public static final String TEST_STATION_CSV = "test_station.csv";
    public static final String STATION_CSV = "station_data.csv";
    
    /**
     * Read weather data from a CSV file and aggregate by day
     * @param filePath Path to CSV file
     * @return List of WeatherData objects
     */
    public static List<WeatherData> readWeatherData(String filePath) {
        List<WeatherData> weatherDataList = new ArrayList<>();
        
        File file = new File(filePath);
        if (!file.exists()) {
            Log.e(TAG, "Weather CSV file not found: " + filePath);
            return weatherDataList;
        }
        
        try (CSVReader reader = new CSVReader(new FileReader(file))) {
            List<String[]> rows = reader.readAll();
            
            // Skip header row
            boolean isFirst = true;
            
            // Map to store aggregated data by date
            Map<String, List<String[]>> dataByDate = new TreeMap<>();
            
            // Group rows by date
            for (String[] row : rows) {
                if (isFirst) {
                    isFirst = false;
                    continue;
                }
                
                try {
                    // Extract date part from datetime
                    String timestamp = row[0];
                    String dateStr = timestamp.split("T")[0]; // Get only the date part
                    
                    // Add row to the corresponding date group
                    if (!dataByDate.containsKey(dateStr)) {
                        dataByDate.put(dateStr, new ArrayList<>());
                    }
                    dataByDate.get(dateStr).add(row);
                } catch (Exception e) {
                    Log.e(TAG, "Error grouping weather data by date", e);
                }
            }
            
            Log.d(TAG, "Found " + dataByDate.size() + " unique dates in weather data");
            
            // Process each date group
            for (String dateStr : dataByDate.keySet()) {
                List<String[]> dayRows = dataByDate.get(dateStr);
                
                if (dayRows != null && !dayRows.isEmpty()) {
                    try {
                        // Create a new WeatherData object for this date
                        WeatherData weatherData = new WeatherData();
                        weatherData.setDate(DATE_FORMAT.parse(dateStr));
                        weatherData.setLastUpdated(new Date());
                        
                        // Calculate min, max, and average values
                        double minTemp = Double.MAX_VALUE;
                        double maxTemp = Double.MIN_VALUE;
                        double sumTemp = 0;
                        double sumCloud = 0;
                        double sumRadiation = 0;
                        double sumWind = 0;
                        
                        int validTempCount = 0;
                        int validCloudCount = 0;
                        int validRadiationCount = 0;
                        int validWindCount = 0;
                        
                        for (String[] row : dayRows) {
                            try {
                                // Temperature processing (column 1)
                                if (row.length > 1 && !row[1].isEmpty()) {
                                    double temp = Double.parseDouble(row[1]);
                                    if (!Double.isNaN(temp)) {
                                        minTemp = Math.min(minTemp, temp);
                                        maxTemp = Math.max(maxTemp, temp);
                                        sumTemp += temp;
                                        validTempCount++;
                                    }
                                }
                                
                                // Cloud cover processing (column 2)
                                if (row.length > 2 && !row[2].isEmpty()) {
                                    double cloudCover = Double.parseDouble(row[2]);
                                    if (!Double.isNaN(cloudCover)) {
                                        // Keep original scale (0-100%)
                                        sumCloud += cloudCover;
                                        validCloudCount++;
                                    }
                                }
                                
                                // Shortwave radiation processing (column 3)
                                if (row.length > 3 && !row[3].isEmpty()) {
                                    double radiation = Double.parseDouble(row[3]);
                                    if (!Double.isNaN(radiation)) {
                                        sumRadiation += radiation;
                                        validRadiationCount++;
                                    }
                                }
                                
                                // Wind speed processing (column 6)
                                if (row.length > 6 && !row[6].isEmpty()) {
                                    double wind = Double.parseDouble(row[6]);
                                    if (!Double.isNaN(wind)) {
                                        sumWind += wind;
                                        validWindCount++;
                                    }
                                }
                            } catch (NumberFormatException e) {
                                Log.e(TAG, "Error parsing weather data value: " + e.getMessage());
                            }
                        }
                        
                        // Set aggregated values with validation and rounding to 1 decimal place
                        if (validTempCount > 0) {
                            // Round to 1 decimal place
                            double avgTemp = Math.round((sumTemp / validTempCount) * 10.0) / 10.0;
                            double minTempRounded = Math.round(minTemp * 10.0) / 10.0;
                            double maxTempRounded = Math.round(maxTemp * 10.0) / 10.0;
                            
                            weatherData.setTemperatureMin(minTempRounded);
                            weatherData.setTemperatureMax(maxTempRounded);
                            weatherData.setTemperatureAvg(avgTemp);
                        } else {
                            weatherData.setTemperatureMin(0);
                            weatherData.setTemperatureMax(0);
                            weatherData.setTemperatureAvg(0);
                        }
                        
                        // Round cloud cover, radiation and wind speed to 1 decimal place
                        double avgCloud = validCloudCount > 0 ? Math.round((sumCloud / validCloudCount) * 10.0) / 10.0 : 0;
                        double dailyRadiation = validRadiationCount > 0 ? Math.round((sumRadiation / 1000.0) * 10.0) / 10.0 : 0;
                        double avgWind = validWindCount > 0 ? Math.round((sumWind / validWindCount) * 10.0) / 10.0 : 0;
                        
                        weatherData.setCloudCover(avgCloud);
                        weatherData.setShortwaveRadiation(dailyRadiation);
                        weatherData.setWindSpeed(avgWind);
                        
                        weatherDataList.add(weatherData);
                    } catch (ParseException e) {
                        Log.e(TAG, "Error parsing date: " + dateStr, e);
                    }
                }
            }
            
            // Sort by date (already sorted by TreeMap, but just to be sure)
            java.util.Collections.sort(weatherDataList, (a, b) -> a.getDate().compareTo(b.getDate()));
            
            Log.d(TAG, "Final weather data count: " + weatherDataList.size() + " days");
            
        } catch (IOException | CsvException e) {
            Log.e(TAG, "Error reading weather CSV file", e);
        }
        
        return weatherDataList;
    }
    
    /**
     * Read generation data from a CSV file
     *
     * @param application
     * @param filePath    Path to CSV file
     * @return List of GenerationData objects
     */
    public static List<GenerationData> readGenerationData(Application application, String filePath) {
        List<GenerationData> generationDataList = new ArrayList<>();

        File file = new File(filePath);
        if (!file.exists()) {
            Log.e(TAG, "Generation CSV file not found: " + filePath);
            return generationDataList;
        }

        try (CSVReader reader = new CSVReader(new FileReader(file))) {
            List<String[]> rows = reader.readAll();
            if (rows.isEmpty()) {
                Log.e(TAG, "Generation CSV is empty: " + filePath);
                return generationDataList;
            }

            String[] header = rows.get(0);

            generationDataList = parseSolarmanGeneration(rows);

        } catch (IOException | CsvException e) {
            Log.e(TAG, "Error reading generation CSV file", e);
        }

        return generationDataList;
    }

    private static List<GenerationData> parseSolarmanGeneration(List<String[]> rows) {
        List<GenerationData> list = new ArrayList<>();

        if (rows.isEmpty()) return list;

        String[] header = rows.get(0);
        int idxTime = -1;
        int idxPv = -1;

        // Find collectTime index (or first column with "time")
        for (int i = 0; i < header.length; i++) {
            String col = header[i] != null ? header[i].trim().toLowerCase(Locale.ROOT) : "";
            if (idxTime == -1 && col.contains("time")) {
                idxTime = i;
            }
            if (col.equals("pvtp")) {
                idxPv = i;
            }
        }

        if (idxTime == -1 || idxPv == -1) {
            Log.e(TAG, "Solarman CSV missing required columns: timeIndex=" + idxTime + ", pvIndex=" + idxPv);
            return list;
        }

        Log.d(TAG, "Solarman indices: time=" + idxTime + ", PVTP=" + idxPv);

        // Aggregate by date -> energy (kWh)
        Map<String, Double> energyByDate = new TreeMap<>();

        // Create regex similar to Python extract_power_kw
        java.util.regex.Pattern numPattern = java.util.regex.Pattern.compile(
                "([-+]?\\d*\\.?\\d+(?:[eE][-+]?\\d+)?)"
        );

        boolean isFirst = true;
        for (String[] row : rows) {
            if (isFirst) {
                isFirst = false;
                continue;
            }

            if (row.length <= Math.max(idxTime, idxPv)) continue;

            try {
                String timeStr = row[idxTime] != null ? row[idxTime].trim() : "";
                if (timeStr.isEmpty()) continue;

                Date ts;

                if (timeStr.matches("\\d+")) {
                    // Unix time (seconds or milliseconds)
                    long t = Long.parseLong(timeStr);
                    long millis;
                    if (t < 2_000_000_000L) { // until ~2033
                        millis = t * 1000L;
                    } else {
                        millis = t;
                    }
                    ts = new Date(millis);
                } else {
                    // Format "2025-08-02 00:03:04"
                    ts = DATE_TIME_FORMAT.parse(timeStr);
                }

                // PVTP (AC power in watts)
                String pvStr = row[idxPv] != null ? row[idxPv].trim().replace(",", ".") : "";
                if (pvStr.isEmpty()) continue;

                java.util.regex.Matcher m = numPattern.matcher(pvStr);
                if (!m.find()) continue;

                double powerW = Double.parseDouble(m.group(1));
                if (Double.isNaN(powerW) || Double.isInfinite(powerW)) continue;
                if (powerW < 0) powerW = 0.0;

                double powerKw = powerW / 1000.0;

                // Group by date
                String dateStr = DATE_FORMAT.format(ts);
                // 5-minute interval → kWh
                double energyKwh = powerKw * (5.0 / 60.0);

                Double prev = energyByDate.get(dateStr);
                energyByDate.put(dateStr, (prev == null ? 0.0 : prev) + energyKwh);

            } catch (Exception e) {
                Log.e(TAG, "Error parsing Solarman row for generation", e);
            }
        }

        // Convert map to GenerationData list
        for (Map.Entry<String, Double> entry : energyByDate.entrySet()) {
            try {
                GenerationData gd = new GenerationData();
                gd.setDate(DATE_FORMAT.parse(entry.getKey()));
                gd.setGenerationKwh(entry.getValue());
                gd.setPredictedGenerationKwh(0.0);  // Only actual data for now
                gd.setActual(true);
                gd.setLastUpdated(new Date());
                list.add(gd);
            } catch (Exception e) {
                Log.e(TAG, "Error creating GenerationData from Solarman aggregated map", e);
            }
        }

        Log.d(TAG, "Parsed " + list.size() + " daily generation records from Solarman raw");
        return list;
    }



    public static List<GenerationData> readGenerationDataFromRaw(Context context, int rawResId) {
        List<GenerationData> generationDataList = new ArrayList<>();
        try (InputStream is = context.getResources().openRawResource(rawResId);
             InputStreamReader isr = new InputStreamReader(is);
             CSVReader reader = new CSVReader(isr)) {

            List<String[]> rows = reader.readAll();
            boolean isFirst = true;

            for (String[] row : rows) {
                if (isFirst) {
                    isFirst = false;
                    continue;
                }
                try {
                    GenerationData gd = new GenerationData();
                    gd.setDate(DATE_FORMAT.parse(row[0]));
                    gd.setGenerationKwh(Double.parseDouble(row[1]));
                    gd.setPredictedGenerationKwh(Double.parseDouble(row[2]));
                    gd.setActual(Boolean.parseBoolean(row[3]));
                    gd.setLastUpdated(new Date());
                    generationDataList.add(gd);
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing generation data row", e);
                }
            }

            Log.d(TAG, "Read " + generationDataList.size() + " generation records from raw resource");

        } catch (Exception e) {
            Log.e(TAG, "Error reading generation CSV from raw resource", e);
        }
        return generationDataList;
    }


    /**
     * Read station data from a CSV file
     *
     * @param application
     * @param filePath    Path to CSV file
     * @return List of StationData objects
     */
    public static List<StationData> readStationData(Application application, String filePath) {
        List<StationData> stationDataList = new ArrayList<>();
        
        File file = new File(filePath);
        if (!file.exists()) {
            Log.e(TAG, "Station CSV file not found: " + filePath);
            return stationDataList;
        }
        
        try (CSVReader reader = new CSVReader(new FileReader(file))) {
            List<String[]> rows = reader.readAll();
            
            // Skip header row
            boolean isFirst = true;
            for (String[] row : rows) {
                if (isFirst) {
                    isFirst = false;
                    continue;
                }
                
                try {
                    StationData stationData = new StationData();
                    
                    // Parse CSV columns
                    stationData.setTimestamp(DATE_TIME_FORMAT.parse(row[0]));
                    stationData.setPowerKw(Double.parseDouble(row[1]));
                    stationData.setEnergyTodayKwh(Double.parseDouble(row[2]));
                    stationData.setEnergyTotalKwh(Double.parseDouble(row[3]));
                    stationData.setBatteryStateOfChargePercent(Double.parseDouble(row[4]));
                    stationData.setBatteryPowerKw(Double.parseDouble(row[5]));
                    stationData.setGridPowerKw(Double.parseDouble(row[6]));
                    stationData.setLoadPowerKw(Double.parseDouble(row[7]));
                    stationData.setLastUpdated(new Date());
                    
                    stationDataList.add(stationData);
                } catch (ParseException | NumberFormatException e) {
                    Log.e(TAG, "Error parsing station data row", e);
                }
            }
        } catch (IOException | CsvException e) {
            Log.e(TAG, "Error reading station CSV file", e);
        }
        
        return stationDataList;
    }
    
    /**
     * Write weather data to a CSV file
     * @param filePath Path to CSV file
     * @param weatherDataList List of WeatherData objects
     * @return true if successful
     */
    public static boolean writeWeatherData(String filePath, List<WeatherData> weatherDataList) {
        File file = new File(filePath);
        
        // Ensure parent directory exists
        FileUtils.ensureDirectoryExists(file.getParentFile());
        
        try (CSVWriter writer = new CSVWriter(new FileWriter(file))) {
            // Write header
            String[] header = {"Date", "Temperature", "CloudCover", "ShortwaveRadiation", "WindSpeed"};
            writer.writeNext(header);
            
            // Write data rows
            for (WeatherData weatherData : weatherDataList) {
                String[] row = {
                        DATE_FORMAT.format(weatherData.getDate()),
                        Double.isNaN(weatherData.getTemperature()) || Double.isInfinite(weatherData.getTemperature()) ? "" : String.format(Locale.US, "%.1f", weatherData.getTemperature()),
                        Double.isNaN(weatherData.getCloudCover()) || Double.isInfinite(weatherData.getCloudCover()) ? "" : String.format(Locale.US, "%.1f", weatherData.getCloudCover()),
                        Double.isNaN(weatherData.getShortwaveRadiation()) || Double.isInfinite(weatherData.getShortwaveRadiation()) ? "" : String.format(Locale.US, "%.1f", weatherData.getShortwaveRadiation()),
                        Double.isNaN(weatherData.getWindSpeed()) || Double.isInfinite(weatherData.getWindSpeed()) ? "" : String.format(Locale.US, "%.1f", weatherData.getWindSpeed())
                };
                writer.writeNext(row);
            }
            
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error writing weather CSV file", e);
            return false;
        }
    }
    
    /**
     * Write generation data to a CSV file
     * @param filePath Path to CSV file
     * @param generationDataList List of GenerationData objects
     * @return true if successful
     */
    public static boolean writeGenerationData(String filePath, List<GenerationData> generationDataList) {
        File file = new File(filePath);
        
        // Ensure parent directory exists
        FileUtils.ensureDirectoryExists(file.getParentFile());
        
        try (CSVWriter writer = new CSVWriter(new FileWriter(file))) {
            // Write header
            String[] header = {"Date", "GenerationKwh", "PredictedGenerationKwh", "IsActual"};
            writer.writeNext(header);
            
            // Write data rows
            for (GenerationData generationData : generationDataList) {
                String[] row = {
                        DATE_FORMAT.format(generationData.getDate()),
                        Double.isNaN(generationData.getGenerationKwh()) || Double.isInfinite(generationData.getGenerationKwh()) ? "" : String.format(Locale.US, "%.1f", generationData.getGenerationKwh()),
                        Double.isNaN(generationData.getPredictedGenerationKwh()) || Double.isInfinite(generationData.getPredictedGenerationKwh()) ? "" : String.format(Locale.US, "%.1f", generationData.getPredictedGenerationKwh()),
                        String.valueOf(generationData.isActual())
                };
                writer.writeNext(row);
            }
            
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error writing generation CSV file", e);
            return false;
        }
    }
    
    /**
     * Write station data to a CSV file
     * @param filePath Path to CSV file
     * @param stationDataList List of StationData objects
     * @return true if successful
     */
    public static boolean writeStationData(String filePath, List<StationData> stationDataList) {
        File file = new File(filePath);
        
        // Ensure parent directory exists
        FileUtils.ensureDirectoryExists(file.getParentFile());
        
        try (CSVWriter writer = new CSVWriter(new FileWriter(file))) {
            // Write header
            String[] header = {"Timestamp", "PowerKw", "EnergyTodayKwh", "EnergyTotalKwh", 
                    "BatteryStateOfChargePercent", "BatteryPowerKw", "GridPowerKw", "LoadPowerKw"};
            writer.writeNext(header);
            
            // Write data rows
            for (StationData stationData : stationDataList) {
                String[] row = {
                        DATE_TIME_FORMAT.format(stationData.getTimestamp()),
                        Double.isNaN(stationData.getPowerKw()) || Double.isInfinite(stationData.getPowerKw()) ? "" : String.format(Locale.US, "%.1f", stationData.getPowerKw()),
                        Double.isNaN(stationData.getEnergyTodayKwh()) || Double.isInfinite(stationData.getEnergyTodayKwh()) ? "" : String.format(Locale.US, "%.1f", stationData.getEnergyTodayKwh()),
                        Double.isNaN(stationData.getEnergyTotalKwh()) || Double.isInfinite(stationData.getEnergyTotalKwh()) ? "" : String.format(Locale.US, "%.1f", stationData.getEnergyTotalKwh()),
                        Double.isNaN(stationData.getBatteryStateOfChargePercent()) || Double.isInfinite(stationData.getBatteryStateOfChargePercent()) ? "" : String.format(Locale.US, "%.1f", stationData.getBatteryStateOfChargePercent()),
                        Double.isNaN(stationData.getBatteryPowerKw()) || Double.isInfinite(stationData.getBatteryPowerKw()) ? "" : String.format(Locale.US, "%.1f", stationData.getBatteryPowerKw()),
                        Double.isNaN(stationData.getGridPowerKw()) || Double.isInfinite(stationData.getGridPowerKw()) ? "" : String.format(Locale.US, "%.1f", stationData.getGridPowerKw()),
                        Double.isNaN(stationData.getLoadPowerKw()) || Double.isInfinite(stationData.getLoadPowerKw()) ? "" : String.format(Locale.US, "%.1f", stationData.getLoadPowerKw())
                };
                writer.writeNext(row);
            }
            
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error writing station CSV file", e);
            return false;
        }
    }
    
    /**
     * Read date range from CSV file (first and last date from time column)
     * This method reads the file line by line to avoid OutOfMemoryError with large files
     * @param filePath Path to CSV file
     * @return Array with two strings: [firstDate, lastDate] in format "dd.MM.yyyy", or null if file doesn't exist or is empty
     */
    public static String[] readDateRangeFromCsv(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            Log.e(TAG, "CSV file not found: " + filePath);
            return null;
        }
        
        try (CSVReader reader = new CSVReader(new FileReader(file))) {
            // Read header (first line)
            String[] header = reader.readNext();
            if (header == null || header.length == 0) {
                Log.e(TAG, "CSV file is empty: " + filePath);
                return null;
            }
            
            // Find time column index
            int timeIndex = -1;
            for (int i = 0; i < header.length; i++) {
                String col = header[i] != null ? header[i].trim().toLowerCase(Locale.ROOT) : "";
                if (col.contains("time") || col.equals("collecttime")) {
                    timeIndex = i;
                    break;
                }
            }
            
            if (timeIndex == -1) {
                Log.e(TAG, "Time column not found in CSV: " + filePath);
                return null;
            }
            
            // Find first and last valid date by reading line by line
            String firstDate = null;
            String lastDate = null;
            SimpleDateFormat outputFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
            
            // Read rows one by one instead of loading all into memory
            String[] row;
            while ((row = reader.readNext()) != null) {
                if (row.length <= timeIndex) continue;
                
                String timeStr = row[timeIndex] != null ? row[timeIndex].trim() : "";
                if (timeStr.isEmpty()) continue;
                
                try {
                    Date date = null;
                    
                    // Try ISO format (2025-01-15T14:30:00)
                    try {
                        date = ISO_DATE_FORMAT.parse(timeStr);
                    } catch (ParseException e) {
                        // Try date-time format (2025-01-15 14:30:00)
                        try {
                            date = DATE_TIME_FORMAT.parse(timeStr);
                        } catch (ParseException e2) {
                            // Try date format (2025-01-15)
                            try {
                                date = DATE_FORMAT.parse(timeStr);
                            } catch (ParseException e3) {
                                // Try Unix timestamp
                                if (timeStr.matches("\\d+")) {
                                    long t = Long.parseLong(timeStr);
                                    long millis = (t < 2_000_000_000L) ? t * 1000L : t;
                                    date = new Date(millis);
                                }
                            }
                        }
                    }
                    
                    if (date != null) {
                        String formattedDate = outputFormat.format(date);
                        if (firstDate == null) {
                            firstDate = formattedDate;
                        }
                        lastDate = formattedDate; // Update last date on each valid row
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Error parsing date from CSV row: " + timeStr, e);
                }
            }
            
            if (firstDate == null || lastDate == null) {
                Log.e(TAG, "No valid dates found in CSV: " + filePath);
                return null;
            }
            
            return new String[]{firstDate, lastDate};
            
        } catch (IOException | CsvException e) {
            Log.e(TAG, "Error reading CSV file for date range: " + filePath, e);
            return null;
        }
    }
}