package com.zj.playerLib.source;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.CheckResult;
import androidx.annotation.Nullable;
import com.zj.playerLib.C;
import com.zj.playerLib.Format;
import com.zj.playerLib.source.MediaSource.MediaPeriodId;
import com.zj.playerLib.upstream.DataSpec;
import com.zj.playerLib.util.Assertions;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public interface MediaSourceEventListener {
    void onMediaPeriodCreated(int var1, MediaPeriodId var2);

    void onMediaPeriodReleased(int var1, MediaPeriodId var2);

    void onLoadStarted(int var1, @Nullable MediaPeriodId var2, LoadEventInfo var3, MediaLoadData var4);

    void onLoadCompleted(int var1, @Nullable MediaPeriodId var2, LoadEventInfo var3, MediaLoadData var4);

    void onLoadCanceled(int var1, @Nullable MediaPeriodId var2, LoadEventInfo var3, MediaLoadData var4);

    void onLoadError(int var1, @Nullable MediaPeriodId var2, LoadEventInfo var3, MediaLoadData var4, IOException var5, boolean var6);

    void onReadingStarted(int var1, MediaPeriodId var2);

    void onUpstreamDiscarded(int var1, MediaPeriodId var2, MediaLoadData var3);

    void onDownstreamFormatChanged(int var1, @Nullable MediaPeriodId var2, MediaLoadData var3);

    final class EventDispatcher {
        public final int windowIndex;
        @Nullable
        public final MediaPeriodId mediaPeriodId;
        private final CopyOnWriteArrayList<ListenerAndHandler> listenerAndHandlers;
        private final long mediaTimeOffsetMs;

        public EventDispatcher() {
            this(new CopyOnWriteArrayList(), 0, null, 0L);
        }

        private EventDispatcher(CopyOnWriteArrayList<ListenerAndHandler> listenerAndHandlers, int windowIndex, @Nullable MediaPeriodId mediaPeriodId, long mediaTimeOffsetMs) {
            this.listenerAndHandlers = listenerAndHandlers;
            this.windowIndex = windowIndex;
            this.mediaPeriodId = mediaPeriodId;
            this.mediaTimeOffsetMs = mediaTimeOffsetMs;
        }

        @CheckResult
        public MediaSourceEventListener.EventDispatcher withParameters(int windowIndex, @Nullable MediaPeriodId mediaPeriodId, long mediaTimeOffsetMs) {
            return new EventDispatcher(this.listenerAndHandlers, windowIndex, mediaPeriodId, mediaTimeOffsetMs);
        }

        public void addEventListener(Handler handler, MediaSourceEventListener eventListener) {
            Assertions.checkArgument(handler != null && eventListener != null);
            this.listenerAndHandlers.add(new ListenerAndHandler(handler, eventListener));
        }

        public void removeEventListener(MediaSourceEventListener eventListener) {
            Iterator var2 = this.listenerAndHandlers.iterator();

            while(var2.hasNext()) {
                ListenerAndHandler listenerAndHandler = (ListenerAndHandler)var2.next();
                if (listenerAndHandler.listener == eventListener) {
                    this.listenerAndHandlers.remove(listenerAndHandler);
                }
            }

        }

        public void mediaPeriodCreated() {
            MediaPeriodId mediaPeriodId = Assertions.checkNotNull(this.mediaPeriodId);
            Iterator var2 = this.listenerAndHandlers.iterator();

            while(var2.hasNext()) {
                ListenerAndHandler listenerAndHandler = (ListenerAndHandler)var2.next();
                MediaSourceEventListener listener = listenerAndHandler.listener;
                this.postOrRun(listenerAndHandler.handler, () -> {
                    listener.onMediaPeriodCreated(this.windowIndex, mediaPeriodId);
                });
            }

        }

        public void mediaPeriodReleased() {
            MediaPeriodId mediaPeriodId = Assertions.checkNotNull(this.mediaPeriodId);
            Iterator var2 = this.listenerAndHandlers.iterator();

            while(var2.hasNext()) {
                ListenerAndHandler listenerAndHandler = (ListenerAndHandler)var2.next();
                MediaSourceEventListener listener = listenerAndHandler.listener;
                this.postOrRun(listenerAndHandler.handler, () -> {
                    listener.onMediaPeriodReleased(this.windowIndex, mediaPeriodId);
                });
            }

        }

        public void loadStarted(DataSpec dataSpec, int dataType, long elapsedRealtimeMs) {
            this.loadStarted(dataSpec, dataType, -1, null, 0, null, -Long.MAX_VALUE, -Long.MAX_VALUE, elapsedRealtimeMs);
        }

        public void loadStarted(DataSpec dataSpec, int dataType, int trackType, @Nullable Format trackFormat, int trackSelectionReason, @Nullable Object trackSelectionData, long mediaStartTimeUs, long mediaEndTimeUs, long elapsedRealtimeMs) {
            this.loadStarted(new LoadEventInfo(dataSpec, dataSpec.uri, Collections.emptyMap(), elapsedRealtimeMs, 0L, 0L), new MediaLoadData(dataType, trackType, trackFormat, trackSelectionReason, trackSelectionData, this.adjustMediaTime(mediaStartTimeUs), this.adjustMediaTime(mediaEndTimeUs)));
        }

        public void loadStarted(LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
            Iterator var3 = this.listenerAndHandlers.iterator();

            while(var3.hasNext()) {
                ListenerAndHandler listenerAndHandler = (ListenerAndHandler)var3.next();
                MediaSourceEventListener listener = listenerAndHandler.listener;
                this.postOrRun(listenerAndHandler.handler, () -> {
                    listener.onLoadStarted(this.windowIndex, this.mediaPeriodId, loadEventInfo, mediaLoadData);
                });
            }

        }

        public void loadCompleted(DataSpec dataSpec, Uri uri, Map<String, List<String>> responseHeaders, int dataType, long elapsedRealtimeMs, long loadDurationMs, long bytesLoaded) {
            this.loadCompleted(dataSpec, uri, responseHeaders, dataType, -1, null, 0, null, -Long.MAX_VALUE, -Long.MAX_VALUE, elapsedRealtimeMs, loadDurationMs, bytesLoaded);
        }

        public void loadCompleted(DataSpec dataSpec, Uri uri, Map<String, List<String>> responseHeaders, int dataType, int trackType, @Nullable Format trackFormat, int trackSelectionReason, @Nullable Object trackSelectionData, long mediaStartTimeUs, long mediaEndTimeUs, long elapsedRealtimeMs, long loadDurationMs, long bytesLoaded) {
            this.loadCompleted(new LoadEventInfo(dataSpec, uri, responseHeaders, elapsedRealtimeMs, loadDurationMs, bytesLoaded), new MediaLoadData(dataType, trackType, trackFormat, trackSelectionReason, trackSelectionData, this.adjustMediaTime(mediaStartTimeUs), this.adjustMediaTime(mediaEndTimeUs)));
        }

        public void loadCompleted(LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
            Iterator var3 = this.listenerAndHandlers.iterator();

            while(var3.hasNext()) {
                ListenerAndHandler listenerAndHandler = (ListenerAndHandler)var3.next();
                MediaSourceEventListener listener = listenerAndHandler.listener;
                this.postOrRun(listenerAndHandler.handler, () -> {
                    listener.onLoadCompleted(this.windowIndex, this.mediaPeriodId, loadEventInfo, mediaLoadData);
                });
            }

        }

        public void loadCanceled(DataSpec dataSpec, Uri uri, Map<String, List<String>> responseHeaders, int dataType, long elapsedRealtimeMs, long loadDurationMs, long bytesLoaded) {
            this.loadCanceled(dataSpec, uri, responseHeaders, dataType, -1, null, 0, null, -Long.MAX_VALUE, -Long.MAX_VALUE, elapsedRealtimeMs, loadDurationMs, bytesLoaded);
        }

        public void loadCanceled(DataSpec dataSpec, Uri uri, Map<String, List<String>> responseHeaders, int dataType, int trackType, @Nullable Format trackFormat, int trackSelectionReason, @Nullable Object trackSelectionData, long mediaStartTimeUs, long mediaEndTimeUs, long elapsedRealtimeMs, long loadDurationMs, long bytesLoaded) {
            this.loadCanceled(new LoadEventInfo(dataSpec, uri, responseHeaders, elapsedRealtimeMs, loadDurationMs, bytesLoaded), new MediaLoadData(dataType, trackType, trackFormat, trackSelectionReason, trackSelectionData, this.adjustMediaTime(mediaStartTimeUs), this.adjustMediaTime(mediaEndTimeUs)));
        }

        public void loadCanceled(LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
            Iterator var3 = this.listenerAndHandlers.iterator();

            while(var3.hasNext()) {
                ListenerAndHandler listenerAndHandler = (ListenerAndHandler)var3.next();
                MediaSourceEventListener listener = listenerAndHandler.listener;
                this.postOrRun(listenerAndHandler.handler, () -> {
                    listener.onLoadCanceled(this.windowIndex, this.mediaPeriodId, loadEventInfo, mediaLoadData);
                });
            }

        }

        public void loadError(DataSpec dataSpec, Uri uri, Map<String, List<String>> responseHeaders, int dataType, long elapsedRealtimeMs, long loadDurationMs, long bytesLoaded, IOException error, boolean wasCanceled) {
            this.loadError(dataSpec, uri, responseHeaders, dataType, -1, null, 0, null, -Long.MAX_VALUE, -Long.MAX_VALUE, elapsedRealtimeMs, loadDurationMs, bytesLoaded, error, wasCanceled);
        }

        public void loadError(DataSpec dataSpec, Uri uri, Map<String, List<String>> responseHeaders, int dataType, int trackType, @Nullable Format trackFormat, int trackSelectionReason, @Nullable Object trackSelectionData, long mediaStartTimeUs, long mediaEndTimeUs, long elapsedRealtimeMs, long loadDurationMs, long bytesLoaded, IOException error, boolean wasCanceled) {
            this.loadError(new LoadEventInfo(dataSpec, uri, responseHeaders, elapsedRealtimeMs, loadDurationMs, bytesLoaded), new MediaLoadData(dataType, trackType, trackFormat, trackSelectionReason, trackSelectionData, this.adjustMediaTime(mediaStartTimeUs), this.adjustMediaTime(mediaEndTimeUs)), error, wasCanceled);
        }

        public void loadError(LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData, IOException error, boolean wasCanceled) {
            Iterator var5 = this.listenerAndHandlers.iterator();

            while(var5.hasNext()) {
                ListenerAndHandler listenerAndHandler = (ListenerAndHandler)var5.next();
                MediaSourceEventListener listener = listenerAndHandler.listener;
                this.postOrRun(listenerAndHandler.handler, () -> {
                    listener.onLoadError(this.windowIndex, this.mediaPeriodId, loadEventInfo, mediaLoadData, error, wasCanceled);
                });
            }

        }

        public void readingStarted() {
            MediaPeriodId mediaPeriodId = Assertions.checkNotNull(this.mediaPeriodId);
            Iterator var2 = this.listenerAndHandlers.iterator();

            while(var2.hasNext()) {
                ListenerAndHandler listenerAndHandler = (ListenerAndHandler)var2.next();
                MediaSourceEventListener listener = listenerAndHandler.listener;
                this.postOrRun(listenerAndHandler.handler, () -> {
                    listener.onReadingStarted(this.windowIndex, mediaPeriodId);
                });
            }

        }

        public void upstreamDiscarded(int trackType, long mediaStartTimeUs, long mediaEndTimeUs) {
            this.upstreamDiscarded(new MediaLoadData(1, trackType, null, 3, null, this.adjustMediaTime(mediaStartTimeUs), this.adjustMediaTime(mediaEndTimeUs)));
        }

        public void upstreamDiscarded(MediaLoadData mediaLoadData) {
            MediaPeriodId mediaPeriodId = Assertions.checkNotNull(this.mediaPeriodId);
            Iterator var3 = this.listenerAndHandlers.iterator();

            while(var3.hasNext()) {
                ListenerAndHandler listenerAndHandler = (ListenerAndHandler)var3.next();
                MediaSourceEventListener listener = listenerAndHandler.listener;
                this.postOrRun(listenerAndHandler.handler, () -> {
                    listener.onUpstreamDiscarded(this.windowIndex, mediaPeriodId, mediaLoadData);
                });
            }

        }

        public void downstreamFormatChanged(int trackType, @Nullable Format trackFormat, int trackSelectionReason, @Nullable Object trackSelectionData, long mediaTimeUs) {
            this.downstreamFormatChanged(new MediaLoadData(1, trackType, trackFormat, trackSelectionReason, trackSelectionData, this.adjustMediaTime(mediaTimeUs), -Long.MAX_VALUE));
        }

        public void downstreamFormatChanged(MediaLoadData mediaLoadData) {
            Iterator var2 = this.listenerAndHandlers.iterator();

            while(var2.hasNext()) {
                ListenerAndHandler listenerAndHandler = (ListenerAndHandler)var2.next();
                MediaSourceEventListener listener = listenerAndHandler.listener;
                this.postOrRun(listenerAndHandler.handler, () -> {
                    listener.onDownstreamFormatChanged(this.windowIndex, this.mediaPeriodId, mediaLoadData);
                });
            }

        }

        private long adjustMediaTime(long mediaTimeUs) {
            long mediaTimeMs = C.usToMs(mediaTimeUs);
            return mediaTimeMs == -Long.MAX_VALUE ? -Long.MAX_VALUE : this.mediaTimeOffsetMs + mediaTimeMs;
        }

        private void postOrRun(Handler handler, Runnable runnable) {
            if (handler.getLooper() == Looper.myLooper()) {
                runnable.run();
            } else {
                handler.post(runnable);
            }

        }

        private static final class ListenerAndHandler {
            public final Handler handler;
            public final MediaSourceEventListener listener;

            public ListenerAndHandler(Handler handler, MediaSourceEventListener listener) {
                this.handler = handler;
                this.listener = listener;
            }
        }
    }

    final class MediaLoadData {
        public final int dataType;
        public final int trackType;
        @Nullable
        public final Format trackFormat;
        public final int trackSelectionReason;
        @Nullable
        public final Object trackSelectionData;
        public final long mediaStartTimeMs;
        public final long mediaEndTimeMs;

        public MediaLoadData(int dataType, int trackType, @Nullable Format trackFormat, int trackSelectionReason, @Nullable Object trackSelectionData, long mediaStartTimeMs, long mediaEndTimeMs) {
            this.dataType = dataType;
            this.trackType = trackType;
            this.trackFormat = trackFormat;
            this.trackSelectionReason = trackSelectionReason;
            this.trackSelectionData = trackSelectionData;
            this.mediaStartTimeMs = mediaStartTimeMs;
            this.mediaEndTimeMs = mediaEndTimeMs;
        }
    }

    final class LoadEventInfo {
        public final DataSpec dataSpec;
        public final Uri uri;
        public final Map<String, List<String>> responseHeaders;
        public final long elapsedRealtimeMs;
        public final long loadDurationMs;
        public final long bytesLoaded;

        public LoadEventInfo(DataSpec dataSpec, Uri uri, Map<String, List<String>> responseHeaders, long elapsedRealtimeMs, long loadDurationMs, long bytesLoaded) {
            this.dataSpec = dataSpec;
            this.uri = uri;
            this.responseHeaders = responseHeaders;
            this.elapsedRealtimeMs = elapsedRealtimeMs;
            this.loadDurationMs = loadDurationMs;
            this.bytesLoaded = bytesLoaded;
        }
    }
}
