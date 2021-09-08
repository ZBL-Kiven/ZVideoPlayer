//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib;

import android.util.Pair;

import androidx.annotation.Nullable;

import com.zj.playerLib.source.ads.AdPlaybackState;
import com.zj.playerLib.source.ads.AdPlaybackState.AdGroup;
import com.zj.playerLib.util.Assertions;

public abstract class Timeline {
    public static final Timeline EMPTY = new Timeline() {
        public int getWindowCount() {
            return 0;
        }

        public Window getWindow(int windowIndex, Window window, boolean setTag, long defaultPositionProjectionUs) {
            throw new IndexOutOfBoundsException();
        }

        public int getPeriodCount() {
            return 0;
        }

        public Period getPeriod(int periodIndex, Period period, boolean setIds) {
            throw new IndexOutOfBoundsException();
        }

        public int getIndexOfPeriod(Object uid) {
            return -1;
        }

        public Object getUidOfPeriod(int periodIndex) {
            throw new IndexOutOfBoundsException();
        }
    };

    public Timeline() {
    }

    public final boolean isEmpty() {
        return this.getWindowCount() == 0;
    }

    public abstract int getWindowCount();

    public int getNextWindowIndex(int windowIndex, int repeatMode, boolean shuffleModeEnabled) {
        switch(repeatMode) {
        case 0:
            return windowIndex == this.getLastWindowIndex(shuffleModeEnabled) ? -1 : windowIndex + 1;
        case 1:
            return windowIndex;
        case 2:
            return windowIndex == this.getLastWindowIndex(shuffleModeEnabled) ? this.getFirstWindowIndex(shuffleModeEnabled) : windowIndex + 1;
        default:
            throw new IllegalStateException();
        }
    }

    public int getPreviousWindowIndex(int windowIndex, int repeatMode, boolean shuffleModeEnabled) {
        switch(repeatMode) {
        case 0:
            return windowIndex == this.getFirstWindowIndex(shuffleModeEnabled) ? -1 : windowIndex - 1;
        case 1:
            return windowIndex;
        case 2:
            return windowIndex == this.getFirstWindowIndex(shuffleModeEnabled) ? this.getLastWindowIndex(shuffleModeEnabled) : windowIndex - 1;
        default:
            throw new IllegalStateException();
        }
    }

    public int getLastWindowIndex(boolean shuffleModeEnabled) {
        return this.isEmpty() ? -1 : this.getWindowCount() - 1;
    }

    public int getFirstWindowIndex(boolean shuffleModeEnabled) {
        return this.isEmpty() ? -1 : 0;
    }

    public final Window getWindow(int windowIndex, Window window) {
        return this.getWindow(windowIndex, window, false);
    }

    public final Window getWindow(int windowIndex, Window window, boolean setTag) {
        return this.getWindow(windowIndex, window, setTag, 0L);
    }

    public abstract Window getWindow(int var1, Window var2, boolean var3, long var4);

    public abstract int getPeriodCount();

    public final int getNextPeriodIndex(int periodIndex, Period period, Window window, int repeatMode, boolean shuffleModeEnabled) {
        int windowIndex = this.getPeriod(periodIndex, period).windowIndex;
        if (this.getWindow(windowIndex, window).lastPeriodIndex == periodIndex) {
            int nextWindowIndex = this.getNextWindowIndex(windowIndex, repeatMode, shuffleModeEnabled);
            return nextWindowIndex == -1 ? -1 : this.getWindow(nextWindowIndex, window).firstPeriodIndex;
        } else {
            return periodIndex + 1;
        }
    }

    public final boolean isLastPeriod(int periodIndex, Period period, Window window, int repeatMode, boolean shuffleModeEnabled) {
        return this.getNextPeriodIndex(periodIndex, period, window, repeatMode, shuffleModeEnabled) == -1;
    }

    public final Pair<Object, Long> getPeriodPosition(Window window, Period period, int windowIndex, long windowPositionUs) {
        return this.getPeriodPosition(window, period, windowIndex, windowPositionUs, 0L);
    }

    public final Pair<Object, Long> getPeriodPosition(Window window, Period period, int windowIndex, long windowPositionUs, long defaultPositionProjectionUs) {
        Assertions.checkIndex(windowIndex, 0, this.getWindowCount());
        this.getWindow(windowIndex, window, false, defaultPositionProjectionUs);
        if (windowPositionUs == -9223372036854775807L) {
            windowPositionUs = window.getDefaultPositionUs();
            if (windowPositionUs == -9223372036854775807L) {
                return null;
            }
        }

        int periodIndex = window.firstPeriodIndex;
        long periodPositionUs = window.getPositionInFirstPeriodUs() + windowPositionUs;

        for(long periodDurationUs = this.getPeriod(periodIndex, period, true).getDurationUs(); periodDurationUs != -9223372036854775807L && periodPositionUs >= periodDurationUs && periodIndex < window.lastPeriodIndex; periodDurationUs = this.getPeriod(periodIndex, period, true).getDurationUs()) {
            periodPositionUs -= periodDurationUs;
            ++periodIndex;
        }

        return Pair.create(period.uid, periodPositionUs);
    }

    public Period getPeriodByUid(Object periodUid, Period period) {
        return this.getPeriod(this.getIndexOfPeriod(periodUid), period, true);
    }

