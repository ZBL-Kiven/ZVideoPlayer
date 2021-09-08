//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.text.tx3g;

import com.zj.playerLib.text.Cue;
import com.zj.playerLib.text.Subtitle;
import com.zj.playerLib.util.Assertions;

import java.util.Collections;
import java.util.List;

final class Tx3gSubtitle implements Subtitle {
    public static final Tx3gSubtitle EMPTY = new Tx3gSubtitle();
    private final List<Cue> cues;

    public Tx3gSubtitle(Cue cue) {
        this.cues = Collections.singletonList(cue);
    }

    private Tx3gSubtitle() {
        this.cues = Collections.emptyList();
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
