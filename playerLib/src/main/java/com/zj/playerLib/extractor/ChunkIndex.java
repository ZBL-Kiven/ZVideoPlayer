package com.zj.playerLib.extractor;

import com.zj.playerLib.util.Util;

import java.util.Arrays;

public final class ChunkIndex implements SeekMap {
    public final int length;
    public final int[] sizes;
    public final long[] offsets;
    public final long[] durationsUs;
    public final long[] timesUs;
    private final long durationUs;

    public ChunkIndex(int[] sizes, long[] offsets, long[] durationsUs, long[] timesUs) {
        this.sizes = sizes;
        this.offsets = offsets;
        this.durationsUs = durationsUs;
        this.timesUs = timesUs;
        this.length = sizes.length;
        if (this.length > 0) {
            this.durationUs = durationsUs[this.length - 1] + timesUs[this.length - 1];
        } else {
            this.durationUs = 0L;
        }

    }

    public int getChunkIndex(long timeUs) {
        return Util.binarySearchFloor(this.timesUs, timeUs, true, true);
    }

    public boolean isSeekable() {
        return true;
    }

    public long getDurationUs() {
        return this.durationUs;
    }

    public SeekPoints getSeekPoints(long timeUs) {
        int chunkIndex = this.getChunkIndex(timeUs);
        SeekPoint seekPoint = new SeekPoint(this.timesUs[chunkIndex], this.offsets[chunkIndex]);
        if (seekPoint.timeUs < timeUs && chunkIndex != this.length - 1) {
            SeekPoint nextSeekPoint = new SeekPoint(this.timesUs[chunkIndex + 1], this.offsets[chunkIndex + 1]);
            return new SeekPoints(seekPoint, nextSeekPoint);
        } else {
            return new SeekPoints(seekPoint);
        }
    }

    public String toString() {
        return "ChunkIndex(length=" + this.length + ", sizes=" + Arrays.toString(this.sizes) + ", offsets=" + Arrays.toString(this.offsets) + ", timeUs=" + Arrays.toString(this.timesUs) + ", durationsUs=" + Arrays.toString(this.durationsUs) + ")";
    }
}
