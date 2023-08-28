package com.zj.playerLib.source.ads;

import android.view.ViewGroup;
import com.zj.playerLib.InlinePlayer;
import com.zj.playerLib.source.ads.AdsMediaSource.AdLoadException;
import com.zj.playerLib.upstream.DataSpec;
import java.io.IOException;

public interface AdsLoader {
    void setSupportedContentTypes(int... var1);

    void attachPlayer(InlinePlayer var1, EventListener var2, ViewGroup var3);

    void detachPlayer();

    void release();

    void handlePrepareError(int var1, int var2, IOException var3);

    interface EventListener {
        void onAdPlaybackState(AdPlaybackState var1);

        void onAdLoadError(AdLoadException var1, DataSpec var2);

        void onAdClicked();

        void onAdTapped();
    }
}