    public final Period getPeriod(int periodIndex, Period period) {
        return this.getPeriod(periodIndex, period, false);
    }

    public abstract Period getPeriod(int var1, Period var2, boolean var3);

    public abstract int getIndexOfPeriod(Object var1);

    public abstract Object getUidOfPeriod(int var1);

    public static final class Period {
        public Object id;
        public Object uid;
        public int windowIndex;
        public long durationUs;
        private long positionInWindowUs;
        private AdPlaybackState adPlaybackState;

        public Period() {
        }

        public Period set(Object id, Object uid, int windowIndex, long durationUs, long positionInWindowUs) {
            return this.set(id, uid, windowIndex, durationUs, positionInWindowUs, AdPlaybackState.NONE);
        }

        public Period set(Object id, Object uid, int windowIndex, long durationUs, long positionInWindowUs, AdPlaybackState adPlaybackState) {
            this.id = id;
            this.uid = uid;
            this.windowIndex = windowIndex;
            this.durationUs = durationUs;
            this.positionInWindowUs = positionInWindowUs;
            this.adPlaybackState = adPlaybackState;
            return this;
        }

        public long getDurationMs() {
            return C.usToMs(this.durationUs);
        }

        public long getDurationUs() {
            return this.durationUs;
        }

        public long getPositionInWindowMs() {
            return C.usToMs(this.positionInWindowUs);
        }

        public long getPositionInWindowUs() {
            return this.positionInWindowUs;
        }

        public int getAdGroupCount() {
            return this.adPlaybackState.adGroupCount;
        }

        public long getAdGroupTimeUs(int adGroupIndex) {
            return this.adPlaybackState.adGroupTimesUs[adGroupIndex];
        }

        public int getFirstAdIndexToPlay(int adGroupIndex) {
            return this.adPlaybackState.adGroups[adGroupIndex].getFirstAdIndexToPlay();
        }

        public int getNextAdIndexToPlay(int adGroupIndex, int lastPlayedAdIndex) {
            return this.adPlaybackState.adGroups[adGroupIndex].getNextAdIndexToPlay(lastPlayedAdIndex);
        }

        public boolean hasPlayedAdGroup(int adGroupIndex) {
            return !this.adPlaybackState.adGroups[adGroupIndex].hasUnplayedAds();
        }

        public int getAdGroupIndexForPositionUs(long positionUs) {
            return this.adPlaybackState.getAdGroupIndexForPositionUs(positionUs);
        }

        public int getAdGroupIndexAfterPositionUs(long positionUs) {
            return this.adPlaybackState.getAdGroupIndexAfterPositionUs(positionUs);
        }

        public int getAdCountInAdGroup(int adGroupIndex) {
            return this.adPlaybackState.adGroups[adGroupIndex].count;
        }

        public boolean isAdAvailable(int adGroupIndex, int adIndexInAdGroup) {
            AdGroup adGroup = this.adPlaybackState.adGroups[adGroupIndex];
            return adGroup.count != -1 && adGroup.states[adIndexInAdGroup] != 0;
        }

        public long getAdDurationUs(int adGroupIndex, int adIndexInAdGroup) {
            AdGroup adGroup = this.adPlaybackState.adGroups[adGroupIndex];
            return adGroup.count != -1 ? adGroup.durationsUs[adIndexInAdGroup] : -9223372036854775807L;
        }

        public long getAdResumePositionUs() {
            return this.adPlaybackState.adResumePositionUs;
        }
    }

    public static final class Window {
        @Nullable
        public Object tag;
        public long presentationStartTimeMs;
        public long windowStartTimeMs;
        public boolean isSeekable;
        public boolean isDynamic;
        public int firstPeriodIndex;
        public int lastPeriodIndex;
        public long defaultPositionUs;
        public long durationUs;
        public long positionInFirstPeriodUs;

        public Window() {
        }

        public Window set(@Nullable Object tag, long presentationStartTimeMs, long windowStartTimeMs, boolean isSeekable, boolean isDynamic, long defaultPositionUs, long durationUs, int firstPeriodIndex, int lastPeriodIndex, long positionInFirstPeriodUs) {
            this.tag = tag;
            this.presentationStartTimeMs = presentationStartTimeMs;
            this.windowStartTimeMs = windowStartTimeMs;
            this.isSeekable = isSeekable;
            this.isDynamic = isDynamic;
            this.defaultPositionUs = defaultPositionUs;
            this.durationUs = durationUs;
            this.firstPeriodIndex = firstPeriodIndex;
            this.lastPeriodIndex = lastPeriodIndex;
            this.positionInFirstPeriodUs = positionInFirstPeriodUs;
            return this;
        }

        public long getDefaultPositionMs() {
            return C.usToMs(this.defaultPositionUs);
        }

        public long getDefaultPositionUs() {
            return this.defaultPositionUs;
        }

        public long getDurationMs() {
            return C.usToMs(this.durationUs);
        }

        public long getDurationUs() {
            return this.durationUs;
        }

        public long getPositionInFirstPeriodMs() {
            return C.usToMs(this.positionInFirstPeriodUs);
        }

        public long getPositionInFirstPeriodUs() {
            return this.positionInFirstPeriodUs;
        }
    }
}
