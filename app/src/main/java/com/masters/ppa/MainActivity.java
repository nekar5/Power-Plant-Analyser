package com.masters.ppa;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.masters.ppa.databinding.ActivityMainBinding;
import com.masters.ppa.utils.NetworkUtils;

/**
 * Main activity for the application
 */
public class MainActivity extends AppCompatActivity implements NetworkUtils.NetworkStatusListener {

    private ActivityMainBinding binding;
    private TextView connectionStatus;
    private BottomNavigationView navView;
    private NavController navController;
    private boolean navigationEnabled = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        connectionStatus = binding.connectionStatus;
        navView = binding.navView;

        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_settings,
                R.id.navigation_project,
                R.id.navigation_analysis,
                R.id.navigation_weather)
                .build();

        NavHostFragment navHostFragment =
                (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        navController = navHostFragment.getNavController();

        NavigationUI.setupWithNavController(navView, navController);
        
        navView.setOnItemSelectedListener(item -> {
            if (!navigationEnabled) {
                navController.getCurrentDestination();
                return false;
            }
            return NavigationUI.onNavDestinationSelected(item, navController);
        });
        
        NetworkUtils.registerNetworkCallback(this, this);
        
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            int destinationId = destination.getId();
            boolean shouldHide = destinationId == R.id.navigation_settings || 
                                destinationId == R.id.navigation_weather || 
                                destinationId == R.id.navigation_project ||
                                destinationId == R.id.navigation_analysis;
            connectionStatus.setVisibility(shouldHide ? View.GONE : View.VISIBLE);
        });
    }
    
    @Override
    public void onNetworkStatusChanged(boolean isConnected, NetworkUtils.NetworkType networkType) {
        if (connectionStatus == null || connectionStatus.getVisibility() != View.VISIBLE) {
            return;
        }
        
        if (isConnected) {
            String networkName = networkType == NetworkUtils.NetworkType.WIFI ? "Wi-Fi" : 
                                (networkType == NetworkUtils.NetworkType.MOBILE ? "Mobile" : "Ethernet");
            connectionStatus.setText("Online (" + networkName + ")");
            connectionStatus.setCompoundDrawablesWithIntrinsicBounds(R.drawable.status_indicator_online, 0, 0, 0);
        } else {
            connectionStatus.setText("Offline");
            connectionStatus.setCompoundDrawablesWithIntrinsicBounds(R.drawable.status_indicator_offline, 0, 0, 0);
        }
    }

    /**
     * Enable or disable navigation
     */
    public void setNavigationEnabled(boolean enabled) {
        navigationEnabled = enabled;
        runOnUiThread(() -> {
            if (navView != null) {
                navView.setEnabled(enabled);
                for (int i = 0; i < navView.getMenu().size(); i++) {
                    navView.getMenu().getItem(i).setEnabled(enabled);
                }
                navView.setClickable(enabled);
            }
            if (navController != null) {
                // Navigation is controlled by navigationEnabled flag in listener
            }
        });
    }
    
    /**
     * Check if navigation is enabled
     */
    public boolean isNavigationEnabled() {
        return navigationEnabled;
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        NavHostFragment navHostFragment =
                (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        NavController navController = navHostFragment.getNavController();
        return navController.navigateUp() || super.onSupportNavigateUp();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        NetworkUtils.unregisterNetworkCallback(this, this);
        binding = null;
    }
}
