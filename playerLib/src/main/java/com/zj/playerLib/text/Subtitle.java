package com.zj.playerLib.text;

import java.util.List;

public interface Subtitle {
    int getNextEventTimeIndex(long var1);

    int getEventTimeCount();

    long getEventTime(int var1);

    List<Cue> getCues(long var1);
}
