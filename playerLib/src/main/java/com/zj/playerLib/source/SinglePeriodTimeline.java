//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.source;

import androidx.annotation.Nullable;

import com.zj.playerLib.Timeline;
import com.zj.playerLib.util.Assertions;

public final class SinglePeriodTimeline extends Timeline {
    private static final Object UID = new Object();
    private final long presentationStartTimeMs;
    private final long windowStartTimeMs;
    private final long periodDurationUs;
    private final long windowDurationUs;
    private final long windowPositionInPeriodUs;
    private final long windowDefaultStartPositionUs;
    private final boolean isSeekable;
    private final boolean isDynamic;
    @Nullable
    private final Object tag;

    public SinglePeriodTimeline(long durationUs, boolean isSeekable, boolean isDynamic) {
        this(durationUs, isSeekable, isDynamic, (Object)null);
    }

    public SinglePeriodTimeline(long durationUs, boolean isSeekable, boolean isDynamic, @Nullable Object tag) {
        this(durationUs, durationUs, 0L, 0L, isSeekable, isDynamic, tag);
    }

    public SinglePeriodTimeline(long periodDurationUs, long windowDurationUs, long windowPositionInPeriodUs, long windowDefaultStartPositionUs, boolean isSeekable, boolean isDynamic, @Nullable Object tag) {
        this(-9223372036854775807L, -9223372036854775807L, periodDurationUs, windowDurationUs, windowPositionInPeriodUs, windowDefaultStartPositionUs, isSeekable, isDynamic, tag);
    }

    public SinglePeriodTimeline(long presentationStartTimeMs, long windowStartTimeMs, long periodDurationUs, long windowDurationUs, long windowPositionInPeriodUs, long windowDefaultStartPositionUs, boolean isSeekable, boolean isDynamic, @Nullable Object tag) {
        this.presentationStartTimeMs = presentationStartTimeMs;
        this.windowStartTimeMs = windowStartTimeMs;
        this.periodDurationUs = periodDurationUs;
        this.windowDurationUs = windowDurationUs;
        this.windowPositionInPeriodUs = windowPositionInPeriodUs;
        this.windowDefaultStartPositionUs = windowDefaultStartPositionUs;
        this.isSeekable = isSeekable;
        this.isDynamic = isDynamic;
        this.tag = tag;
    }

    public int getWindowCount() {
        return 1;
    }

    public Window getWindow(int windowIndex, Window window, boolean setTag, long defaultPositionProjectionUs) {
        Assertions.checkIndex(windowIndex, 0, 1);
        Object tag = setTag ? this.tag : null;
        long windowDefaultStartPositionUs = this.windowDefaultStartPositionUs;
        if (this.isDynamic && defaultPositionProjectionUs != 0L) {
            if (this.windowDurationUs == -9223372036854775807L) {
                windowDefaultStartPositionUs = -9223372036854775807L;
            } else {
                windowDefaultStartPositionUs += defaultPositionProjectionUs;
                if (windowDefaultStartPositionUs > this.windowDurationUs) {
                    windowDefaultStartPositionUs = -9223372036854775807L;
                }
            }
        }

        return window.set(tag, this.presentationStartTimeMs, this.windowStartTimeMs, this.isSeekable, this.isDynamic, windowDefaultStartPositionUs, this.windowDurationUs, 0, 0, this.windowPositionInPeriodUs);
    }

    public int getPeriodCount() {
        return 1;
    }

    public Period getPeriod(int periodIndex, Period period, boolean setIds) {
        Assertions.checkIndex(periodIndex, 0, 1);
        Object uid = setIds ? UID : null;
        return period.set((Object)null, uid, 0, this.periodDurationUs, -this.windowPositionInPeriodUs);
    }

    public int getIndexOfPeriod(Object uid) {
        return UID.equals(uid) ? 0 : -1;
    }

    public Object getUidOfPeriod(int periodIndex) {
        Assertions.checkIndex(periodIndex, 0, 1);
        return UID;
    }
}
