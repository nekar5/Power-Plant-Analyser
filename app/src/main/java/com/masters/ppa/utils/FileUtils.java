package com.masters.ppa.utils;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Utility class for file operations
 */
public class FileUtils {
    
    private static final String TAG = "FileUtils";
    
    // Directory for CSV files
    public static final String CSV_DIR = "csv";
    
    /**
     * Create required directories for the application
     * @param context Application context
     */
    public static void createRequiredDirectories(Context context) {
        File csvDir = new File(context.getFilesDir(), CSV_DIR);
        if (!csvDir.exists()) {
            boolean created = csvDir.mkdirs();
            if (!created) {
                Log.e(TAG, "Failed to create CSV directory");
            } else {
                Log.d(TAG, "CSV directory created at: " + csvDir.getAbsolutePath());
            }
        }
    }
    
    /**
     * Check if a file exists
     * @param context Application context
     * @param filename Filename to check
     * @param directory Optional directory (null for root)
     * @return true if file exists
     */
    public static boolean fileExists(Context context, String filename, String directory) {
        File file;
        if (directory != null) {
            file = new File(new File(context.getFilesDir(), directory), filename);
        } else {
            file = new File(context.getFilesDir(), filename);
        }
        return file.exists();
    }
    
    /**
     * Get the last modified date of a file
     * @param context Application context
     * @param filename Filename to check
     * @param directory Optional directory (null for root)
     * @return Date object or null if file doesn't exist
     */
    public static Date getLastModifiedDate(Context context, String filename, String directory) {
        File file;
        if (directory != null) {
            file = new File(new File(context.getFilesDir(), directory), filename);
        } else {
            file = new File(context.getFilesDir(), filename);
        }
        
        if (file.exists()) {
            return new Date(file.lastModified());
        }
        return null;
    }
    
