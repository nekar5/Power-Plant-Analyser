package com.masters.ppa.ml;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.flex.FlexDelegate;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Real TensorFlow Lite model loader
 * Supports multiple model types
 */
public class ModelLoader {

    private static final String TAG = "ModelLoader";
    
    /**
     * Model type enum for different model configurations
     */
    public enum ModelType {
        PREDICTION("prediction"),
        BATTERY("battery");
        
        private final String value;
        
        ModelType(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
    }

    private static ModelLoader instance;
    private final Context context;
    private Interpreter tflite;
    private FlexDelegate flexDelegate; // For Select TF Ops support (battery model)
    private double[] mean;
    private double[] scale;
    private List<String> features;
    private ModelType currentModelType;
    private int maxTimesteps; // For battery model

    // Post-processing constants (matching Python defaults)
    private float capKw = 10f;

    private ModelLoader(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized ModelLoader getInstance(Context context) {
        if (instance == null) {
            instance = new ModelLoader(context);
        }
        return instance;
    }

    /**
     * Load TensorFlow Lite model and preprocessing parameters
     * @param modelType Type of model to load (defaults to PREDICTION)
     * @return true if loaded successfully
     */
    public boolean loadModel(ModelType modelType) {
        if (modelType == null) {
            modelType = ModelType.PREDICTION;
        }
        
        try {
            Log.d(TAG, "Loading TensorFlow Lite model: " + modelType.getValue());
            
            // Close previous model if exists
            if (tflite != null) {
                tflite.close();
                tflite = null;
            }
            
            // Close previous Flex delegate if exists
            if (flexDelegate != null) {
                try {
                    flexDelegate.close();
                } catch (Exception e) {
                    Log.w(TAG, "Error closing Flex delegate", e);
                }
                flexDelegate = null;
            }
            
            String modelPath;
            String scalerPath;
            
            switch (modelType) {
                case PREDICTION:
                    modelPath = "models/prediction/model.tflite";
                    scalerPath = "models/prediction/scaler.json";
                    break;
                case BATTERY:
                    modelPath = "models/battery/model.tflite";
                    scalerPath = "models/battery/scaler.json";
                    break;
                default:
                    Log.e(TAG, "Unknown model type: " + modelType);
                    return false;
            }
            
            Log.d(TAG, "Loading model file from assets: " + modelPath);
            MappedByteBuffer modelBuffer = loadModelFile(modelPath);
            if (modelBuffer == null || modelBuffer.capacity() == 0) {
                Log.e(TAG, "Failed to load model file or file is empty: " + modelPath);
                return false;
            }
            
            Log.d(TAG, "Model file loaded, size: " + modelBuffer.capacity() + " bytes");
            
            // Create interpreter options
            Interpreter.Options options = new Interpreter.Options();
            
            // Battery model requires Flex delegate for Select TF Ops
            if (modelType == ModelType.BATTERY) {
                try {
                    // Use reflection to create FlexDelegate to avoid import issues during compilation
                    Class<?> flexDelegateClass = Class.forName("org.tensorflow.lite.flex.FlexDelegate");
                    flexDelegate = (FlexDelegate) flexDelegateClass.getDeclaredConstructor().newInstance();
                    options.addDelegate((org.tensorflow.lite.Delegate) flexDelegate);
                    Log.d(TAG, "Flex delegate added for battery model (Select TF Ops support)");
                } catch (ClassNotFoundException e) {
                    Log.e(TAG, "FlexDelegate class not found. Make sure tensorflow-lite-select-tf-ops is included.", e);
                    throw new Exception("FlexDelegate not found. Please ensure tensorflow-lite-select-tf-ops dependency is included.", e);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to create Flex delegate", e);
                    throw new Exception("Failed to create Flex delegate for battery model: " + e.getMessage(), e);
                }
            }
            
            try {
                tflite = new Interpreter(modelBuffer, options);
                Log.d(TAG, "TensorFlow Lite interpreter created");
            } catch (IllegalStateException e) {
                String errorMsg = e.getMessage();
                if (errorMsg != null && errorMsg.contains("Select TensorFlow op")) {
                    throw new Exception("Battery model requires Select TF Ops support. " +
                        "Please ensure tensorflow-lite-select-tf-ops dependency is included.", e);
                }
                throw e;
            } catch (IllegalArgumentException e) {
                String errorMsg = e.getMessage();
                if (errorMsg != null && errorMsg.contains("FULLY_CONNECTED") && errorMsg.contains("version")) {
                    throw new Exception("Model version incompatibility: The model may use operations " +
                        "not fully supported by TensorFlow Lite.", e);
                }
                throw e;
            }

            JSONObject scalerJson = loadJSONFromAssets(scalerPath);
            JSONArray meanArray = scalerJson.getJSONArray("mean");
            JSONArray scaleArray = scalerJson.getJSONArray("scale");
            JSONArray featuresArray = scalerJson.getJSONArray("features");

            mean = new double[meanArray.length()];
            scale = new double[scaleArray.length()];
            features = new ArrayList<>();

            for (int i = 0; i < meanArray.length(); i++) {
                mean[i] = meanArray.getDouble(i);
                scale[i] = scaleArray.getDouble(i);
                features.add(featuresArray.getString(i));
            }
            
            // Get max_timesteps for battery model
            if (modelType == ModelType.BATTERY && scalerJson.has("max_timesteps")) {
                maxTimesteps = scalerJson.getInt("max_timesteps");
            } else {
                maxTimesteps = 0;
            }
            
            // Validate that all arrays have the same length
            if (mean.length != scale.length || mean.length != features.size()) {
                Log.e(TAG, "❌ Mismatch in scaler dimensions: mean=" + mean.length + 
                      ", scale=" + scale.length + ", features=" + features.size());
                return false;
            }

            currentModelType = modelType;
            Log.d(TAG, "✅ Model and scaler loaded successfully (" + features.size() + " features, max_timesteps=" + maxTimesteps + ")");
            return true;

        } catch (java.io.FileNotFoundException e) {
            Log.e(TAG, "❌ Model or scaler file not found", e);
            return false;
        } catch (Exception e) {
            Log.e(TAG, "❌ Error loading model", e);
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Load model with default type (PREDICTION)
     */
    public boolean loadModel() {
        return loadModel(ModelType.PREDICTION);
    }

    /** Helper: load model file from assets */
    private MappedByteBuffer loadModelFile(String path) throws IOException {
        try (InputStream inputStream = context.getAssets().open(path)) {
            // Copy to temp file because asset streams don't support FileChannel
            FileInputStream fis = (FileInputStream) inputStream;
            FileChannel fileChannel = fis.getChannel();
            long startOffset = 0;
            long declaredLength = fileChannel.size();
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        } catch (ClassCastException e) {
            // Fallback for non-FileInputStream assets
            java.io.File file = new java.io.File(context.getFilesDir(), "temp_model.tflite");
            try (InputStream in = context.getAssets().open(path);
                 java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
                byte[] buffer = new byte[1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                out.flush();
            }
            try (FileInputStream fis = new FileInputStream(file);
                 FileChannel channel = fis.getChannel()) {
                return channel.map(FileChannel.MapMode.READ_ONLY, 0, file.length());
            }
        }
    }

    /** Helper: load JSON from assets */
    private JSONObject loadJSONFromAssets(String path) throws IOException {
        try (InputStream is = context.getAssets().open(path);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) builder.append(line);
            return new JSONObject(builder.toString());
        } catch (Exception e) {
            throw new IOException("Error reading JSON: " + e.getMessage(), e);
        }
    }

    /** Get number of input features */
    public int getInputSize() {
        return (features != null) ? features.size() : 0;
    }

    /** Get feature list (unmodifiable) */
    public List<String> getFeatures() {
        if (features == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(features);
    }
    
    /**
     * Set configuration for post-processing
     * @param capKw Power capacity in kW
     */
    public void setConfig(float capKw) {
        this.capKw = capKw;
    }
    
    /**
     * Get mean values for features (used to fill missing features)
     * @return Array of mean values
     */
    public double[] getMeanValues() {
        if (mean == null) {
            return new double[0];
        }
        return mean.clone();
    }
    
    /**
     * Get raw model prediction without post-processing (no cap applied)
     * Used for forecast where we need to apply fade, performance_ratio, cap, and calibration in correct order
     * @param rawInput Input features in EXACT order as features list from scaler.json
     * @return Predicted power in WATTS (W), without cap applied
     */
    public float getRawModelPrediction(float[] rawInput) {
        if (tflite == null) {
            Log.e(TAG, "Model not loaded");
            return -1;
        }

        int n = Math.min(rawInput.length, mean.length);
        
        // Validate input length
        if (rawInput.length != mean.length) {
            Log.w(TAG, "Input length mismatch: expected " + mean.length + 
                  ", got " + rawInput.length);
        }

        // Step 1: Normalize input exactly like Python: (X - mean) / scale
        float[][] input = new float[1][n];
        for (int i = 0; i < n; i++) {
            if (scale[i] == 0) {
                input[0][i] = 0f; // Avoid division by zero
            } else {
                input[0][i] = (float) ((rawInput[i] - mean[i]) / scale[i]);
            }
        }

        // Step 2: Run TFLite inference
        float[][] output = new float[1][1];
        tflite.run(input, output);
        float predKw = output[0][0]; // Model output is in kW (same as Python y)

        // Step 3: Apply only np.maximum(0, pred) - NO CAP (cap will be applied later in ForecastProcessor)
        if (predKw < 0f) predKw = 0f;

        // Convert to watts (W) to match Python predicted_power_w
        float postW = predKw * 1000f;

        return postW;
    }
    
    /**
     * Predict battery usage classes, stress, and utilization from sequences
     * For battery model only
     * 
     * @param sequences Input sequences: [n_days, max_timesteps, n_features + 1]
     *                  Last channel is is_valid mask (0/1)
     *                  Features should already be normalized except for is_valid channel
     * @return Result containing class predictions, stress, and utilization arrays
     */
    public BatteryPredictionResult predictBattery(float[][][] sequences) {
        if (tflite == null || currentModelType != ModelType.BATTERY) {
            Log.e(TAG, "Battery model not loaded");
            return null;
        }
        
        int nDays = sequences.length;
        if (nDays == 0) {
            Log.e(TAG, "Empty sequences");
            return null;
        }
        
        int maxSteps = sequences[0].length;
        int nFeatures = features.size();
        
        // Validate input shape
        if (sequences[0][0].length != nFeatures + 1) {
            Log.e(TAG, "Input shape mismatch: expected " + (nFeatures + 1) + 
                  " channels (features + is_valid), got " + sequences[0][0].length);
            return null;
        }
        
        Log.d(TAG, String.format("Running battery inference: %d days, %d timesteps, %d features", 
            nDays, maxSteps, nFeatures));
        
        // Prepare output arrays - model outputs one prediction per sequence (day)
        float[][] classProbs = new float[nDays][3];
        float[][] stress2d = new float[nDays][1];
        float[][] util2d = new float[nDays][1];
        
        // Input shape: [n_days, max_timesteps, n_features + 1]
        // Output shapes: [n_days, 3] for classes, [n_days, 1] for stress/util
        Object[] inputs = {sequences};
        Map<Integer, Object> outputs = new java.util.HashMap<>();
        outputs.put(0, classProbs);
        outputs.put(1, stress2d);
        outputs.put(2, util2d);
        
        try {
            // Run model inference for all days at once
            tflite.runForMultipleInputsOutputs(inputs, outputs);
            
            // Extract stress and utilization from [n_days, 1] to [n_days]
            float[] stress = new float[nDays];
            float[] utilization = new float[nDays];
            for (int i = 0; i < nDays; i++) {
                stress[i] = Math.max(0f, stress2d[i][0]); // Apply np.maximum(0, pred)
                utilization[i] = Math.max(0f, util2d[i][0]); // Apply np.maximum(0, pred)
            }
            
            Log.d(TAG, "Battery inference completed successfully");
            return new BatteryPredictionResult(classProbs, stress, utilization);
        } catch (Exception e) {
            Log.e(TAG, "Error during battery model inference", e);
            return null;
        }
    }
    
    /**
     * Get max timesteps for battery model
     */
    public int getMaxTimesteps() {
        return maxTimesteps;
    }
    
    /**
     * Result class for battery predictions
     */
    public static class BatteryPredictionResult {
        public final float[][] classProbabilities; // [n_days, 3]
        public final float[] stress; // [n_days]
        public final float[] utilization; // [n_days]
        
        public BatteryPredictionResult(float[][] classProbabilities, float[] stress, float[] utilization) {
            this.classProbabilities = classProbabilities;
            this.stress = stress;
            this.utilization = utilization;
        }
        
        /**
         * Get predicted class ID for each day (argmax)
         */
        public int[] getPredictedClasses() {
            int[] classes = new int[classProbabilities.length];
            for (int i = 0; i < classProbabilities.length; i++) {
                int maxIdx = 0;
                float maxProb = classProbabilities[i][0];
                for (int j = 1; j < 3; j++) {
                    if (classProbabilities[i][j] > maxProb) {
                        maxProb = classProbabilities[i][j];
                        maxIdx = j;
                    }
                }
                classes[i] = maxIdx;
            }
            return classes;
        }
    }
}
