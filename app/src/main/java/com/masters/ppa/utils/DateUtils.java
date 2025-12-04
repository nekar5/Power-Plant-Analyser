package com.masters.ppa.utils;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;

/**
 * Utility class for date formatting and date operations
 */
public class DateUtils {
    
    // Common date format patterns
    public static final String PATTERN_DATE = "yyyy-MM-dd";
    public static final String PATTERN_DATE_TIME = "yyyy-MM-dd HH:mm:ss";
    public static final String PATTERN_ISO_DATE_TIME = "yyyy-MM-dd'T'HH:mm";
    public static final String PATTERN_ISO_DATE_TIME_FULL = "yyyy-MM-dd'T'HH:mm:ss";
    public static final String PATTERN_DISPLAY_DATE = "dd.MM.yyyy";
    public static final String PATTERN_DISPLAY_DATE_TIME = "dd.MM.yyyy HH:mm";
    public static final String PATTERN_DISPLAY_SHORT_DATE = "d MMM";
    public static final String PATTERN_TIMESTAMP = "yyyyMMdd_HHmmss";
    
    // Thread-safe formatters for common patterns
    private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT = 
        ThreadLocal.withInitial(() -> new SimpleDateFormat(PATTERN_DATE, Locale.getDefault()));
    private static final ThreadLocal<SimpleDateFormat> DATE_TIME_FORMAT = 
        ThreadLocal.withInitial(() -> new SimpleDateFormat(PATTERN_DATE_TIME, Locale.getDefault()));
    private static final ThreadLocal<SimpleDateFormat> ISO_DATE_TIME_FORMAT = 
        ThreadLocal.withInitial(() -> new SimpleDateFormat(PATTERN_ISO_DATE_TIME, Locale.getDefault()));
    private static final ThreadLocal<SimpleDateFormat> DISPLAY_DATE_FORMAT = 
        ThreadLocal.withInitial(() -> new SimpleDateFormat(PATTERN_DISPLAY_DATE, Locale.getDefault()));
    private static final ThreadLocal<SimpleDateFormat> DISPLAY_DATE_TIME_FORMAT = 
        ThreadLocal.withInitial(() -> new SimpleDateFormat(PATTERN_DISPLAY_DATE_TIME, Locale.getDefault()));
    private static final ThreadLocal<SimpleDateFormat> DISPLAY_SHORT_DATE_FORMAT = 
        ThreadLocal.withInitial(() -> new SimpleDateFormat(PATTERN_DISPLAY_SHORT_DATE, Locale.getDefault()));
    
    /**
     * Format date range for display (LocalDate)
     * @param startDate Start date
     * @param endDate End date
     * @return Formatted date range string (e.g., "01.01 – 07.01")
     */
    public static String formatDateRange(LocalDate startDate, LocalDate endDate) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM", Locale.getDefault());
        return startDate.format(formatter) + " – " + endDate.format(formatter);
    }
    
    /**
     * Format Date to string using pattern
     * @param date Date to format
     * @param pattern Date pattern (e.g., "yyyy-MM-dd")
     * @return Formatted date string
     */
    public static String formatDate(Date date, String pattern) {
        if (date == null) {
            return "";
        }
        SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.getDefault());
        return sdf.format(date);
    }
    
    /**
     * Format Date to "yyyy-MM-dd" format
     * @param date Date to format
     * @return Formatted date string
     */
    public static String formatDate(Date date) {
        if (date == null) {
            return "";
        }
        return DATE_FORMAT.get().format(date);
    }
    
    /**
     * Format Date to "yyyy-MM-dd HH:mm:ss" format
     * @param date Date to format
     * @return Formatted date string
     */
    public static String formatDateTime(Date date) {
        if (date == null) {
            return "";
        }
        return DATE_TIME_FORMAT.get().format(date);
    }
    
    /**
     * Format Date to "dd.MM.yyyy" format
     * @param date Date to format
     * @return Formatted date string
     */
    public static String formatDisplayDate(Date date) {
        if (date == null) {
            return "";
        }
        return DISPLAY_DATE_FORMAT.get().format(date);
    }
    
    /**
     * Format Date to "dd.MM.yyyy HH:mm" format
     * @param date Date to format
     * @return Formatted date string
     */
    public static String formatDisplayDateTime(Date date) {
        if (date == null) {
            return "";
        }
        return DISPLAY_DATE_TIME_FORMAT.get().format(date);
    }
    
    /**
     * Format Date to "d MMM" format (e.g., "1 Jan")
     * @param date Date to format
     * @return Formatted date string
     */
    public static String formatShortDate(Date date) {
        if (date == null) {
            return "";
        }
        return DISPLAY_SHORT_DATE_FORMAT.get().format(date);
    }
    
    /**
     * Parse date string using pattern
     * @param dateString Date string to parse
     * @param pattern Date pattern
     * @return Parsed Date or null if parsing fails
     */
    public static Date parseDate(String dateString, String pattern) {
        if (dateString == null || dateString.isEmpty()) {
            return null;
        }
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.getDefault());
            return sdf.parse(dateString);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Try to parse date string using multiple common formats
     * @param dateString Date string to parse
     * @return Parsed Date or null if parsing fails
     */
    public static Date parseDateFlexible(String dateString) {
        if (dateString == null || dateString.isEmpty()) {
            return null;
        }
        
        // Try common formats
        String[] patterns = {
            PATTERN_ISO_DATE_TIME_FULL,
            PATTERN_ISO_DATE_TIME,
            PATTERN_DATE_TIME,
            PATTERN_DATE
        };
        
        for (String pattern : patterns) {
            Date date = parseDate(dateString, pattern);
            if (date != null) {
                return date;
            }
        }
        
        // Try Unix timestamp
        if (dateString.matches("\\d+")) {
            try {
                long t = Long.parseLong(dateString);
                long millis = (t < 2_000_000_000L) ? t * 1000L : t;
                return new Date(millis);
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        
        return null;
    }
}

