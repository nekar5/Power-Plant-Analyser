package com.masters.ppa.data.api;

import android.content.Context;
import android.util.Log;

import com.masters.ppa.data.model.SolarmanApiConfig;
import com.masters.ppa.data.repository.SolarmanApiConfigRepository;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Service for interacting with Solarman API
 */
public class InverterApiService {
    
    private static final String TAG = "InverterApiService";
    private static final String API_BASE = "https://globalapi.solarmanpv.com";
    
    private final Context context;
    private final ExecutorService executor;
    private final SolarmanApiConfigRepository configRepository;
    
    private String cachedAccessToken;
    private long tokenExpiryTime = 0;

    public InverterApiService(Context context) {
        this.context = context.getApplicationContext();
        this.executor = Executors.newSingleThreadExecutor();
        // SolarmanApiConfigRepository requires Application, so we cast
        if (context instanceof android.app.Application) {
            this.configRepository = new SolarmanApiConfigRepository((android.app.Application) context);
        } else {
            this.configRepository = new SolarmanApiConfigRepository((android.app.Application) this.context);
        }
    }

    /**
     * Get access token from Solarman API
     */
    public Future<String> getAccessTokenAsync() {
        return executor.submit(new Callable<String>() {
            @Override
            public String call() throws Exception {
                return getAccessToken();
            }
        });
    }

    /**
     * Get access token (synchronous, called on background thread)
     */
    private String getAccessToken() throws Exception {
        // Check if we have a valid cached token
        if (cachedAccessToken != null && System.currentTimeMillis() < tokenExpiryTime) {
            Log.d(TAG, "✅ Using cached access token (expires in " + 
                  ((tokenExpiryTime - System.currentTimeMillis()) / 1000 / 60) + " minutes)");
            return cachedAccessToken;
        }

        SolarmanApiConfig config = configRepository.getSolarmanApiConfigSync();
        if (config == null) {
            throw new Exception("Solarman API configuration not found. Please configure in Settings.");
        }

        // Validate required fields
        if (config.getAppId() == null || config.getAppId().isEmpty()) {
            throw new Exception("App ID is not configured");
        }
        if (config.getAppSecret() == null || config.getAppSecret().isEmpty()) {
            throw new Exception("App Secret is not configured");
        }
        if (config.getEmail() == null || config.getEmail().isEmpty()) {
            throw new Exception("Email is not configured");
        }
        if (config.getPassword() == null || config.getPassword().isEmpty()) {
            throw new Exception("Password is not configured");
        }

        String url = API_BASE + "/account/v1.0/token?appId=" + config.getAppId() + "&language=en";
        
        // Hash password with SHA-256 (Solarman API requires hashed password)
        String hashedPassword = hashPassword(config.getPassword());
        
        JSONObject payload = new JSONObject();
        payload.put("appSecret", config.getAppSecret());
        payload.put("email", config.getEmail());
        payload.put("password", hashedPassword);

        Log.d(TAG, "🔐 Requesting access token from Solarman API...");
        Log.d(TAG, "URL: " + url);
        Log.d(TAG, "Payload: {appSecret: ***, email: " + config.getEmail() + ", password: (hashed)}");

        HttpURLConnection connection = null;
        try {
            URL requestUrl = new URL(url);
            connection = (HttpURLConnection) requestUrl.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);

            // Send request
            String payloadString = payload.toString();
            // Log payload without showing hashed password (for security)
            JSONObject logPayload = new JSONObject();
            logPayload.put("appSecret", "***");
            logPayload.put("email", config.getEmail());
            logPayload.put("password", "(hashed - SHA-256)");
            Log.d(TAG, "Sending payload: " + logPayload.toString());
            
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = payloadString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
                os.flush();
            }

            // Read response
            // NOTE: getResponseCode() must be called before reading the response
            int responseCode = connection.getResponseCode();
            Log.d(TAG, "Response code: " + responseCode);
            
            String responseBody = readResponse(connection, responseCode);
            Log.d(TAG, "Response body: " + (responseBody.length() > 200 ? 
                  responseBody.substring(0, 200) + "..." : responseBody));

