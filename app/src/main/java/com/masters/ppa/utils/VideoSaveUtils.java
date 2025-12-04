package com.masters.ppa.utils;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;

/**
 * Utility class for saving videos to the device gallery
 */
public class VideoSaveUtils {
    
    private static final String TAG = "VideoSaveUtils";
    
    /**
     * Save video file to gallery
     * @param context The context
     * @param videoFile The video file to save
     * @return true if successful, false otherwise
     */
    public static boolean saveVideoToGallery(Context context, File videoFile) {
        if (context == null || videoFile == null || !videoFile.exists()) {
            Log.e(TAG, "Invalid parameters or file does not exist");
            return false;
        }
        
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Video.Media.DISPLAY_NAME, videoFile.getName());
            values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            values.put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000);
            values.put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis());
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/PowerPlantAnalyserProjects");
                values.put(MediaStore.Video.Media.IS_PENDING, 1);
            } else {
                values.put(MediaStore.Video.Media.DATA, videoFile.getAbsolutePath());
            }
            
            Uri uri = context.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
            
            if (uri != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Copy file content to the URI
                    try (java.io.InputStream in = new java.io.FileInputStream(videoFile);
                         java.io.OutputStream out = context.getContentResolver().openOutputStream(uri)) {
                        
                        if (out != null) {
                            byte[] buffer = new byte[8192];
                            int bytesRead;
                            while ((bytesRead = in.read(buffer)) != -1) {
                                out.write(buffer, 0, bytesRead);
                            }
                        }
                    }
                    
                    // Mark as not pending
                    values.clear();
                    values.put(MediaStore.Video.Media.IS_PENDING, 0);
                    context.getContentResolver().update(uri, values, null, null);
                }
                
                Log.d(TAG, "Video saved to gallery: " + uri);
                return true;
            } else {
                Log.e(TAG, "Failed to insert video into MediaStore");
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving video to gallery", e);
            return false;
        }
    }
}

