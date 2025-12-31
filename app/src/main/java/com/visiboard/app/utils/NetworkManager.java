package com.visiboard.app.utils;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.visiboard.app.R;

public class NetworkManager {

    private static View noInternetBanner = null;
    private static FrameLayout bannerContainer = null;

    /**
     * Check if network is available
     */
    public static boolean isNetworkAvailable(Context context) {
        if (context == null) return false;
        
        ConnectivityManager cm = (ConnectivityManager) 
            context.getSystemService(Context.CONNECTIVITY_SERVICE);
        
        if (cm == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = cm.getActiveNetwork();
            if (network == null) return false;
            
            NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
            return capabilities != null && (
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            );
        } else {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        }
    }

    /**
     * Show no internet banner at the top of activity
     * Banner persists across fragment navigation
     */
    public static void showNoInternetBanner(Activity activity, Runnable retryAction) {
        if (activity == null) return;
        
        // Find or create banner container
        if (bannerContainer == null) {
            bannerContainer = activity.findViewById(R.id.banner_container);
            if (bannerContainer == null) {
                // Create container if it doesn't exist
                ViewGroup rootView = activity.findViewById(android.R.id.content);
                if (rootView != null && rootView.getChildCount() > 0) {
                    View contentView = rootView.getChildAt(0);
                    if (contentView instanceof FrameLayout) {
                        bannerContainer = new FrameLayout(activity);
                        bannerContainer.setId(R.id.banner_container);
                        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        );
                        params.topMargin = 0;
                        bannerContainer.setLayoutParams(params);
                        ((ViewGroup) contentView).addView(bannerContainer, 0);
                    }
                }
            }
        }

        // Don't show if already visible
        if (noInternetBanner != null && noInternetBanner.getParent() != null) {
            return;
        }

        // Inflate banner
        noInternetBanner = LayoutInflater.from(activity)
            .inflate(R.layout.layout_no_internet, bannerContainer, false);

        // Setup retry button
        Button btnRetry = noInternetBanner.findViewById(R.id.btn_retry);
        btnRetry.setOnClickListener(v -> {
            if (isNetworkAvailable(activity)) {
                hideNoInternetBanner();
                if (retryAction != null) {
                    retryAction.run();
                }
            } else {
                // Use UiHelper if view is available, else log (or ignore if background)
                 // Finding a suitable view from activity
                 android.view.View rootView = activity.findViewById(android.R.id.content);
                 if (rootView != null) {
                     // In this context, network is not available, so isConnected is false.
                     com.visiboard.app.utils.UiHelper.showInfo(rootView, "Network Disconnected");
                 }
            }
        });

        // Setup close button
        ImageView btnClose = noInternetBanner.findViewById(R.id.btn_close_no_internet);
        btnClose.setOnClickListener(v -> hideNoInternetBanner());

        // Add to container
        if (bannerContainer != null) {
            bannerContainer.addView(noInternetBanner);
            bannerContainer.setVisibility(View.VISIBLE);
            
            // Animate in
            noInternetBanner.setTranslationY(-200f);
            noInternetBanner.animate()
                .translationY(0f)
                .setDuration(300)
                .start();
        }
    }

    /**
     * Hide the no internet banner
     */
    public static void hideNoInternetBanner() {
        if (noInternetBanner != null && bannerContainer != null) {
            noInternetBanner.animate()
                .translationY(-200f)
                .setDuration(300)
                .withEndAction(() -> {
                    bannerContainer.removeView(noInternetBanner);
                    bannerContainer.setVisibility(View.GONE);
                    noInternetBanner = null;
                })
                .start();
        }
    }

    /**
     * Check if banner is currently showing
     */
    public static boolean isBannerShowing() {
        return noInternetBanner != null && noInternetBanner.getParent() != null;
    }

    /**
     * Clean up references (call in onDestroy)
     */
    public static void cleanup() {
        noInternetBanner = null;
        bannerContainer = null;
    }
}
