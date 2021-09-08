//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.extractor.mp3;

import androidx.annotation.Nullable;

import com.zj.playerLib.extractor.MpegAudioHeader;
import com.zj.playerLib.extractor.SeekPoint;
import com.zj.playerLib.extractor.mp3.Mp3Extractor.Seeker;
import com.zj.playerLib.util.Log;
import com.zj.playerLib.util.ParsableByteArray;
import com.zj.playerLib.util.Util;

final class VbriSeeker implements Seeker {
    private static final String TAG = "VbriSeeker";
    private final long[] timesUs;
    private final long[] positions;
    private final long durationUs;
    private final long dataEndPosition;

    @Nullable
    public static VbriSeeker create(long inputLength, long position, MpegAudioHeader mpegAudioHeader, ParsableByteArray frame) {
        frame.skipBytes(10);
        int numFrames = frame.readInt();
        if (numFrames <= 0) {
            return null;
        } else {
            int sampleRate = mpegAudioHeader.sampleRate;
            long durationUs = Util.scaleLargeTimestamp((long)numFrames, 1000000L * (long)(sampleRate >= 32000 ? 1152 : 576), (long)sampleRate);
            int entryCount = frame.readUnsignedShort();
            int scale = frame.readUnsignedShort();
            int entrySize = frame.readUnsignedShort();
            frame.skipBytes(2);
            long minPosition = position + (long)mpegAudioHeader.frameSize;
            long[] timesUs = new long[entryCount];
            long[] positions = new long[entryCount];

            for(int index = 0; index < entryCount; ++index) {
                timesUs[index] = (long)index * durationUs / (long)entryCount;
                positions[index] = Math.max(position, minPosition);
                int segmentSize;
                switch(entrySize) {
                case 1:
                    segmentSize = frame.readUnsignedByte();
                    break;
                case 2:
                    segmentSize = frame.readUnsignedShort();
                    break;
                case 3:
                    segmentSize = frame.readUnsignedInt24();
                    break;
                case 4:
                    segmentSize = frame.readUnsignedIntToInt();
                    break;
                default:
                    return null;
                }

                position += (long)(segmentSize * scale);
            }

            if (inputLength != -1L && inputLength != position) {
                Log.w("VbriSeeker", "VBRI data size mismatch: " + inputLength + ", " + position);
            }

            return new VbriSeeker(timesUs, positions, durationUs, position);
        }
    }

    private VbriSeeker(long[] timesUs, long[] positions, long durationUs, long dataEndPosition) {
        this.timesUs = timesUs;
        this.positions = positions;
        this.durationUs = durationUs;
        this.dataEndPosition = dataEndPosition;
    }

    public boolean isSeekable() {
        return true;
    }

    public SeekPoints getSeekPoints(long timeUs) {
        int tableIndex = Util.binarySearchFloor(this.timesUs, timeUs, true, true);
        SeekPoint seekPoint = new SeekPoint(this.timesUs[tableIndex], this.positions[tableIndex]);
        if (seekPoint.timeUs < timeUs && tableIndex != this.timesUs.length - 1) {
            SeekPoint nextSeekPoint = new SeekPoint(this.timesUs[tableIndex + 1], this.positions[tableIndex + 1]);
            return new SeekPoints(seekPoint, nextSeekPoint);
        } else {
            return new SeekPoints(seekPoint);
        }
    }

    public long getTimeUs(long position) {
        return this.timesUs[Util.binarySearchFloor(this.positions, position, true, true)];
    }

    public long getDurationUs() {
        return this.durationUs;
    }

    public long getDataEndPosition() {
        return this.dataEndPosition;
    }
}
