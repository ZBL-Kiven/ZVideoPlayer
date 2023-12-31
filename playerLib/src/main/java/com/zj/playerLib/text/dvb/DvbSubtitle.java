package com.zj.playerLib.text.dvb;

import com.zj.playerLib.text.Cue;
import com.zj.playerLib.text.Subtitle;

import java.util.List;

final class DvbSubtitle implements Subtitle {
    private final List<Cue> cues;

    public DvbSubtitle(List<Cue> cues) {
        this.cues = cues;
    }

    public int getNextEventTimeIndex(long timeUs) {
        return -1;
    }

    public int getEventTimeCount() {
        return 1;
    }

    public long getEventTime(int index) {
        return 0L;
    }

    public List<Cue> getCues(long timeUs) {
        return this.cues;
    }
}
