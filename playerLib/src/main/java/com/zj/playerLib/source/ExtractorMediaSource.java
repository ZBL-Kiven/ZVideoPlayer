package com.zj.playerLib.source;

import android.net.Uri;
import android.os.Handler;
import androidx.annotation.Nullable;
import com.zj.playerLib.InlinePlayer;
import com.zj.playerLib.extractor.DefaultExtractorsFactory;
import com.zj.playerLib.extractor.ExtractorsFactory;
import com.zj.playerLib.source.ExtractorMediaPeriod.Listener;
import com.zj.playerLib.source.ads.AdsMediaSource.MediaSourceFactory;
import com.zj.playerLib.upstream.Allocator;
import com.zj.playerLib.upstream.DataSource;
import com.zj.playerLib.upstream.DefaultLoadErrorHandlingPolicy;
import com.zj.playerLib.upstream.LoadErrorHandlingPolicy;
import com.zj.playerLib.upstream.TransferListener;
import com.zj.playerLib.util.Assertions;
import java.io.IOException;

public final class ExtractorMediaSource extends BaseMediaSource implements Listener {
    public static final int DEFAULT_LOADING_CHECK_INTERVAL_BYTES = 1048576;
    private final Uri uri;
    private final DataSource.Factory dataSourceFactory;
    private final ExtractorsFactory extractorsFactory;
    private final LoadErrorHandlingPolicy loadableLoadErrorHandlingPolicy;
    private final String customCacheKey;
    private final int continueLoadingCheckIntervalBytes;
    @Nullable
    private final Object tag;
    private long timelineDurationUs;
    private boolean timelineIsSeekable;
    @Nullable
    private TransferListener transferListener;

    /** @deprecated */
    @Deprecated
    public ExtractorMediaSource(Uri uri, DataSource.Factory dataSourceFactory, ExtractorsFactory extractorsFactory, Handler eventHandler, EventListener eventListener) {
        this(uri, dataSourceFactory, extractorsFactory, eventHandler, eventListener, null);
    }

    /** @deprecated */
    @Deprecated
    public ExtractorMediaSource(Uri uri, DataSource.Factory dataSourceFactory, ExtractorsFactory extractorsFactory, Handler eventHandler, EventListener eventListener, String customCacheKey) {
        this(uri, dataSourceFactory, extractorsFactory, eventHandler, eventListener, customCacheKey, 1048576);
    }

    /** @deprecated */
    @Deprecated
    public ExtractorMediaSource(Uri uri, DataSource.Factory dataSourceFactory, ExtractorsFactory extractorsFactory, Handler eventHandler, EventListener eventListener, String customCacheKey, int continueLoadingCheckIntervalBytes) {
        this(uri, dataSourceFactory, extractorsFactory, new DefaultLoadErrorHandlingPolicy(), customCacheKey, continueLoadingCheckIntervalBytes, null);
        if (eventListener != null && eventHandler != null) {
            this.addEventListener(eventHandler, new EventListenerWrapper(eventListener));
        }

    }

    private ExtractorMediaSource(Uri uri, DataSource.Factory dataSourceFactory, ExtractorsFactory extractorsFactory, LoadErrorHandlingPolicy loadableLoadErrorHandlingPolicy, @Nullable String customCacheKey, int continueLoadingCheckIntervalBytes, @Nullable Object tag) {
        this.uri = uri;
        this.dataSourceFactory = dataSourceFactory;
        this.extractorsFactory = extractorsFactory;
        this.loadableLoadErrorHandlingPolicy = loadableLoadErrorHandlingPolicy;
        this.customCacheKey = customCacheKey;
        this.continueLoadingCheckIntervalBytes = continueLoadingCheckIntervalBytes;
        this.timelineDurationUs = -Long.MAX_VALUE;
        this.tag = tag;
    }

    @Nullable
    public Object getTag() {
        return this.tag;
    }

    public void prepareSourceInternal(InlinePlayer player, boolean isTopLevelSource, @Nullable TransferListener mediaTransferListener) {
        this.transferListener = mediaTransferListener;
        this.notifySourceInfoRefreshed(this.timelineDurationUs, false);
    }

    public void maybeThrowSourceInfoRefreshError() throws IOException {
    }

