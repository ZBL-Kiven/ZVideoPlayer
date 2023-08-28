package com.zj.playerLib.scheduler;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.Network;
import android.net.NetworkRequest;
import android.net.NetworkRequest.Builder;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.RequiresApi;

import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.Util;

public final class RequirementsWatcher {
    private static final String TAG = "RequirementsWatcher";
    private final Context context;
    private final Listener listener;
    private final Requirements requirements;
    private DeviceStatusChangeReceiver receiver;
    private boolean requirementsWereMet;
    private CapabilityValidatedCallback networkCallback;

    public RequirementsWatcher(Context context, Listener listener, Requirements requirements) {
        this.requirements = requirements;
        this.listener = listener;
        this.context = context.getApplicationContext();
        logd(this + " created");
    }

    public void start() {
        Assertions.checkNotNull(Looper.myLooper());
        this.requirementsWereMet = this.requirements.checkRequirements(this.context);
        IntentFilter filter = new IntentFilter();
        if (this.requirements.getRequiredNetworkType() != 0) {
            if (Util.SDK_INT >= 23) {
                this.registerNetworkCallbackV23();
            } else {
                filter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
            }
        }

        if (this.requirements.isChargingRequired()) {
            filter.addAction("android.intent.action.ACTION_POWER_CONNECTED");
            filter.addAction("android.intent.action.ACTION_POWER_DISCONNECTED");
        }

        if (this.requirements.isIdleRequired()) {
            if (Util.SDK_INT >= 23) {
                filter.addAction("android.os.action.DEVICE_IDLE_MODE_CHANGED");
            } else {
                filter.addAction("android.intent.action.SCREEN_ON");
                filter.addAction("android.intent.action.SCREEN_OFF");
            }
        }

        this.receiver = new DeviceStatusChangeReceiver();
        this.context.registerReceiver(this.receiver, filter, null, new Handler());
        logd(this + " started");
    }

    public void stop() {
        this.context.unregisterReceiver(this.receiver);
        this.receiver = null;
        if (this.networkCallback != null) {
            this.unregisterNetworkCallback();
        }

        logd(this + " stopped");
    }

    public Requirements getRequirements() {
        return this.requirements;
    }

    public String toString() {
        return super.toString();
    }

    @TargetApi(23)
    private void registerNetworkCallbackV23() {
        ConnectivityManager connectivityManager = (ConnectivityManager)this.context.getSystemService("connectivity");
        NetworkRequest request = (new Builder()).addCapability(16).build();
        this.networkCallback = new CapabilityValidatedCallback();
        connectivityManager.registerNetworkCallback(request, this.networkCallback);
    }

    private void unregisterNetworkCallback() {
        if (Util.SDK_INT >= 21) {
            ConnectivityManager connectivityManager = (ConnectivityManager)this.context.getSystemService("connectivity");
            connectivityManager.unregisterNetworkCallback(this.networkCallback);
            this.networkCallback = null;
        }

    }

    private void checkRequirements() {
        boolean requirementsAreMet = this.requirements.checkRequirements(this.context);
        if (requirementsAreMet == this.requirementsWereMet) {
            logd("requirementsAreMet is still " + requirementsAreMet);
        } else {
            this.requirementsWereMet = requirementsAreMet;
            if (requirementsAreMet) {
                logd("start job");
                this.listener.requirementsMet(this);
            } else {
                logd("stop job");
                this.listener.requirementsNotMet(this);
            }

        }
    }

    private static void logd(String message) {
    }

    @RequiresApi(
        api = 21
    )
    private final class CapabilityValidatedCallback extends NetworkCallback {
        private CapabilityValidatedCallback() {
        }

        public void onAvailable(Network network) {
            super.onAvailable(network);
            RequirementsWatcher.logd(RequirementsWatcher.this + " NetworkCallback.onAvailable");
            RequirementsWatcher.this.checkRequirements();
        }

        public void onLost(Network network) {
            super.onLost(network);
            RequirementsWatcher.logd(RequirementsWatcher.this + " NetworkCallback.onLost");
            RequirementsWatcher.this.checkRequirements();
        }
    }

    private class DeviceStatusChangeReceiver extends BroadcastReceiver {
        private DeviceStatusChangeReceiver() {
        }

        public void onReceive(Context context, Intent intent) {
            if (!this.isInitialStickyBroadcast()) {
                RequirementsWatcher.logd(RequirementsWatcher.this + " received " + intent.getAction());
                RequirementsWatcher.this.checkRequirements();
            }

        }
    }

    public interface Listener {
        void requirementsMet(RequirementsWatcher var1);

        void requirementsNotMet(RequirementsWatcher var1);
    }
}
