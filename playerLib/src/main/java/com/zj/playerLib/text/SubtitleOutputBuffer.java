package com.zj.playerLib.text;

import com.zj.playerLib.decoder.OutputBuffer;

import java.util.List;

public abstract class SubtitleOutputBuffer extends OutputBuffer implements Subtitle {
    private Subtitle subtitle;
    private long subSampleOffsetUs;

    public SubtitleOutputBuffer() {
    }

    public void setContent(long timeUs, Subtitle subtitle, long subSampleOffsetUs) {
        this.timeUs = timeUs;
        this.subtitle = subtitle;
        this.subSampleOffsetUs = subSampleOffsetUs == Long.MAX_VALUE ? this.timeUs : subSampleOffsetUs;
    }

    public int getEventTimeCount() {
        return this.subtitle.getEventTimeCount();
    }

    public long getEventTime(int index) {
        return this.subtitle.getEventTime(index) + this.subSampleOffsetUs;
    }

    public int getNextEventTimeIndex(long timeUs) {
        return this.subtitle.getNextEventTimeIndex(timeUs - this.subSampleOffsetUs);
    }

    public List<Cue> getCues(long timeUs) {
        return this.subtitle.getCues(timeUs - this.subSampleOffsetUs);
    }

    public abstract void release();

    public void clear() {
        super.clear();
        this.subtitle = null;
    }
}
