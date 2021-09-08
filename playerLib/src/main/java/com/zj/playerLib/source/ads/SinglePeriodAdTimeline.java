//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.source.ads;

import androidx.annotation.VisibleForTesting;
import com.zj.playerLib.Timeline;
import com.zj.playerLib.source.ForwardingTimeline;
import com.zj.playerLib.util.Assertions;

@VisibleForTesting(
    otherwise = 3
)
public final class SinglePeriodAdTimeline extends ForwardingTimeline {
    private final AdPlaybackState adPlaybackState;

    public SinglePeriodAdTimeline(Timeline contentTimeline, AdPlaybackState adPlaybackState) {
        super(contentTimeline);
        Assertions.checkState(contentTimeline.getPeriodCount() == 1);
        Assertions.checkState(contentTimeline.getWindowCount() == 1);
        this.adPlaybackState = adPlaybackState;
    }

    public Period getPeriod(int periodIndex, Period period, boolean setIds) {
        this.timeline.getPeriod(periodIndex, period, setIds);
        period.set(period.id, period.uid, period.windowIndex, period.durationUs, period.getPositionInWindowUs(), this.adPlaybackState);
        return period;
    }

    public Window getWindow(int windowIndex, Window window, boolean setTag, long defaultPositionProjectionUs) {
        window = super.getWindow(windowIndex, window, setTag, defaultPositionProjectionUs);
        if (window.durationUs == -9223372036854775807L) {
            window.durationUs = this.adPlaybackState.contentDurationUs;
        }

        return window;
    }
}
