//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.scheduler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.PowerManager;

import com.zj.playerLib.util.Util;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public final class Requirements {
    public static final int NETWORK_TYPE_NONE = 0;
    public static final int NETWORK_TYPE_ANY = 1;
    public static final int NETWORK_TYPE_UNMETERED = 2;
    public static final int NETWORK_TYPE_NOT_ROAMING = 3;
    public static final int NETWORK_TYPE_METERED = 4;
    private static final int DEVICE_IDLE = 8;
    private static final int DEVICE_CHARGING = 16;
    private static final int NETWORK_TYPE_MASK = 7;
    private static final String TAG = "Requirements";
    private static final String[] NETWORK_TYPE_STRINGS = null;
    private final int requirements;

    public Requirements(int networkType, boolean charging, boolean idle) {
        this(networkType | (charging ? 16 : 0) | (idle ? 8 : 0));
    }

    public Requirements(int requirementsData) {
        this.requirements = requirementsData;
    }

    public int getRequiredNetworkType() {
        return this.requirements & 7;
    }

    public boolean isChargingRequired() {
        return (this.requirements & 16) != 0;
    }

    public boolean isIdleRequired() {
        return (this.requirements & 8) != 0;
    }

    public boolean checkRequirements(Context context) {
        return this.checkNetworkRequirements(context) && this.checkChargingRequirement(context) && this.checkIdleRequirement(context);
    }

    public int getRequirementsData() {
        return this.requirements;
    }

    private boolean checkNetworkRequirements(Context context) {
        int networkRequirement = this.getRequiredNetworkType();
        if (networkRequirement == 0) {
            return true;
        } else {
            ConnectivityManager connectivityManager = (ConnectivityManager)context.getSystemService("connectivity");
            NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
            if (networkInfo != null && networkInfo.isConnected()) {
                if (!checkInternetConnectivity(connectivityManager)) {
                    return false;
                } else if (networkRequirement == 1) {
                    return true;
                } else {
                    boolean activeNetworkMetered;
                    if (networkRequirement == 3) {
                        activeNetworkMetered = networkInfo.isRoaming();
                        logd("Roaming: " + activeNetworkMetered);
                        return !activeNetworkMetered;
                    } else {
                        activeNetworkMetered = isActiveNetworkMetered(connectivityManager, networkInfo);
                        logd("Metered network: " + activeNetworkMetered);
                        if (networkRequirement == 2) {
                            return !activeNetworkMetered;
                        } else if (networkRequirement == 4) {
                            return activeNetworkMetered;
                        } else {
                            throw new IllegalStateException();
                        }
                    }
                }
            } else {
                logd("No network info or no connection.");
                return false;
            }
        }
    }

    private boolean checkChargingRequirement(Context context) {
        if (!this.isChargingRequired()) {
            return true;
        } else {
            Intent batteryStatus = context.registerReceiver((BroadcastReceiver)null, new IntentFilter("android.intent.action.BATTERY_CHANGED"));
            if (batteryStatus == null) {
                return false;
            } else {
                int status = batteryStatus.getIntExtra("status", -1);
                return status == 2 || status == 5;
            }
        }
    }

    private boolean checkIdleRequirement(Context context) {
        if (!this.isIdleRequired()) {
            return true;
        } else {
            PowerManager powerManager = (PowerManager)context.getSystemService("power");
            return Util.SDK_INT >= 23 ? powerManager.isDeviceIdleMode() : (Util.SDK_INT >= 20 ? !powerManager.isInteractive() : !powerManager.isScreenOn());
        }
    }

    private static boolean checkInternetConnectivity(ConnectivityManager connectivityManager) {
        if (Util.SDK_INT < 23) {
            return true;
        } else {
            Network activeNetwork = connectivityManager.getActiveNetwork();
            if (activeNetwork == null) {
                logd("No active network.");
                return false;
            } else {
                NetworkCapabilities networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
                boolean validated = networkCapabilities == null || !networkCapabilities.hasCapability(16);
                logd("Network capability validated: " + validated);
                return !validated;
            }
        }
    }

    private static boolean isActiveNetworkMetered(ConnectivityManager connectivityManager, NetworkInfo networkInfo) {
        if (Util.SDK_INT >= 16) {
            return connectivityManager.isActiveNetworkMetered();
        } else {
            int type = networkInfo.getType();
            return type != 1 && type != 7 && type != 9;
        }
    }

    private static void logd(String message) {
    }

    public String toString() {
        return super.toString();
    }

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    public @interface NetworkType {
    }
}
