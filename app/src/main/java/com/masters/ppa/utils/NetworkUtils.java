package com.masters.ppa.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.util.Log;
import android.os.Handler;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for network operations
 */
public class NetworkUtils {
    
    private static final String TAG = "NetworkUtils";
    
    public interface NetworkStatusListener {
        void onNetworkStatusChanged(boolean isConnected, NetworkType networkType);
    }
    
    public enum NetworkType {
        WIFI, MOBILE, ETHERNET, NONE
    }
    
    private static ConnectivityManager.NetworkCallback networkCallback;
    private static List<NetworkStatusListener> listeners = new ArrayList<>();
    private static boolean isCallbackRegistered = false;
    
    /**
     * Register network callback to monitor network changes
     * @param context Application context
     * @param listener Callback for network status changes
     */
    public static void registerNetworkCallback(Context context, NetworkStatusListener listener) {
        if (listener == null) {
            Log.w(TAG, "Listener is null, ignoring registration");
            return;
        }
        
        // Add listener to list
        synchronized (listeners) {
            if (!listeners.contains(listener)) {
                listeners.add(listener);
                Log.d(TAG, "Added listener, total listeners: " + listeners.size());
            }
        }
        
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        
        if (connectivityManager == null) {
            Log.e(TAG, "ConnectivityManager not available");
            return;
        }
        
        // Register callback only once
        if (!isCallbackRegistered) {
            networkCallback = new ConnectivityManager.NetworkCallback() {
                private final Handler mainHandler = new Handler(android.os.Looper.getMainLooper());

                @Override
                public void onAvailable(@NonNull Network network) {
                    super.onAvailable(network);
                    NetworkType type = getNetworkType(connectivityManager, network);
                    mainHandler.post(() -> notifyListeners(true, type));
                }

                @Override
                public void onLost(@NonNull Network network) {
                    super.onLost(network);
                    mainHandler.post(() -> notifyListeners(false, NetworkType.NONE));
                }

                @Override
                public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
                    super.onCapabilitiesChanged(network, networkCapabilities);
                    NetworkType type = getNetworkType(connectivityManager, network);
                    mainHandler.post(() -> notifyListeners(true, type));
                }
            };

            NetworkRequest networkRequest = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();
            
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback);
            isCallbackRegistered = true;
            Log.d(TAG, "Network callback registered");
        }
        
        // Initial check for new listener
        boolean isConnected = isNetworkConnected(context);
        NetworkType initialType = getCurrentNetworkType(context);
        listener.onNetworkStatusChanged(isConnected, initialType);
    }
    
    /**
     * Notify all registered listeners
     */
    private static void notifyListeners(boolean isConnected, NetworkType networkType) {
        synchronized (listeners) {
            List<NetworkStatusListener> listenersCopy = new ArrayList<>(listeners);
            for (NetworkStatusListener listener : listenersCopy) {
                try {
                    listener.onNetworkStatusChanged(isConnected, networkType);
                } catch (Exception e) {
                    Log.e(TAG, "Error notifying listener", e);
                }
            }
        }
    }
    
    /**
     * Unregister specific listener
     * @param context Application context
     * @param listener Listener to unregister (null to unregister all)
     */
    public static void unregisterNetworkCallback(Context context, NetworkStatusListener listener) {
        if (listener != null) {
            // Remove specific listener
            synchronized (listeners) {
                listeners.remove(listener);
                Log.d(TAG, "Removed listener, remaining listeners: " + listeners.size());
            }
            
            // If no more listeners, unregister callback
            if (listeners.isEmpty() && isCallbackRegistered) {
                unregisterCallback(context);
            }
        } else {
            // Remove all listeners and unregister callback
            synchronized (listeners) {
                listeners.clear();
            }
            unregisterCallback(context);
        }
    }
    
    /**
     * Unregister the actual network callback
     */
    private static void unregisterCallback(Context context) {
        if (networkCallback != null && isCallbackRegistered) {
            try {
                ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                if (connectivityManager != null) {
                    connectivityManager.unregisterNetworkCallback(networkCallback);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering network callback", e);
            }
            networkCallback = null;
            isCallbackRegistered = false;
            Log.d(TAG, "Network callback unregistered");
        }
    }
    
    /**
     * Check if network is connected
     * @param context Application context
     * @return true if connected
     */
    public static boolean isNetworkConnected(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            NetworkCapabilities capabilities = cm.getNetworkCapabilities(cm.getActiveNetwork());
            return capabilities != null && (
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        } else {
            // Deprecated in API 29
            @SuppressWarnings("deprecation")
            android.net.NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        }
    }
    
    /**
     * Get current network type
     * @param context Application context
     * @return NetworkType enum
     */
    public static NetworkType getCurrentNetworkType(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return NetworkType.NONE;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            NetworkCapabilities capabilities = cm.getNetworkCapabilities(cm.getActiveNetwork());
            if (capabilities == null) return NetworkType.NONE;
            
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return NetworkType.WIFI;
            } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                return NetworkType.MOBILE;
            } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                return NetworkType.ETHERNET;
            }
        } else {
            // Deprecated in API 29
            @SuppressWarnings("deprecation")
            android.net.NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            if (activeNetwork == null) return NetworkType.NONE;
            
            @SuppressWarnings("deprecation")
            int type = activeNetwork.getType();
            
            if (type == ConnectivityManager.TYPE_WIFI) {
                return NetworkType.WIFI;
            } else if (type == ConnectivityManager.TYPE_MOBILE) {
                return NetworkType.MOBILE;
            } else if (type == ConnectivityManager.TYPE_ETHERNET) {
                return NetworkType.ETHERNET;
            }
        }
        
        return NetworkType.NONE;
    }
    
    /**
     * Get network type from network capabilities
     * @param cm ConnectivityManager
     * @param network Network
     * @return NetworkType enum
     */
    private static NetworkType getNetworkType(ConnectivityManager cm, Network network) {
        NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
        if (capabilities == null) return NetworkType.NONE;
        
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return NetworkType.WIFI;
        } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            return NetworkType.MOBILE;
        } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            return NetworkType.ETHERNET;
        }
        
        return NetworkType.NONE;
    }
}
