package com.zj.playerLib.util;

public final class TimestampAdjuster {
    public static final long DO_NOT_OFFSET = Long.MAX_VALUE;
    private static final long MAX_PTS_PLUS_ONE = 8589934592L;
    private long firstSampleTimestampUs;
    private long timestampOffsetUs;
    private volatile long lastSampleTimestampUs = -Long.MAX_VALUE;

    public TimestampAdjuster(long firstSampleTimestampUs) {
        this.setFirstSampleTimestampUs(firstSampleTimestampUs);
    }

    public synchronized void setFirstSampleTimestampUs(long firstSampleTimestampUs) {
        Assertions.checkState(this.lastSampleTimestampUs == -Long.MAX_VALUE);
        this.firstSampleTimestampUs = firstSampleTimestampUs;
    }

    public long getFirstSampleTimestampUs() {
        return this.firstSampleTimestampUs;
    }

    public long getLastAdjustedTimestampUs() {
        return this.lastSampleTimestampUs != -Long.MAX_VALUE ? this.lastSampleTimestampUs + this.timestampOffsetUs : (this.firstSampleTimestampUs != Long.MAX_VALUE ? this.firstSampleTimestampUs : -Long.MAX_VALUE);
    }

    public long getTimestampOffsetUs() {
        return this.firstSampleTimestampUs == Long.MAX_VALUE ? 0L : (this.lastSampleTimestampUs == -Long.MAX_VALUE ? -Long.MAX_VALUE : this.timestampOffsetUs);
    }

    public void reset() {
        this.lastSampleTimestampUs = -Long.MAX_VALUE;
    }

    public long adjustTsTimestamp(long pts90Khz) {
        if (pts90Khz == -Long.MAX_VALUE) {
            return -Long.MAX_VALUE;
        } else {
            if (this.lastSampleTimestampUs != -Long.MAX_VALUE) {
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
        if (timeUs == -Long.MAX_VALUE) {
            return -Long.MAX_VALUE;
        } else {
            if (this.lastSampleTimestampUs != -Long.MAX_VALUE) {
                this.lastSampleTimestampUs = timeUs;
            } else {
                if (this.firstSampleTimestampUs != Long.MAX_VALUE) {
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
        while(this.lastSampleTimestampUs == -Long.MAX_VALUE) {
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
