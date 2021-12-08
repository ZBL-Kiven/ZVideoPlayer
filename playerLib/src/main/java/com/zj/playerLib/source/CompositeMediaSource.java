package com.zj.playerLib.source;

import android.os.Handler;

import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;

import com.zj.playerLib.InlinePlayer;
import com.zj.playerLib.Timeline;
import com.zj.playerLib.upstream.TransferListener;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.Util;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;

public abstract class CompositeMediaSource<T> extends BaseMediaSource {
    private final HashMap<T, MediaSourceAndListener> childSources = new HashMap();
    @Nullable
    private InlinePlayer player;
    @Nullable
    private Handler eventHandler;
    @Nullable
    private TransferListener mediaTransferListener;

    protected CompositeMediaSource() {
    }

    @CallSuper
    public void prepareSourceInternal(InlinePlayer player, boolean isTopLevelSource, @Nullable TransferListener mediaTransferListener) {
        this.player = player;
        this.mediaTransferListener = mediaTransferListener;
        this.eventHandler = new Handler();
    }

    @CallSuper
    public void maybeThrowSourceInfoRefreshError() throws IOException {
        Iterator var1 = this.childSources.values().iterator();

        while(var1.hasNext()) {
            MediaSourceAndListener childSource = (MediaSourceAndListener)var1.next();
            childSource.mediaSource.maybeThrowSourceInfoRefreshError();
        }

    }

    @CallSuper
    public void releaseSourceInternal() {
        Iterator var1 = this.childSources.values().iterator();

        while(var1.hasNext()) {
            MediaSourceAndListener childSource = (MediaSourceAndListener)var1.next();
            childSource.mediaSource.releaseSource(childSource.listener);
            childSource.mediaSource.removeEventListener(childSource.eventListener);
        }

        this.childSources.clear();
        this.player = null;
    }

    protected abstract void onChildSourceInfoRefreshed(T var1, MediaSource var2, Timeline var3, @Nullable Object var4);

    protected final void prepareChildSource(T id, MediaSource mediaSource) {
        Assertions.checkArgument(!this.childSources.containsKey(id));
        SourceInfoRefreshListener sourceListener = (source, timeline, manifest) -> {
            this.onChildSourceInfoRefreshed(id, source, timeline, manifest);
        };
        MediaSourceEventListener eventListener = new ForwardingEventListener(id);
        this.childSources.put(id, new MediaSourceAndListener(mediaSource, sourceListener, eventListener));
        mediaSource.addEventListener(Assertions.checkNotNull(this.eventHandler), eventListener);
        mediaSource.prepareSource(Assertions.checkNotNull(this.player), false, sourceListener, this.mediaTransferListener);
    }

    protected final void releaseChildSource(T id) {
        MediaSourceAndListener removedChild = Assertions.checkNotNull(this.childSources.remove(id));
        removedChild.mediaSource.releaseSource(removedChild.listener);
        removedChild.mediaSource.removeEventListener(removedChild.eventListener);
    }

    protected int getWindowIndexForChildWindowIndex(T id, int windowIndex) {
        return windowIndex;
    }

    @Nullable
    protected MediaPeriodId getMediaPeriodIdForChildMediaPeriodId(T id, MediaPeriodId mediaPeriodId) {
        return mediaPeriodId;
    }

    protected long getMediaTimeForChildMediaTime(@Nullable T id, long mediaTimeMs) {
        return mediaTimeMs;
    }

    private final class ForwardingEventListener implements MediaSourceEventListener {
        private final T id;
        private EventDispatcher eventDispatcher = CompositeMediaSource.this.createEventDispatcher(null);

        public ForwardingEventListener(T id) {
            this.id = id;
        }

        public void onMediaPeriodCreated(int windowIndex, MediaPeriodId mediaPeriodId) {
            if (this.maybeUpdateEventDispatcher(windowIndex, mediaPeriodId)) {
                this.eventDispatcher.mediaPeriodCreated();
            }

        }

        public void onMediaPeriodReleased(int windowIndex, MediaPeriodId mediaPeriodId) {
            if (this.maybeUpdateEventDispatcher(windowIndex, mediaPeriodId)) {
                this.eventDispatcher.mediaPeriodReleased();
            }

        }

        public void onLoadStarted(int windowIndex, @Nullable MediaPeriodId mediaPeriodId, LoadEventInfo loadEventData, MediaLoadData mediaLoadData) {
            if (this.maybeUpdateEventDispatcher(windowIndex, mediaPeriodId)) {
                this.eventDispatcher.loadStarted(loadEventData, this.maybeUpdateMediaLoadData(mediaLoadData));
            }

        }

