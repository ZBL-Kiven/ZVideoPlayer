package com.zj.playerLib.source.ads;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import androidx.annotation.Nullable;
import com.zj.playerLib.InlinePlayer;
import com.zj.playerLib.Timeline;
import com.zj.playerLib.Timeline.Period;
import com.zj.playerLib.source.CompositeMediaSource;
import com.zj.playerLib.source.DeferredMediaPeriod;
import com.zj.playerLib.source.MediaPeriod;
import com.zj.playerLib.source.MediaSource;
import com.zj.playerLib.source.DeferredMediaPeriod.PrepareErrorListener;
import com.zj.playerLib.source.MediaSource.MediaPeriodId;
import com.zj.playerLib.upstream.Allocator;
import com.zj.playerLib.upstream.DataSpec;
import com.zj.playerLib.upstream.TransferListener;
import com.zj.playerLib.upstream.DataSource.Factory;
import com.zj.playerLib.util.Assertions;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AdsMediaSource extends CompositeMediaSource<MediaPeriodId> {
    private static final MediaPeriodId DUMMY_CONTENT_MEDIA_PERIOD_ID = new MediaPeriodId(new Object());
    private final MediaSource contentMediaSource;
    private final MediaSourceFactory adMediaSourceFactory;
    private final AdsLoader adsLoader;
    private final ViewGroup adUiViewGroup;
    @Nullable
    private final Handler eventHandler;
    @Nullable
    private final AdsMediaSource.EventListener eventListener;
    private final Handler mainHandler;
    private final Map<MediaSource, List<DeferredMediaPeriod>> deferredMediaPeriodByAdMediaSource;
    private final Period period;
    private ComponentListener componentListener;
    private Timeline contentTimeline;
    private Object contentManifest;
    private AdPlaybackState adPlaybackState;
    private MediaSource[][] adGroupMediaSources;
    private Timeline[][] adGroupTimelines;

    public AdsMediaSource(MediaSource contentMediaSource, Factory dataSourceFactory, AdsLoader adsLoader, ViewGroup adUiViewGroup) {
        this(contentMediaSource, new com.zj.playerLib.source.ExtractorMediaSource.Factory(dataSourceFactory), adsLoader, adUiViewGroup, null, null);
    }

    public AdsMediaSource(MediaSource contentMediaSource, MediaSourceFactory adMediaSourceFactory, AdsLoader adsLoader, ViewGroup adUiViewGroup) {
        this(contentMediaSource, adMediaSourceFactory, adsLoader, adUiViewGroup, null, null);
    }

    /** @deprecated */
    @Deprecated
    public AdsMediaSource(MediaSource contentMediaSource, Factory dataSourceFactory, AdsLoader adsLoader, ViewGroup adUiViewGroup, @Nullable Handler eventHandler, @Nullable AdsMediaSource.EventListener eventListener) {
        this(contentMediaSource, new com.zj.playerLib.source.ExtractorMediaSource.Factory(dataSourceFactory), adsLoader, adUiViewGroup, eventHandler, eventListener);
    }

    /** @deprecated */
    @Deprecated
    public AdsMediaSource(MediaSource contentMediaSource, MediaSourceFactory adMediaSourceFactory, AdsLoader adsLoader, ViewGroup adUiViewGroup, @Nullable Handler eventHandler, @Nullable AdsMediaSource.EventListener eventListener) {
        this.contentMediaSource = contentMediaSource;
        this.adMediaSourceFactory = adMediaSourceFactory;
        this.adsLoader = adsLoader;
        this.adUiViewGroup = adUiViewGroup;
        this.eventHandler = eventHandler;
        this.eventListener = eventListener;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.deferredMediaPeriodByAdMediaSource = new HashMap();
        this.period = new Period();
        this.adGroupMediaSources = new MediaSource[0][];
        this.adGroupTimelines = new Timeline[0][];
        adsLoader.setSupportedContentTypes(adMediaSourceFactory.getSupportedTypes());
    }

    @Nullable
    public Object getTag() {
        return this.contentMediaSource.getTag();
    }

    public void prepareSourceInternal(InlinePlayer player, boolean isTopLevelSource, @Nullable TransferListener mediaTransferListener) {
        super.prepareSourceInternal(player, isTopLevelSource, mediaTransferListener);
        Assertions.checkArgument(isTopLevelSource, "AdsMediaSource must be the top-level source used to prepare the player.");
        ComponentListener componentListener = new ComponentListener();
        this.componentListener = componentListener;
        this.prepareChildSource(DUMMY_CONTENT_MEDIA_PERIOD_ID, this.contentMediaSource);
        this.mainHandler.post(() -> {
            this.adsLoader.attachPlayer(player, componentListener, this.adUiViewGroup);
        });
    }

    public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator) {
        if (this.adPlaybackState.adGroupCount > 0 && id.isAd()) {
            int adGroupIndex = id.adGroupIndex;
            int adIndexInAdGroup = id.adIndexInAdGroup;
            Uri adUri = this.adPlaybackState.adGroups[adGroupIndex].uris[adIndexInAdGroup];
            MediaSource adMediaSource;
            if (this.adGroupMediaSources[adGroupIndex].length <= adIndexInAdGroup) {
                adMediaSource = this.adMediaSourceFactory.createMediaSource(adUri);
                int oldAdCount = this.adGroupMediaSources[adGroupIndex].length;
                if (adIndexInAdGroup >= oldAdCount) {
                    int adCount = adIndexInAdGroup + 1;
                    this.adGroupMediaSources[adGroupIndex] = Arrays.copyOf(this.adGroupMediaSources[adGroupIndex], adCount);
                    this.adGroupTimelines[adGroupIndex] = Arrays.copyOf(this.adGroupTimelines[adGroupIndex], adCount);
                }

                this.adGroupMediaSources[adGroupIndex][adIndexInAdGroup] = adMediaSource;
                this.deferredMediaPeriodByAdMediaSource.put(adMediaSource, new ArrayList());
                this.prepareChildSource(id, adMediaSource);
            }

            adMediaSource = this.adGroupMediaSources[adGroupIndex][adIndexInAdGroup];
            DeferredMediaPeriod deferredMediaPeriod = new DeferredMediaPeriod(adMediaSource, id, allocator);
            deferredMediaPeriod.setPrepareErrorListener(new AdPrepareErrorListener(adUri, adGroupIndex, adIndexInAdGroup));
            List<DeferredMediaPeriod> mediaPeriods = this.deferredMediaPeriodByAdMediaSource.get(adMediaSource);
            if (mediaPeriods == null) {
                Object periodUid = this.adGroupTimelines[adGroupIndex][adIndexInAdGroup].getUidOfPeriod(0);
                MediaPeriodId adSourceMediaPeriodId = new MediaPeriodId(periodUid, id.windowSequenceNumber);
                deferredMediaPeriod.createPeriod(adSourceMediaPeriodId);
            } else {
                mediaPeriods.add(deferredMediaPeriod);
            }

            return deferredMediaPeriod;
        } else {
            DeferredMediaPeriod mediaPeriod = new DeferredMediaPeriod(this.contentMediaSource, id, allocator);
            mediaPeriod.createPeriod(id);
            return mediaPeriod;
        }
    }

    public void releasePeriod(MediaPeriod mediaPeriod) {
        DeferredMediaPeriod deferredMediaPeriod = (DeferredMediaPeriod)mediaPeriod;
        List<DeferredMediaPeriod> mediaPeriods = this.deferredMediaPeriodByAdMediaSource.get(deferredMediaPeriod.mediaSource);
        if (mediaPeriods != null) {
            mediaPeriods.remove(deferredMediaPeriod);
        }

        deferredMediaPeriod.releasePeriod();
    }

    public void releaseSourceInternal() {
        super.releaseSourceInternal();
        this.componentListener.release();
        this.componentListener = null;
        this.deferredMediaPeriodByAdMediaSource.clear();
        this.contentTimeline = null;
        this.contentManifest = null;
        this.adPlaybackState = null;
        this.adGroupMediaSources = new MediaSource[0][];
        this.adGroupTimelines = new Timeline[0][];
        AdsLoader var10001 = this.adsLoader;
        this.mainHandler.post(var10001::detachPlayer);
    }

    protected void onChildSourceInfoRefreshed(MediaPeriodId mediaPeriodId, MediaSource mediaSource, Timeline timeline, @Nullable Object manifest) {
        if (mediaPeriodId.isAd()) {
            int adGroupIndex = mediaPeriodId.adGroupIndex;
            int adIndexInAdGroup = mediaPeriodId.adIndexInAdGroup;
            this.onAdSourceInfoRefreshed(mediaSource, adGroupIndex, adIndexInAdGroup, timeline);
        } else {
            this.onContentSourceInfoRefreshed(timeline, manifest);
        }

    }

    @Nullable
    protected MediaPeriodId getMediaPeriodIdForChildMediaPeriodId(MediaPeriodId childId, MediaPeriodId mediaPeriodId) {
        return childId.isAd() ? childId : mediaPeriodId;
    }

    private void onAdPlaybackState(AdPlaybackState adPlaybackState) {
        if (this.adPlaybackState == null) {
            this.adGroupMediaSources = new MediaSource[adPlaybackState.adGroupCount][];
            Arrays.fill(this.adGroupMediaSources, new MediaSource[0]);
            this.adGroupTimelines = new Timeline[adPlaybackState.adGroupCount][];
            Arrays.fill(this.adGroupTimelines, new Timeline[0]);
        }

        this.adPlaybackState = adPlaybackState;
        this.maybeUpdateSourceInfo();
    }

    private void onContentSourceInfoRefreshed(Timeline timeline, Object manifest) {
        this.contentTimeline = timeline;
        this.contentManifest = manifest;
        this.maybeUpdateSourceInfo();
    }

    private void onAdSourceInfoRefreshed(MediaSource mediaSource, int adGroupIndex, int adIndexInAdGroup, Timeline timeline) {
        Assertions.checkArgument(timeline.getPeriodCount() == 1);
        this.adGroupTimelines[adGroupIndex][adIndexInAdGroup] = timeline;
        List<DeferredMediaPeriod> mediaPeriods = this.deferredMediaPeriodByAdMediaSource.remove(mediaSource);
        if (mediaPeriods != null) {
            Object periodUid = timeline.getUidOfPeriod(0);

            for(int i = 0; i < mediaPeriods.size(); ++i) {
                DeferredMediaPeriod mediaPeriod = mediaPeriods.get(i);
                MediaPeriodId adSourceMediaPeriodId = new MediaPeriodId(periodUid, mediaPeriod.id.windowSequenceNumber);
                mediaPeriod.createPeriod(adSourceMediaPeriodId);
            }
        }

        this.maybeUpdateSourceInfo();
    }

    private void maybeUpdateSourceInfo() {
        if (this.adPlaybackState != null && this.contentTimeline != null) {
            this.adPlaybackState = this.adPlaybackState.withAdDurationsUs(getAdDurations(this.adGroupTimelines, this.period));
            Timeline timeline = this.adPlaybackState.adGroupCount == 0 ? this.contentTimeline : new SinglePeriodAdTimeline(this.contentTimeline, this.adPlaybackState);
            this.refreshSourceInfo(timeline, this.contentManifest);
        }

    }

    private static long[][] getAdDurations(Timeline[][] adTimelines, Period period) {
        long[][] adDurations = new long[adTimelines.length][];

        for(int i = 0; i < adTimelines.length; ++i) {
            adDurations[i] = new long[adTimelines[i].length];

            for(int j = 0; j < adTimelines[i].length; ++j) {
                adDurations[i][j] = adTimelines[i][j] == null ? -Long.MAX_VALUE : adTimelines[i][j].getPeriod(0, period).getDurationUs();
            }
        }

        return adDurations;
    }

    private final class AdPrepareErrorListener implements PrepareErrorListener {
        private final Uri adUri;
        private final int adGroupIndex;
        private final int adIndexInAdGroup;

        public AdPrepareErrorListener(Uri adUri, int adGroupIndex, int adIndexInAdGroup) {
            this.adUri = adUri;
            this.adGroupIndex = adGroupIndex;
            this.adIndexInAdGroup = adIndexInAdGroup;
        }

        public void onPrepareError(MediaPeriodId mediaPeriodId, IOException exception) {
            AdsMediaSource.this.createEventDispatcher(mediaPeriodId).loadError(new DataSpec(this.adUri), this.adUri, Collections.emptyMap(), 6, -1L, 0L, 0L, AdLoadException.createForAd(exception), true);
            AdsMediaSource.this.mainHandler.post(() -> {
                AdsMediaSource.this.adsLoader.handlePrepareError(this.adGroupIndex, this.adIndexInAdGroup, exception);
            });
        }
    }

    private final class ComponentListener implements AdsLoader.EventListener {
        private final Handler playerHandler = new Handler();
        private volatile boolean released;

        public ComponentListener() {
        }

        public void release() {
            this.released = true;
            this.playerHandler.removeCallbacksAndMessages(null);
        }

        public void onAdPlaybackState(AdPlaybackState adPlaybackState) {
            if (!this.released) {
                this.playerHandler.post(() -> {
                    if (!this.released) {
                        AdsMediaSource.this.onAdPlaybackState(adPlaybackState);
                    }
                });
            }
        }

        public void onAdClicked() {
            if (!this.released) {
                if (AdsMediaSource.this.eventHandler != null && AdsMediaSource.this.eventListener != null) {
                    AdsMediaSource.this.eventHandler.post(() -> {
                        if (!this.released) {
                            AdsMediaSource.this.eventListener.onAdClicked();
                        }

                    });
                }

            }
        }

        public void onAdTapped() {
            if (!this.released) {
                if (AdsMediaSource.this.eventHandler != null && AdsMediaSource.this.eventListener != null) {
                    AdsMediaSource.this.eventHandler.post(() -> {
                        if (!this.released) {
                            AdsMediaSource.this.eventListener.onAdTapped();
                        }

                    });
                }

            }
        }

        public void onAdLoadError(AdLoadException error, DataSpec dataSpec) {
            if (!this.released) {
                AdsMediaSource.this.createEventDispatcher(null).loadError(dataSpec, dataSpec.uri, Collections.emptyMap(), 6, -1L, 0L, 0L, error, true);
                if (AdsMediaSource.this.eventHandler != null && AdsMediaSource.this.eventListener != null) {
                    AdsMediaSource.this.eventHandler.post(() -> {
                        if (!this.released) {
                            if (error.type == 3) {
                                AdsMediaSource.this.eventListener.onInternalAdLoadError(error.getRuntimeExceptionForUnexpected());
                            } else {
                                AdsMediaSource.this.eventListener.onAdLoadError(error);
                            }
                        }

                    });
                }

            }
        }
    }

    /** @deprecated */
    @Deprecated
    public interface EventListener {
        void onAdLoadError(IOException var1);

        void onInternalAdLoadError(RuntimeException var1);

        void onAdClicked();

        void onAdTapped();
    }

    public static final class AdLoadException extends IOException {
        public static final int TYPE_AD = 0;
        public static final int TYPE_AD_GROUP = 1;
        public static final int TYPE_ALL_ADS = 2;
        public static final int TYPE_UNEXPECTED = 3;
        public final int type;

        public static AdLoadException createForAd(Exception error) {
            return new AdLoadException(0, error);
        }

        public static AdLoadException createForAdGroup(Exception error, int adGroupIndex) {
            return new AdLoadException(1, new IOException("Failed to load ad group " + adGroupIndex, error));
        }

        public static AdLoadException createForAllAds(Exception error) {
            return new AdLoadException(2, error);
        }

        public static AdLoadException createForUnexpected(RuntimeException error) {
            return new AdLoadException(3, error);
        }

        private AdLoadException(int type, Exception cause) {
            super(cause);
            this.type = type;
        }

        public RuntimeException getRuntimeExceptionForUnexpected() {
            Assertions.checkState(this.type == 3);
            return (RuntimeException)this.getCause();
        }

        @Documented
        @Retention(RetentionPolicy.SOURCE)
        public @interface Type {
        }
    }

    public interface MediaSourceFactory {
        MediaSource createMediaSource(Uri var1);

        int[] getSupportedTypes();
    }
}
