//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.util;

public final class TimestampAdjuster {
    public static final long DO_NOT_OFFSET = 9223372036854775807L;
    private static final long MAX_PTS_PLUS_ONE = 8589934592L;
    private long firstSampleTimestampUs;
    private long timestampOffsetUs;
    private volatile long lastSampleTimestampUs = -9223372036854775807L;

    public TimestampAdjuster(long firstSampleTimestampUs) {
        this.setFirstSampleTimestampUs(firstSampleTimestampUs);
    }

    public synchronized void setFirstSampleTimestampUs(long firstSampleTimestampUs) {
        Assertions.checkState(this.lastSampleTimestampUs == -9223372036854775807L);
        this.firstSampleTimestampUs = firstSampleTimestampUs;
    }

    public long getFirstSampleTimestampUs() {
        return this.firstSampleTimestampUs;
    }

    public long getLastAdjustedTimestampUs() {
        return this.lastSampleTimestampUs != -9223372036854775807L ? this.lastSampleTimestampUs + this.timestampOffsetUs : (this.firstSampleTimestampUs != 9223372036854775807L ? this.firstSampleTimestampUs : -9223372036854775807L);
    }

    public long getTimestampOffsetUs() {
        return this.firstSampleTimestampUs == 9223372036854775807L ? 0L : (this.lastSampleTimestampUs == -9223372036854775807L ? -9223372036854775807L : this.timestampOffsetUs);
    }

    public void reset() {
        this.lastSampleTimestampUs = -9223372036854775807L;
    }

    public long adjustTsTimestamp(long pts90Khz) {
        if (pts90Khz == -9223372036854775807L) {
            return -9223372036854775807L;
        } else {
            if (this.lastSampleTimestampUs != -9223372036854775807L) {
                long lastPts = usToPts(this.lastSampleTimestampUs);
                long closestWrapCount = (lastPts + 4294967296L) / 8589934592L;
                long ptsWrapBelow = pts90Khz + 8589934592L * (closestWrapCount - 1L);
                long ptsWrapAbove = pts90Khz + 8589934592L * closestWrapCount;
                pts90Khz = Math.abs(ptsWrapBelow - lastPts) < Math.abs(ptsWrapAbove - lastPts) ? ptsWrapBelow : ptsWrapAbove;
            }

            return this.adjustSampleTimestamp(ptsToUs(pts90Khz));
        }
    }

    public long adjustSampleTimestamp(long timeUs) {
        if (timeUs == -9223372036854775807L) {
            return -9223372036854775807L;
        } else {
            if (this.lastSampleTimestampUs != -9223372036854775807L) {
                this.lastSampleTimestampUs = timeUs;
            } else {
                if (this.firstSampleTimestampUs != 9223372036854775807L) {
                    this.timestampOffsetUs = this.firstSampleTimestampUs - timeUs;
                }

                synchronized(this) {
                    this.lastSampleTimestampUs = timeUs;
                    this.notifyAll();
                }
            }

            return timeUs + this.timestampOffsetUs;
        }
    }

    public synchronized void waitUntilInitialized() throws InterruptedException {
        while(this.lastSampleTimestampUs == -9223372036854775807L) {
            this.wait();
        }

    }

    public static long ptsToUs(long pts) {
        return pts * 1000000L / 90000L;
    }

    public static long usToPts(long us) {
        return us * 90000L / 1000000L;
    }
}
