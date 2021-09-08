//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.source;

import android.net.Uri;
import android.os.Handler;

import androidx.annotation.Nullable;

import com.zj.playerLib.Format;
import com.zj.playerLib.InlinePlayer;
import com.zj.playerLib.Timeline;
import com.zj.playerLib.upstream.Allocator;
import com.zj.playerLib.upstream.DataSpec;
import com.zj.playerLib.upstream.DefaultLoadErrorHandlingPolicy;
import com.zj.playerLib.upstream.LoadErrorHandlingPolicy;
import com.zj.playerLib.upstream.TransferListener;
import com.zj.playerLib.util.Assertions;

import java.io.IOException;

public final class SingleSampleMediaSource extends BaseMediaSource {
    private final DataSpec dataSpec;
    private final com.zj.playerLib.upstream.DataSource.Factory dataSourceFactory;
    private final Format format;
    private final long durationUs;
    private final LoadErrorHandlingPolicy loadErrorHandlingPolicy;
    private final boolean treatLoadErrorsAsEndOfStream;
    private final Timeline timeline;
    @Nullable
    private final Object tag;
    @Nullable
    private TransferListener transferListener;

    /** @deprecated */
    @Deprecated
    public SingleSampleMediaSource(Uri uri, com.zj.playerLib.upstream.DataSource.Factory dataSourceFactory, Format format, long durationUs) {
        this(uri, dataSourceFactory, format, durationUs, 3);
    }

    /** @deprecated */
    @Deprecated
    public SingleSampleMediaSource(Uri uri, com.zj.playerLib.upstream.DataSource.Factory dataSourceFactory, Format format, long durationUs, int minLoadableRetryCount) {
        this(uri, dataSourceFactory, format, durationUs, new DefaultLoadErrorHandlingPolicy(minLoadableRetryCount), false, (Object)null);
    }

    /** @deprecated */
    @Deprecated
    public SingleSampleMediaSource(Uri uri, com.zj.playerLib.upstream.DataSource.Factory dataSourceFactory, Format format, long durationUs, int minLoadableRetryCount, Handler eventHandler, EventListener eventListener, int eventSourceId, boolean treatLoadErrorsAsEndOfStream) {
        this(uri, dataSourceFactory, format, durationUs, new DefaultLoadErrorHandlingPolicy(minLoadableRetryCount), treatLoadErrorsAsEndOfStream, (Object)null);
        if (eventHandler != null && eventListener != null) {
            this.addEventListener(eventHandler, new EventListenerWrapper(eventListener, eventSourceId));
        }

    }

    private SingleSampleMediaSource(Uri uri, com.zj.playerLib.upstream.DataSource.Factory dataSourceFactory, Format format, long durationUs, LoadErrorHandlingPolicy loadErrorHandlingPolicy, boolean treatLoadErrorsAsEndOfStream, @Nullable Object tag) {
        this.dataSourceFactory = dataSourceFactory;
        this.format = format;
        this.durationUs = durationUs;
        this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
        this.treatLoadErrorsAsEndOfStream = treatLoadErrorsAsEndOfStream;
        this.tag = tag;
        this.dataSpec = new DataSpec(uri, 3);
        this.timeline = new SinglePeriodTimeline(durationUs, true, false, tag);
    }

    @Nullable
    public Object getTag() {
        return this.tag;
    }

    public void prepareSourceInternal(InlinePlayer player, boolean isTopLevelSource, @Nullable TransferListener mediaTransferListener) {
        this.transferListener = mediaTransferListener;
        this.refreshSourceInfo(this.timeline, (Object)null);
    }

    public void maybeThrowSourceInfoRefreshError() throws IOException {
    }

    public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator) {
        return new SingleSampleMediaPeriod(this.dataSpec, this.dataSourceFactory, this.transferListener, this.format, this.durationUs, this.loadErrorHandlingPolicy, this.createEventDispatcher(id), this.treatLoadErrorsAsEndOfStream);
    }

    public void releasePeriod(MediaPeriod mediaPeriod) {
        ((SingleSampleMediaPeriod)mediaPeriod).release();
    }

    public void releaseSourceInternal() {
    }

    /** @deprecated */
    @Deprecated
    private static final class EventListenerWrapper extends DefaultMediaSourceEventListener {
        private final EventListener eventListener;
        private final int eventSourceId;

        public EventListenerWrapper(EventListener eventListener, int eventSourceId) {
            this.eventListener = (EventListener)Assertions.checkNotNull(eventListener);
            this.eventSourceId = eventSourceId;
        }

        public void onLoadError(int windowIndex, @Nullable MediaPeriodId mediaPeriodId, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData, IOException error, boolean wasCanceled) {
            this.eventListener.onLoadError(this.eventSourceId, error);
        }
    }

    public static final class Factory {
        private final com.zj.playerLib.upstream.DataSource.Factory dataSourceFactory;
        private LoadErrorHandlingPolicy loadErrorHandlingPolicy;
        private boolean treatLoadErrorsAsEndOfStream;
        private boolean isCreateCalled;
        @Nullable
        private Object tag;

        public Factory(com.zj.playerLib.upstream.DataSource.Factory dataSourceFactory) {
            this.dataSourceFactory = (com.zj.playerLib.upstream.DataSource.Factory)Assertions.checkNotNull(dataSourceFactory);
            this.loadErrorHandlingPolicy = new DefaultLoadErrorHandlingPolicy();
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

        public Factory setTreatLoadErrorsAsEndOfStream(boolean treatLoadErrorsAsEndOfStream) {
            Assertions.checkState(!this.isCreateCalled);
            this.treatLoadErrorsAsEndOfStream = treatLoadErrorsAsEndOfStream;
            return this;
        }

        public SingleSampleMediaSource createMediaSource(Uri uri, Format format, long durationUs) {
            this.isCreateCalled = true;
            return new SingleSampleMediaSource(uri, this.dataSourceFactory, format, durationUs, this.loadErrorHandlingPolicy, this.treatLoadErrorsAsEndOfStream, this.tag);
        }

        /** @deprecated */
        @Deprecated
        public SingleSampleMediaSource createMediaSource(Uri uri, Format format, long durationUs, @Nullable Handler eventHandler, @Nullable MediaSourceEventListener eventListener) {
            SingleSampleMediaSource mediaSource = this.createMediaSource(uri, format, durationUs);
            if (eventHandler != null && eventListener != null) {
                mediaSource.addEventListener(eventHandler, eventListener);
            }

            return mediaSource;
        }
    }

    /** @deprecated */
    @Deprecated
    public interface EventListener {
        void onLoadError(int var1, IOException var2);
    }
}