    /**
     * Format a date for display
     * @param date Date to format
     * @return Formatted date string
     */
    public static String formatDate(Date date) {
        if (date == null) {
            return "N/A";
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(date);
    }
    
    /**
     * Copy a raw resource to the internal storage
     * @param context Application context
     * @param resourceId Raw resource ID
     * @param filename Target filename
     * @param directory Optional directory (null for root)
     * @return File object pointing to the copied file, or null if failed
     * @throws IOException if resource not found or copy failed
     */
    public static File copyRawResourceToFile(Context context, int resourceId, String filename, String directory) throws IOException {
        InputStream in = null;
        
        try {
            // Try to open the resource with given ID
            try {
                in = context.getResources().openRawResource(resourceId);
                Log.d(TAG, "Opened raw resource with ID: " + resourceId);
            } catch (Resources.NotFoundException e) {
                // If resource not found by ID, try to find it dynamically
                // First, try to extract resource name from ID
                String resourceName = extractResourceName(resourceId, context);
                
                // If cannot extract from ID, try to derive from filename (without extension)
                if (resourceName == null || resourceName.isEmpty()) {
                    String nameWithoutExt = filename;
                    int lastDot = nameWithoutExt.lastIndexOf('.');
                    if (lastDot > 0) {
                        nameWithoutExt = nameWithoutExt.substring(0, lastDot);
                    }
                    resourceName = nameWithoutExt;
                    Log.d(TAG, "Trying to find resource by derived name: " + resourceName);
                }
                
                if (resourceName != null && !resourceName.isEmpty()) {
                    int dynamicResId = context.getResources().getIdentifier(resourceName, "raw", context.getPackageName());
                    if (dynamicResId != 0) {
                        Log.d(TAG, "Found resource dynamically: " + resourceName + " (ID: " + dynamicResId + ")");
                        in = context.getResources().openRawResource(dynamicResId);
                    } else {
                        throw new IOException("Resource not found: " + resourceName + " (tried ID: " + resourceId + ")");
                    }
                } else {
                    throw new IOException("Resource not found with ID: " + resourceId + " and could not determine resource name");
                }
            }
            
            File outFile;
            if (directory != null) {
                File dir = new File(context.getFilesDir(), directory);
                if (!dir.exists()) {
                    boolean created = dir.mkdirs();
                    if (!created) {
                        throw new IOException("Failed to create directory: " + dir.getAbsolutePath());
                    }
                }
                outFile = new File(dir, filename);
            } else {
                outFile = new File(context.getFilesDir(), filename);
            }
            
            Log.d(TAG, "Copying raw resource to internal storage: " + outFile.getAbsolutePath());
            
            OutputStream out = new FileOutputStream(outFile);
            
            byte[] buffer = new byte[1024];
            int read;
            long totalBytes = 0;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                totalBytes += read;
            }
            
            in.close();
            in = null;
            out.flush();
            out.close();
            
            // Verify file was created and has content
            if (!outFile.exists() || outFile.length() == 0) {
                throw new IOException("File was not created or is empty: " + outFile.getAbsolutePath());
            }
            
            Log.d(TAG, "✅ Resource copied successfully: " + outFile.getAbsolutePath() + " (" + totalBytes + " bytes)");
            return outFile;
            
        } catch (Resources.NotFoundException e) {
            String errorMsg = "Raw resource not found with ID: " + resourceId;
            Log.e(TAG, errorMsg, e);
            throw new IOException(errorMsg, e);
        } catch (IOException e) {
            Log.e(TAG, "Failed to copy resource to: " + (directory != null ? directory + "/" : "") + filename, e);
            throw e;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing input stream", e);
                }
            }
        }
    }
    
    /**
     * Extract resource name from resource ID (helper method)
     * @param resourceId Resource ID
     * @param context Application context
     * @return Resource name or null if cannot determine
     */
    private static String extractResourceName(int resourceId, Context context) {
        try {
            // Try to get resource name from the ID
            String resourceName = context.getResources().getResourceEntryName(resourceId);
            if (resourceName != null && !resourceName.isEmpty()) {
                return resourceName;
            }
        } catch (Resources.NotFoundException e) {
            // Resource not found by ID, cannot extract name
        }
        return null;
    }
    
    /**
     * Get the full path to a file
     * @param context Application context
     * @param filename Filename
     * @param directory Optional directory (null for root)
     * @return Full path to file
     */
    public static String getFilePath(Context context, String filename, String directory) {
        if (directory != null) {
            return new File(new File(context.getFilesDir(), directory), filename).getAbsolutePath();
        } else {
            return new File(context.getFilesDir(), filename).getAbsolutePath();
        }
    }
    
    /**
     * Get File object for a file in a directory
     * @param context Application context
     * @param filename Filename
     * @param directory Optional directory (null for root)
     * @return File object
     */
    public static File getFile(Context context, String filename, String directory) {
        if (directory != null) {
            return new File(new File(context.getFilesDir(), directory), filename);
        } else {
            return new File(context.getFilesDir(), filename);
        }
    }
    
    /**
     * Ensure directory exists, create if it doesn't
     * @param directory Directory to ensure exists
     * @return true if directory exists or was created successfully
     */
    public static boolean ensureDirectoryExists(File directory) {
        if (directory == null) {
            return false;
        }
        if (directory.exists()) {
            return directory.isDirectory();
        }
        boolean created = directory.mkdirs();
        if (!created) {
            Log.e(TAG, "Failed to create directory: " + directory.getAbsolutePath());
        } else {
            Log.d(TAG, "Directory created: " + directory.getAbsolutePath());
        }
        return created;
    }
    
    /**
     * Ensure directory exists in app's files directory
     * @param context Application context
     * @param directoryName Directory name (can be nested, e.g., "csv/weather")
     * @return File object for the directory, or null if creation failed
     */
    public static File ensureDirectoryExists(Context context, String directoryName) {
        if (directoryName == null || directoryName.isEmpty()) {
            return null;
        }
        File dir = new File(context.getFilesDir(), directoryName);
        if (ensureDirectoryExists(dir)) {
            return dir;
        }
        return null;
    }
}
