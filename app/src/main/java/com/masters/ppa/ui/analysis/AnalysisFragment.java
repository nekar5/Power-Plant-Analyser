package com.masters.ppa.ui.analysis;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.masters.ppa.R;
import com.masters.ppa.databinding.FragmentAnalysisBinding;
import com.masters.ppa.ui.analysis.tabs.BatteryAnalysisFragment;
import com.masters.ppa.ui.analysis.tabs.CurrentDataFragment;
import com.masters.ppa.ui.analysis.tabs.GenerationForecastFragment;
import com.masters.ppa.ui.analysis.tabs.StationAnalysisFragment;

/**
 * Fragment for the Analysis screen
 */
public class AnalysisFragment extends Fragment {

    private FragmentAnalysisBinding binding;
    private AnalysisViewModel viewModel;
    private AnalysisPagerAdapter pagerAdapter;
    
    /**
     * Enable or disable tab navigation (ViewPager and TabLayout)
     */
    public void setTabNavigationEnabled(boolean enabled) {
        if (binding == null) return;
        
        View view = getView();
        if (view != null) {
            view.post(() -> {
                if (binding != null) {
                    // Disable ViewPager swipe gestures
                    binding.viewPager.setUserInputEnabled(enabled);
                    
                    // Disable TabLayout clicking and swiping
                    binding.tabs.setEnabled(enabled);
                    binding.tabs.setClickable(enabled);
                    
                    // Disable all tabs individually
                    try {
                        for (int i = 0; i < binding.tabs.getTabCount(); i++) {
                            TabLayout.Tab tab = binding.tabs.getTabAt(i);
                            if (tab != null && tab.view != null) {
                                tab.view.setEnabled(enabled);
                                tab.view.setClickable(enabled);
                                // Make tabs non-interactive when disabled
                                if (!enabled) {
                                    tab.view.setAlpha(0.5f); // Visual indication
                                } else {
                                    tab.view.setAlpha(1.0f);
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Ignore errors accessing tab views
                    }
                }
            });
        } else {
            // If view is not available yet, apply directly
            if (binding != null) {
                binding.viewPager.setUserInputEnabled(enabled);
                binding.tabs.setEnabled(enabled);
                binding.tabs.setClickable(enabled);
            }
        }
    }
    
    /**
     * Check if tab navigation is enabled
     */
    public boolean isTabNavigationEnabled() {
        if (binding != null) {
            return binding.viewPager.isUserInputEnabled();
        }
        return true;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentAnalysisBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(AnalysisViewModel.class);
        
        setupViewPager();
        setupObservers();
    }
    
    /**
     * Setup ViewPager with tabs
     */
    private void setupViewPager() {
        pagerAdapter = new AnalysisPagerAdapter(this);
        binding.viewPager.setAdapter(pagerAdapter);
        
        // Connect TabLayout with ViewPager2
        new TabLayoutMediator(binding.tabs, binding.viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText(R.string.tab_current_data);
                    break;
                case 1:
                    tab.setText(R.string.tab_station);
                    break;
                case 2:
                    tab.setText(R.string.tab_battery);
                    break;
                case 3:
                    tab.setText(R.string.tab_forecast);
                    break;
            }
        }).attach();
    }
    
    /**
     * Setup observers for LiveData
     */
    private void setupObservers() {
        // Check if station data exists
        viewModel.hasStationData().observe(getViewLifecycleOwner(), hasStationData -> {
            if (hasStationData != null) {
                binding.viewPager.setVisibility(hasStationData ? View.VISIBLE : View.GONE);
                binding.tabs.setVisibility(hasStationData ? View.VISIBLE : View.GONE);
                binding.textNoData.setVisibility(hasStationData ? View.GONE : View.VISIBLE);
            }
        });
        
        // Operation results
        viewModel.getOperationSuccess().observe(getViewLifecycleOwner(), success -> {
            String message = viewModel.getOperationMessage().getValue();
            if (message != null && success != null) {
                // Use UiUtils for consistent snackbar handling
                com.masters.ppa.utils.UiUtils.showSnackbar(
                    binding.getRoot(), 
                    message, 
                    !success
                );
            }
        });
    }

    @Override
    public void onDestroyView() {
        // Re-enable tab navigation when fragment is destroyed
        setTabNavigationEnabled(true);
        
        // Clear any existing snackbar to prevent memory leaks
        com.masters.ppa.utils.UiUtils.clearSnackbar();
        
        super.onDestroyView();
        binding = null;
    }
    
    /**
     * ViewPager adapter for the tabs
     */
    private static class AnalysisPagerAdapter extends FragmentStateAdapter {
        
        public AnalysisPagerAdapter(Fragment fragment) {
            super(fragment);
        }
        
        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return new CurrentDataFragment();
                case 1:
                    return new StationAnalysisFragment();
                case 2:
                    return new BatteryAnalysisFragment();
                case 3:
                    return new GenerationForecastFragment();
                default:
                    return new CurrentDataFragment();
            }
        }
        
        @Override
        public int getItemCount() {
            return 4;
        }
    }
}