            // Try to parse JSON response regardless of status code
            JSONObject response;
            try {
                response = new JSONObject(responseBody);
            } catch (JSONException e) {
                Log.e(TAG, "❌ Failed to parse response as JSON", e);
                throw new Exception("HTTP Error " + responseCode + ": Invalid JSON response: " + responseBody);
            }
            
            // Check for error messages in response first
            if (response.has("msg") && !response.isNull("msg")) {
                String errorMsg = response.optString("msg", "");
                if (!errorMsg.isEmpty()) {
                    Log.e(TAG, "❌ API Error message: " + errorMsg);
                    // Include response code in error if not 200
                    if (responseCode != 200) {
                        throw new Exception("HTTP Error " + responseCode + ": " + errorMsg);
                    } else if (!response.optBoolean("success", false)) {
                        throw new Exception("Authentication failed: " + errorMsg);
                    }
                }
            }
            
            if (responseCode != 200) {
                Log.e(TAG, "❌ HTTP Error " + responseCode + ": " + responseBody);
                String errorMsg = response.optString("msg", "Unknown error");
                throw new Exception("HTTP Error " + responseCode + ": " + errorMsg);
            }
            
            if (!response.optBoolean("success", false)) {
                Log.e(TAG, "❌ Authentication failed. Response: " + response.toString());
                String errorMsg = response.optString("msg", "Authentication failed");
                throw new Exception("Authentication failed: " + errorMsg);
            }

            if (!response.has("access_token") || response.isNull("access_token")) {
                Log.e(TAG, "❌ No access_token in response: " + response.toString());
                throw new Exception("No access_token in response: " + response.toString());
            }

            String accessToken = response.getString("access_token");
            if (accessToken == null || accessToken.isEmpty()) {
                throw new Exception("Access token is empty");
            }
            
            cachedAccessToken = accessToken;
            // Token typically expires in 24 hours, cache for 23 hours
            tokenExpiryTime = System.currentTimeMillis() + (23 * 60 * 60 * 1000);
            
