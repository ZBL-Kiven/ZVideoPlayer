package com.zj.playerLib.text.cea;

import com.zj.playerLib.text.Cue;
import com.zj.playerLib.text.Subtitle;
import com.zj.playerLib.util.Assertions;

import java.util.Collections;
import java.util.List;

final class CeaSubtitle implements Subtitle {
    private final List<Cue> cues;

    public CeaSubtitle(List<Cue> cues) {
        this.cues = cues;
    }

    public int getNextEventTimeIndex(long timeUs) {
        return timeUs < 0L ? 0 : -1;
    }

    public int getEventTimeCount() {
        return 1;
    }

    public long getEventTime(int index) {
        Assertions.checkArgument(index == 0);
        return 0L;
    }

    public List<Cue> getCues(long timeUs) {
        return timeUs >= 0L ? this.cues : Collections.emptyList();
    }
}
