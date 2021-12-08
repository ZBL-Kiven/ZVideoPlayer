package com.zj.playerLib.extractor;

import com.zj.playerLib.util.Util;

public class ConstantBitrateSeekMap implements SeekMap {
    private final long inputLength;
    private final long firstFrameBytePosition;
    private final int frameSize;
    private final long dataSize;
    private final int bitrate;
    private final long durationUs;

    public ConstantBitrateSeekMap(long inputLength, long firstFrameBytePosition, int bitrate, int frameSize) {
        this.inputLength = inputLength;
        this.firstFrameBytePosition = firstFrameBytePosition;
        this.frameSize = frameSize == -1 ? 1 : frameSize;
        this.bitrate = bitrate;
        if (inputLength == -1L) {
            this.dataSize = -1L;
            this.durationUs = -Long.MAX_VALUE;
        } else {
            this.dataSize = inputLength - firstFrameBytePosition;
            this.durationUs = getTimeUsAtPosition(inputLength, firstFrameBytePosition, bitrate);
        }

    }

    public boolean isSeekable() {
        return this.dataSize != -1L;
    }

    public SeekPoints getSeekPoints(long timeUs) {
        if (this.dataSize == -1L) {
            return new SeekPoints(new SeekPoint(0L, this.firstFrameBytePosition));
        } else {
            long seekFramePosition = this.getFramePositionForTimeUs(timeUs);
            long seekTimeUs = this.getTimeUsAtPosition(seekFramePosition);
            SeekPoint seekPoint = new SeekPoint(seekTimeUs, seekFramePosition);
            if (seekTimeUs < timeUs && seekFramePosition + (long)this.frameSize < this.inputLength) {
                long secondSeekPosition = seekFramePosition + (long)this.frameSize;
                long secondSeekTimeUs = this.getTimeUsAtPosition(secondSeekPosition);
                SeekPoint secondSeekPoint = new SeekPoint(secondSeekTimeUs, secondSeekPosition);
                return new SeekPoints(seekPoint, secondSeekPoint);
            } else {
                return new SeekPoints(seekPoint);
            }
        }
    }

    public long getDurationUs() {
        return this.durationUs;
    }

    public long getTimeUsAtPosition(long position) {
        return getTimeUsAtPosition(position, this.firstFrameBytePosition, this.bitrate);
    }

    private static long getTimeUsAtPosition(long position, long firstFrameBytePosition, int bitrate) {
        return Math.max(0L, position - firstFrameBytePosition) * 8L * 1000000L / (long)bitrate;
    }

    private long getFramePositionForTimeUs(long timeUs) {
        long positionOffset = timeUs * (long)this.bitrate / 8000000L;
        positionOffset = positionOffset / (long)this.frameSize * (long)this.frameSize;
        positionOffset = Util.constrainValue(positionOffset, 0L, this.dataSize - (long)this.frameSize);
        return this.firstFrameBytePosition + positionOffset;
    }
}