            Log.d(TAG, "✅ Access token obtained successfully (length: " + accessToken.length() + " chars)");
            Log.d(TAG, "Token expires at: " + new java.util.Date(tokenExpiryTime).toString());
            return accessToken;

        } catch (JSONException e) {
            Log.e(TAG, "❌ JSON parsing error", e);
            throw new Exception("Failed to parse authentication response: " + e.getMessage(), e);
        } catch (java.io.IOException e) {
            Log.e(TAG, "❌ Network error during authentication", e);
            throw new Exception("Network error: " + e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Get current inverter data
     */
    public Future<JSONObject> getCurrentDataAsync(String deviceSn) {
        return executor.submit(new Callable<JSONObject>() {
            @Override
            public JSONObject call() throws Exception {
                return getCurrentData(deviceSn);
            }
        });
    }

    /**
     * Get current inverter data (synchronous, called on background thread)
     */
    private JSONObject getCurrentData(String deviceSn) throws Exception {
        SolarmanApiConfig config = configRepository.getSolarmanApiConfigSync();
        if (config == null) {
            throw new Exception("Solarman API configuration not found");
        }

        if (deviceSn == null || deviceSn.isEmpty()) {
            throw new Exception("Device SN is required");
        }

        // Get access token (will use cache if available)
        String accessToken = getAccessToken();
        
        String url = API_BASE + "/device/v1.0/currentData?appId=" + config.getAppId() + "&language=en";
        
        JSONObject payload = new JSONObject();
        payload.put("deviceSn", deviceSn);

        Log.d(TAG, "📡 Requesting inverter data for device: " + deviceSn);
        Log.d(TAG, "URL: " + url);

        HttpURLConnection connection = null;
        try {
            URL requestUrl = new URL(url);
            connection = (HttpURLConnection) requestUrl.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Authorization", "bearer " + accessToken);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);

            // Send request
            String payloadString = payload.toString();
            Log.d(TAG, "Sending payload: " + payloadString);
            
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = payloadString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
                os.flush();
            }

            // Read response
            // NOTE: getResponseCode() must be called before reading the response
            int responseCode = connection.getResponseCode();
            Log.d(TAG, "Response code: " + responseCode);
            
            String responseBody = readResponse(connection, responseCode);
            
            // Log response summary (don't log full body if too large)
            if (responseBody.length() > 500) {
                Log.d(TAG, "Response body (first 500 chars): " + responseBody.substring(0, 500) + "...");
            } else {
                Log.d(TAG, "Response body: " + responseBody);
            }

            // Try to parse JSON response regardless of status code
            JSONObject response;
            try {
                response = new JSONObject(responseBody);
            } catch (JSONException e) {
                Log.e(TAG, "❌ Failed to parse response as JSON", e);
                throw new Exception("HTTP Error " + responseCode + ": Invalid JSON response: " + responseBody);
            }
            
            // Check for error messages in response first
            if (response.has("msg") && !response.isNull("msg")) {
                String errorMsg = response.optString("msg", "");
                if (!errorMsg.isEmpty()) {
                    Log.e(TAG, "❌ API Error message: " + errorMsg);
                    // Include response code in error if not 200
                    if (responseCode != 200) {
                        throw new Exception("HTTP Error " + responseCode + ": " + errorMsg);
                    } else if (!response.optBoolean("success", false)) {
                        throw new Exception("Data request failed: " + errorMsg);
                    }
                }
            }
            
            if (responseCode != 200) {
                Log.e(TAG, "❌ HTTP Error " + responseCode + ": " + responseBody);
                String errorMsg = response.optString("msg", "Unknown error");
                throw new Exception("HTTP Error " + responseCode + ": " + errorMsg);
            }
            
            if (!response.optBoolean("success", false)) {
                Log.e(TAG, "❌ Data request failed. Response: " + response.toString());
                String errorMsg = response.optString("msg", "Data request failed");
                throw new Exception("Data request failed: " + errorMsg);
            }

            // Check if dataList exists
            if (!response.has("dataList")) {
                Log.w(TAG, "⚠️ No dataList in response");
            } else {
                int dataCount = response.optJSONArray("dataList").length();
                Log.d(TAG, "✅ Inverter data successfully retrieved (" + dataCount + " metrics)");
            }
            
            return response;

        } catch (JSONException e) {
            Log.e(TAG, "❌ JSON parsing error", e);
            throw new Exception("Failed to parse inverter data response: " + e.getMessage(), e);
        } catch (java.io.IOException e) {
            Log.e(TAG, "❌ Network error during data request", e);
            throw new Exception("Network error: " + e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Hash password with SHA-256 for Solarman API authentication
     * @param password The plain text password
     * @return SHA-256 hash in hex format (64 characters)
     */
    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            
            // Convert byte array to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            Log.e(TAG, "❌ Error hashing password", e);
            // If hashing fails, return original password (fallback)
            return password;
        }
    }
    
    /**
     * Read response from HTTP connection
     * @param connection The HTTP connection
     * @param responseCode The response code (already retrieved to avoid multiple calls)
     */
    private String readResponse(HttpURLConnection connection, int responseCode) throws Exception {
        InputStream inputStream;
        if (responseCode >= 200 && responseCode < 300) {
            inputStream = connection.getInputStream();
        } else {
            inputStream = connection.getErrorStream();
        }

        if (inputStream == null) {
            // Some servers don't send error body, return a default message
            return "{\"success\":false,\"msg\":\"HTTP " + responseCode + " - No response body\"}";
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }
        
        String result = response.toString();
        // If response is empty, return a default JSON error message
        if (result.isEmpty()) {
            return "{\"success\":false,\"msg\":\"HTTP " + responseCode + " - Empty response\"}";
        }
        
        return result;
    }

    /**
     * Clear cached token (force refresh on next request)
     */
    public void clearCachedToken() {
        cachedAccessToken = null;
        tokenExpiryTime = 0;
    }

    /**
     * Shutdown executor service
     */
    public void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}

