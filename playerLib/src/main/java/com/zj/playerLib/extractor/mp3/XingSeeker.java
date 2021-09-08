//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.extractor.mp3;

import androidx.annotation.Nullable;

import com.zj.playerLib.extractor.MpegAudioHeader;
import com.zj.playerLib.extractor.SeekPoint;
import com.zj.playerLib.extractor.mp3.Mp3Extractor.Seeker;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.Log;
import com.zj.playerLib.util.ParsableByteArray;
import com.zj.playerLib.util.Util;

final class XingSeeker implements Seeker {
    private static final String TAG = "XingSeeker";
    private final long dataStartPosition;
    private final int xingFrameSize;
    private final long durationUs;
    private final long dataSize;
    private final long dataEndPosition;
    @Nullable
    private final long[] tableOfContents;

    @Nullable
    public static XingSeeker create(long inputLength, long position, MpegAudioHeader mpegAudioHeader, ParsableByteArray frame) {
        int samplesPerFrame = mpegAudioHeader.samplesPerFrame;
        int sampleRate = mpegAudioHeader.sampleRate;
        int flags = frame.readInt();
        int frameCount;
        if ((flags & 1) == 1 && (frameCount = frame.readUnsignedIntToInt()) != 0) {
            long durationUs = Util.scaleLargeTimestamp((long)frameCount, (long)samplesPerFrame * 1000000L, (long)sampleRate);
            if ((flags & 6) != 6) {
                return new XingSeeker(position, mpegAudioHeader.frameSize, durationUs);
            } else {
                long dataSize = (long)frame.readUnsignedIntToInt();
                long[] tableOfContents = new long[100];

                for(int i = 0; i < 100; ++i) {
                    tableOfContents[i] = (long)frame.readUnsignedByte();
                }

                if (inputLength != -1L && inputLength != position + dataSize) {
                    Log.w("XingSeeker", "XING data size mismatch: " + inputLength + ", " + (position + dataSize));
                }

                return new XingSeeker(position, mpegAudioHeader.frameSize, durationUs, dataSize, tableOfContents);
            }
        } else {
            return null;
        }
    }

    private XingSeeker(long dataStartPosition, int xingFrameSize, long durationUs) {
        this(dataStartPosition, xingFrameSize, durationUs, -1L, (long[])null);
    }

    private XingSeeker(long dataStartPosition, int xingFrameSize, long durationUs, long dataSize, @Nullable long[] tableOfContents) {
        this.dataStartPosition = dataStartPosition;
        this.xingFrameSize = xingFrameSize;
        this.durationUs = durationUs;
        this.tableOfContents = tableOfContents;
        this.dataSize = dataSize;
        this.dataEndPosition = dataSize == -1L ? -1L : dataStartPosition + dataSize;
    }

    public boolean isSeekable() {
        return this.tableOfContents != null;
    }

    public SeekPoints getSeekPoints(long timeUs) {
        if (!this.isSeekable()) {
            return new SeekPoints(new SeekPoint(0L, this.dataStartPosition + (long)this.xingFrameSize));
        } else {
            timeUs = Util.constrainValue(timeUs, 0L, this.durationUs);
            double percent = (double)timeUs * 100.0D / (double)this.durationUs;
            double scaledPosition;
            if (percent <= 0.0D) {
                scaledPosition = 0.0D;
            } else if (percent >= 100.0D) {
                scaledPosition = 256.0D;
            } else {
                int prevTableIndex = (int)percent;
                long[] tableOfContents = (long[])Assertions.checkNotNull(this.tableOfContents);
                double prevScaledPosition = (double)tableOfContents[prevTableIndex];
                double nextScaledPosition = prevTableIndex == 99 ? 256.0D : (double)tableOfContents[prevTableIndex + 1];
                double interpolateFraction = percent - (double)prevTableIndex;
                scaledPosition = prevScaledPosition + interpolateFraction * (nextScaledPosition - prevScaledPosition);
            }

            long positionOffset = Math.round(scaledPosition / 256.0D * (double)this.dataSize);
            positionOffset = Util.constrainValue(positionOffset, (long)this.xingFrameSize, this.dataSize - 1L);
            return new SeekPoints(new SeekPoint(timeUs, this.dataStartPosition + positionOffset));
        }
    }

    public long getTimeUs(long position) {
        long positionOffset = position - this.dataStartPosition;
        if (this.isSeekable() && positionOffset > (long)this.xingFrameSize) {
            long[] tableOfContents = (long[])Assertions.checkNotNull(this.tableOfContents);
            double scaledPosition = (double)positionOffset * 256.0D / (double)this.dataSize;
            int prevTableIndex = Util.binarySearchFloor(tableOfContents, (long)scaledPosition, true, true);
            long prevTimeUs = this.getTimeUsForTableIndex(prevTableIndex);
            long prevScaledPosition = tableOfContents[prevTableIndex];
            long nextTimeUs = this.getTimeUsForTableIndex(prevTableIndex + 1);
            long nextScaledPosition = prevTableIndex == 99 ? 256L : tableOfContents[prevTableIndex + 1];
            double interpolateFraction = prevScaledPosition == nextScaledPosition ? 0.0D : (scaledPosition - (double)prevScaledPosition) / (double)(nextScaledPosition - prevScaledPosition);
            return prevTimeUs + Math.round(interpolateFraction * (double)(nextTimeUs - prevTimeUs));
        } else {
            return 0L;
        }
    }

    public long getDurationUs() {
        return this.durationUs;
    }

    public long getDataEndPosition() {
        return this.dataEndPosition;
    }

    private long getTimeUsForTableIndex(int tableIndex) {
        return this.durationUs * (long)tableIndex / 100L;
    }
}