    public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator) {
        DataSource dataSource = this.dataSourceFactory.createDataSource();
        if (this.transferListener != null) {
            dataSource.addTransferListener(this.transferListener);
        }

        return new ExtractorMediaPeriod(this.uri, dataSource, this.extractorsFactory.createExtractors(), this.loadableLoadErrorHandlingPolicy, this.createEventDispatcher(id), this, allocator, this.customCacheKey, this.continueLoadingCheckIntervalBytes);
    }

    public void releasePeriod(MediaPeriod mediaPeriod) {
        ((ExtractorMediaPeriod)mediaPeriod).release();
    }

    public void releaseSourceInternal() {
    }

    public void onSourceInfoRefreshed(long durationUs, boolean isSeekable) {
        durationUs = durationUs == -Long.MAX_VALUE ? this.timelineDurationUs : durationUs;
        if (this.timelineDurationUs != durationUs || this.timelineIsSeekable != isSeekable) {
            this.notifySourceInfoRefreshed(durationUs, isSeekable);
        }
    }

    private void notifySourceInfoRefreshed(long durationUs, boolean isSeekable) {
        this.timelineDurationUs = durationUs;
        this.timelineIsSeekable = isSeekable;
        this.refreshSourceInfo(new SinglePeriodTimeline(this.timelineDurationUs, this.timelineIsSeekable, false, this.tag), null);
    }

    /** @deprecated */
    @Deprecated
    private static final class EventListenerWrapper extends DefaultMediaSourceEventListener {
        private final EventListener eventListener;

        public EventListenerWrapper(EventListener eventListener) {
            this.eventListener = Assertions.checkNotNull(eventListener);
        }

        public void onLoadError(int windowIndex, @Nullable MediaPeriodId mediaPeriodId, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData, IOException error, boolean wasCanceled) {
            this.eventListener.onLoadError(error);
        }
    }

    public static final class Factory implements MediaSourceFactory {
        private final DataSource.Factory dataSourceFactory;
        @Nullable
        private ExtractorsFactory extractorsFactory;
        @Nullable
        private String customCacheKey;
        @Nullable
        private Object tag;
        private LoadErrorHandlingPolicy loadErrorHandlingPolicy;
        private int continueLoadingCheckIntervalBytes;
        private boolean isCreateCalled;

        public Factory(DataSource.Factory dataSourceFactory) {
            this.dataSourceFactory = dataSourceFactory;
            this.loadErrorHandlingPolicy = new DefaultLoadErrorHandlingPolicy();
            this.continueLoadingCheckIntervalBytes = 1048576;
        }

        public Factory setExtractorsFactory(ExtractorsFactory extractorsFactory) {
            Assertions.checkState(!this.isCreateCalled);
            this.extractorsFactory = extractorsFactory;
            return this;
        }

        public Factory setCustomCacheKey(String customCacheKey) {
            Assertions.checkState(!this.isCreateCalled);
            this.customCacheKey = customCacheKey;
            return this;
        }

        public Factory setTag(Object tag) {
            Assertions.checkState(!this.isCreateCalled);
            this.tag = tag;
            return this;
        }

        /** @deprecated */
        @Deprecated
        public Factory setMinLoadableRetryCount(int minLoadableRetryCount) {
            return this.setLoadErrorHandlingPolicy(new DefaultLoadErrorHandlingPolicy(minLoadableRetryCount));
        }

        public Factory setLoadErrorHandlingPolicy(LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
            Assertions.checkState(!this.isCreateCalled);
            this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
            return this;
        }

        public Factory setContinueLoadingCheckIntervalBytes(int continueLoadingCheckIntervalBytes) {
            Assertions.checkState(!this.isCreateCalled);
            this.continueLoadingCheckIntervalBytes = continueLoadingCheckIntervalBytes;
            return this;
        }

        public ExtractorMediaSource createMediaSource(Uri uri) {
            this.isCreateCalled = true;
            if (this.extractorsFactory == null) {
                this.extractorsFactory = new DefaultExtractorsFactory();
            }

            return new ExtractorMediaSource(uri, this.dataSourceFactory, this.extractorsFactory, this.loadErrorHandlingPolicy, this.customCacheKey, this.continueLoadingCheckIntervalBytes, this.tag);
        }

        /** @deprecated */
        @Deprecated
        public ExtractorMediaSource createMediaSource(Uri uri, @Nullable Handler eventHandler, @Nullable MediaSourceEventListener eventListener) {
            ExtractorMediaSource mediaSource = this.createMediaSource(uri);
            if (eventHandler != null && eventListener != null) {
                mediaSource.addEventListener(eventHandler, eventListener);
            }

            return mediaSource;
        }

        public int[] getSupportedTypes() {
            return new int[]{3};
        }
    }

    /** @deprecated */
    @Deprecated
    public interface EventListener {
        void onLoadError(IOException var1);
    }
}
