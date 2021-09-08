//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.upstream;

import android.os.Handler;

import androidx.annotation.Nullable;

public interface BandwidthMeter {
    long getBitrateEstimate();

    @Nullable
    TransferListener getTransferListener();

    void addEventListener(Handler var1, EventListener var2);

    void removeEventListener(EventListener var1);

    public interface EventListener {
        void onBandwidthSample(int var1, long var2, long var4);
    }
}
