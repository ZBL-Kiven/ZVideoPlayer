//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.source;

import androidx.annotation.Nullable;

import com.zj.playerLib.C;
import com.zj.playerLib.InlinePlayer;
import com.zj.playerLib.Timeline;
import com.zj.playerLib.Timeline.Window;
import com.zj.playerLib.upstream.Allocator;
import com.zj.playerLib.upstream.TransferListener;
import com.zj.playerLib.util.Assertions;

import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;

public final class ClippingMediaSource extends CompositeMediaSource<Void> {
    private final MediaSource mediaSource;
    private final long startUs;
    private final long endUs;
    private final boolean enableInitialDiscontinuity;
    private final boolean allowDynamicClippingUpdates;
    private final boolean relativeToDefaultPosition;
    private final ArrayList<ClippingMediaPeriod> mediaPeriods;
    private final Window window;
    @Nullable
    private Object manifest;
    private ClippingTimeline clippingTimeline;
    private IllegalClippingException clippingError;
    private long periodStartUs;
    private long periodEndUs;

    public ClippingMediaSource(MediaSource mediaSource, long startPositionUs, long endPositionUs) {
        this(mediaSource, startPositionUs, endPositionUs, true, false, false);
    }

    /** @deprecated */
    @Deprecated
    public ClippingMediaSource(MediaSource mediaSource, long startPositionUs, long endPositionUs, boolean enableInitialDiscontinuity) {
        this(mediaSource, startPositionUs, endPositionUs, enableInitialDiscontinuity, false, false);
    }

    public ClippingMediaSource(MediaSource mediaSource, long durationUs) {
        this(mediaSource, 0L, durationUs, true, false, true);
    }

    public ClippingMediaSource(MediaSource mediaSource, long startPositionUs, long endPositionUs, boolean enableInitialDiscontinuity, boolean allowDynamicClippingUpdates, boolean relativeToDefaultPosition) {
        Assertions.checkArgument(startPositionUs >= 0L);
        this.mediaSource = (MediaSource)Assertions.checkNotNull(mediaSource);
        this.startUs = startPositionUs;
        this.endUs = endPositionUs;
        this.enableInitialDiscontinuity = enableInitialDiscontinuity;
        this.allowDynamicClippingUpdates = allowDynamicClippingUpdates;
        this.relativeToDefaultPosition = relativeToDefaultPosition;
        this.mediaPeriods = new ArrayList<>();
        this.window = new Window();
    }

    @Nullable
    public Object getTag() {
        return this.mediaSource.getTag();
    }

    public void prepareSourceInternal(InlinePlayer player, boolean isTopLevelSource, @Nullable TransferListener mediaTransferListener) {
        super.prepareSourceInternal(player, isTopLevelSource, mediaTransferListener);
        this.prepareChildSource(null, this.mediaSource);
    }

    public void maybeThrowSourceInfoRefreshError() throws IOException {
        if (this.clippingError != null) {
            throw this.clippingError;
        } else {
            super.maybeThrowSourceInfoRefreshError();
        }
    }

