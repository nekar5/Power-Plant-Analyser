package com.masters.ppa.utils;

import android.view.View;

import androidx.annotation.ColorRes;
import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;
import com.masters.ppa.R;

/**
 * Utility class for UI operations
 */
public class UiUtils {

    private static Snackbar currentSnackbar;

    /**
     * Show a snackbar with the given message and color
     * @param view The view to show the snackbar from
     * @param message The message to show
     * @param isError Whether the message is an error
     * @return The created snackbar
     */
    public static Snackbar showSnackbar(View view, String message, boolean isError) {
        return showSnackbar(view, message, isError ? SnackbarType.ERROR : SnackbarType.SUCCESS);
    }

    /**
     * Show a snackbar with the given message and color
     * @param view The view to show the snackbar from
     * @param message The message to show
     * @param type The type of snackbar to show
     * @return The created snackbar
     */
    public static Snackbar showSnackbar(View view, String message, SnackbarType type) {
        // Dismiss any existing snackbar
        if (currentSnackbar != null && currentSnackbar.isShown()) {
            currentSnackbar.dismiss();
        }

        // Create a new snackbar
        int duration = (type == SnackbarType.ERROR) ? Snackbar.LENGTH_LONG : Snackbar.LENGTH_SHORT;
        currentSnackbar = Snackbar.make(view, message, duration);

        // Set the background color based on the type
        if (type != SnackbarType.DEFAULT) {
            @ColorRes int colorRes;
            switch (type) {
                case SUCCESS:
                    colorRes = R.color.accent_green;
                    break;
                case ERROR:
                    colorRes = R.color.accent_red;
                    break;
                case WARNING:
                    colorRes = R.color.accent_orange;
                    break;
                default:
                    colorRes = R.color.primary;
                    break;
            }
            currentSnackbar.setBackgroundTint(ContextCompat.getColor(view.getContext(), colorRes));
        }

        // Show the snackbar
        currentSnackbar.show();
        return currentSnackbar;
    }

    /**
     * Clear the current snackbar reference
     */
    public static void clearSnackbar() {
        if (currentSnackbar != null) {
            currentSnackbar.dismiss();
            currentSnackbar = null;
        }
    }

    /**
     * Enum for snackbar types
     */
    public enum SnackbarType {
        DEFAULT,
        SUCCESS,
        ERROR,
        WARNING
    }
}
