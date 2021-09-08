//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.metadata.scte35;

import android.os.Parcel;

import com.zj.playerLib.util.ParsableByteArray;
import com.zj.playerLib.util.TimestampAdjuster;

public final class TimeSignalCommand extends SpliceCommand {
    public final long ptsTime;
    public final long playbackPositionUs;
    public static final Creator<TimeSignalCommand> CREATOR = new Creator<TimeSignalCommand>() {
        public TimeSignalCommand createFromParcel(Parcel in) {
            return new TimeSignalCommand(in.readLong(), in.readLong());
        }

        public TimeSignalCommand[] newArray(int size) {
            return new TimeSignalCommand[size];
        }
    };

    private TimeSignalCommand(long ptsTime, long playbackPositionUs) {
        this.ptsTime = ptsTime;
        this.playbackPositionUs = playbackPositionUs;
    }

    static TimeSignalCommand parseFromSection(ParsableByteArray sectionData, long ptsAdjustment, TimestampAdjuster timestampAdjuster) {
        long ptsTime = parseSpliceTime(sectionData, ptsAdjustment);
        long playbackPositionUs = timestampAdjuster.adjustTsTimestamp(ptsTime);
        return new TimeSignalCommand(ptsTime, playbackPositionUs);
    }

    static long parseSpliceTime(ParsableByteArray sectionData, long ptsAdjustment) {
        long firstByte = (long)sectionData.readUnsignedByte();
        long ptsTime = -9223372036854775807L;
        if ((firstByte & 128L) != 0L) {
            ptsTime = (firstByte & 1L) << 32 | sectionData.readUnsignedInt();
            ptsTime += ptsAdjustment;
            ptsTime &= 8589934591L;
        }

        return ptsTime;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(this.ptsTime);
        dest.writeLong(this.playbackPositionUs);
    }
}