    public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator) {
        ClippingMediaPeriod mediaPeriod = new ClippingMediaPeriod(this.mediaSource.createPeriod(id, allocator), this.enableInitialDiscontinuity, this.periodStartUs, this.periodEndUs);
        this.mediaPeriods.add(mediaPeriod);
        return mediaPeriod;
    }

    public void releasePeriod(MediaPeriod mediaPeriod) {
        Assertions.checkState(this.mediaPeriods.remove(mediaPeriod));
        this.mediaSource.releasePeriod(((ClippingMediaPeriod)mediaPeriod).mediaPeriod);
        if (this.mediaPeriods.isEmpty() && !this.allowDynamicClippingUpdates) {
            this.refreshClippedTimeline(this.clippingTimeline.timeline);
        }

    }

    public void releaseSourceInternal() {
        super.releaseSourceInternal();
        this.clippingError = null;
        this.clippingTimeline = null;
    }

    protected void onChildSourceInfoRefreshed(Void id, MediaSource mediaSource, Timeline timeline, @Nullable Object manifest) {
        if (this.clippingError == null) {
            this.manifest = manifest;
            this.refreshClippedTimeline(timeline);
        }
    }

    private void refreshClippedTimeline(Timeline timeline) {
        timeline.getWindow(0, this.window);
        long windowPositionInPeriodUs = this.window.getPositionInFirstPeriodUs();
        long windowStartUs;
        long windowEndUs;
        if (this.clippingTimeline != null && !this.mediaPeriods.isEmpty() && !this.allowDynamicClippingUpdates) {
            windowStartUs = this.periodStartUs - windowPositionInPeriodUs;
            windowEndUs = this.endUs == -9223372036854775808L ? -9223372036854775808L : this.periodEndUs - windowPositionInPeriodUs;
        } else {
            windowStartUs = this.startUs;
            windowEndUs = this.endUs;
            if (this.relativeToDefaultPosition) {
                long windowDefaultPositionUs = this.window.getDefaultPositionUs();
                windowStartUs += windowDefaultPositionUs;
                windowEndUs += windowDefaultPositionUs;
            }

            this.periodStartUs = windowPositionInPeriodUs + windowStartUs;
            this.periodEndUs = this.endUs == -9223372036854775808L ? -9223372036854775808L : windowPositionInPeriodUs + windowEndUs;
            int count = this.mediaPeriods.size();

            for(int i = 0; i < count; ++i) {
                this.mediaPeriods.get(i).updateClipping(this.periodStartUs, this.periodEndUs);
            }
        }

        try {
            this.clippingTimeline = new ClippingTimeline(timeline, windowStartUs, windowEndUs);
        } catch (IllegalClippingException var10) {
            this.clippingError = var10;
            return;
        }

        this.refreshSourceInfo(this.clippingTimeline, this.manifest);
    }

    protected long getMediaTimeForChildMediaTime(Void id, long mediaTimeMs) {
        if (mediaTimeMs == -9223372036854775807L) {
            return -9223372036854775807L;
        } else {
            long startMs = C.usToMs(this.startUs);
            long clippedTimeMs = Math.max(0L, mediaTimeMs - startMs);
            if (this.endUs != -9223372036854775808L) {
                clippedTimeMs = Math.min(C.usToMs(this.endUs) - startMs, clippedTimeMs);
            }

            return clippedTimeMs;
        }
    }

    private static final class ClippingTimeline extends ForwardingTimeline {
        private final long startUs;
        private final long endUs;
        private final long durationUs;
        private final boolean isDynamic;

        public ClippingTimeline(Timeline timeline, long startUs, long endUs) throws IllegalClippingException {
            super(timeline);
            if (timeline.getPeriodCount() != 1) {
                throw new IllegalClippingException(0);
            } else {
                Window window = timeline.getWindow(0, new Window());
                startUs = Math.max(0L, startUs);
                long resolvedEndUs = endUs == -9223372036854775808L ? window.durationUs : Math.max(0L, endUs);
                if (window.durationUs != -9223372036854775807L) {
                    if (resolvedEndUs > window.durationUs) {
                        resolvedEndUs = window.durationUs;
                    }

                    if (startUs != 0L && !window.isSeekable) {
                        throw new IllegalClippingException(1);
                    }

                    if (startUs > resolvedEndUs) {
                        throw new IllegalClippingException(2);
                    }
                }

                this.startUs = startUs;
                this.endUs = resolvedEndUs;
                this.durationUs = resolvedEndUs == -9223372036854775807L ? -9223372036854775807L : resolvedEndUs - startUs;
                this.isDynamic = window.isDynamic && (resolvedEndUs == -9223372036854775807L || window.durationUs != -9223372036854775807L && resolvedEndUs == window.durationUs);
            }
        }

        public Window getWindow(int windowIndex, Window window, boolean setTag, long defaultPositionProjectionUs) {
            this.timeline.getWindow(0, window, setTag, 0L);
            window.positionInFirstPeriodUs += this.startUs;
            window.durationUs = this.durationUs;
            window.isDynamic = this.isDynamic;
            if (window.defaultPositionUs != -9223372036854775807L) {
                window.defaultPositionUs = Math.max(window.defaultPositionUs, this.startUs);
                window.defaultPositionUs = this.endUs == -9223372036854775807L ? window.defaultPositionUs : Math.min(window.defaultPositionUs, this.endUs);
                window.defaultPositionUs -= this.startUs;
            }

            long startMs = C.usToMs(this.startUs);
            if (window.presentationStartTimeMs != -9223372036854775807L) {
                window.presentationStartTimeMs += startMs;
            }

            if (window.windowStartTimeMs != -9223372036854775807L) {
                window.windowStartTimeMs += startMs;
            }

            return window;
        }

        public Period getPeriod(int periodIndex, Period period, boolean setIds) {
            this.timeline.getPeriod(0, period, setIds);
            long positionInClippedWindowUs = period.getPositionInWindowUs() - this.startUs;
            long periodDurationUs = this.durationUs == -9223372036854775807L ? -9223372036854775807L : this.durationUs - positionInClippedWindowUs;
            return period.set(period.id, period.uid, 0, periodDurationUs, positionInClippedWindowUs);
        }
    }

    public static final class IllegalClippingException extends IOException {
        public static final int REASON_INVALID_PERIOD_COUNT = 0;
        public static final int REASON_NOT_SEEKABLE_TO_START = 1;
        public static final int REASON_START_EXCEEDS_END = 2;
        public final int reason;

        public IllegalClippingException(int reason) {
            super("Illegal clipping: " + getReasonDescription(reason));
            this.reason = reason;
        }

        private static String getReasonDescription(int reason) {
            switch(reason) {
            case 0:
                return "invalid period count";
            case 1:
                return "not seekable to start";
            case 2:
                return "start exceeds end";
            default:
                return "unknown";
            }
        }

        @Documented
        @Retention(RetentionPolicy.SOURCE)
        public @interface Reason {
        }
    }
}
