//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib;

import android.content.Context;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.zj.playerLib.analytics.PlayerStateCollector.Factory;
import com.zj.playerLib.drm.DrmSessionManager;
import com.zj.playerLib.drm.FrameworkMediaCrypto;
import com.zj.playerLib.trackselection.DefaultTrackSelector;
import com.zj.playerLib.trackselection.TrackSelector;
import com.zj.playerLib.upstream.BandwidthMeter;
import com.zj.playerLib.upstream.DefaultBandwidthMeter.Builder;
import com.zj.playerLib.util.Clock;
import com.zj.playerLib.util.Util;

@SuppressWarnings("unused")
public final class PlayerFactory {
    @Nullable
    private static BandwidthMeter singletonBandwidthMeter;

    private PlayerFactory() {
    }

    /**
     * @deprecated
     */
    @Deprecated
    public static SimplePlayer newSimpleInstance(Context context, TrackSelector trackSelector, LoadControl loadControl) {
        RenderersFactory renderersFactory = new DefaultRenderersFactory(context);
        return newSimpleInstance(context, renderersFactory, trackSelector, loadControl);
    }

    /**
     * @deprecated
     */
    @Deprecated
    public static SimplePlayer newSimpleInstance(Context context, TrackSelector trackSelector, LoadControl loadControl, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager) {
        RenderersFactory renderersFactory = new DefaultRenderersFactory(context);
        return newSimpleInstance(context, renderersFactory, trackSelector, loadControl, drmSessionManager);
    }

    /**
     * @deprecated
     */
    @Deprecated
    public static SimplePlayer newSimpleInstance(Context context, TrackSelector trackSelector, LoadControl loadControl, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, int extensionRendererMode) {
        RenderersFactory renderersFactory = new DefaultRenderersFactory(context, extensionRendererMode);
        return newSimpleInstance(context, renderersFactory, trackSelector, loadControl, drmSessionManager);
    }

    /**
     * @deprecated
     */
    @Deprecated
    public static SimplePlayer newSimpleInstance(Context context, TrackSelector trackSelector, LoadControl loadControl, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, int extensionRendererMode, long allowedVideoJoiningTimeMs) {
        RenderersFactory renderersFactory = new DefaultRenderersFactory(context, extensionRendererMode, allowedVideoJoiningTimeMs);
        return newSimpleInstance(context, renderersFactory, trackSelector, loadControl, drmSessionManager);
    }

    public static SimplePlayer newSimpleInstance(Context context) {
        return newSimpleInstance(context, new DefaultTrackSelector());
    }

    public static SimplePlayer newSimpleInstance(Context context, TrackSelector trackSelector) {
        return newSimpleInstance(context, new DefaultRenderersFactory(context), trackSelector);
    }

    /**
     * @deprecated
     */
    @Deprecated
    public static SimplePlayer newSimpleInstance(RenderersFactory renderersFactory, TrackSelector trackSelector) {
        return newSimpleInstance(null, renderersFactory, trackSelector, new DefaultLoadControl());
    }

    public static SimplePlayer newSimpleInstance(Context context, RenderersFactory renderersFactory, TrackSelector trackSelector) {
        return newSimpleInstance(context, renderersFactory, trackSelector, new DefaultLoadControl());
    }

    public static SimplePlayer newSimpleInstance(Context context, RenderersFactory renderersFactory, TrackSelector trackSelector, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager) {
        return newSimpleInstance(context, renderersFactory, trackSelector, new DefaultLoadControl(), drmSessionManager);
    }

    public static SimplePlayer newSimpleInstance(Context context, RenderersFactory renderersFactory, TrackSelector trackSelector, LoadControl loadControl) {
        return newSimpleInstance(context, renderersFactory, trackSelector, loadControl, null, Util.getLooper());
    }

    public static SimplePlayer newSimpleInstance(Context context, RenderersFactory renderersFactory, TrackSelector trackSelector, LoadControl loadControl, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager) {
        return newSimpleInstance(context, renderersFactory, trackSelector, loadControl, drmSessionManager, Util.getLooper());
    }

    public static SimplePlayer newSimpleInstance(Context context, RenderersFactory renderersFactory, TrackSelector trackSelector, LoadControl loadControl, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, BandwidthMeter bandwidthMeter) {
        return newSimpleInstance(context, renderersFactory, trackSelector, loadControl, drmSessionManager, bandwidthMeter, new Factory(), Util.getLooper());
    }

    public static SimplePlayer newSimpleInstance(Context context, RenderersFactory renderersFactory, TrackSelector trackSelector, LoadControl loadControl, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, Factory analyticsCollectorFactory) {
        return newSimpleInstance(context, renderersFactory, trackSelector, loadControl, drmSessionManager, analyticsCollectorFactory, Util.getLooper());
    }

    public static SimplePlayer newSimpleInstance(Context context, RenderersFactory renderersFactory, TrackSelector trackSelector, LoadControl loadControl, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, Looper looper) {
        return newSimpleInstance(context, renderersFactory, trackSelector, loadControl, drmSessionManager, new Factory(), looper);
    }

    public static SimplePlayer newSimpleInstance(Context context, RenderersFactory renderersFactory, TrackSelector trackSelector, LoadControl loadControl, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, Factory analyticsCollectorFactory, Looper looper) {
        return newSimpleInstance(context, renderersFactory, trackSelector, loadControl, drmSessionManager, getDefaultBandwidthMeter(context), analyticsCollectorFactory, looper);
    }

    public static SimplePlayer newSimpleInstance(Context context, RenderersFactory renderersFactory, TrackSelector trackSelector, LoadControl loadControl, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, BandwidthMeter bandwidthMeter, Factory analyticsCollectorFactory, Looper looper) {
        return new SimplePlayer(context, renderersFactory, trackSelector, loadControl, drmSessionManager, bandwidthMeter, analyticsCollectorFactory, looper);
    }

    public static InlinePlayer newInstance(Context context, Renderer[] renderers, TrackSelector trackSelector) {
        return newInstance(context, renderers, trackSelector, new DefaultLoadControl());
    }

    public static InlinePlayer newInstance(Context context, Renderer[] renderers, TrackSelector trackSelector, LoadControl loadControl) {
        return newInstance(context, renderers, trackSelector, loadControl, Util.getLooper());
    }

    public static InlinePlayer newInstance(Context context, Renderer[] renderers, TrackSelector trackSelector, LoadControl loadControl, Looper looper) {
        return newInstance(renderers, trackSelector, loadControl, getDefaultBandwidthMeter(context), looper);
    }

    public static InlinePlayer newInstance(Renderer[] renderers, TrackSelector trackSelector, LoadControl loadControl, BandwidthMeter bandwidthMeter, Looper looper) {
        return new PlayerImpl(renderers, trackSelector, loadControl, bandwidthMeter, Clock.DEFAULT, looper);
    }

    private static synchronized BandwidthMeter getDefaultBandwidthMeter(Context context) {
        if (singletonBandwidthMeter == null) {
            singletonBandwidthMeter = (new Builder(context)).build();
        }
        return singletonBandwidthMeter;
    }
}
