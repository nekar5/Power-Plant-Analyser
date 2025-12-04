package com.masters.ppa.utils;

import android.content.res.Configuration;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.util.Log;
import android.util.Size;

import java.io.File;
import java.io.IOException;

/**
 * Utility class for video recording with MediaRecorder
 */
public class VideoRecorderUtils {
    
    private static final String TAG = "VideoRecorderUtils";
    
    private static final int[] FALLBACK_QUALITY_LEVELS = {
        CamcorderProfile.QUALITY_HIGH,
        CamcorderProfile.QUALITY_480P,
        CamcorderProfile.QUALITY_720P,
        CamcorderProfile.QUALITY_1080P
    };
    
    private MediaRecorder mediaRecorder;
    private Size videoSize;
    private int videoCodec = MediaRecorder.VideoEncoder.H264;
    private int bitRate = 10 * 1000 * 1000; // 10 Mbps
    private int frameRate = 30;
    private boolean recordingVideoFlag = false;
    
    /**
     * Setup MediaRecorder for recording
     * @param videoPath Output file path
     * @throws IOException if setup fails
     */
    public void setUpMediaRecorder(File videoPath) throws IOException {
        if (mediaRecorder == null) {
            mediaRecorder = new MediaRecorder();
        }
        
        if (videoSize == null) {
            throw new IllegalStateException("Video size must be set before setting up MediaRecorder");
        }
        
        Log.d(TAG, "Setting up MediaRecorder with size: " + videoSize.getWidth() + "x" + videoSize.getHeight());
        
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        
        mediaRecorder.setOutputFile(videoPath.getAbsolutePath());
        mediaRecorder.setVideoEncodingBitRate(bitRate);
        mediaRecorder.setVideoFrameRate(frameRate);
        
        // Set video size - this is critical for correct recording
        mediaRecorder.setVideoSize(videoSize.getWidth(), videoSize.getHeight());
        Log.d(TAG, "MediaRecorder video size set to: " + videoSize.getWidth() + "x" + videoSize.getHeight());
        
        mediaRecorder.setVideoEncoder(videoCodec);
        
        mediaRecorder.prepare();
        
        try {
            mediaRecorder.start();
            recordingVideoFlag = true;
            Log.d(TAG, "MediaRecorder started successfully");
        } catch (IllegalStateException e) {
            Log.e(TAG, "Exception starting capture: " + e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Set video size
     * @param width Video width
     * @param height Video height
     */
    public void setVideoSize(int width, int height) {
        videoSize = new Size(width, height);
    }
    
    /**
     * Set video quality based on CamcorderProfile
     * @param quality Quality level (e.g., CamcorderProfile.QUALITY_HIGH)
     * @param orientation Screen orientation
     */
    public void setVideoQuality(int quality, int orientation) {
        CamcorderProfile profile = null;
        if (CamcorderProfile.hasProfile(quality)) {
            profile = CamcorderProfile.get(quality);
        }
        if (profile == null) {
            // Select a quality that is available on this device
            for (int level : FALLBACK_QUALITY_LEVELS) {
                if (CamcorderProfile.hasProfile(level)) {
                    profile = CamcorderProfile.get(level);
                    break;
                }
            }
        }
        if (profile != null) {
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight);
            } else {
                setVideoSize(profile.videoFrameHeight, profile.videoFrameWidth);
            }
            setVideoCodec(profile.videoCodec);
            setBitRate(profile.videoBitRate);
            setFrameRate(profile.videoFrameRate);
        }
    }
    
    /**
     * Set video codec
     * @param videoCodec Codec to use (e.g., MediaRecorder.VideoEncoder.H264)
     */
    public void setVideoCodec(int videoCodec) {
        this.videoCodec = videoCodec;
    }
    
    /**
     * Set bit rate
     * @param bitRate Bit rate in bits per second
     */
    public void setBitRate(int bitRate) {
        this.bitRate = bitRate;
    }
    
    /**
     * Set frame rate
     * @param frameRate Frames per second
     */
    public void setFrameRate(int frameRate) {
        this.frameRate = frameRate;
    }
    
    /**
     * Check if currently recording
     * @return true if recording
     */
    public boolean isRecording() {
        return recordingVideoFlag;
    }
    
    /**
     * Get MediaRecorder instance
     * @return MediaRecorder instance
     */
    public MediaRecorder getMediaRecorder() {
        return mediaRecorder;
    }
    
    /**
     * Stop recording
     */
    public void stopRecording() {
        if (mediaRecorder != null && recordingVideoFlag) {
            try {
                mediaRecorder.stop();
                recordingVideoFlag = false;
            } catch (Exception e) {
                Log.e(TAG, "Error stopping MediaRecorder", e);
            }
        }
    }
    
    /**
     * Reset MediaRecorder
     */
    public void reset() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.reset();
            } catch (Exception e) {
                Log.e(TAG, "Error resetting MediaRecorder", e);
            }
        }
    }
    
    /**
     * Release MediaRecorder resources
     */
    public void release() {
        if (mediaRecorder != null) {
            try {
                if (recordingVideoFlag) {
                    mediaRecorder.stop();
                }
                mediaRecorder.reset();
                mediaRecorder.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing MediaRecorder", e);
            }
            mediaRecorder = null;
            recordingVideoFlag = false;
        }
    }
}

