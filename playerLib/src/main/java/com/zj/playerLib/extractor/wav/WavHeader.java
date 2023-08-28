package com.zj.playerLib.extractor.wav;

import com.zj.playerLib.extractor.SeekMap;
import com.zj.playerLib.extractor.SeekPoint;
import com.zj.playerLib.util.Util;

final class WavHeader implements SeekMap {
    private final int numChannels;
    private final int sampleRateHz;
    private final int averageBytesPerSecond;
    private final int blockAlignment;
    private final int bitsPerSample;
    private final int encoding;
    private long dataStartPosition;
    private long dataSize;

    public WavHeader(int numChannels, int sampleRateHz, int averageBytesPerSecond, int blockAlignment, int bitsPerSample, int encoding) {
        this.numChannels = numChannels;
        this.sampleRateHz = sampleRateHz;
        this.averageBytesPerSecond = averageBytesPerSecond;
        this.blockAlignment = blockAlignment;
        this.bitsPerSample = bitsPerSample;
        this.encoding = encoding;
    }

    public void setDataBounds(long dataStartPosition, long dataSize) {
        this.dataStartPosition = dataStartPosition;
        this.dataSize = dataSize;
    }

    public long getDataLimit() {
        return this.hasDataBounds() ? this.dataStartPosition + this.dataSize : -1L;
    }

    public boolean hasDataBounds() {
        return this.dataStartPosition != 0L && this.dataSize != 0L;
    }

    public boolean isSeekable() {
        return true;
    }

    public long getDurationUs() {
        long numFrames = this.dataSize / (long)this.blockAlignment;
        return numFrames * 1000000L / (long)this.sampleRateHz;
    }

    public SeekPoints getSeekPoints(long timeUs) {
        long positionOffset = timeUs * (long)this.averageBytesPerSecond / 1000000L;
        positionOffset = positionOffset / (long)this.blockAlignment * (long)this.blockAlignment;
        positionOffset = Util.constrainValue(positionOffset, 0L, this.dataSize - (long)this.blockAlignment);
        long seekPosition = this.dataStartPosition + positionOffset;
        long seekTimeUs = this.getTimeUs(seekPosition);
        SeekPoint seekPoint = new SeekPoint(seekTimeUs, seekPosition);
        if (seekTimeUs < timeUs && positionOffset != this.dataSize - (long)this.blockAlignment) {
            long secondSeekPosition = seekPosition + (long)this.blockAlignment;
            long secondSeekTimeUs = this.getTimeUs(secondSeekPosition);
            SeekPoint secondSeekPoint = new SeekPoint(secondSeekTimeUs, secondSeekPosition);
            return new SeekPoints(seekPoint, secondSeekPoint);
        } else {
            return new SeekPoints(seekPoint);
        }
    }

    public long getTimeUs(long position) {
        long positionOffset = Math.max(0L, position - this.dataStartPosition);
        return positionOffset * 1000000L / (long)this.averageBytesPerSecond;
    }

    public int getBytesPerFrame() {
        return this.blockAlignment;
    }

    public int getBitrate() {
        return this.sampleRateHz * this.bitsPerSample * this.numChannels;
    }

    public int getSampleRateHz() {
        return this.sampleRateHz;
    }

    public int getNumChannels() {
        return this.numChannels;
    }

    public int getEncoding() {
        return this.encoding;
    }
}
