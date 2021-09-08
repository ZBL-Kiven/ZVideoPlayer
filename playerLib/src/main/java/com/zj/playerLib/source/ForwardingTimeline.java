//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.source;

import com.zj.playerLib.Timeline;

public abstract class ForwardingTimeline extends Timeline {
    protected final Timeline timeline;

    public ForwardingTimeline(Timeline timeline) {
        this.timeline = timeline;
    }

    public int getWindowCount() {
        return this.timeline.getWindowCount();
    }

    public int getNextWindowIndex(int windowIndex, int repeatMode, boolean shuffleModeEnabled) {
        return this.timeline.getNextWindowIndex(windowIndex, repeatMode, shuffleModeEnabled);
    }

    public int getPreviousWindowIndex(int windowIndex, int repeatMode, boolean shuffleModeEnabled) {
        return this.timeline.getPreviousWindowIndex(windowIndex, repeatMode, shuffleModeEnabled);
    }

    public int getLastWindowIndex(boolean shuffleModeEnabled) {
        return this.timeline.getLastWindowIndex(shuffleModeEnabled);
    }

    public int getFirstWindowIndex(boolean shuffleModeEnabled) {
        return this.timeline.getFirstWindowIndex(shuffleModeEnabled);
    }

    public Window getWindow(int windowIndex, Window window, boolean setTag, long defaultPositionProjectionUs) {
        return this.timeline.getWindow(windowIndex, window, setTag, defaultPositionProjectionUs);
    }

    public int getPeriodCount() {
        return this.timeline.getPeriodCount();
    }

    public Period getPeriod(int periodIndex, Period period, boolean setIds) {
        return this.timeline.getPeriod(periodIndex, period, setIds);
    }

    public int getIndexOfPeriod(Object uid) {
        return this.timeline.getIndexOfPeriod(uid);
    }

    public Object getUidOfPeriod(int periodIndex) {
        return this.timeline.getUidOfPeriod(periodIndex);
    }
}
