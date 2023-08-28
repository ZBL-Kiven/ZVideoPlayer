package com.zj.playerLib.audio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import androidx.annotation.Nullable;

import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.Util;

public final class AudioCapabilitiesReceiver {
    private final Context context;
    @Nullable
    private final Handler handler;
    private final Listener listener;
    @Nullable
    private final BroadcastReceiver receiver;
    @Nullable
    AudioCapabilities audioCapabilities;

    public AudioCapabilitiesReceiver(Context context, Listener listener) {
        this(context, null, listener);
    }

    public AudioCapabilitiesReceiver(Context context, @Nullable Handler handler, Listener listener) {
        this.context = Assertions.checkNotNull(context);
        this.handler = handler;
        this.listener = Assertions.checkNotNull(listener);
        this.receiver = Util.SDK_INT >= 21 ? new HdmiAudioPlugBroadcastReceiver() : null;
    }

    public AudioCapabilities register() {
        Intent stickyIntent = null;
        if (this.receiver != null) {
            IntentFilter intentFilter = new IntentFilter("android.media.action.HDMI_AUDIO_PLUG");
            if (this.handler != null) {
                stickyIntent = this.context.registerReceiver(this.receiver, intentFilter, null, this.handler);
            } else {
                stickyIntent = this.context.registerReceiver(this.receiver, intentFilter);
            }
        }

        this.audioCapabilities = AudioCapabilities.getCapabilities(stickyIntent);
        return this.audioCapabilities;
    }

    public void unregister() {
        if (this.receiver != null) {
            this.context.unregisterReceiver(this.receiver);
        }

    }

    private final class HdmiAudioPlugBroadcastReceiver extends BroadcastReceiver {
        private HdmiAudioPlugBroadcastReceiver() {
        }

        public void onReceive(Context context, Intent intent) {
            if (!this.isInitialStickyBroadcast()) {
                AudioCapabilities newAudioCapabilities = AudioCapabilities.getCapabilities(intent);
                if (!newAudioCapabilities.equals(AudioCapabilitiesReceiver.this.audioCapabilities)) {
                    AudioCapabilitiesReceiver.this.audioCapabilities = newAudioCapabilities;
                    AudioCapabilitiesReceiver.this.listener.onAudioCapabilitiesChanged(newAudioCapabilities);
                }
            }

        }
    }

    public interface Listener {
        void onAudioCapabilitiesChanged(AudioCapabilities var1);
    }
}
