package com.zj.playerLib.text.webvtt;

import android.text.SpannableStringBuilder;
import com.zj.playerLib.text.Cue;
import com.zj.playerLib.text.Subtitle;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.Util;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class WebvttSubtitle implements Subtitle {
    private final List<WebvttCue> cues;
    private final int numCues;
    private final long[] cueTimesUs;
    private final long[] sortedCueTimesUs;

    public WebvttSubtitle(List<WebvttCue> cues) {
        this.cues = cues;
        this.numCues = cues.size();
        this.cueTimesUs = new long[2 * this.numCues];

        for(int cueIndex = 0; cueIndex < this.numCues; ++cueIndex) {
            WebvttCue cue = cues.get(cueIndex);
            int arrayIndex = cueIndex * 2;
            this.cueTimesUs[arrayIndex] = cue.startTime;
            this.cueTimesUs[arrayIndex + 1] = cue.endTime;
        }

        this.sortedCueTimesUs = Arrays.copyOf(this.cueTimesUs, this.cueTimesUs.length);
        Arrays.sort(this.sortedCueTimesUs);
    }

    public int getNextEventTimeIndex(long timeUs) {
        int index = Util.binarySearchCeil(this.sortedCueTimesUs, timeUs, false, false);
        return index < this.sortedCueTimesUs.length ? index : -1;
    }

    public int getEventTimeCount() {
        return this.sortedCueTimesUs.length;
    }

    public long getEventTime(int index) {
        Assertions.checkArgument(index >= 0);
        Assertions.checkArgument(index < this.sortedCueTimesUs.length);
        return this.sortedCueTimesUs[index];
    }

    public List<Cue> getCues(long timeUs) {
        ArrayList<Cue> list = null;
        WebvttCue firstNormalCue = null;
        SpannableStringBuilder normalCueTextBuilder = null;

        for(int i = 0; i < this.numCues; ++i) {
            if (this.cueTimesUs[i * 2] <= timeUs && timeUs < this.cueTimesUs[i * 2 + 1]) {
                if (list == null) {
                    list = new ArrayList();
                }

                WebvttCue cue = this.cues.get(i);
                if (cue.isNormalCue()) {
                    if (firstNormalCue == null) {
                        firstNormalCue = cue;
                    } else if (normalCueTextBuilder == null) {
                        normalCueTextBuilder = new SpannableStringBuilder();
                        normalCueTextBuilder.append(firstNormalCue.text).append("\n").append(cue.text);
                    } else {
                        normalCueTextBuilder.append("\n").append(cue.text);
                    }
                } else {
                    list.add(cue);
                }
            }
        }

        if (normalCueTextBuilder != null) {
            list.add(new WebvttCue(normalCueTextBuilder));
        } else if (firstNormalCue != null) {
            list.add(firstNormalCue);
        }

        if (list != null) {
            return list;
        } else {
            return Collections.emptyList();
        }
    }
}
