package com.zj.playerLib.source;

import com.zj.playerLib.SeekParameters;
import com.zj.playerLib.trackselection.TrackSelection;

import java.io.IOException;

public interface MediaPeriod extends SequenceableLoader {
    void prepare(Callback var1, long var2);

    void maybeThrowPrepareError() throws IOException;

    TrackGroupArray getTrackGroups();

    long selectTracks(TrackSelection[] var1, boolean[] var2, SampleStream[] var3, boolean[] var4, long var5);

    void discardBuffer(long var1, boolean var3);

    long readDiscontinuity();

    long seekToUs(long var1);

    long getAdjustedSeekPositionUs(long var1, SeekParameters var3);

    long getBufferedPositionUs();

    long getNextLoadPositionUs();

    boolean continueLoading(long var1);

    void reevaluateBuffer(long var1);

    interface Callback extends SequenceableLoader.Callback<MediaPeriod> {
        void onPrepared(MediaPeriod var1);
    }
}
