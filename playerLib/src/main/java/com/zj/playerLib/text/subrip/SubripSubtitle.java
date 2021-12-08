package com.zj.playerLib.text.subrip;

import com.zj.playerLib.text.Cue;
import com.zj.playerLib.text.Subtitle;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.Util;

import java.util.Collections;
import java.util.List;

final class SubripSubtitle implements Subtitle {
    private final Cue[] cues;
    private final long[] cueTimesUs;

    public SubripSubtitle(Cue[] cues, long[] cueTimesUs) {
        this.cues = cues;
        this.cueTimesUs = cueTimesUs;
    }

    public int getNextEventTimeIndex(long timeUs) {
        int index = Util.binarySearchCeil(this.cueTimesUs, timeUs, false, false);
        return index < this.cueTimesUs.length ? index : -1;
    }

    public int getEventTimeCount() {
        return this.cueTimesUs.length;
    }

    public long getEventTime(int index) {
        Assertions.checkArgument(index >= 0);
        Assertions.checkArgument(index < this.cueTimesUs.length);
        return this.cueTimesUs[index];
    }

    public List<Cue> getCues(long timeUs) {
        int index = Util.binarySearchFloor(this.cueTimesUs, timeUs, true, false);
        return index != -1 && this.cues[index] != null ? Collections.singletonList(this.cues[index]) : Collections.emptyList();
    }
}
