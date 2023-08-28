package com.zj.playerLib.extractor.mp3;

import android.util.Pair;

import com.zj.playerLib.C;
import com.zj.playerLib.extractor.SeekPoint;
import com.zj.playerLib.extractor.mp3.Mp3Extractor.Seeker;
import com.zj.playerLib.metadata.id3.MlltFrame;
import com.zj.playerLib.util.Util;

final class MlltSeeker implements Seeker {
    private final long[] referencePositions;
    private final long[] referenceTimesMs;
    private final long durationUs;

    public static MlltSeeker create(long firstFramePosition, MlltFrame mlltFrame) {
        int referenceCount = mlltFrame.bytesDeviations.length;
        long[] referencePositions = new long[1 + referenceCount];
        long[] referenceTimesMs = new long[1 + referenceCount];
        referencePositions[0] = firstFramePosition;
        referenceTimesMs[0] = 0L;
        long position = firstFramePosition;
        long timeMs = 0L;

        for(int i = 1; i <= referenceCount; ++i) {
            position += mlltFrame.bytesBetweenReference + mlltFrame.bytesDeviations[i - 1];
            timeMs += mlltFrame.millisecondsBetweenReference + mlltFrame.millisecondsDeviations[i - 1];
            referencePositions[i] = position;
            referenceTimesMs[i] = timeMs;
        }

        return new MlltSeeker(referencePositions, referenceTimesMs);
    }

    private MlltSeeker(long[] referencePositions, long[] referenceTimesMs) {
        this.referencePositions = referencePositions;
        this.referenceTimesMs = referenceTimesMs;
        this.durationUs = C.msToUs(referenceTimesMs[referenceTimesMs.length - 1]);
    }

    public boolean isSeekable() {
        return true;
    }

    public SeekPoints getSeekPoints(long timeUs) {
        timeUs = Util.constrainValue(timeUs, 0L, this.durationUs);
        Pair<Long, Long> timeMsAndPosition = linearlyInterpolate(C.usToMs(timeUs), this.referenceTimesMs, this.referencePositions);
        timeUs = C.msToUs(timeMsAndPosition.first);
        long position = timeMsAndPosition.second;
        return new SeekPoints(new SeekPoint(timeUs, position));
    }

    public long getTimeUs(long position) {
        Pair<Long, Long> positionAndTimeMs = linearlyInterpolate(position, this.referencePositions, this.referenceTimesMs);
        return C.msToUs(positionAndTimeMs.second);
    }

    public long getDurationUs() {
        return this.durationUs;
    }

    private static Pair<Long, Long> linearlyInterpolate(long x, long[] xReferences, long[] yReferences) {
        int previousReferenceIndex = Util.binarySearchFloor(xReferences, x, true, true);
        long xPreviousReference = xReferences[previousReferenceIndex];
        long yPreviousReference = yReferences[previousReferenceIndex];
        int nextReferenceIndex = previousReferenceIndex + 1;
        if (nextReferenceIndex == xReferences.length) {
            return Pair.create(xPreviousReference, yPreviousReference);
        } else {
            long xNextReference = xReferences[nextReferenceIndex];
            long yNextReference = yReferences[nextReferenceIndex];
            double proportion = xNextReference == xPreviousReference ? 0.0D : ((double)x - (double)xPreviousReference) / (double)(xNextReference - xPreviousReference);
            long y = (long)(proportion * (double)(yNextReference - yPreviousReference)) + yPreviousReference;
            return Pair.create(x, y);
        }
    }

    public long getDataEndPosition() {
        return -1L;
    }
}