        public void onLoadCompleted(int windowIndex, @Nullable MediaPeriodId mediaPeriodId, LoadEventInfo loadEventData, MediaLoadData mediaLoadData) {
            if (this.maybeUpdateEventDispatcher(windowIndex, mediaPeriodId)) {
                this.eventDispatcher.loadCompleted(loadEventData, this.maybeUpdateMediaLoadData(mediaLoadData));
            }

        }

        public void onLoadCanceled(int windowIndex, @Nullable MediaPeriodId mediaPeriodId, LoadEventInfo loadEventData, MediaLoadData mediaLoadData) {
            if (this.maybeUpdateEventDispatcher(windowIndex, mediaPeriodId)) {
                this.eventDispatcher.loadCanceled(loadEventData, this.maybeUpdateMediaLoadData(mediaLoadData));
            }

        }

        public void onLoadError(int windowIndex, @Nullable MediaPeriodId mediaPeriodId, LoadEventInfo loadEventData, MediaLoadData mediaLoadData, IOException error, boolean wasCanceled) {
            if (this.maybeUpdateEventDispatcher(windowIndex, mediaPeriodId)) {
                this.eventDispatcher.loadError(loadEventData, this.maybeUpdateMediaLoadData(mediaLoadData), error, wasCanceled);
            }

        }

        public void onReadingStarted(int windowIndex, MediaPeriodId mediaPeriodId) {
            if (this.maybeUpdateEventDispatcher(windowIndex, mediaPeriodId)) {
                this.eventDispatcher.readingStarted();
            }

        }

        public void onUpstreamDiscarded(int windowIndex, @Nullable MediaPeriodId mediaPeriodId, MediaLoadData mediaLoadData) {
            if (this.maybeUpdateEventDispatcher(windowIndex, mediaPeriodId)) {
                this.eventDispatcher.upstreamDiscarded(this.maybeUpdateMediaLoadData(mediaLoadData));
            }

        }

        public void onDownstreamFormatChanged(int windowIndex, @Nullable MediaPeriodId mediaPeriodId, MediaLoadData mediaLoadData) {
            if (this.maybeUpdateEventDispatcher(windowIndex, mediaPeriodId)) {
                this.eventDispatcher.downstreamFormatChanged(this.maybeUpdateMediaLoadData(mediaLoadData));
            }

        }

        private boolean maybeUpdateEventDispatcher(int childWindowIndex, @Nullable MediaPeriodId childMediaPeriodId) {
            MediaPeriodId mediaPeriodId = null;
            if (childMediaPeriodId != null) {
                mediaPeriodId = CompositeMediaSource.this.getMediaPeriodIdForChildMediaPeriodId(this.id, childMediaPeriodId);
                if (mediaPeriodId == null) {
                    return false;
                }
            }

            int windowIndex = CompositeMediaSource.this.getWindowIndexForChildWindowIndex(this.id, childWindowIndex);
            if (this.eventDispatcher.windowIndex != windowIndex || !Util.areEqual(this.eventDispatcher.mediaPeriodId, mediaPeriodId)) {
                this.eventDispatcher = CompositeMediaSource.this.createEventDispatcher(windowIndex, mediaPeriodId, 0L);
            }

            return true;
        }

        private MediaLoadData maybeUpdateMediaLoadData(MediaLoadData mediaLoadData) {
            long mediaStartTimeMs = CompositeMediaSource.this.getMediaTimeForChildMediaTime(this.id, mediaLoadData.mediaStartTimeMs);
            long mediaEndTimeMs = CompositeMediaSource.this.getMediaTimeForChildMediaTime(this.id, mediaLoadData.mediaEndTimeMs);
            return mediaStartTimeMs == mediaLoadData.mediaStartTimeMs && mediaEndTimeMs == mediaLoadData.mediaEndTimeMs ? mediaLoadData : new MediaLoadData(mediaLoadData.dataType, mediaLoadData.trackType, mediaLoadData.trackFormat, mediaLoadData.trackSelectionReason, mediaLoadData.trackSelectionData, mediaStartTimeMs, mediaEndTimeMs);
        }
    }

    private static final class MediaSourceAndListener {
        public final MediaSource mediaSource;
        public final SourceInfoRefreshListener listener;
        public final MediaSourceEventListener eventListener;

        public MediaSourceAndListener(MediaSource mediaSource, SourceInfoRefreshListener listener, MediaSourceEventListener eventListener) {
            this.mediaSource = mediaSource;
            this.listener = listener;
            this.eventListener = eventListener;
        }
    }
}
